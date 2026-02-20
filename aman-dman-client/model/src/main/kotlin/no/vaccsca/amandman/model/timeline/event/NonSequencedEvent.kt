package no.vaccsca.amandman.model.timeline.event

import no.vaccsca.amandman.model.timeline.NonSequencedReason

data class NonSequencedEvent(
    val callsign: String,
    val aircraftType: String,
    val wakeCategory: Char?,
    val reason: NonSequencedReason,
)