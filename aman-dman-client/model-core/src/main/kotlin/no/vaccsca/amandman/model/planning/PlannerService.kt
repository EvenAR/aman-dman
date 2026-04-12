package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.integration.AirportDataSource
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent

/**
 * Interface for services that can actively plan and modify sequences.
 * Only implemented by local/master planners, never by read-only mirrors.
 */
interface SequencePlanner : AirportDataSource {
    override val isReadOnly: Boolean get() = false

    fun setMinimumSpacing(minimumSpacingDistanceNm: Double)
    fun refreshWeatherData()
    fun refreshCdmData()
    fun suggestScheduledTime(timelineEvent: TimelineEvent, scheduledTime: Instant, newRunway: String?)
    fun reSchedule(callSign: String? = null)
    fun isTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant, runway: String): Boolean
    fun getDescentProfileForCallsign(callsign: String): List<TrajectoryPoint>?
    fun getAvailableRunways(): List<String>
    fun setShowDepartures(showDepartures: Boolean)
}