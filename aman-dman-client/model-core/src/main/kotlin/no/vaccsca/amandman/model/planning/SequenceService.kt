package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class SequencingOptions(
    val minimumSeparationNm: Double,
    val sequencingHorizon: Duration,
)

object SequenceService {

    /**
     * Clears the current sequence, forcing a full rescheduling of all aircraft.
     * This is useful when the sequence needs to be recalculated from scratch.
     */
    fun reSchedule(currentSequence: List<SequencePlace>): List<SequencePlace> {
        return emptyList()
    }

    /**
     * Suggests a new scheduled time for an aircraft in the sequence.
     * Used for manually adjusting the sequence when necessary.
     * Marks the aircraft as manually assigned so it won't be automatically rescheduled.
     */
    fun suggestScheduledTime(
        currentSequence: List<SequencePlace>,
        callsign: String,
        suggestion: Instant,
        minimumSeparationNm: Double
    ): List<SequencePlace> {
        val oldIdx = currentSequence.indexOfFirst { it.item.id == callsign }
        if (oldIdx == -1) return currentSequence // Not found
        val oldPlace = currentSequence[oldIdx]
        val updatedPlaces = currentSequence.toMutableList()
        updatedPlaces.removeAt(oldIdx)

        // Find the new index where the aircraft should be inserted (by requested time)
        var insertIdx = updatedPlaces.indexOfFirst { it.scheduledTime > suggestion }
        if (insertIdx == -1) insertIdx = updatedPlaces.size

        val prev = updatedPlaces.getOrNull(insertIdx - 1)
        val newTime = if (prev == null) {
            suggestion
        } else {
            maxOf(suggestion, safeLandingTimeAfter(prev, oldPlace, minimumSeparationNm))
        }

        // Insert the moved aircraft at the new index, marked as manually assigned
        updatedPlaces.add(insertIdx, oldPlace.copy(scheduledTime = newTime, isManuallyAssigned = true))
        updatedPlaces.moveFollowersToSafeTimesAfter(insertIdx, minimumSeparationNm)

        return updatedPlaces
    }

    private fun MutableList<SequencePlace>.moveFollowersToSafeTimesAfter(
        leaderIndex: Int,
        minimumSeparationNm: Double,
    ) {
        for (i in (leaderIndex + 1) until size) {
            val follower = this[i]
            val safeTime = safeLandingTimeAfter(this[i - 1], follower, minimumSeparationNm)

            if (follower.scheduledTime < safeTime) {
                val scheduledTime = if (follower.isManuallyAssigned) {
                    safeTime
                } else {
                    maxOf(safeTime, follower.item.preferredTime)
                }
                this[i] = follower.copy(scheduledTime = scheduledTime)
            }
        }
    }

    /**
     * Removes an aircraft from the sequence and the sequencing horizon,
     * allowing it to be re-sequenced.
     */
    fun removeFromSequence(sequence: List<SequencePlace>, vararg callsigns: String): List<SequencePlace> =
        sequence.filter { it.item.id !in arrayOf(*callsigns) }

    /**
     * Check if an aircraft with the given wake turbulence category can be placed
     * in the sequence at the specified scheduled time. It can be placed if the separation
     * to the preceding aircraft is sufficient based on the wake turbulence category.
     * Succeeding aircraft are not considered in this check.
     *
     * TODO: also consider departures and runway closures.
     *
     * @param currentSequence The current sequence of aircraft.
     * @param timelineEvent The timeline event representing the aircraft to check.
     * @param requestedTime The requested scheduled time for the aircraft.
     * @param minimumSeparationNm The minimum separation to use for different runways.
     * @return True if the time slot is available, false otherwise.
     */
    fun isTimeSlotAvailable(
        currentSequence: List<SequencePlace>,
        candidate: SequenceCandidate,
        requestedTime: Instant,
        minimumSeparationNm: Double
    ): Boolean {

        val closestLeader = currentSequence
            .filter { it.scheduledTime <= requestedTime }
            .maxByOrNull { it.scheduledTime }

        if (closestLeader == null) {
            // No preceding aircraft, so the time slot is available
            return true
        }

        if (closestLeader.item.id == candidate.id) {
            // The aircraft cannot conflict with itself
            return true
        }

        // TODO: handle other than aircraft
        val safeLandingTime = calculateSafeLandingTime(
            referenceTime = closestLeader.scheduledTime,
            leader = closestLeader.item as AircraftSequenceCandidate,
            follower = candidate as AircraftSequenceCandidate,
            minimumSeparationNm = minimumSeparationNm
        )

        return requestedTime >= safeLandingTime
    }

