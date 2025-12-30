package no.vaccsca.amandman.common.domain.valueobjects.timelineEvent

import kotlinx.datetime.Instant

sealed class TimelineEvent(
    open val scheduledTime: Instant,
    open val airportIcao: String,
)