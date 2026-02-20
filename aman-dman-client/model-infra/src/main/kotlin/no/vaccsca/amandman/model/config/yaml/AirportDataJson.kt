package no.vaccsca.amandman.model.config.yaml

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

data class AirportDataJson(
    @field:NotNull
    val airports: Map<String, AirportJson>
)

data class AirportJson(
    @field:NotNull
    val location: LocationJson,

    @JsonProperty("runways")
    val runwayThresholds: Map<String, RunwayThresholdJson>,

    val independentRunwaySystems: List<List<String>>? = null,

    val sequencingHorizonMinutes: Int? = null,

    val lockedHorizonMinutes: Int? = null,
)

data class RunwayThresholdJson(
    @field:NotNull
    val location: LocationJson,

    val elevation: Float,

    val trueHeading: Float,
)

data class LocationJson(
    val latitude: Double,
    val longitude: Double,
)