    fun updateSequence(
        currentSequence: List<SequencePlace>,
        candidates: List<SequenceCandidate>,
        config: SequencingOptions
    ): List<SequencePlace> {
        val entries = buildSequenceEntries(currentSequence, candidates)
            .sortedWith(sequenceEntryComparator())

        return entries
            .moveEntriesBehindFrozenSlotsTheyCannotPrecede(config.minimumSeparationNm)
            .scheduleWithSpacing(config.minimumSeparationNm)
    }

    private data class SequenceEntry(
        val candidate: AircraftSequenceCandidate,
        val existingPlace: SequencePlace?,
        val originalIndex: Int?,
    ) {
        val desiredTime: Instant
            get() {
                val place = existingPlace
                return if (place?.isManuallyAssigned == true) place.scheduledTime else candidate.preferredTime
            }

        val frozenSlotTime: Instant?
            get() = if (candidate.isInFrozenSequenceWindow) existingPlace?.scheduledTime else null

        val isManuallyAssigned: Boolean
            get() = existingPlace?.isManuallyAssigned == true
    }

    private fun buildSequenceEntries(
        currentSequence: List<SequencePlace>,
        candidates: List<SequenceCandidate>,
    ): List<SequenceEntry> {
        val latestCandidatesById = candidates
            .filterIsInstance<AircraftSequenceCandidate>()
            .associateBy { it.id }

        val existingEntries = currentSequence.mapIndexedNotNull { index, place ->
            val updatedCandidate = latestCandidatesById[place.item.id] ?: return@mapIndexedNotNull null
            if (!updatedCandidate.isInSequencingWindow) return@mapIndexedNotNull null

            SequenceEntry(
                candidate = updatedCandidate,
                existingPlace = place,
                originalIndex = index,
            )
        }

        val existingIds = currentSequence.map { it.item.id }.toSet()
        val newEntries = latestCandidatesById.values
            .filter { it.id !in existingIds && it.isInSequencingWindow }
            .map { candidate ->
                SequenceEntry(
                    candidate = candidate,
                    existingPlace = null,
                    originalIndex = null,
                )
            }

        return existingEntries + newEntries
    }

    private fun sequenceEntryComparator(): Comparator<SequenceEntry> =
        compareBy<SequenceEntry> { it.desiredTime }
            .thenBy { it.originalIndex ?: Int.MAX_VALUE }
            .thenBy { it.candidate.id }

    private fun List<SequenceEntry>.moveEntriesBehindFrozenSlotsTheyCannotPrecede(
        minimumSeparationNm: Double,
    ): List<SequenceEntry> {
        val orderedEntries = toMutableList()
        var index = 0

        while (index < orderedEntries.size) {
            val entry = orderedEntries[index]
            val frozenSlotIndex = ((index + 1) until orderedEntries.size).lastOrNull { frozenIndex ->
                val frozenEntry = orderedEntries[frozenIndex]
                frozenEntry.candidate.isInFrozenSequenceWindow &&
                    !entry.canRemainBeforeFrozenSlot(frozenEntry, minimumSeparationNm)
            }

            if (frozenSlotIndex == null) {
                index++
            } else {
                orderedEntries.removeAt(index)
                orderedEntries.add(frozenSlotIndex, entry)
            }
        }

        return orderedEntries
    }

    private fun SequenceEntry.canRemainBeforeFrozenSlot(
        frozenEntry: SequenceEntry,
        minimumSeparationNm: Double,
    ): Boolean {
        if (candidate.isInFrozenSequenceWindow) {
            return canFrozenAircraftRemainBefore(frozenEntry)
        }

        val frozenSlotTime = frozenEntry.frozenSlotTime ?: return true
        val currentScheduledTime = existingPlace?.scheduledTime ?: return false
        val earliestFrozenTime = calculateSafeLandingTime(
            referenceTime = currentScheduledTime,
            leader = candidate,
            follower = frozenEntry.candidate,
            minimumSeparationNm = minimumSeparationNm,
        )

        return frozenSlotTime >= earliestFrozenTime
    }

