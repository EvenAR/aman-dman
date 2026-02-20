package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant

sealed class SequenceCandidate(
    open val id: String,
    open val preferredTime: Instant,
)
