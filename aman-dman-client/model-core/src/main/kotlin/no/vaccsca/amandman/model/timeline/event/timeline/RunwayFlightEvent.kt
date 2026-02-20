package no.vaccsca.amandman.model.timeline.event.timeline

import kotlinx.datetime.Instant

sealed class RunwayFlightEvent(
    override val scheduledTime: Instant,
    override val estimatedTime: Instant,
    override val runway: String,
    override val airportIcao: String,
    open val callsign: String,
    open val icaoType: String,
    open val wakeCategory: Char,
    open val trackingController: String?,
    open val lastTimestamp: Instant,
) : RunwayEvent(
    scheduledTime = scheduledTime,
    airportIcao = airportIcao,
    runway = runway,
    estimatedTime = estimatedTime
)