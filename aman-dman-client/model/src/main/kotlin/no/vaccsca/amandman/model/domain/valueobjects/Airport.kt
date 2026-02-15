package no.vaccsca.amandman.model.domain.valueobjects

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class Airport(
    val icao: String,
    val location: LatLng,
    val runways: Map<String, RunwayThreshold>,
    val independentRunwaySystems: List<Set<String>>,
    val sequencingHorizon: Duration,
    val lockedHorizon: Duration,
)

data class RunwayThreshold(
    val id: String,
    val latLng: LatLng,
    val elevation: Float,
    val trueHeading: Float,
    val stars: List<Star>,
)
