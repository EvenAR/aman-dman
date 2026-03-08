package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.FeederFixTiming
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent

interface FeederFixTimingStrategy {
    fun computeTimingsForArrival(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        targetFixes: Set<String>,
    ): Map<String, FeederFixTiming>
}

class DynamicFromTrajectoryFeederFixTimingStrategy : FeederFixTimingStrategy {
    override fun computeTimingsForArrival(
        arrival: RunwayArrivalEvent,
        trajectory: List<TrajectoryPoint>,
        targetFixes: Set<String>,
    ): Map<String, FeederFixTiming> {
        if (trajectory.isEmpty() || targetFixes.isEmpty()) return emptyMap()

        val timings = linkedMapOf<String, FeederFixTiming>()

        targetFixes.forEach { feederFix ->
            val fixPoint = trajectory.firstOrNull { point ->
                point.fixId?.uppercase() == feederFix
            } ?: return@forEach

            timings[feederFix] = FeederFixTiming(
                eta = arrival.estimatedTime - fixPoint.remainingTime,
                sta = arrival.scheduledTime - fixPoint.remainingTime,
            )
        }

        return timings
    }
}

class FeederFixTimingService(
    private val timingStrategy: FeederFixTimingStrategy = DynamicFromTrajectoryFeederFixTimingStrategy(),
) {
    fun buildState(
        airport: Airport,
        arrivals: List<RunwayArrivalEvent>,
        trajectoryProvider: (callsign: String) -> List<TrajectoryPoint>?,
    ): FeederFixState {
        val configuredFixes = airport.feederFixes.map { it.uppercase() }.distinct()
        val targetFixes = configuredFixes.toSet()

        val timingsByCallsign = arrivals.mapNotNull { arrival ->
            val trajectory = trajectoryProvider(arrival.callsign) ?: return@mapNotNull null
            val timings = timingStrategy.computeTimingsForArrival(arrival, trajectory, targetFixes)
            if (timings.isEmpty()) null else arrival.callsign to timings
        }.toMap()

        return FeederFixState(
            availableFixes = configuredFixes,
            timingsByCallsign = timingsByCallsign,
        )
    }
}
