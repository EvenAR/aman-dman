package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.config.SettingsRepository
import no.vaccsca.amandman.model.planning.AirportDataSource
import no.vaccsca.amandman.model.sharedstate.DataUpdateListener
import no.vaccsca.amandman.model.planning.SequencePlanner
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayFlightEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import org.slf4j.LoggerFactory
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

class AirportPresenter(
    override val airportIcao: String,
    private val dataSource: AirportDataSource,
    private val view: AirportViewInterface,
    private val controllerInfoProvider: () -> ControllerInfoData?,
    private val showErrorMessage: (String) -> Unit,
    private val onAircraftSelectedCallback: (String) -> Unit,
    private val onOpenVerticalProfileCallback: (String) -> Unit,
    private val onRemove: () -> Unit,
) : AirportPresenterInterface, DataUpdateListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val sequencePlanner: SequencePlanner? = dataSource as? SequencePlanner
    private val isReadOnly: Boolean = dataSource.isReadOnly

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
        dataSource.start()
        dataSource.startDataCollection()
    }

    fun stop() {
        dataSource.stop()
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

        sequencePlanner?.let { planner ->
            val isAvailable = planner.isTimeSlotAvailable(timelineEvent, newInstant, timelineEvent.runway)
            view.updateDraggedLabel(timelineEvent, newInstant, isAvailable)
        } ?: run {
            showReadOnlyMessage()
        }
    }

    override fun onLabelDragEnd(timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String?) {
        sequencePlanner?.suggestScheduledTime(timelineEvent, newScheduledTime, newRunway)
            ?: showReadOnlyMessage()
    }

    override fun onRecalculateSequenceClicked(callSign: String?) {
        sequencePlanner?.reSchedule(callSign)
            ?: showReadOnlyMessage()
    }

    override fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double) {
        sequencePlanner?.setMinimumSpacing(minimumSpacingDistanceNm)
            ?: showReadOnlyMessage()
    }

    override fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?) {
        if (isReadOnly) {
            showReadOnlyMessage()
            return
        }
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
        if (isReadOnly) {
            onSubmit(null)
            return
        }

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
        sequencePlanner?.setShowDepartures(selected)
            ?: showReadOnlyMessage()
    }

    override fun onReloadWindsClicked() {
        sequencePlanner?.refreshWeatherData()
            ?: showReadOnlyMessage()
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

    fun onAircraftSelectionChanged(callsign: String) {
        view.setSelectedAircraftCallsign(callsign)
    }

    private fun showReadOnlyMessage() {
        showErrorMessage("This operation is not available in read-only mode")
    }

    private data class CachedTimelineEvent(
        val lastTimestamp: Instant,
        val timelineEvent: TimelineEvent
    )
}
