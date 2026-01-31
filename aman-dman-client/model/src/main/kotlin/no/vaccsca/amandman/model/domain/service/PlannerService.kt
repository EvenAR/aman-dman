package no.vaccsca.amandman.model.domain.service

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent

abstract class PlannerService(
    val airportIcao: String
) {
    abstract fun startDataCollection()
    abstract fun setMinimumSpacing(minimumSpacingDistanceNm: Double): Result<Unit>
    abstract fun refreshWeatherData(): Result<Unit>
    abstract fun refreshCdmData(): Result<Unit>
    abstract fun suggestScheduledTime(timelineEvent: TimelineEvent, scheduledTime: Instant, newRunway: String?): Result<Unit>
    abstract fun reSchedule(callSign: String? = null): Result<Unit>
    abstract fun isTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant, runway: String): Result<Boolean>
    abstract fun getDescentProfileForCallsign(callsign: String): Result<List<TrajectoryPoint>?>
    abstract fun stop()
    abstract fun start()
    abstract fun getAvailableRunways(): Result<List<String>>
    abstract fun setShowDepartures(showDepartures: Boolean)
}