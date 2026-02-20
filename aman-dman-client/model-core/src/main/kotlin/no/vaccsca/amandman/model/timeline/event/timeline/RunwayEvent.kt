package no.vaccsca.amandman.model.timeline.event.timeline

import kotlinx.datetime.Instant

sealed class RunwayEvent(
    override val scheduledTime: Instant,
    override val airportIcao: String,
    open val runway: String,
    open val estimatedTime: Instant,
) : TimelineEvent(
    scheduledTime = scheduledTime,
    airportIcao = airportIcao
)