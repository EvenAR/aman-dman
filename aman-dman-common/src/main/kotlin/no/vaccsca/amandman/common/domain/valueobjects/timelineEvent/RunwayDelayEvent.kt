package no.vaccsca.amandman.common.domain.valueobjects.timelineEvent

import kotlinx.datetime.Instant
import kotlin.time.Duration

data class RunwayDelayEvent(
    override val scheduledTime: Instant,
    override val estimatedTime: Instant,
    override val runway: String,
    override val airportIcao: String,
    val delay: Duration,
    val name: String,
) : RunwayEvent(
    scheduledTime = scheduledTime,
    airportIcao = airportIcao,
    runway = runway,
    estimatedTime = estimatedTime
)