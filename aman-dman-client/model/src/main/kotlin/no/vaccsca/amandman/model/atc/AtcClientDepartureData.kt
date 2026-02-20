package no.vaccsca.amandman.model.atc

import kotlinx.datetime.Instant

/**
 * All data about a departure received from the ATC client.
 */
data class AtcClientDepartureData(
    val departureIcao: String,
    val callsign: String,
    val icaoType: String,
    val assignedSid: String?,
    val trackingController: String?,
    val scratchPad: String?,
    val assignedRunway: String?,
    val wakeCategory: Char,
    val recvTimestamp: Instant,
)