package no.vaccsca.amandman.model.timeline

import kotlinx.datetime.Instant

data class FeederFixState(
    val availableFixes: List<String> = emptyList(),
    val timingsByCallsign: Map<String, Map<String, FeederFixTiming>> = emptyMap(),
)

data class FeederFixTiming(
    val eta: Instant,
    val sta: Instant,
)