    private fun SequenceEntry.canFrozenAircraftRemainBefore(frozenEntry: SequenceEntry): Boolean {
        val currentOrder = originalIndex
        val frozenOrder = frozenEntry.originalIndex

        return when {
            currentOrder != null && frozenOrder != null -> currentOrder < frozenOrder
            currentOrder == null && frozenOrder != null -> false
            else -> true
        }
    }

    private fun List<SequenceEntry>.scheduleWithSpacing(minimumSeparationNm: Double): List<SequencePlace> {
        val places = mutableListOf<SequencePlace>()

        for (entry in this) {
            val leader = places.lastOrNull()
            val earliestTime = if (leader == null) {
                entry.desiredTime
            } else {
                val safeTime = calculateSafeLandingTime(
                    referenceTime = leader.scheduledTime,
                    leader = leader.item as AircraftSequenceCandidate,
                    follower = entry.candidate,
                    minimumSeparationNm = minimumSeparationNm,
                )
                maxOf(entry.desiredTime, safeTime)
            }

            places.add(
                SequencePlace(
                    item = entry.candidate,
                    scheduledTime = earliestTime,
                    isManuallyAssigned = entry.isManuallyAssigned,
                )
            )
        }

        return places
    }

    private fun safeLandingTimeAfter(
        leader: SequencePlace,
        follower: SequencePlace,
        minimumSeparationNm: Double,
    ): Instant =
        calculateSafeLandingTime(
            referenceTime = leader.scheduledTime,
            leader = leader.item as AircraftSequenceCandidate,
            follower = follower.item as AircraftSequenceCandidate,
            minimumSeparationNm = minimumSeparationNm,
        )

    /**
     * Calculates the final scheduled time for an aircraft based on runway assignment and wake turbulence category.
     * Uses minimum separation for different runways, wake category spacing for same runway.
     *
     * @param referenceTime The scheduled time of the preceding aircraft in the sequence.
     * @param leader The preceding aircraft in the sequence.
     * @param follower The aircraft for which the final time is being calculated.
     * @param minimumSeparationNm The minimum separation to use for different runways.
     */
    private fun calculateSafeLandingTime(
        referenceTime: Instant,
        leader: AircraftSequenceCandidate,
        follower: AircraftSequenceCandidate,
        minimumSeparationNm: Double
    ): Instant {
        val effectiveSpacingNm = if (areOnDifferentRunways(leader, follower)) {
            // Use minimum separation for aircraft on different runways
            minimumSeparationNm
        } else {
            // Use wake category spacing for aircraft on same runway
            val wakeSpacingNm = nmSpacingMap[Pair(leader.wakeCategory, follower.wakeCategory)] ?: 3.0
            maxOf(wakeSpacingNm, minimumSeparationNm)
        }

        val requiredSpacing = nmToDuration(effectiveSpacingNm, follower.landingIas)
        return referenceTime + requiredSpacing
    }

    /**
     * Checks if two aircraft are assigned to different runways.
     * Returns true if they have different non-null runway assignments, false otherwise.
     */
    private fun areOnDifferentRunways(aircraft1: AircraftSequenceCandidate, aircraft2: AircraftSequenceCandidate): Boolean {
        val runway1 = aircraft1.runway
        val runway2 = aircraft2.runway

        // If either aircraft doesn't have a runway assignment, treat as same runway (use wake spacing)
        if (runway1 == null || runway2 == null) {
            return false
        }

        // Return true if runways are different
        return runway1 != runway2
    }

    private val nmSpacingMap = mapOf(
        // Leader <> Follower
        Pair('H', 'H') to 4.0,
        Pair('H', 'M') to 5.0,
        Pair('H', 'L') to 6.0,
        Pair('M', 'L') to 5.0,
        Pair('J', 'H') to 6.0,
        Pair('J', 'M') to 7.0,
        Pair('J', 'L') to 8.0,
    )

    private fun nmToDuration(distanceNm: Double, groundSpeedKt: Int): Duration {
        val hours = distanceNm / groundSpeedKt
        return (hours * 3600).seconds
    }

}
