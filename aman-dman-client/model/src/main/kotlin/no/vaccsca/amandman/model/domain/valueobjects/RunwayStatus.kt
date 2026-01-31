package no.vaccsca.amandman.model.domain.valueobjects

data class RunwayStatus(
    val isActiveForArrivals: Boolean,
    val isActiveForDepartures: Boolean
)