package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant

data class SequencePlace(
    val item: SequenceCandidate,
    val scheduledTime: Instant,
    val isManuallyAssigned: Boolean = false
)