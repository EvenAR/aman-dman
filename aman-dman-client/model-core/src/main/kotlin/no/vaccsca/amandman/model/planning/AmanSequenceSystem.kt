package no.vaccsca.amandman.model.planning

data class AmanSequenceSystem(
    val runwaySystem: Set<String>,
    val places: List<SequencePlace>
)