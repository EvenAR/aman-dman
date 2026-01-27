package no.vaccsca.amandman.model.domain.service

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.domain.valueobjects.sequence.AircraftSequenceCandidate
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.sequence.Sequence
import no.vaccsca.amandman.model.domain.valueobjects.sequence.SequenceCandidate
import no.vaccsca.amandman.model.domain.valueobjects.sequence.SequencePlace
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object SequenceService {

    val SEQUENCING_HORIZON: Duration = 30.minutes // When aircraft enter active sequencing management
    val LOCKED_HORIZON: Duration = 10.minutes // When aircraft positions become locked/unchangeable

    /**
     * Clears the current sequence, forcing a full rescheduling of all aircraft.
     * This is useful when the sequence needs to be recalculated from scratch.
     */
    fun reSchedule(currentSequence: Sequence): Sequence {
        return Sequence(
            sequecencePlaces = emptyList()
        )
    }

    /**
     * Suggests a new scheduled time for an aircraft in the sequence.
     * Used for manually adjusting the sequence when necessary.
     * Marks the aircraft as manually assigned so it won't be automatically rescheduled.
     */
    fun suggestScheduledTime(
        currentSequence: Sequence,
        callsign: String,
        scheduledTime: Instant,
        minimumSeparationNm: Double,
        independentRunwaySystems: List<Set<String>>
    ): Sequence {
        val oldIdx = currentSequence.sequecencePlaces.indexOfFirst { it.item.id == callsign }
        if (oldIdx == -1) return currentSequence // Not found
        val oldPlace = currentSequence.sequecencePlaces[oldIdx]
        val updatedPlaces = currentSequence.sequecencePlaces.toMutableList()
        updatedPlaces.removeAt(oldIdx)

        // Find the new index where the aircraft should be inserted (by requested time)
        var insertIdx = updatedPlaces.indexOfFirst { it.scheduledTime > scheduledTime }
        if (insertIdx == -1) insertIdx = updatedPlaces.size

        var prevDependent: SequencePlace? = null
        for (i in (insertIdx - 1) downTo 0) {
            if (!areIndependent(updatedPlaces[i].item as AircraftSequenceCandidate,
                    oldPlace.item as AircraftSequenceCandidate, independentRunwaySystems)) {
                prevDependent = updatedPlaces[i]
                break
            }
        }
        var newTime = scheduledTime
        if (insertIdx > 0) {
            val immediatePrev = updatedPlaces[insertIdx - 1]
            if (newTime < immediatePrev.scheduledTime) newTime = immediatePrev.scheduledTime
        }

        if (prevDependent != null) {
            val minTime = calculateSafeLandingTime(
                referenceTime = prevDependent.scheduledTime,
                leader = prevDependent.item as AircraftSequenceCandidate,
                follower = oldPlace.item as AircraftSequenceCandidate,
                minimumSeparationNm = minimumSeparationNm,
                independentRunwaySystems = independentRunwaySystems
            )
            if (newTime < minTime) newTime = minTime
        }
        // Insert the moved aircraft at the new index, marked as manually assigned
        updatedPlaces.add(insertIdx, oldPlace.copy(scheduledTime = newTime, isManuallyAssigned = true))

        // Recalculate scheduled times for following aircraft, ensuring proper spacing
        for (i in (insertIdx + 1) until updatedPlaces.size) {
            val follower = updatedPlaces[i]
            val leader = updatedPlaces.subList(0, i).lastOrNull {
                !areIndependent(it.item as AircraftSequenceCandidate, follower.item as AircraftSequenceCandidate, independentRunwaySystems)
            }
            val minSafeTime = if (leader != null && leader.item is AircraftSequenceCandidate && follower.item is AircraftSequenceCandidate) {
                calculateSafeLandingTime(
                    referenceTime = leader.scheduledTime,
                    leader = leader.item, // Smart-cast to AircraftSequenceCandidate
                    follower = follower.item, // Smart-cast to AircraftSequenceCandidate
                    minimumSeparationNm = minimumSeparationNm,
                    independentRunwaySystems = independentRunwaySystems
                )
            } else {
                follower.item.preferredTime // Fallback if no leader or types don't match
            }

            val immediatePrevTime = updatedPlaces[i - 1].scheduledTime
            val minTime = maxOf(minSafeTime, immediatePrevTime)

            // Always move following aircraft if there's a spacing conflict, regardless of manual assignment
            if (follower.scheduledTime < minTime) {
                // For manually assigned aircraft, move them to the minimum safe time
                // For auto-scheduled aircraft, try to keep preferred time if possible
                val newScheduledTime = if (follower.isManuallyAssigned) {
                    minTime
                } else {
                    maxOf(minTime, follower.item.preferredTime)
                }
                updatedPlaces[i] = follower.copy(scheduledTime = newScheduledTime)
            }
        }
        return currentSequence.copy(sequecencePlaces = updatedPlaces)
    }

    /**
     * Removes an aircraft from the sequence and the sequencing horizon,
     * allowing it to be re-sequenced.
     */
    fun removeFromSequence(sequence: Sequence, vararg callsigns: String): Sequence =
        sequence.copy(
            sequecencePlaces = sequence.sequecencePlaces.filter { it.item.id !in arrayOf(*callsigns) }.toList()
        )

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
        currentSequence: Sequence,
        timelineEvent: TimelineEvent,
        requestedTime: Instant,
        minimumSeparationNm: Double,
        independentRunwaySystems: List<Set<String>>
    ): Boolean {
        if (timelineEvent !is RunwayArrivalEvent) return false

        val arrivalToCheck = findSequenceItem(currentSequence.sequecencePlaces.map { it.item }, timelineEvent.callsign)

        if (arrivalToCheck == null) {
            // The aircraft is not in the sequence yet
            return false
        }

        val closestLeader = currentSequence.sequecencePlaces
            .filter { it.scheduledTime <= requestedTime }
            .sortedByDescending { it.scheduledTime }
            .firstOrNull {
                !areIndependent(it.item as AircraftSequenceCandidate,
                    arrivalToCheck as AircraftSequenceCandidate, independentRunwaySystems)
            }

        if (closestLeader == null) {
            // No preceding aircraft, so the time slot is available
            return true
        }

        if (closestLeader.item.id == timelineEvent.callsign) {
            // The aircraft cannot conflict with itself
            return true
        }

        // TODO: handle other than aircraft
        val safeLandingTime = calculateSafeLandingTime(
            referenceTime = closestLeader.scheduledTime,
            leader = closestLeader.item as AircraftSequenceCandidate,
            follower = arrivalToCheck as AircraftSequenceCandidate,
            minimumSeparationNm = minimumSeparationNm,
            independentRunwaySystems = independentRunwaySystems
        )

        return requestedTime >= safeLandingTime
    }

    fun updateSequence(
        currentSequence: Sequence,
        candidates: List<SequenceCandidate>,
        minimumSeparationNm: Double,
        independentRunwaySystems: List<Set<String>>,
    ): Sequence {
        // Build a map of the latest candidate data by ID
        val latestCandidateData = candidates.associateBy { it.id }

        // Build a map of existing sequence places to preserve manual assignments and order
        val existingPlacesByCandidate = currentSequence.sequecencePlaces.associateBy { it.item.id }

        // Start with existing aircraft, but update them with latest candidate data
        val allCandidates = mutableListOf<SequenceCandidate>()

        // Add existing aircraft from sequence, but with updated candidate data if available
        currentSequence.sequecencePlaces.forEach { place ->
            val updatedCandidate = latestCandidateData[place.item.id] ?: place.item
            allCandidates.add(updatedCandidate)
        }

        // Add new candidates that are in sequencing horizon
        val existingIds = currentSequence.sequecencePlaces.map { it.item.id }.toSet()
        val newCandidates = candidates.filter { it.id !in existingIds && it.isInSequencingHorizon() }
        allCandidates.addAll(newCandidates)

        // Remove aircraft that are no longer in sequencing horizon or no longer candidates
        val activeCandidateIds = candidates.map { it.id }.toSet()
        val filteredCandidates = allCandidates.filter { candidate ->
            candidate.id in activeCandidateIds && candidate.isInSequencingHorizon()
        }

        // Separate manually assigned and automatically scheduled aircraft
        val manuallyAssignedCandidates = filteredCandidates.filter { candidate ->
            existingPlacesByCandidate[candidate.id]?.isManuallyAssigned == true
        }
        val autoScheduledCandidates = filteredCandidates.filter { candidate ->
            existingPlacesByCandidate[candidate.id]?.isManuallyAssigned != true
        }

        // For auto-scheduled aircraft, preserve their original sequence order rather than sorting by preferred time
        val existingAutoScheduled = autoScheduledCandidates.filter { candidate ->
            existingPlacesByCandidate.containsKey(candidate.id)
        }
        val newAutoScheduled = autoScheduledCandidates.filter { candidate ->
            !existingPlacesByCandidate.containsKey(candidate.id)
        }

        // Preserve original order for existing auto-scheduled aircraft
        val originalOrderMap = currentSequence.sequecencePlaces.mapIndexed { index, place -> place.item.id to index }.toMap()
        val sortedExistingAutoScheduled = existingAutoScheduled.sortedBy { originalOrderMap[it.id] ?: Int.MAX_VALUE }

        // Sort new aircraft by preferred time
        val sortedNewAutoScheduled = newAutoScheduled.sortedBy { it.preferredTime }

        // Build the new sequence by preserving the original relative order while respecting manual assignments
        // Create all sequence places first (both manual and auto)
        val allSequencePlaces = mutableListOf<SequencePlace>()

        // Add manually assigned aircraft with preserved scheduled times
        manuallyAssignedCandidates.forEach { candidate ->
            val existingPlace = existingPlacesByCandidate[candidate.id]!!
            allSequencePlaces.add(SequencePlace(
                item = candidate,
                scheduledTime = existingPlace.scheduledTime,
                isManuallyAssigned = true
            ))
        }

        // Add existing auto-scheduled aircraft, but respect their position relative to manually assigned aircraft
        sortedExistingAutoScheduled.forEach { candidate ->
            val originalIndex = originalOrderMap[candidate.id] ?: Int.MAX_VALUE

            // Find the best time that doesn't violate spacing and respects original order
            var bestTime = candidate.preferredTime

            // Check spacing with manually assigned aircraft that were originally before this aircraft
            val precedingManualAircraft = manuallyAssignedCandidates.filter { manualCandidate ->
                val manualOriginalIndex = originalOrderMap[manualCandidate.id] ?: Int.MAX_VALUE
                manualOriginalIndex < originalIndex
            }

            for (precedingManual in precedingManualAircraft) {
                val precedingPlace = existingPlacesByCandidate[precedingManual.id]!!
                val minTime = calculateSafeLandingTime(
                    referenceTime = precedingPlace.scheduledTime,
                    leader = precedingManual as AircraftSequenceCandidate,
                    follower = candidate as AircraftSequenceCandidate,
                    minimumSeparationNm = minimumSeparationNm,
                    independentRunwaySystems = independentRunwaySystems
                )
                bestTime = maxOf(bestTime, minTime)
            }

            allSequencePlaces.add(SequencePlace(
                item = candidate,
                scheduledTime = bestTime,
                isManuallyAssigned = false
            ))
        }

        // Add new auto-scheduled aircraft
        sortedNewAutoScheduled.forEach { candidate ->
            val bestTime = findBestInsertionTime(
                allSequencePlaces,
                candidate as AircraftSequenceCandidate,
                minimumSeparationNm,
                independentRunwaySystems
            )

            allSequencePlaces.add(SequencePlace(
                item = candidate,
                scheduledTime = bestTime,
                isManuallyAssigned = false
            ))
        }

        // Sort by scheduled time, but preserve relative order for aircraft with same time
        allSequencePlaces.sortWith(compareBy<SequencePlace> { it.scheduledTime }.thenBy { place ->
            originalOrderMap[place.item.id] ?: Int.MAX_VALUE
        })

        // Final pass: ensure spacing is maintained in the sorted sequence
        for (i in 1 until allSequencePlaces.size) {
            val follower = allSequencePlaces[i]

            val dependentLeader = allSequencePlaces.subList(0, i).lastOrNull {
                !areIndependent(it.item as AircraftSequenceCandidate, follower.item as AircraftSequenceCandidate, independentRunwaySystems)
            }

            val minSafeTime = if (dependentLeader != null &&
                dependentLeader.item is AircraftSequenceCandidate &&
                follower.item is AircraftSequenceCandidate) {
                calculateSafeLandingTime(
                    referenceTime = dependentLeader.scheduledTime,
                    leader = dependentLeader.item, // Smart-cast to AircraftSequenceCandidate
                    follower = follower.item,      // Smart-cast to AircraftSequenceCandidate
                    minimumSeparationNm = minimumSeparationNm,
                    independentRunwaySystems = independentRunwaySystems
                )
            } else {
                follower.item.preferredTime // Fallback if no dependent leader or types don't match
            }

            // Also ensure we don't start before the immediate predecessor (preserve list order/monotonicity)
            val immediatePrevTime = allSequencePlaces[i - 1].scheduledTime
            val requiredTime = maxOf(minSafeTime, immediatePrevTime)

            if (follower.scheduledTime < requiredTime) {
                val adjustedTime = if (follower.isManuallyAssigned) {
                    requiredTime // Move manually assigned aircraft to maintain spacing
                } else {
                    maxOf(requiredTime, follower.item.preferredTime) // Try to keep preferred time for auto-scheduled
                }
                allSequencePlaces[i] = follower.copy(scheduledTime = adjustedTime)
            }
        }

        return Sequence(sequecencePlaces = allSequencePlaces)
    }

    /**
     * Finds the best insertion time for a new aircraft, preventing overtaking of aircraft in locked horizon
     */
    private fun findBestInsertionTime(
        existingPlaces: List<SequencePlace>,
        newCandidate: AircraftSequenceCandidate,
        minimumSeparationNm: Double,
        independentRunwaySystems: List<Set<String>>
    ): Instant {
        var bestTime = newCandidate.preferredTime

        // AMAN Compliance: Check for aircraft in locked horizon that cannot be overtaken
        val lockedAircraft = existingPlaces.filter { it.item.isInLockedHorizon() }
        val lastLockedAircraft = lockedAircraft.maxByOrNull { it.scheduledTime }

        // If there are locked aircraft, new aircraft cannot be inserted before the last locked aircraft
        if (lastLockedAircraft != null && bestTime <= lastLockedAircraft.scheduledTime) {
            val minTimeAfterLocked = calculateSafeLandingTime(
                referenceTime = lastLockedAircraft.scheduledTime,
                leader = lastLockedAircraft.item as AircraftSequenceCandidate,
                follower = newCandidate,
                minimumSeparationNm = minimumSeparationNm,
                independentRunwaySystems = independentRunwaySystems
            )
            bestTime = maxOf(bestTime, minTimeAfterLocked)
        }

        // Check if there's a conflict with the leader (aircraft before preferred time)
        val leader = existingPlaces
            .filter { it.scheduledTime <= bestTime }
            .sortedByDescending { it.scheduledTime }
            .firstOrNull { !areIndependent(it.item as AircraftSequenceCandidate, newCandidate, independentRunwaySystems) }

        if (leader != null) {
            val requiredTime = calculateSafeLandingTime(
                referenceTime = leader.scheduledTime,
                leader = leader.item as AircraftSequenceCandidate,
                follower = newCandidate,
                minimumSeparationNm = minimumSeparationNm,
                independentRunwaySystems = independentRunwaySystems
            )
            // Only change time if there's a conflict (preferred time is too early)
            if (bestTime < requiredTime) {
                bestTime = requiredTime
            }
        }

        // Check if there's a conflict with the follower (aircraft after preferred time)
        val follower = existingPlaces
            .filter { it.scheduledTime > bestTime }
            .sortedBy { it.scheduledTime } // Sort by time ascending
            .firstOrNull {
                val item = it.item
                item is AircraftSequenceCandidate &&
                        !areIndependent(item, newCandidate, independentRunwaySystems)
            }

        if (follower != null) {
            val requiredFollowerTime = calculateSafeLandingTime(
                referenceTime = bestTime,
                leader = newCandidate,
                follower = follower.item as AircraftSequenceCandidate,
                minimumSeparationNm = minimumSeparationNm,
                independentRunwaySystems = independentRunwaySystems
            )
            if (follower.scheduledTime < requiredFollowerTime && !follower.item.isInLockedHorizon()) {
                // Move to an earlier time to avoid pushing the follower
                val effectiveSpacing = if (areIndependent(newCandidate, follower.item, independentRunwaySystems)) {
                    0.0
                } else if (areOnDifferentRunways(newCandidate, follower.item as AircraftSequenceCandidate)) {
                    minimumSeparationNm
                } else {
                    maxOf(
                        nmSpacingMap[Pair(newCandidate.wakeCategory, (follower.item as AircraftSequenceCandidate).wakeCategory)] ?: 3.0,
                        minimumSeparationNm
                    )
                }
                bestTime = follower.scheduledTime - nmToDuration(
                    effectiveSpacing,
                    (follower.item as AircraftSequenceCandidate).landingIas
                )
            }
        }

        return bestTime
    }

    private fun findSequenceItem(arrivals: List<SequenceCandidate>, callsign: String): SequenceCandidate? {
        return arrivals.find { it.id == callsign }
    }

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
        minimumSeparationNm: Double,
        independentRunwaySystems: List<Set<String>>
    ): Instant {
        val effectiveSpacingNm = if (areIndependent(leader, follower, independentRunwaySystems)) {
            0.0
        } else if (areOnDifferentRunways(leader, follower)) {
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
        val runway1 = aircraft1.assignedRunway
        val runway2 = aircraft2.assignedRunway

        // If either aircraft doesn't have a runway assignment, treat as same runway (use wake spacing)
        if (runway1 == null || runway2 == null) {
            return false
        }

        // Return true if runways are different
        return runway1 != runway2
    }

    /**
     * Checks if the aircraft is within the Sequencing Horizon.
     */
    private fun SequenceCandidate.isInSequencingHorizon(): Boolean {
        val remainingTime = this.preferredTime - NtpClock.now()
        return remainingTime < SEQUENCING_HORIZON
    }

    /**
     * Checks if the aircraft is within the Locked Horizon where order cannot be changed.
     */
    private fun SequenceCandidate.isInLockedHorizon(): Boolean {
        val remainingTime = this.preferredTime - NtpClock.now()
        return remainingTime < LOCKED_HORIZON
    }

    private fun areIndependent(
        candidate1: AircraftSequenceCandidate,
        candidate2: AircraftSequenceCandidate,
        independentRunwaySystems: List<Set<String>>
    ): Boolean {
        val rwy1 = candidate1.assignedRunway
        val rwy2 = candidate2.assignedRunway

        if (rwy1 == null || rwy2 == null) return false

        val sys1 = independentRunwaySystems.find { rwy1 in it }
        val sys2 = independentRunwaySystems.find { rwy2 in it }

        // Must be in defined systems, and those systems must be different objects/sets
        return sys1 != null && sys2 != null && sys1 !== sys2
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
