package no.vaccsca.amandman.model.config.yaml

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

data class StarYamlFile(
    @field:NotNull
    val stars: List<StarYamlEntry>
)

data class StarYamlEntry(
    @field:NotNull
    val name: String,

    @field:NotNull
    val runway: String,

    @field:NotNull
    val waypoints: List<StarYamlWaypoint>
)

data class StarYamlWaypoint(
    @field:NotNull
    val id: String,

    val typicalAltitude: Int? = null,

    val typicalSpeed: Int? = null,
)
