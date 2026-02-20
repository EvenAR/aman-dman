package no.vaccsca.amandman.model.config.yaml

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.Valid

data class AirportDataJson(
    @field:NotEmpty
    @field:Valid
    val airports: Map<@NotEmpty String, @Valid AirportJson>
)

data class AirportJson(
    @field:NotNull
    @field:Valid
    val location: LocationJson,

    @field:NotNull
    @field:Valid
    val runwayThresholds: Map<@NotEmpty String, @Valid RunwayThresholdJson>,

    @field:Min(1)
    val sequencingHorizonMinutes: Int? = null,

    @field:Min(1)
    val lockedHorizonMinutes: Int? = null,

    val independentRunwaySystems: List<List<String>>? = null
)

data class LocationJson(
    @field:Min(-90)
    @field:Max(90)
    val latitude: Double,

    @field:Min(-180)
    @field:Max(180)
    val longitude: Double
)

data class RunwayThresholdJson(
    @field:NotNull
    @field:Valid
    val location: LocationJson,

    @field:Min(0)
    val elevation: Float,

    @field:Min(0)
    @field:Max(360)
    val trueHeading: Float
)
