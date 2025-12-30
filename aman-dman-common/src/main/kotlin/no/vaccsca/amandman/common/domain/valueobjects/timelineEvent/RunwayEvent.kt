package no.vaccsca.amandman.common.domain.valueobjects.timelineEvent

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