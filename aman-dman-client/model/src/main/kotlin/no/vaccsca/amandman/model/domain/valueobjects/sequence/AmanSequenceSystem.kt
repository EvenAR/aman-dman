package no.vaccsca.amandman.model.domain.valueobjects.sequence

data class AmanSequenceSystem(
    val runwaySystem: Set<String>,
    val places: List<SequencePlace>
)