package no.vaccsca.amandman.model.airport

import kotlinx.datetime.Instant

data class AirportStatus(
    val abbreviation: String,
    val level: AirportStatusLevel,
    val time: Instant,
    val message: String
)

enum class AirportStatusLevel {
    UNKNOWN,
    NORMAL,
    WARNING,
    CRITICAL,
}