package no.vaccsca.amandman.model.config.yaml

import jakarta.validation.constraints.NotNull

data class ArrivalFixYamlEntry(
    @field:NotNull
    val name: String,

    @field:NotNull
    val runways: List<String>,

    val role: ArrivalFixRoleYaml? = null,

    val typicalAltitude: Int? = null,

    val typicalAirspeed: Int? = null,
)

enum class ArrivalFixRoleYaml {
    IF,
    IAF,
}
