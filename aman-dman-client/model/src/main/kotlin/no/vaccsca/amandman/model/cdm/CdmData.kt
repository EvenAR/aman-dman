package no.vaccsca.amandman.model.cdm

import kotlinx.datetime.Instant

data class CdmData(
    val callsign: String,
    val ttot: Instant?,
    val ctot: Instant?
)
