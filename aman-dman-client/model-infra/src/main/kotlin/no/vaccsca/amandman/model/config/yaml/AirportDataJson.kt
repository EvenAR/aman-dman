package no.vaccsca.amandman.model.config.yaml

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Duration

data class AirportDataJson(
    @field:NotNull
    val location: LocationJson,

    @field:NotNull
    val runwayThresholds: Map<String, RunwayThresholdJson>,

    val independentRunwaySystems: List<List<String>>? = null,

    val sequencingHorizon: Duration? = null,

    val lockedHorizon: Duration? = null,

    @field:Positive
    val weatherFetchRadiusNm: Double? = null,

    val feederFixes: List<String> = emptyList(),

    val feederFixTimelineArrivalLabelLayoutId: String? = null,

    // Reserved for future fixed-transit strategy, currently not used in projection.
    val feederFixTransitTimesMinutes: Map<String, Map<String, Int>>? = null,

    val arrivalFixes: List<ArrivalFixYamlEntry> = emptyList(),
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
