package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.navigation.Star

data class DescentTrajectoryResult(
    val trajectoryPoints: List<TrajectoryPoint>,
    val runwayThreshold: RunwayThreshold,
    val star: Star?
)
