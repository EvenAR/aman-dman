package no.vaccsca.amandman.model

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.planning.SequenceService
import no.vaccsca.amandman.model.planning.SequencingOptions
import no.vaccsca.amandman.model.planning.AircraftSequenceCandidate
import no.vaccsca.amandman.model.planning.SequencePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SequenceServiceTest {

    private val defaultConfig = SequencingOptions(
        minimumSeparationNm = 3.0,
        sequencingHorizon = 30.minutes,
    )

    @Test
    fun `Aircraft entering AAH should be added to sequence`() {
        val sequence: List<SequencePlace> = emptyList()
        val now = NtpClock.now()

        val aircraft = makeSequenceCandidate(
            callsign = "TEST123",
            preferredTime = now + 15.minutes // Within AAH (30 min threshold)
        )

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(aircraft), defaultConfig)

        assertEquals(1, updatedSequence.size)
        assertEquals("TEST123", updatedSequence[0].item.id)
    }

    @Test
    fun `Aircraft outside sequencing horizon should not be added to sequence`() {
        val sequence: List<SequencePlace> = emptyList()
        val now = NtpClock.now()

        val aircraft = makeSequenceCandidate(
            callsign = "TEST123",
            preferredTime = now + 35.minutes, // Outside sequencing horizon (30 min threshold)
            isInSequencingWindow = false,
        )

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(aircraft), defaultConfig)

        assertEquals(0, updatedSequence.size)
    }

    @Test
    fun `Aircraft with no conflicts should keep preferred time`() {
        val sequence: List<SequencePlace> = emptyList()
        val now = NtpClock.now()

        val aircraft = makeSequenceCandidate(
            callsign = "TEST123",
            preferredTime = now + 15.minutes
        )

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(aircraft), defaultConfig)

        // Should keep preferred time since there are no conflicts
        assertEquals(aircraft.preferredTime, updatedSequence[0].scheduledTime)
    }

    @Test
    fun `Aircraft should get delayed scheduled time when spacing conflict exists`() {
        val now = NtpClock.now()

        // First aircraft already in sequence
        val firstAircraft = makeSequenceCandidate(
            callsign = "FIRST",
            preferredTime = now + 10.minutes,
            wakeCategory = 'H'
        )


        // Second aircraft wants to land too close behind heavy aircraft
        val secondAircraft = makeSequenceCandidate(
            callsign = "SECOND",
            preferredTime = firstAircraft.preferredTime + 30.seconds, // Too close behind Heavy
            wakeCategory = 'M'
        )

        val initialSequence = listOf(SequencePlace(firstAircraft, firstAircraft.preferredTime, false))
        val updatedSequence = SequenceService.updateSequence(initialSequence, listOf(firstAircraft, secondAircraft), defaultConfig)

        assertEquals(2, updatedSequence.size)
        val secondPlace = updatedSequence.find { it.item.id == "SECOND" }!!

        // Should be delayed due to H->M spacing requirement (5nm)
        assertTrue(secondPlace.scheduledTime > secondAircraft.preferredTime)
    }

    @Test
    fun `Wake category spacing should be correctly applied`() {
        val now = NtpClock.now()
        val sequence: List<SequencePlace> = emptyList()

        val heavy = makeSequenceCandidate("HEAVY", now + 10.minutes, wakeCategory = 'H')
        val medium = makeSequenceCandidate("MEDIUM", now + 10.minutes + 1.seconds, wakeCategory = 'M')
        val light = makeSequenceCandidate("LIGHT", now + 10.minutes + 2.seconds, wakeCategory = 'L')

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(heavy, medium, light), defaultConfig)

        assertEquals(3, updatedSequence.size)

        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }
        val heavyPlace = sortedPlaces[0]
        val mediumPlace = sortedPlaces[1]
        val lightPlace = sortedPlaces[2]

        // H->M spacing should be 5nm, H->L should be 6nm
        val heavyToMediumSpacing = mediumPlace.scheduledTime - heavyPlace.scheduledTime
        val heavyToLightSpacing = lightPlace.scheduledTime - heavyPlace.scheduledTime

        // Convert 5nm and 6nm to time at 150kt
        val expectedHMSpacing = (5.0 / 150 * 3600).seconds
        val expectedHLSpacing = (6.0 / 150 * 3600).seconds

        assertTrue(heavyToMediumSpacing >= expectedHMSpacing)
        assertTrue(heavyToLightSpacing >= expectedHLSpacing)
    }

    @Test
    fun `Existing aircraft should be scheduled to land at preferred time when no conflict exists`() {
        val now = NtpClock.now()
        val preferredTime = now + 15.minutes
        val scheduledTime = now + 12.minutes

        val sequenceCandidate = makeSequenceCandidate("TEST123", preferredTime)
        val initialSequence = listOf(SequencePlace(sequenceCandidate, scheduledTime, false))
        val updatedSequence = SequenceService.updateSequence(initialSequence, listOf(sequenceCandidate), defaultConfig)

        assertEquals(1, updatedSequence.size)
        assertEquals(preferredTime, updatedSequence[0].scheduledTime)
    }

    @Test
    fun `Manually assigned aircraft should preserve their scheduled times`() {
        val now = NtpClock.now()
        val manualTime = now + 8.minutes

        val sequenceCandidate = makeSequenceCandidate("MANUAL123", now + 15.minutes)
        val initialSequence = listOf(SequencePlace(sequenceCandidate, manualTime, isManuallyAssigned = true))
        val updatedSequence = SequenceService.updateSequence(initialSequence, listOf(sequenceCandidate), defaultConfig)

        assertEquals(1, updatedSequence.size)
        assertEquals(manualTime, updatedSequence[0].scheduledTime)
        assertTrue(updatedSequence[0].isManuallyAssigned)
    }

    @Test
    fun `Manual movement should adjust following aircraft when spacing conflict occurs`() {
        val now = NtpClock.now()

        val aircraft1 = makeSequenceCandidate("FIRST", now + 10.minutes, wakeCategory = 'M')
        val aircraft2 = makeSequenceCandidate("SECOND", aircraft1.preferredTime + 10.minutes, wakeCategory = 'L')

        val sequencePlace1 = SequencePlace(aircraft1, scheduledTime = aircraft1.preferredTime, isManuallyAssigned = false)
        val sequencePlace2 = SequencePlace(aircraft2, scheduledTime = aircraft2.preferredTime, isManuallyAssigned = false)
        val initialSequence = listOf(sequencePlace1,sequencePlace2)

        // Manually move FIRST to 1 minute before SECOND
        val updatedSequence = SequenceService.suggestScheduledTime(
            initialSequence, "FIRST", sequencePlace2.scheduledTime - 1.minutes, 3.0
        )

        val firstPlace = updatedSequence[0]
        val secondPlace = updatedSequence[1]

        // Verify correct order
        assertEquals("FIRST", firstPlace.item.id)
        assertEquals("SECOND", secondPlace.item.id)

        assertTrue(firstPlace.isManuallyAssigned)

        // Check that spacing is maintained (M->L requires 5nm spacing)
        val spacing = secondPlace.scheduledTime - firstPlace.scheduledTime
        val requiredSpacing = (5.0 / 150 * 3600).seconds
        assertTrue(spacing >= requiredSpacing)
    }

    @Test
    fun `Aircraft should return to preferred time when conflict is resolved`() {
        val now = NtpClock.now()

        // Aircraft are initially too close
        val aircraft1 = makeSequenceCandidate("FIRST", now + 10.minutes)
        val aircraft2 = makeSequenceCandidate("SECOND", aircraft1.preferredTime + 30.seconds)

        // When inserted to sequence
        val initialSequence = SequenceService.updateSequence(emptyList(), listOf(aircraft1, aircraft2), defaultConfig)
        val aircraft2SequencePlace = initialSequence.find { it.item.id == "SECOND" }!!

        // SECOND should be delayed due to conflict
        assertTrue(aircraft2SequencePlace.scheduledTime > aircraft2.preferredTime)

        // FIRST gets a shortcut time, removing conflict
        val updatedAircraft1 = aircraft1.copy(preferredTime = aircraft1.preferredTime - 5.minutes)
        val finalSequence = SequenceService.updateSequence(
            initialSequence,
            listOf(updatedAircraft1, aircraft2),
            defaultConfig
        )

        val aircraft2NewSequencePlace = finalSequence.find { it.item.id == "SECOND" }!!

        // SECOND should now be able to use its preferred time
        assertEquals(aircraft2.preferredTime, aircraft2NewSequencePlace.scheduledTime)
    }

    @Test
    fun `Aircraft in frozen area should maintain their order`() {
        val now = NtpClock.now()

        val frozenAircraft = makeSequenceCandidate(callsign = "FROZEN", preferredTime = now + 5.minutes, isInFrozenSequenceWindow = true)
        val initialSequence = SequenceService.updateSequence(currentSequence = emptyList(), candidates = listOf(frozenAircraft), defaultConfig)

        // New aircraft wants to land earlier but should be placed after frozen aircraft
        val newFasterAircraft = makeSequenceCandidate(callsign = "NEW", preferredTime = frozenAircraft.preferredTime - 3.minutes)
        val updatedSequence = SequenceService.updateSequence(currentSequence = initialSequence,candidates = listOf(frozenAircraft, newFasterAircraft), defaultConfig)

        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }
        assertEquals("FROZEN", sortedPlaces[0].item.id)
        assertEquals("NEW", sortedPlaces[1].item.id)

        // New aircraft should be placed after frozen aircraft with proper spacing
        assertTrue(sortedPlaces[1].scheduledTime > sortedPlaces[0].scheduledTime)
    }

    @Test
    fun `Existing aircraft should maintain relative order when possible`() {
        val now = NtpClock.now()

        // Non-conflicting preferred times
        val aircraft1 = makeSequenceCandidate("FIRST", now + 10.minutes)
        val aircraft2 = makeSequenceCandidate("SECOND", now + 15.minutes)
        val aircraft3 = makeSequenceCandidate("THIRD", now + 20.minutes)

        val sequence =
            listOf(
                SequencePlace(aircraft1, now + 10.minutes, false),
                SequencePlace(aircraft2, now + 15.minutes, false),
                SequencePlace(aircraft3, now + 20.minutes, false)
            )

        // Update with same aircraft - order should be preserved
        val updatedSequence = SequenceService.updateSequence(
            sequence,
            listOf(aircraft1, aircraft2, aircraft3),
            defaultConfig
        )

        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }
        assertEquals("FIRST", sortedPlaces[0].item.id)
        assertEquals("SECOND", sortedPlaces[1].item.id)
        assertEquals("THIRD", sortedPlaces[2].item.id)
    }

    @Test
    fun `Should be able to calculate TTL when aircraft is delayed`() {
        val now = NtpClock.now()

        val aircraft1 = makeSequenceCandidate("LEADER", now + 10.minutes, wakeCategory = 'H')
        val aircraft2 = makeSequenceCandidate("FOLLOWER", aircraft1.preferredTime + 30.seconds, wakeCategory = 'L')

        val sequence = SequenceService.updateSequence(
            emptyList(),
            listOf(aircraft1, aircraft2),
            defaultConfig
        )

        val followerPlace = sequence.find { it.item.id == "FOLLOWER" }!!
        val ttl = followerPlace.scheduledTime - followerPlace.item.preferredTime

        // TTL should be positive (aircraft is delayed)
        assertTrue(ttl > 0.seconds)

        // TTL should reflect the wake turbulence spacing requirement
        val expectedMinSpacing = (6.0 / 150 * 3600).seconds // H->L = 6nm
        assertTrue(ttl >= expectedMinSpacing - 30.seconds) // Minus the initial 30s gap
    }

    @Test
    fun `Should remove aircraft from sequence`() {
        val now = NtpClock.now()

        val aircraft1 = makeSequenceCandidate("KEEP", now + 10.minutes)
        val aircraft2 = makeSequenceCandidate("REMOVE", now + 15.minutes)

        val sequence =
            listOf(
                SequencePlace(aircraft1, now + 10.minutes, false),
                SequencePlace(aircraft2, now + 15.minutes, false)
            )

        val updatedSequence = SequenceService.removeFromSequence(sequence, "REMOVE")

        assertEquals(1, updatedSequence.size)
        assertEquals("KEEP", updatedSequence[0].item.id)
    }

    @Test
    fun `Multiple aircraft entering sequencing are should be properly spaced`() {
        val now = NtpClock.now()

        // Create aircraft with slightly different preferred times to ensure deterministic ordering
        val aircraft1 = makeSequenceCandidate(callsign = "FIRST", preferredTime = now + 10.minutes, wakeCategory = 'H')
        val aircraft2 = makeSequenceCandidate(callsign = "SECOND", preferredTime = aircraft1.preferredTime + 1.seconds, wakeCategory = 'M')
        val aircraft3 = makeSequenceCandidate(callsign = "THIRD", preferredTime = aircraft2.preferredTime + 1.seconds, wakeCategory = 'L')

        val updatedSequence = SequenceService.updateSequence(
            currentSequence = emptyList(),
            candidates = listOf(aircraft1, aircraft2, aircraft3),
            defaultConfig
        )

        assertEquals(3, updatedSequence.size)

        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }

        // Verify that the aircraft are in the expected order based on their preferred times
        assertEquals("FIRST", sortedPlaces[0].item.id)
        assertEquals("SECOND", sortedPlaces[1].item.id)
        assertEquals("THIRD", sortedPlaces[2].item.id)

        // Check that proper spacing is maintained between consecutive aircraft
        val spacing12 = sortedPlaces[1].scheduledTime - sortedPlaces[0].scheduledTime
        val spacing23 = sortedPlaces[2].scheduledTime - sortedPlaces[1].scheduledTime

        // Verify that each aircraft is properly spaced from the previous one
        // The actual spacing will depend on the wake category combinations and implementation
        assertTrue(spacing12 > 0.seconds, "SECOND should be scheduled after FIRST")
        assertTrue(spacing23 > 0.seconds, "THIRD should be scheduled after SECOND")

        // Verify that the total sequence maintains proper ordering and spacing
        assertTrue(sortedPlaces[0].scheduledTime < sortedPlaces[1].scheduledTime)
        assertTrue(sortedPlaces[1].scheduledTime < sortedPlaces[2].scheduledTime)
    }

    @Test
    fun `Aircraft on different runways should use minimum separation instead of wake spacing`() {
        val now = NtpClock.now()
        val sequence: List<SequencePlace> = emptyList()

        // Heavy aircraft on runway 09L
        val heavy = makeSequenceCandidate("HEAVY", now + 10.minutes, wakeCategory = 'H', assignedRunway = "09L")
        // Light aircraft on runway 09R (different runway)
        val light = makeSequenceCandidate("LIGHT", heavy.preferredTime + 30.seconds, wakeCategory = 'L', assignedRunway = "09R")

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(heavy, light), defaultConfig)

        assertEquals(2, updatedSequence.size)
        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }

        val spacing = sortedPlaces[1].scheduledTime - sortedPlaces[0].scheduledTime

        // With different runways, should use minimum separation (3.0nm) instead of wake spacing (6.0nm for H->L)
        val minimumSpacing = (3.0 / 150 * 3600).seconds
        val wakeSpacing = (6.0 / 150 * 3600).seconds

        // Spacing should be close to minimum separation, not wake separation
        assertTrue(spacing >= minimumSpacing, "Should use minimum separation for different runways")
        assertTrue(spacing < wakeSpacing, "Should not use wake spacing for different runways")
    }

    @Test
    fun `Aircraft on same runway should use wake category spacing`() {
        val now = NtpClock.now()
        val sequence: List<SequencePlace> = emptyList()

        // Heavy and light aircraft both on runway 09L (same runway)
        val heavy = makeSequenceCandidate(callsign = "HEAVY", preferredTime = now + 10.minutes, wakeCategory = 'H', assignedRunway = "09L")
        val light = makeSequenceCandidate(callsign = "LIGHT", preferredTime = heavy.preferredTime + 30.seconds, wakeCategory = 'L', assignedRunway = "09L")

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(heavy, light), defaultConfig)

        assertEquals(2, updatedSequence.size)
        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }

        val spacing = sortedPlaces[1].scheduledTime - sortedPlaces[0].scheduledTime

        // With same runway, should use wake spacing (6.0nm for H->L)
        val wakeSpacing = (6.0 / 150 * 3600).seconds

        assertTrue(spacing >= wakeSpacing, "Should use wake category spacing for same runway")
    }

    @Test
    fun `Aircraft without runway assignment should use wake category spacing`() {
        val now = NtpClock.now()
        val sequence: List<SequencePlace> = emptyList()

        // Aircraft without runway assignments
        val heavy = makeSequenceCandidate(callsign = "HEAVY", preferredTime = now + 10.minutes, wakeCategory = 'H', assignedRunway = null)
        val light = makeSequenceCandidate(callsign = "LIGHT", preferredTime = heavy.preferredTime + 30.seconds, wakeCategory = 'L', assignedRunway = null)

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(heavy, light), defaultConfig)

        assertEquals(2, updatedSequence.size)
        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }

        val spacing = sortedPlaces[1].scheduledTime - sortedPlaces[0].scheduledTime

        // Without runway assignments, should use wake spacing (6.0nm for H->L)
        val wakeSpacing = (6.0 / 150 * 3600).seconds

        assertTrue(spacing >= wakeSpacing, "Should use wake category spacing when no runway assigned")
    }

    @Test
    fun `Mixed runway assignments should handle spacing correctly`() {
        val now = NtpClock.now()
        val sequence: List<SequencePlace> = emptyList()

        // First aircraft with runway assignment
        val first = makeSequenceCandidate(callsign = "FIRST", preferredTime = now + 10.minutes, wakeCategory = 'H', assignedRunway = "09L")
        // Second aircraft without runway assignment
        val second = makeSequenceCandidate(callsign = "SECOND", preferredTime = first.preferredTime + 30.seconds, wakeCategory = 'L', assignedRunway = null)

        val updatedSequence = SequenceService.updateSequence(sequence, listOf(first, second), defaultConfig)

        assertEquals(2, updatedSequence.size)
        val sortedPlaces = updatedSequence.sortedBy { it.scheduledTime }

        val spacing = sortedPlaces[1].scheduledTime - sortedPlaces[0].scheduledTime

        // When one aircraft has no runway assignment, should use wake spacing
        val wakeSpacing = (6.0 / 150 * 3600).seconds

        assertTrue(spacing >= wakeSpacing, "Should use wake category spacing when one aircraft has no runway")
    }

    // Test 20: Manual movement should respect runway-based spacing
    @Test
    fun `Manual movement should respect runway-based spacing rules`() {
        val now = NtpClock.now()

        val aircraft1 = makeSequenceCandidate(callsign = "FIRST", preferredTime = now + 10.minutes, wakeCategory = 'H', assignedRunway = "09L")
        val aircraft2 = makeSequenceCandidate(callsign = "SECOND", preferredTime = aircraft1.preferredTime + 10.minutes, wakeCategory = 'L', assignedRunway = "09R")

        val sequence =
            listOf(
                SequencePlace(aircraft1, scheduledTime = aircraft1.preferredTime, isManuallyAssigned = false),
                SequencePlace(aircraft2, scheduledTime = aircraft2.preferredTime, isManuallyAssigned = false)
            )

        // Manually move FIRST to a later time, creating potential conflict with SECOND
        val updatedSequence = SequenceService.suggestScheduledTime(
            sequence, callsign = "FIRST", suggestion = aircraft2.preferredTime - 1.minutes, 3.0
        )

        val firstPlace = updatedSequence[0]
        val secondPlace = updatedSequence[1]

        assertEquals("FIRST", firstPlace.item.id)
        assertEquals("SECOND", secondPlace.item.id)

        assertTrue(firstPlace.isManuallyAssigned)

        // Check that spacing uses minimum separation for different runways (3nm)
        val spacing = secondPlace.scheduledTime - firstPlace.scheduledTime
        val minimumSpacing = (3.0 / 150 * 3600).seconds

        assertTrue(spacing >= minimumSpacing, "Should use minimum separation for different runways in manual movement")
    }

    @Test
    fun `Frozen aircraft should not be overtaken by new arrivals entering frozen area`() {
        val now = NtpClock.now()

        val frozenCandidate = makeSequenceCandidate(callsign = "FROZEN", preferredTime = now + 12.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = true)
        val newFasterCandidate = makeSequenceCandidate(callsign = "NEW", preferredTime = frozenCandidate.preferredTime - 1.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = true)

        val initialSequence = listOf(SequencePlace(item = frozenCandidate, scheduledTime = frozenCandidate.preferredTime, isManuallyAssigned = false))

        val updatedSequence = SequenceService.updateSequence(
            currentSequence = initialSequence,
            candidates = listOf(frozenCandidate, newFasterCandidate),
            config = defaultConfig,
        )

        val firstPlace = updatedSequence[0]
        val secondPlace = updatedSequence[1]

        assertEquals("FROZEN", firstPlace.item.id)
        assertEquals("NEW", secondPlace.item.id)

        assertTrue(secondPlace.scheduledTime > firstPlace.scheduledTime)
    }

    @Test
    fun `Two aircraft entering frozen area at the same time should be ordered by preferred arrival time`() {
        val now = NtpClock.now()

        val fast = makeSequenceCandidate(callsign = "FASTEST", preferredTime = now + 12.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = true)
        val slow = makeSequenceCandidate(callsign = "SLOWEST", preferredTime = fast.preferredTime + 1.seconds, assignedRunway = "19L", isInFrozenSequenceWindow = true)

        val createdSequence = SequenceService.updateSequence(
            currentSequence = emptyList(),
            candidates = listOf(fast, slow),
            config = defaultConfig,
        )

        assertEquals("FASTEST", createdSequence[0].item.id)
        assertEquals("SLOWEST", createdSequence[1].item.id)

        val createdSequence2 = SequenceService.updateSequence(
            currentSequence = emptyList(),
            candidates = listOf(slow, fast),
            config = defaultConfig,
        )

        assertEquals("FASTEST", createdSequence2[0].item.id)
        assertEquals("SLOWEST", createdSequence2[1].item.id)
    }

    @Test
    fun `Two aircraft in frozen area should not change places`() {
        val now = NtpClock.now()

        val aircraft1 = makeSequenceCandidate(callsign = "FIRST", preferredTime = now + 12.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = true)
        val aircraft2 = makeSequenceCandidate(callsign = "SECOND", preferredTime = aircraft1.preferredTime + 1.seconds, assignedRunway = "19L", isInFrozenSequenceWindow = true)

        val initialSequence = SequenceService.updateSequence(
            currentSequence = emptyList(),
            candidates = listOf(aircraft1, aircraft2),
            config = defaultConfig,
        )

        assertEquals("FIRST", initialSequence[0].item.id)
        assertEquals("SECOND", initialSequence[1].item.id)

        val aircraft2overtaking1 = makeSequenceCandidate(callsign = "SECOND", preferredTime = aircraft1.preferredTime - 1.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = true)

        val finalSequence = SequenceService.updateSequence(
            currentSequence = initialSequence,
            candidates = listOf(aircraft1, aircraft2overtaking1),
            config = defaultConfig,
        )

        assertEquals("FIRST", finalSequence[0].item.id)
        assertEquals("SECOND", finalSequence[1].item.id)
    }

    @Test
    fun `Aircraft outside frozen area should never be placed in front of aircraft in frozen area, even if preferred time is earlier`() {
        val now = NtpClock.now()

        val frozen = makeSequenceCandidate(callsign = "FROZEN", preferredTime = now + 12.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = true)
        val newFaster = makeSequenceCandidate(callsign = "NEW", preferredTime = frozen.preferredTime - 1.minutes, assignedRunway = "19L", isInFrozenSequenceWindow = false)

        val initialSequence = SequenceService.updateSequence(
            currentSequence = emptyList(),
            candidates = listOf(frozen),
            config = defaultConfig,
        )

        val updatedSequence = SequenceService.updateSequence(
            currentSequence = initialSequence,
            candidates = listOf(newFaster, frozen),
            config = defaultConfig,
        )

        assertEquals("FROZEN", updatedSequence[0].item.id)
        assertEquals("NEW", updatedSequence[1].item.id)
    }

    @Test
    fun `When an aircraft enters the frozen area, the sequence remains unchanged if no aircraft is violating its position`() {
        val now = NtpClock.now()

        val firstAircraft = makeSequenceCandidate(
            callsign = "A",
            preferredTime = now + 10.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        val middleAircraft = makeSequenceCandidate(
            callsign = "B",
            preferredTime = now + 11.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        val lastAircraft = makeSequenceCandidate(
            callsign = "C",
            preferredTime = now + 12.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        // Initial sequencing
        val initialSequence = SequenceService.updateSequence(
            currentSequence = emptyList(),
            candidates = listOf(firstAircraft, middleAircraft, lastAircraft),
            config = defaultConfig,
        )

        // B enters frozen area
        val middleAircraftFrozen = middleAircraft.copy(isInFrozenSequenceWindow = true)

        val updatedSequence = SequenceService.updateSequence(
            currentSequence = initialSequence,
            candidates = listOf(firstAircraft, middleAircraftFrozen, lastAircraft),
            config = defaultConfig,
        )

        // ✅ Sequence should NOT change
        assertEquals("A", updatedSequence[0].item.id)
        assertEquals("B", updatedSequence[1].item.id)
        assertEquals("C", updatedSequence[2].item.id)
    }

    @Test
    fun `When an aircraft enters the frozen area, aircraft outside scheduled ahead must be re-sequenced behind it`() {
        val now = NtpClock.now()

        val aircraftA = makeSequenceCandidate(
            callsign = "A",
            preferredTime = now + 10.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        val aircraftB = makeSequenceCandidate(
            callsign = "B",
            preferredTime = now + 11.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        val aircraftC = makeSequenceCandidate(
            callsign = "C",
            preferredTime = now + 12.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        // Force violation: A is scheduled BEFORE B
        val initialSequence = listOf(
            SequencePlace(
                aircraftA,
                scheduledTime = aircraftB.preferredTime - 1.minutes // A before B → violation
            ),
            SequencePlace(
                aircraftB,
                scheduledTime = aircraftB.preferredTime
            ),
            SequencePlace(
                aircraftC,
                scheduledTime = aircraftC.preferredTime
            )
        )

        // B enters frozen area
        val aircraftBFrozen = aircraftB.copy(isInFrozenSequenceWindow = true)

        val updatedSequence = SequenceService.updateSequence(
            currentSequence = initialSequence,
            candidates = listOf(aircraftA, aircraftBFrozen, aircraftC),
            config = defaultConfig,
        )

        // ✅ B must stay ahead of A now
        assertEquals("B", updatedSequence[0].item.id)
        assertEquals("A", updatedSequence[1].item.id)
        assertEquals("C", updatedSequence[2].item.id)
    }

    @Test
    fun `When multiple aircraft are in the frozen area, outside aircraft cannot be inserted before or between them`() {
        val now = NtpClock.now()

        val aircraftA = makeSequenceCandidate(
            callsign = "A",
            preferredTime = now + 10.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = false
        )

        val aircraftB = makeSequenceCandidate(
            callsign = "B",
            preferredTime = now + 11.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = true
        )

        val aircraftC = makeSequenceCandidate(
            callsign = "C",
            preferredTime = now + 12.minutes,
            assignedRunway = "19L",
            isInFrozenSequenceWindow = true
        )

        // ❗ Force violation:
        // A is scheduled between B and C
        val initialSequence = listOf(
            SequencePlace(
                aircraftB,
                scheduledTime = now + 11.minutes
            ),
            SequencePlace(
                aircraftA,
                scheduledTime = now + 11.minutes + 30.seconds // between B and C
            ),
            SequencePlace(
                aircraftC,
                scheduledTime = now + 12.minutes
            )
        )

        val updatedSequence = SequenceService.updateSequence(
            currentSequence = initialSequence,
            candidates = listOf(aircraftA, aircraftB, aircraftC),
            config = defaultConfig,
        )

        // ✅ Frozen order must be preserved: B → C
        // ✅ A must be moved AFTER both
        assertEquals("B", updatedSequence[0].item.id)
        assertEquals("C", updatedSequence[1].item.id)
        assertEquals("A", updatedSequence[2].item.id)
    }

    // Helper function to create test aircraft sequence candidates
    private fun makeSequenceCandidate(
        callsign: String,
        preferredTime: Instant,
        landingIas: Int = 150,
        wakeCategory: Char = 'M',
        assignedRunway: String? = null,
        isInFrozenSequenceWindow: Boolean = false,
        isInSequencingWindow: Boolean = true,
    ) = AircraftSequenceCandidate(
        callsign = callsign,
        preferredTime = preferredTime,
        landingIas = landingIas,
        wakeCategory = wakeCategory,
        runway = assignedRunway,
        isInFrozenSequenceWindow = isInFrozenSequenceWindow,
        isInSequencingWindow = isInSequencingWindow,
    )
}
