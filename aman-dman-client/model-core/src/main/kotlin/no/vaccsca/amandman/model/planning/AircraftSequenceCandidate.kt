package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant

data class AircraftSequenceCandidate(
    val callsign: String,
    override val preferredTime: Instant,
    val landingIas: Int,
    val wakeCategory: Char,
    val runway: String?,
    val isLockedForSequencing: Boolean = false,
) : SequenceCandidate(
    id = callsign,
    preferredTime = preferredTime,
)
