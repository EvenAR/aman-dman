package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.atc.ExtractedRoutePoint
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.FeederFixTiming
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import kotlin.math.cos
import kotlin.math.hypot

interface FeederFixTimingStrategy {
    fun computeTimingsForArrival(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        extractedRoute: List<ExtractedRoutePoint>,
        targetFixes: Set<String>,
    ): Map<String, FeederFixTiming>
}

class DynamicFromTrajectoryFeederFixTimingStrategy : FeederFixTimingStrategy {
    override fun computeTimingsForArrival(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        extractedRoute: List<ExtractedRoutePoint>,
        targetFixes: Set<String>,
    ): Map<String, FeederFixTiming> {
        if (trajectory.isEmpty() || targetFixes.isEmpty()) return emptyMap()

        val timings = linkedMapOf<String, FeederFixTiming>()

        targetFixes.forEach { feederFix ->
            val fixPoint = trajectory.firstOrNull { point ->
                point.fixId?.uppercase() == feederFix
            }
            if (fixPoint != null) {
                timings[feederFix] = arrival.toFeederFixTiming(fixPoint.remainingTime)
                return@forEach
            }

            findAbeamTiming(arrival, trajectory, extractedRoute, feederFix)?.let { timing ->
                timings[feederFix] = timing
            }
        }

        return timings
    }

    private fun findAbeamTiming(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        extractedRoute: List<ExtractedRoutePoint>,
        feederFix: String,
    ): FeederFixTiming? {
        if (trajectory.size < 2) return null

        val assignedDirect = arrival.assignedDirect?.uppercase() ?: return null
        val directIndex = extractedRoute.indexOfFirst { it.id.uppercase() == assignedDirect }
        if (directIndex <= 0) return null

        val bypassedFix = extractedRoute
            .take(directIndex)
            .lastOrNull { point -> point.isPassed && point.id.uppercase() == feederFix }
            ?: return null

        // A direct past a feeder fix removes the explicit route crossing, but the aircraft can still
        // be operationally relevant to that feeder flow, so we anchor it by the closest abeam time.
        val projectedRemainingTime = trajectory
            .zipWithNext()
            .mapNotNull { (start, end) -> projectRemainingTimeOntoSegment(start, end, bypassedFix.latLng) }
            .minByOrNull { it.distanceNm }
            ?.remainingTime
            ?: return null
        if (projectedRemainingTime >= trajectory.first().remainingTime) {
            return null
        }

        return arrival.toFeederFixTiming(projectedRemainingTime, isAbeamTime = true)
    }

    private fun projectRemainingTimeOntoSegment(
        start: TrajectoryPoint,
        end: TrajectoryPoint,
        feederFixPosition: LatLng,
    ): ProjectedRemainingTime? {
        val projection = SegmentProjection.from(start.latLng, end.latLng, feederFixPosition) ?: return null
        val remainingTimeDelta = start.remainingTime - end.remainingTime
        val projectedRemainingTime = start.remainingTime - (remainingTimeDelta * projection.fractionAlongSegment)
        return ProjectedRemainingTime(
            remainingTime = projectedRemainingTime,
            distanceNm = projection.distanceNm,
        )
    }

    private data class ProjectedRemainingTime(
        val remainingTime: kotlin.time.Duration,
        val distanceNm: Double,
    )

    private data class SegmentProjection(
        val fractionAlongSegment: Double,
        val distanceNm: Double,
    ) {
        companion object {
            fun from(start: LatLng, end: LatLng, target: LatLng): SegmentProjection? {
                val meanLatitudeRadians = Math.toRadians((start.lat + end.lat + target.lat) / 3.0)
                val startX = 0.0
                val startY = 0.0
                val endX = (end.lon - start.lon) * 60.0 * cos(meanLatitudeRadians)
                val endY = (end.lat - start.lat) * 60.0
                val targetX = (target.lon - start.lon) * 60.0 * cos(meanLatitudeRadians)
                val targetY = (target.lat - start.lat) * 60.0

                val segmentLengthSquared = endX * endX + endY * endY
                if (segmentLengthSquared == 0.0) return null

                val unclampedFraction = ((targetX - startX) * (endX - startX) + (targetY - startY) * (endY - startY)) / segmentLengthSquared
                val clampedFraction = unclampedFraction.coerceIn(0.0, 1.0)
                val projectedX = startX + (endX - startX) * clampedFraction
                val projectedY = startY + (endY - startY) * clampedFraction

                return SegmentProjection(
                    fractionAlongSegment = clampedFraction,
                    distanceNm = hypot(targetX - projectedX, targetY - projectedY),
                )
            }
        }
    }

    private fun RunwayArrivalEvent.toFeederFixTiming(
        remainingTime: kotlin.time.Duration,
        isAbeamTime: Boolean = false,
    ) = FeederFixTiming(
        eto = estimatedTime - remainingTime,
        sto = scheduledTime - remainingTime,
        isAbeamTime = isAbeamTime,
    )
}

class FeederFixTimingService(
    private val timingStrategy: FeederFixTimingStrategy = DynamicFromTrajectoryFeederFixTimingStrategy(),
) {
    fun buildState(
        airport: Airport,
        arrivals: List<RunwayArrivalEvent>,
        trajectoryProvider: (callsign: String) -> List<TrajectoryPoint>?,
        extractedRouteProvider: (callsign: String) -> List<ExtractedRoutePoint>?,
    ): FeederFixState {
        val configuredFixes = airport.feederFixes.map { it.uppercase() }.distinct()
        val targetFixes = configuredFixes.toSet()

        val timingsByCallsign = arrivals.mapNotNull { arrival ->
            val trajectory = trajectoryProvider(arrival.callsign) ?: return@mapNotNull null
            val extractedRoute = extractedRouteProvider(arrival.callsign).orEmpty()
            val timings = timingStrategy.computeTimingsForArrival(arrival, trajectory, extractedRoute, targetFixes)
            if (timings.isEmpty()) null else arrival.callsign to timings
        }.toMap()

        return FeederFixState(
            availableFixes = configuredFixes,
            timingsByCallsign = timingsByCallsign,
        )
    }
}
