package no.vaccsca.amandman.model.domain.valueobjects.timelineEvent

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.valueobjects.sequence.SequenceStatus
import kotlin.time.Duration

data class RunwayArrivalEvent(
    override val scheduledTime: Instant,
    override val estimatedTime: Instant,
    override val lastTimestamp: Instant,
    override val runway: String,
    override val callsign: String,
    override val icaoType: String,
    override val wakeCategory: Char,
    override val airportIcao: String,
    override val trackingController: String?,
    val assignedStar: String?,
    val assignedStarOk: Boolean,
    val flightLevel: Int,
    val pressureAltitude: Int,
    val groundSpeed: Int,
    val remainingDistance: Float,
    val withinActiveAdvisoryHorizon: Boolean,
    val sequenceStatus: SequenceStatus,
    val landingIas: Int,
    val distanceToPreceding: Float? = null,
    val timeToPreceding: Duration? = null,
    val assignedDirect: String?,
    val scratchPad: String?,
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
