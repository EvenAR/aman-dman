package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.atc.AtcClientArrivalData

object SequencingStateEvaluator {

    fun isInLockedSequenceWindow(
        airport: Airport,
        arrival: AtcClientArrivalData,
        estimatedTime: Instant,
        now: Instant = NtpClock.now(),
    ): Boolean {
        val assignedRunway = arrival.assignedRunway ?: return false
        val runway = airport.runways[assignedRunway] ?: return false
        val lockedAreaId = runway.frozenSequenceAreaIdFor(arrival.assignedStar)
        val lockedArea = lockedAreaId?.let { airport.areas[it] }

        if (lockedArea != null) {
            return lockedArea.covers(
                position = arrival.currentPosition.latLng,
                altitudeFt = arrival.currentPosition.altitudeFt,
            )
        }

        val remainingTime = estimatedTime - now
        return remainingTime < airport.lockedHorizon
    }

    fun isInSequencingWindow(
        airport: Airport,
        arrival: AtcClientArrivalData,
        estimatedTime: Instant,
        now: Instant = NtpClock.now(),
    ): Boolean {
        val assignedRunway = arrival.assignedRunway ?: return false
        val runway = airport.runways[assignedRunway] ?: return false
        val areaId = runway.sequencingAreaIdFor(arrival.assignedStar)
        val area = areaId?.let { airport.areas[it] }

        if (area != null) {
            return area.covers(
                position = arrival.currentPosition.latLng,
                altitudeFt = arrival.currentPosition.altitudeFt,
            )
        }

        val remainingTime = estimatedTime - now
        return remainingTime < airport.sequencingHorizon
    }
}
