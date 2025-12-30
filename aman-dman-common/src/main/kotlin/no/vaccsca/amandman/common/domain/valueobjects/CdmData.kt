package no.vaccsca.amandman.common.domain.valueobjects

import kotlinx.datetime.Instant

data class CdmData(
    val callsign: String,
    val ttot: Instant?,
    val ctot: Instant?
)
