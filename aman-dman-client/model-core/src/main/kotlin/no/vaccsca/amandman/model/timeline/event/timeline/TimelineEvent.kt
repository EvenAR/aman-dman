package no.vaccsca.amandman.model.timeline.event.timeline

import kotlinx.datetime.Instant

sealed class TimelineEvent(
    open val scheduledTime: Instant,
    open val airportIcao: String,
)