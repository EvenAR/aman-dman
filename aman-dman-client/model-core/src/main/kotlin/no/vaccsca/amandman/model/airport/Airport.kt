package no.vaccsca.amandman.model.airport

import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Star
import kotlin.time.Duration

data class Airport(
    val icao: String,
    val location: LatLng,
    val runways: Map<String, RunwayThreshold>,
    val independentRunwaySystems: List<Set<String>>,
    val sequencingHorizon: Duration,
    val lockedHorizon: Duration,
    val weatherFetchRadiusNm: Double = 200.0,
    val feederFixes: List<String> = emptyList(),
    val feederFixTimelineArrivalLabelLayoutId: String? = null,
    // Reserved for future fixed-transit feeder fix timing strategy.
    val feederFixTransitTimesMinutes: Map<String, Map<String, Int>> = emptyMap(),
)

data class RunwayThreshold(
    val id: String,
    val latLng: LatLng,
    val elevation: Float,
    val trueHeading: Float,
    val stars: List<Star>,
)
