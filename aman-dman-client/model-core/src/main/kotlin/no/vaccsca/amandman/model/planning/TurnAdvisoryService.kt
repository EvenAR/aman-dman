package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.ArrivalFixRole
import no.vaccsca.amandman.model.navigation.bearingTo
import no.vaccsca.amandman.model.navigation.distanceTo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan
import kotlin.time.Duration.Companion.seconds

object TurnAdvisoryService {
    private const val GRAVITY_MPS2 = 9.80665
    private const val KNOTS_TO_MPS = 0.514444

    fun turnToIafAdvisoryTime(
        airport: Airport,
        assignedRunway: String?,
        assignedStar: String?,
        currentPosition: AircraftPosition,
        estimatedRunwayTime: Instant,
        scheduledRunwayTime: Instant,
        trajectory: List<TrajectoryPoint>?,
        advisoryBankAngleDeg: Double,
        isDirectToIafActive: Boolean = false,
        now: Instant = NtpClock.now(),
    ): Instant? {
        if (isDirectToIafActive) return null

        val runway = airport.runways[assignedRunway] ?: return null
        val turnAdvisoryAreaIds = runway.turnAdvisoryAreaIdsFor(assignedStar)
        if (turnAdvisoryAreaIds.isEmpty()) return null
        val insideAdvisoryArea = turnAdvisoryAreaIds
            .mapNotNull { areaId -> airport.areas[areaId] }
            .any { area -> area.covers(currentPosition.latLng, currentPosition.altitudeFt) }
        if (!insideAdvisoryArea) return null

        val runwayDelay = scheduledRunwayTime - estimatedRunwayTime
        if (runwayDelay.inWholeMilliseconds <= 0L) return now

        if (advisoryBankAngleDeg <= 0.0 || advisoryBankAngleDeg >= 90.0) return null
        if (currentPosition.groundspeedKts <= 0) return null

        val firstRemainingIaf = trajectory?.firstOrNull { it.fixRole == ArrivalFixRole.IAF } ?: return null
        val distanceToIafNm = currentPosition.latLng.distanceTo(firstRemainingIaf.latLng)
        val bearingToIaf = currentPosition.latLng.bearingTo(firstRemainingIaf.latLng)
        val turnDurationSeconds = computeTurnDurationSeconds(
            currentTrackDeg = currentPosition.trackDeg,
            targetTrackDeg = bearingToIaf,
            groundspeedKts = currentPosition.groundspeedKts,
            bankAngleDeg = advisoryBankAngleDeg,
        )

        if (!turnDurationSeconds.isFinite()) return null
        val straightLegSeconds = (distanceToIafNm / currentPosition.groundspeedKts) * 3600.0
        if (!straightLegSeconds.isFinite()) return null

        val directToIafDuration = (turnDurationSeconds + straightLegSeconds).seconds
        val iafSto = firstRemainingIaf.time + runwayDelay
        val candidate = iafSto - directToIafDuration
        return if (candidate < now) now else candidate
    }

    private fun computeTurnDurationSeconds(
        currentTrackDeg: Int,
        targetTrackDeg: Int,
        groundspeedKts: Int,
        bankAngleDeg: Double,
    ): Double {
        val headingDeltaDeg = smallestAbsoluteAngleDeltaDeg(currentTrackDeg, targetTrackDeg)
        val speedMps = groundspeedKts * KNOTS_TO_MPS
        if (speedMps <= 0.0) return Double.NaN

        val bankAngleRad = Math.toRadians(bankAngleDeg)
        val turnRateDegPerSec = (GRAVITY_MPS2 * tan(bankAngleRad) / speedMps) * (180.0 / PI)
        if (turnRateDegPerSec <= 0.0 || !turnRateDegPerSec.isFinite()) return Double.NaN
        return headingDeltaDeg / turnRateDegPerSec
    }

    private fun smallestAbsoluteAngleDeltaDeg(fromDeg: Int, toDeg: Int): Double {
        val delta = ((toDeg - fromDeg + 540) % 360) - 180
        return abs(delta.toDouble())
    }
}
