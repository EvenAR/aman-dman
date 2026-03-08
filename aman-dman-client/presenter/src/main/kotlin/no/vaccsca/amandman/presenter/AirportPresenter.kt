package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.MeteringPointTimelineConfig
import no.vaccsca.amandman.common.RunwayTimelineConfig
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.planning.AirportDataSource
import no.vaccsca.amandman.model.sharedstate.DataUpdateListener
import no.vaccsca.amandman.model.planning.SequencePlanner
import no.vaccsca.amandman.model.timeline.MeteringPointState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayFlightEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import org.slf4j.LoggerFactory
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

class AirportPresenter(
    override val airportIcao: String,
    private val dataSource: AirportDataSource,
    private val userRole: UserRole,
    private val view: AirportViewInterface,
    private val settingsProvider: SettingsProvider,
    private val controllerInfoProvider: () -> ControllerInfoData?,
    private val showErrorMessage: (String) -> Unit,
    private val onAircraftSelectedCallback: (String) -> Unit,
    private val onOpenVerticalProfileCallback: (String) -> Unit,
    private val onRemove: () -> Unit,
    private val uiDispatcher: UiDispatcher,
) : AirportPresenterInterface, DataUpdateListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val sequencePlanner: SequencePlanner? = dataSource as? SequencePlanner
    private val isReadOnly: Boolean = dataSource.isReadOnly

    private val cachedTimelineEvents = mutableMapOf<String, CachedTimelineEvent>()
    private var cachedNonSequencedEvents: List<NonSequencedEvent> = emptyList()
    private val runwayModeStateManager = AirportRunwayModeStateManager(airportIcao, view)
    private var minimumSpacingNm: Double = 3.0
    private var availableRunways = setOf<String>()
    private var meteringPointState: MeteringPointState = MeteringPointState()
    private val timelineConfigs = mutableMapOf<String, TimelineConfig>()
    private var editingTimelineConfig: TimelineConfig? = null
    private var hasLoggedMissingMeteringTimelineLayout = false

    init {
        view.airportPresenterInterface = this
        loadTimelineConfigsForAirport()
    }

    private fun loadTimelineConfigsForAirport() {
        val airportTimelines = settingsProvider.getSettings().timelines[airportIcao] ?: return
        airportTimelines.runwayBased.forEach { timeline ->
            val config = RunwayTimelineConfig(
                title = timeline.title,
                airportIcao = airportIcao,
                leftRunways = timeline.left,
                rightRunways = timeline.right,
                depLabelLayout = timeline.departureLabelLayoutId,
                arrLabelLayout = timeline.arrivalLabelLayoutId,
            )
            timelineConfigs[timeline.title] = config
        }
        airportTimelines.meteringPointBased.forEach { timeline ->
            val config = MeteringPointTimelineConfig(
                title = timeline.title,
                airportIcao = airportIcao,
                leftMeteringPoints = timeline.left,
                rightMeteringPoints = timeline.right,
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
        runOnEdt { updateViewFromCacheOnEdt() }
    }

    private fun updateViewFromCacheOnEdt() {
        val cutoffTime = NtpClock.now() - 5.seconds
        cachedTimelineEvents.entries.removeIf { it.value.lastTimestamp < cutoffTime }

        val timelineEvents = cachedTimelineEvents.values.map { it.timelineEvent }
        view.updateTab(timelineEvents, cachedNonSequencedEvents)
        pushIntegrationStatuses()
    }

    // DataUpdateListener implementations
    override fun onTimelineEventsUpdated(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        if (airportIcao != this.airportIcao) return

        runOnEdt {
            timelineEvents.filterIsInstance<RunwayFlightEvent>().forEach {
                cachedTimelineEvents[it.callsign] = CachedTimelineEvent(
                    lastTimestamp = NtpClock.now(),
                    timelineEvent = it
                )
            }
            updateViewFromCacheOnEdt()
        }
    }

    override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
        if (airportIcao != this.airportIcao) return
        runOnEdt {
            availableRunways = runwayStatuses.keys
            runwayModeStateManager.updateRunwayStatuses(runwayStatuses, minimumSpacingNm)
        }
    }

    override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        if (airportIcao != this.airportIcao) return
        runOnEdt {
            this.minimumSpacingNm = minimumSpacingNm
            runwayModeStateManager.updateMinimumSpacing(minimumSpacingNm)
            view.updateMinimumSpacing(minimumSpacingNm)
        }
    }

    override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
        if (airportIcao != this.airportIcao) return
        runOnEdt {
            view.updateWeatherData(data)
        }
    }

    override fun onNonSequencedListUpdated(airportIcao: String, nonSequencedList: List<NonSequencedEvent>) {
        if (airportIcao != this.airportIcao) return
        runOnEdt {
            cachedNonSequencedEvents = nonSequencedList
            updateViewFromCacheOnEdt()
        }
    }

    override fun onMeteringPointStateUpdated(airportIcao: String, meteringPointState: MeteringPointState) {
        if (airportIcao != this.airportIcao) return
        runOnEdt {
            this.meteringPointState = meteringPointState
            view.updateMeteringPointState(meteringPointState)
        }
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
        val customizedTimelines = timelineConfigs.values.toList()
        val generatedMeteringPointTimelines = buildGeneratedMeteringPointTimelines()
        view.showAirportContextMenu(customizedTimelines, generatedMeteringPointTimelines, screenPos)
    }

    override fun onCreateNewTimelineClicked() {
        editingTimelineConfig = null
        view.openTimelineConfigForm(
            availableTagLayoutsDep = settingsProvider.getSettings().departureLabelLayouts.keys,
            availableTagLayoutsArr = settingsProvider.getSettings().arrivalLabelLayouts.keys,
            availableRunways = getKnownRunways(),
            availableMeteringPoints = getKnownMeteringPoints(),
        )
    }

    override fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig) {
        editingTimelineConfig = null
        view.addNewTimeline(timelineConfig)
        view.closeTimelineForm()
    }

    override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {
        view.removeTimeline(timelineConfig)
    }

    override fun onEditTimelineRequested(timelineConfig: TimelineConfig) {
        editingTimelineConfig = timelineConfig
        view.openTimelineConfigForm(
            availableTagLayoutsDep = settingsProvider.getSettings().departureLabelLayouts.keys,
            availableTagLayoutsArr = settingsProvider.getSettings().arrivalLabelLayouts.keys,
            availableRunways = getKnownRunways(),
            availableMeteringPoints = getKnownMeteringPoints(),
            existingConfig = timelineConfig
        )
    }

    override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {
        val timelineConfig: TimelineConfig = when (config) {
            is CreateOrUpdateTimelineDto.Runway -> RunwayTimelineConfig(
                title = config.title,
                airportIcao = airportIcao,
                leftRunways = config.left,
                rightRunways = config.right,
                depLabelLayout = config.depLabelLayout,
                arrLabelLayout = config.arrLabelLayout,
            )
            is CreateOrUpdateTimelineDto.MeteringPoint -> MeteringPointTimelineConfig(
                title = config.title,
                airportIcao = airportIcao,
                leftMeteringPoints = config.left,
                rightMeteringPoints = config.right,
                arrLabelLayout = config.arrLabelLayout,
            )
        }
        editingTimelineConfig?.let { view.removeTimeline(it) }
        editingTimelineConfig = null
        view.addNewTimeline(timelineConfig)
        view.closeTimelineForm()
    }

    override fun onRemoveTab() {
        onRemove()
    }

    fun onAircraftSelectionChanged(callsign: String) {
        runOnEdt {
            view.setSelectedAircraftCallsign(callsign)
        }
    }

    private fun showReadOnlyMessage() {
        showErrorMessage("This operation is not available in read-only mode")
    }

    private fun pushIntegrationStatuses() {
        val statuses = dataSource.getIntegrationStatuses()
        val orderedKinds = listOf(IntegrationKind.ATC, IntegrationKind.CDM, IntegrationKind.SERVER, IntegrationKind.MET)
        val display = linkedMapOf<IntegrationKind, IntegrationDisplayStatus>()

        orderedKinds.forEach { kind ->
            val status = statuses.get(kind)
            if (!status.relevant) return@forEach

            val label = when (kind) {
                IntegrationKind.SERVER -> when (userRole) {
                    UserRole.MASTER -> "SRV M"
                    UserRole.SLAVE -> "SRV S"
                    UserRole.LOCAL -> "SRV"
                }
                IntegrationKind.ATC -> "ATC"
                IntegrationKind.CDM -> "CDM"
                IntegrationKind.MET -> "MET"
            }

            display[kind] = IntegrationDisplayStatus(label, status)
        }
        view.updateIntegrationStatuses(display)
    }

    private fun getKnownRunways(): Set<String> {
        if (availableRunways.isNotEmpty()) {
            return availableRunways.map { it.uppercase() }.toSet()
        }

        return settingsProvider.getAirportData()
            .find { it.icao == airportIcao }
            ?.runways
            ?.keys
            ?.map { it.uppercase() }
            ?.toSet()
            ?: emptySet()
    }

    private fun getKnownMeteringPoints(): Set<String> {
        if (meteringPointState.availableMeteringPoints.isNotEmpty()) {
            return meteringPointState.availableMeteringPoints.map { it.uppercase() }.toSet()
        }

        return settingsProvider.getAirportData()
            .find { it.icao == airportIcao }
            ?.meteringPoints
            ?.map { it.uppercase() }
            ?.toSet()
            ?: emptySet()
    }

    private fun buildGeneratedMeteringPointTimelines(): List<TimelineConfig> {
        val knownMeteringPoints = getKnownMeteringPoints()
            .map { it.uppercase() }
            .sorted()
        if (knownMeteringPoints.isEmpty()) {
            return emptyList()
        }

        val airport = settingsProvider.getAirportData().find { it.icao == airportIcao } ?: return emptyList()
        val arrivalLayout = airport.meteringTimelineArrivalLabelLayoutId
        if (arrivalLayout.isNullOrBlank()) {
            if (!hasLoggedMissingMeteringTimelineLayout) {
                logger.warn(
                    "Airport $airportIcao has metering points but no meteringTimelineArrivalLabelLayoutId configured. " +
                        "Metering point auto-timelines are disabled."
                )
                hasLoggedMissingMeteringTimelineLayout = true
            }
            return emptyList()
        }

        return knownMeteringPoints.map { meteringPoint ->
            MeteringPointTimelineConfig(
                title = meteringPoint,
                airportIcao = airportIcao,
                leftMeteringPoints = emptyList(),
                rightMeteringPoints = listOf(meteringPoint),
                arrLabelLayout = arrivalLayout
            )
        }
    }

    private data class CachedTimelineEvent(
        val lastTimestamp: Instant,
        val timelineEvent: TimelineEvent
    )

    private fun runOnEdt(action: () -> Unit) {
        if (uiDispatcher.isUiThread()) {
            action()
        } else {
            uiDispatcher.dispatch(action)
        }
    }
}
