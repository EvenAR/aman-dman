package no.vaccsca.amandman.presenter

import integration.NetworkEventHandlerInterface
import integration.WebsocketNetworkClient
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayFlightEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.dto.CreateOrUpdateTimelineDto
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

class AirportPresenter(
    val airportIcao: String,
    val mainPresenter: PresenterInterface,
) : NetworkEventHandlerInterface, AirportPresenterInterface {
    private var draggedLabel: TimelineEvent? = null
    private val cachedAmanData = mutableMapOf<String, CachedEvent>()

    private val socketConnection = WebsocketNetworkClient(airportIcao, this)

    init {
        socketConnection.run()
    }

    lateinit var airportView: AirportViewInterface

    override fun onConnected() {
        println("Connected to AirportPresenter")
    }

    override fun onDisconnected() {
        println("Disconnected from AirportPresenter")
    }

    override fun onError(exception: Throwable) {
        println("Error in AirportPresenter: ${exception.message}")
    }

    override suspend fun onLiveData(timelineEvents: List<TimelineEvent>) {
        timelineEvents.filterIsInstance<RunwayFlightEvent>().forEach {
            cachedAmanData[it.callsign] = CachedEvent(
                lastTimestamp = NtpClock.now(),
                timelineEvent = it
            )
        }

        // Delete stale data
        val cutoffTime = NtpClock.now() - 5.seconds
        cachedAmanData.entries.removeIf { entry ->
            entry.value.lastTimestamp < cutoffTime
        }
        updateViewFromCachedData()
    }

    override suspend fun onRunwayModesUpdated(runwayStatuses: Map<String, RunwayStatus>) {
        airportView.updateRunwayModes(runwayStatuses)
    }

    override suspend fun onMinimumSpacingUpdated(minimumSpacingNm: Double) {
        airportView.updateMinimumSpacing(minimumSpacingNm)
    }

    override suspend fun onWeatherDataUpdated(
        data: VerticalWeatherProfile?
    ) {
        airportView.updateWeatherData(data)
    }

    override suspend fun onAircraftTrajectoryUpdated(
        callSign: String,
        trajectory: List<TrajectoryPoint>
    ) {
        airportView.updateDescentTrajectory(callSign, trajectory)
    }

    override suspend fun onTimeAvailabilityResult(
        time: Instant,
        isTimeAvailable: Boolean
    ) {
        draggedLabel?.let { draggedLabel ->
            airportView.updateDraggedLabel(draggedLabel, time, isTimeAvailable)
        }
    }

    private fun updateViewFromCachedData() {
        val snapshot: List<TimelineEvent> = cachedAmanData.values.toList().map { it.timelineEvent }

        /*airportView.updateTab(TabData(
            timelinesData = group.timelines.map { timeline ->
                TimelineData(
                    timelineId = timeline.title,
                    left = snapshot.filter { (it is RunwayFlightEvent) && timeline.runwaysLeft.contains(it.runway) },
                    right = snapshot.filter {
                        (it is RunwayFlightEvent) && timeline.runwaysRight.contains(
                            it.runway
                        )
                    }
                )
            }
        ))*/
    }

    override fun onAircraftSelected(callsign: String) {
        TODO("Not yet implemented")
    }

    override fun onOpenLandingRatesWindow() {
        TODO("Not yet implemented")
    }

    override fun onOpenNonSequencedWindow() {
        TODO("Not yet implemented")
    }

    override fun onLabelDragEnd(
        timelineEvent: TimelineEvent,
        newScheduledTime: Instant,
        newRunway: String?
    ) {
        TODO("Not yet implemented")
    }

    override fun onRecalculateSequenceClicked(callSign: String?) {
        TODO("Not yet implemented")
    }

    override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {
        TODO("Not yet implemented")
    }

    override fun onLabelDrag(
        timelineEvent: TimelineEvent,
        newInstant: Instant
    ) {
        TODO("Not yet implemented")
    }

    override fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double) {
        TODO("Not yet implemented")
    }

    override fun beginRunwaySelection(
        runwayEvent: RunwayEvent,
        onClose: (runway: String?) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun onToggleShowDepartures(selected: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onTabMenu(screenPos: Point) {
        TODO("Not yet implemented")
    }

    override fun onCreateNewTimelineClicked() {
        TODO("Not yet implemented")
    }

    override fun onRemoveTab() {
        TODO("Not yet implemented")
    }

    override fun onAddTimelineButtonClicked(
        timelineConfig: TimelineConfig
    ) {
        TODO("Not yet implemented")
    }

    override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {
        TODO("Not yet implemented")
    }

    override fun onReloadWindsClicked() {
        TODO("Not yet implemented")
    }

    override fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?) {
        TODO("Not yet implemented")
    }

    override fun onOpenMetWindowClicked() {
        TODO("Not yet implemented")
    }

    override fun onOpenVerticalProfileWindowClicked(callsign: String) {
        mainPresenter.onOpenVerticalProfileWindowClicked(callsign)
    }

    private data class CachedEvent(
        val lastTimestamp: Instant,
        val timelineEvent: TimelineEvent
    )
}