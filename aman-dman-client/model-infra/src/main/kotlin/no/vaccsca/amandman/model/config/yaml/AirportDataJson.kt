package no.vaccsca.amandman.model.config.yaml

import jakarta.validation.constraints.NotNull
import java.time.Duration

data class AirportDataJson(
    @field:NotNull
    val airports: Map<String, AirportJson>
)

data class AirportJson(
    @field:NotNull
    val location: LocationJson,

    val runwayThresholds: Map<String, RunwayThresholdJson>,

    val independentRunwaySystems: List<List<String>>? = null,

    val sequencingHorizon: Duration? = null,

    val lockedHorizon: Duration? = null,
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
