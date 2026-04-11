package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.airport.RunwayThreshold

data class DescentTrajectoryResult(
    val trajectoryPoints: List<TrajectoryPoint>,
    val runwayThreshold: RunwayThreshold,
)
