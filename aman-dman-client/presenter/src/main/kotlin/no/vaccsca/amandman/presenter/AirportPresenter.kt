package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.data.dto.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.data.repository.SettingsRepository
import no.vaccsca.amandman.model.domain.TimelineGroup
import no.vaccsca.amandman.model.domain.exception.UnsupportedInSlaveModeException
import no.vaccsca.amandman.model.domain.service.DataUpdateListener
import no.vaccsca.amandman.model.domain.service.PlannerService
import no.vaccsca.amandman.model.domain.valueobjects.NonSequencedEvent
import no.vaccsca.amandman.model.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayFlightEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import org.slf4j.LoggerFactory
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

class AirportPresenter(
    override val airportIcao: String,
    private val plannerService: PlannerService,
    private val view: AirportViewInterface,
    private val timelineGroup: TimelineGroup,
    private val controllerInfoProvider: () -> ControllerInfoData?,
    private val showErrorMessage: (String) -> Unit,
    private val onAircraftSelectedCallback: (String) -> Unit,
    private val onOpenVerticalProfileCallback: (String) -> Unit,
    private val onRemove: () -> Unit,
) : AirportPresenterInterface, DataUpdateListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val cachedTimelineEvents = mutableMapOf<String, CachedTimelineEvent>()
    private var cachedNonSequencedEvents: List<NonSequencedEvent> = emptyList()
    private val runwayModeStateManager = AirportRunwayModeStateManager(airportIcao, view)
    private var minimumSpacingNm: Double = 3.0
    private var availableRunways = setOf<String>()
    private val timelineConfigs = mutableMapOf<String, TimelineConfig>()

    init {
        view.airportPresenterInterface = this
        loadTimelineConfigsForAirport()
    }

    private fun loadTimelineConfigsForAirport() {
        SettingsRepository.getSettings().timelines[airportIcao]?.forEach { timeline ->
            val config = TimelineConfig(
                title = timeline.title,
                runwaysLeft = timeline.left?.runways ?: emptyList(),
                runwaysRight = timeline.right.runways,
                airportIcao = airportIcao,
                depLabelLayout = timeline.departureLabelLayoutId,
                arrLabelLayout = timeline.arrivalLabelLayoutId,
            )
            timelineConfigs[timeline.title] = config
        }
    }

    fun start() {
        plannerService.start()
        plannerService.startDataCollection()
    }

    fun stop() {
        plannerService.stop()
    }

    fun updateViewFromCache() {
        val cutoffTime = NtpClock.now() - 5.seconds
        cachedTimelineEvents.entries.removeIf { it.value.lastTimestamp < cutoffTime }

        val timelineEvents = cachedTimelineEvents.values.map { it.timelineEvent }
        view.updateTab(timelineEvents, cachedNonSequencedEvents)
    }

    // DataUpdateListener implementations
    override fun onTimelineEventsUpdated(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        if (airportIcao != this.airportIcao) return

        timelineEvents.filterIsInstance<RunwayFlightEvent>().forEach {
            cachedTimelineEvents[it.callsign] = CachedTimelineEvent(
                lastTimestamp = NtpClock.now(),
                timelineEvent = it
            )
        }
        updateViewFromCache()
    }

    override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
        if (airportIcao != this.airportIcao) return
        availableRunways = runwayStatuses.keys
        runwayModeStateManager.updateRunwayStatuses(runwayStatuses, minimumSpacingNm)
    }

    override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        if (airportIcao != this.airportIcao) return
        this.minimumSpacingNm = minimumSpacingNm
        runwayModeStateManager.updateMinimumSpacing(minimumSpacingNm)
        view.updateMinimumSpacing(minimumSpacingNm)
    }

    override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
        if (airportIcao != this.airportIcao) return
        view.updateWeatherData(data)
    }

    override fun onNonSequencedListUpdated(airportIcao: String, nonSequencedList: List<NonSequencedEvent>) {
        if (airportIcao != this.airportIcao) return
        cachedNonSequencedEvents = nonSequencedList
        updateViewFromCache()
    }

    // AirportPresenterInterface implementations
    override fun onLabelDrag(timelineEvent: TimelineEvent, newInstant: Instant) {
        if (timelineEvent !is RunwayArrivalEvent) return

        plannerService.isTimeSlotAvailable(timelineEvent, newInstant, timelineEvent.runway)
            .onSuccess { view.updateDraggedLabel(timelineEvent, newInstant, it) }
            .onFailure { handleServiceError(it, "Failed to check time slot availability") }
    }

    override fun onLabelDragEnd(timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String?) {
        plannerService.suggestScheduledTime(timelineEvent, newScheduledTime, newRunway)
            .onFailure { handleServiceError(it, "Failed to move aircraft") }
    }

    override fun onRecalculateSequenceClicked(callSign: String?) {
        plannerService.reSchedule(callSign)
            .onFailure { handleServiceError(it, "Failed to re-schedule") }
    }

    override fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double) {
        plannerService.setMinimumSpacing(minimumSpacingDistanceNm)
            .onFailure { handleServiceError(it, "Failed to set minimum spacing") }
    }

    override fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?) {
        view.showMinimumSpacingDialog(minSpacingSelectionNm ?: minimumSpacingNm)
    }

    override fun onOpenMetWindowClicked() {
        view.openMetWindow()
    }

    override fun onOpenLandingRatesWindow() {
        view.openLandingRatesWindow()
    }

    override fun onOpenNonSequencedWindow() {
        view.openNonSequencedWindow()
    }

    override fun onOpenVerticalProfileWindowClicked(callsign: String) {
        onOpenVerticalProfileCallback(callsign)
    }

    override fun onAircraftSelected(callsign: String) {
        onAircraftSelectedCallback(callsign)
    }

    override fun beginRunwaySelection(runwayEvent: RunwayEvent, onSubmit: (runway: String?) -> Unit, onCancel: () -> Unit) {
        if (runwayEvent is RunwayArrivalEvent) {
            val controllerInfo = controllerInfoProvider()
            val imTheTrackingController = controllerInfo?.callsign != null &&
                    runwayEvent.trackingController == controllerInfo.positionId

            if (imTheTrackingController) {
                view.openSelectRunwayDialog(runwayEvent, availableRunways, onSubmit, onCancel)
            } else {
                onSubmit(null)
                logger.debug(
                    "User is not the tracking controller for ${runwayEvent.callsign}, will not prompt for runway. " +
                            "Tracking controller is ${runwayEvent.trackingController}, my positionId is ${controllerInfo?.positionId}"
                )
            }
        } else {
            logger.error("selectRunway called with unsupported event type")
        }
    }

    override fun onToggleShowDepartures(selected: Boolean) {
        plannerService.setShowDepartures(selected)
    }

    override fun onReloadWindsClicked() {
        plannerService.refreshWeatherData()
            .onFailure { handleServiceError(it, "Failed to reload winds") }
    }

    override fun onTabMenu(screenPos: Point) {
        val availableTimelinesForIcao = timelineConfigs.values.toList()
        view.showAirportContextMenu(availableTimelinesForIcao, screenPos)
    }

    override fun onCreateNewTimelineClicked() {
        view.openTimelineConfigForm(
            availableTagLayoutsDep = SettingsRepository.getSettings().departureLabelLayouts.keys,
            availableTagLayoutsArr = SettingsRepository.getSettings().arrivalLabelLayouts.keys,
        )
    }

    override fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig) {
        view.addNewTimeline(timelineConfig)
        view.closeTimelineForm()
    }

    override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {
        view.removeTimeline(timelineConfig)
    }

    override fun onEditTimelineRequested(timelineTitle: String) {
        val existingConfig = timelineConfigs[timelineTitle]
        if (existingConfig != null) {
            view.openTimelineConfigForm(
                availableTagLayoutsDep = SettingsRepository.getSettings().departureLabelLayouts.keys,
                availableTagLayoutsArr = SettingsRepository.getSettings().arrivalLabelLayouts.keys,
                existingConfig = existingConfig
            )
        }
    }

    override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {
        val timelineConfig = TimelineConfig(
            title = config.title,
            runwaysLeft = config.left.targetRunways,
            runwaysRight = config.right.targetRunways,
            airportIcao = airportIcao,
            depLabelLayout = config.depLabelLayout,
            arrLabelLayout = config.arrLabelLayout
        )
        view.addNewTimeline(timelineConfig)
        view.closeTimelineForm()
    }

    override fun onRemoveTab() {
        onRemove()
    }

    private fun handleServiceError(error: Throwable, defaultMessage: String) {
        when (error) {
            is UnsupportedInSlaveModeException -> showErrorMessage(error.msg)
            else -> showErrorMessage("$defaultMessage: ${error.message}")
        }
    }

    private data class CachedTimelineEvent(
        val lastTimestamp: Instant,
        val timelineEvent: TimelineEvent
    )
}
