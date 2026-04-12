package no.vaccsca.amandman.model.config.yaml

import jakarta.validation.constraints.NotNull

data class AirportsConfigYaml(
    @field:NotNull
    val airports: Map<String, AirportDataJson>,
)
