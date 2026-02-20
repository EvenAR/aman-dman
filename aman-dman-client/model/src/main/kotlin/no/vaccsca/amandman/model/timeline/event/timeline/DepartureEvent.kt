package no.vaccsca.amandman.model.timeline.event.timeline

import kotlinx.datetime.Instant

data class DepartureEvent(
    override val scheduledTime: Instant,
    override val estimatedTime: Instant,
    override val runway: String,
    override val callsign: String,
    override val icaoType: String,
    override val wakeCategory: Char,
    override val airportIcao: String,
    override val trackingController: String?,
    override val lastTimestamp: Instant,
    val sid: String?
) : RunwayFlightEvent(
    scheduledTime = scheduledTime,
    estimatedTime = estimatedTime,
    runway = runway,
    airportIcao = airportIcao,
    callsign = callsign,
    icaoType = icaoType,
    wakeCategory = wakeCategory,
    trackingController = trackingController,
    lastTimestamp = lastTimestamp
)
