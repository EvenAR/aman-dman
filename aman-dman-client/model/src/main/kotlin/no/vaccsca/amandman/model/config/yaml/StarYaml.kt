package no.vaccsca.amandman.model.config.yaml

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.Valid

data class StarYamlFile(
    @field:NotNull
    @field:Valid
    val stars: List<@Valid StarYamlEntry>
)

data class StarYamlEntry(
    @field:NotEmpty
    @field:Pattern(
        regexp = "^[0-9]{2}[A-Z]?$",
        message = "Runway designator must be quoted as a string, e.g., '07', '25L'"
    )
    val runway: String,

    @field:NotEmpty
    val name: String,

    @field:NotNull
    @field:Valid
    val waypoints: List<@Valid StarWaypointYaml>
)

data class StarWaypointYaml(
    @field:NotEmpty
    @field:Pattern(
        regexp = "^[A-Z0-9]+$",
        message = "Waypoint ID must be uppercase alphanumeric"
    )
    val id: String,

    @field:Min(0)
    @field:JsonPropertyDescription("The typical altitude at this waypoint (in feet)")
    val typicalAltitude: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("The typical indicated airspeed at this waypoint (in knots)")
    val typicalSpeed: Int? = null
)
