package no.vaccsca.amandman.model.timeline

import kotlinx.datetime.Instant

data class MeteringPointState(
    val availableMeteringPoints: List<String> = emptyList(),
    val timingsByCallsign: Map<String, Map<String, MeteringPointTiming>> = emptyMap(),
)

data class MeteringPointTiming(
    val eta: Instant,
    val sta: Instant,
)
