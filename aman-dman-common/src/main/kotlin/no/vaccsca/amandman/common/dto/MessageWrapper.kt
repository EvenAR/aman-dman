package no.vaccsca.amandman.common.dto

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock

data class MessageWrapper<T>(
    val type: String,
    val time: Instant = NtpClock.now(),
    val data: T
)