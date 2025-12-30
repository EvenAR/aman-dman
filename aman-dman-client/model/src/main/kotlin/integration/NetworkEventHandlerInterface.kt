package integration

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile

interface NetworkEventHandlerInterface {
    fun onConnected()
    fun onDisconnected()
    fun onError(exception: Throwable)

    suspend fun onLiveData(timelineEvents: List<TimelineEvent>)
    suspend fun onRunwayModesUpdated(runwayStatuses: Map<String, RunwayStatus>)
    suspend fun onMinimumSpacingUpdated(minimumSpacingNm: Double)
    suspend fun onWeatherDataUpdated(data: VerticalWeatherProfile?)
    suspend fun onAircraftTrajectoryUpdated(callSign: String, trajectory: List<TrajectoryPoint>)
    suspend fun onTimeAvailabilityResult(time: Instant, isTimeAvailable: Boolean)
}