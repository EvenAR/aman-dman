package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.timeline.MeteringPointState
import no.vaccsca.amandman.model.timeline.MeteringPointTiming
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent

interface MeteringPointTimingStrategy {
    fun computeTimingsForArrival(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        targetMeteringPoints: Set<String>,
    ): Map<String, MeteringPointTiming>
}

class DynamicFromTrajectoryMeteringPointTimingStrategy : MeteringPointTimingStrategy {
    override fun computeTimingsForArrival(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        targetMeteringPoints: Set<String>,
    ): Map<String, MeteringPointTiming> {
        if (trajectory.isEmpty() || targetMeteringPoints.isEmpty()) return emptyMap()

        val timings = linkedMapOf<String, MeteringPointTiming>()

        targetMeteringPoints.forEach { meteringPoint ->
            val fixPoint = trajectory.firstOrNull { point ->
                point.fixId?.uppercase() == meteringPoint
            } ?: return@forEach

            timings[meteringPoint] = MeteringPointTiming(
                eta = arrival.estimatedTime - fixPoint.remainingTime,
                sta = arrival.scheduledTime - fixPoint.remainingTime,
            )
        }

        return timings
    }
}

class MeteringPointTimingService(
    private val timingStrategy: MeteringPointTimingStrategy = DynamicFromTrajectoryMeteringPointTimingStrategy(),
) {
    fun buildState(
        airport: Airport,
        arrivals: List<RunwayArrivalEvent>,
        trajectoryProvider: (callsign: String) -> List<TrajectoryPoint>?,
    ): MeteringPointState {
        val configuredFixes = airport.meteringPoints.map { it.uppercase() }.distinct()
        val targetFixes = configuredFixes.toSet()

        val timingsByCallsign = arrivals.mapNotNull { arrival ->
            val trajectory = trajectoryProvider(arrival.callsign) ?: return@mapNotNull null
            val timings = timingStrategy.computeTimingsForArrival(arrival, trajectory, targetFixes)
            if (timings.isEmpty()) null else arrival.callsign to timings
        }.toMap()

        return MeteringPointState(
            availableMeteringPoints = configuredFixes,
            timingsByCallsign = timingsByCallsign,
        )
    }
}
