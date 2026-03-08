package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.FeederFixTimelineConfig
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.RunwayTimelineConfig
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.config.AirportTimelines
import no.vaccsca.amandman.model.config.FeederFixTimeline
import no.vaccsca.amandman.model.config.RunwayTimeline
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.config.TimelineDefaults
import no.vaccsca.amandman.model.config.TimelineSettingsStore
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.planning.AirportDataSource
import no.vaccsca.amandman.model.planning.SequencePlanner
import no.vaccsca.amandman.model.sharedstate.DataUpdateListener
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayFlightEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import org.slf4j.LoggerFactory
import java.awt.Point
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class AirportPresenter(
    override val airportIcao: String,
    private val dataSource: AirportDataSource,
    private val userRole: UserRole,
    private val view: AirportViewInterface,
    private val settingsProvider: SettingsProvider,
    private val timelineSettingsStore: TimelineSettingsStore,
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
    private var feederFixState: FeederFixState = FeederFixState()
    private val savedTimelineConfigs = mutableListOf<TimelineConfig>()
    private val liveTimelineSavedOrigins = mutableMapOf<TimelineConfig, TimelineConfig>()
    private var editingTimelineConfig: TimelineConfig? = null
    private var editingSavedTimelineConfig: TimelineConfig? = null
    private var hasLoggedMissingFixTimelineLayout = false

    init {
        view.airportPresenterInterface = this
        loadTimelineConfigsForAirport()
    }

    private fun loadTimelineConfigsForAirport() {
        savedTimelineConfigs.clear()
        val airportTimelines = timelineSettingsStore.getTimelines().getValueOrNull(airportIcao) ?: return
        airportTimelines.runwayBased.forEach { timeline ->
            savedTimelineConfigs += timeline.toViewConfig()
        }
        airportTimelines.feederFixBased.forEach { timeline ->
            savedTimelineConfigs += timeline.toViewConfig()
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

    override fun onFeederFixStateUpdated(airportIcao: String, feederFixState: FeederFixState) {
        if (airportIcao != this.airportIcao) return
        runOnEdt {
            this.feederFixState = feederFixState
            view.updateFeederFixState(feederFixState)
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
        val customizedTimelines = savedTimelineConfigs.toList()
        val generatedFixTimelines = buildGeneratedFixTimelines()
        view.showAirportContextMenu(customizedTimelines, generatedFixTimelines, screenPos)
    }

    override fun onCreateNewTimelineClicked() {
        editingTimelineConfig = null
        editingSavedTimelineConfig = null
        view.openTimelineConfigForm(
            availableTagLayoutsDep = settingsProvider.getSettings().departureLabelLayouts.keys,
            availableTagLayoutsArr = settingsProvider.getSettings().arrivalLabelLayouts.keys,
            availableRunways = getKnownRunways(),
            availableFixes = getKnownFixes(),
        )
    }

    override fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig) {
        editingTimelineConfig = null
        editingSavedTimelineConfig = null
        trackLiveTimelineSavedOrigin(timelineConfig, findSavedTimelineConfig(timelineConfig))
        view.addNewTimeline(timelineConfig)
        view.closeTimelineForm()
    }

    override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {
        liveTimelineSavedOrigins.remove(timelineConfig)
        view.removeTimeline(timelineConfig)
    }

    override fun onEditTimelineRequested(timelineConfig: TimelineConfig) {
        editingTimelineConfig = timelineConfig
        editingSavedTimelineConfig = liveTimelineSavedOrigins[timelineConfig] ?: findSavedTimelineConfig(timelineConfig)
        view.openTimelineConfigForm(
            availableTagLayoutsDep = settingsProvider.getSettings().departureLabelLayouts.keys,
            availableTagLayoutsArr = settingsProvider.getSettings().arrivalLabelLayouts.keys,
            availableRunways = getKnownRunways(),
            availableFixes = getKnownFixes(),
            existingConfig = timelineConfig,
            canDeleteExistingConfig = editingSavedTimelineConfig != null,
        )
    }

    override fun onMoveTimelineLeftRequested(timelineConfig: TimelineConfig) {
        view.moveTimeline(timelineConfig, positions = -1)
    }

    override fun onMoveTimelineRightRequested(timelineConfig: TimelineConfig) {
        view.moveTimeline(timelineConfig, positions = 1)
    }
    override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {
        val updatedTimelineConfig = config.toViewConfig()
        val previousTimelineConfig = editingTimelineConfig
        val previousSavedTimelineConfig = editingSavedTimelineConfig

        val currentAirportTimelines = try {
            timelineSettingsStore.getTimelines(reload = true).getValueOrNull(airportIcao)
        } catch (e: Exception) {
            showErrorMessage("Unable to save timeline: ${e.message}")
            return
        }
        val currentSavedTimelineConfigs = currentAirportTimelines?.toSavedViewConfigs() ?: savedTimelineConfigs.toList()
        val conflictingSavedTimelineConfig = findConflictingSavedTimeline(
            timelineConfig = updatedTimelineConfig,
            previousSavedTimelineConfig = previousSavedTimelineConfig,
            currentSavedTimelineConfigs = currentSavedTimelineConfigs,
        )

        if (conflictingSavedTimelineConfig != null && !view.confirmTimelineOverwrite(conflictingSavedTimelineConfig.title)) {
            return
        }

        val conflictingLiveTimelineConfig = conflictingSavedTimelineConfig?.let { findLiveTimelineForSavedTimeline(it) }
        val savedTimelinesToReplace = listOfNotNull(previousSavedTimelineConfig, conflictingSavedTimelineConfig)
            .distinctBy { it.timelineId ?: it.title }

        try {
            val updatedAirportTimelines = buildUpdatedAirportTimelines(
                currentAirportTimelines = currentAirportTimelines,
                updatedTimelineConfig = updatedTimelineConfig,
                savedTimelinesToReplace = savedTimelinesToReplace,
            )
            timelineSettingsStore.saveAirportTimelines(airportIcao, updatedAirportTimelines)
            refreshSavedTimelineConfigs(updatedAirportTimelines)
        } catch (e: Exception) {
            showErrorMessage("Unable to save timeline: ${e.message}")
            return
        }

        when {
            previousTimelineConfig != null -> {
                if (conflictingLiveTimelineConfig != null && conflictingLiveTimelineConfig != previousTimelineConfig) {
                    liveTimelineSavedOrigins.remove(conflictingLiveTimelineConfig)
                    view.removeTimeline(conflictingLiveTimelineConfig)
                }
                liveTimelineSavedOrigins.remove(previousTimelineConfig)
                view.replaceTimeline(previousTimelineConfig, updatedTimelineConfig)
            }
            conflictingLiveTimelineConfig != null -> {
                liveTimelineSavedOrigins.remove(conflictingLiveTimelineConfig)
                view.replaceTimeline(conflictingLiveTimelineConfig, updatedTimelineConfig)
            }
            else -> view.addNewTimeline(updatedTimelineConfig)
        }
        trackLiveTimelineSavedOrigin(updatedTimelineConfig, findSavedTimelineConfig(updatedTimelineConfig))
        editingTimelineConfig = null
        editingSavedTimelineConfig = null
        view.closeTimelineForm()
    }

    override fun onDeleteEditedTimeline() {
        val savedTimelineToDelete = editingSavedTimelineConfig ?: run {
            showErrorMessage("Only saved timelines can be deleted")
            return
        }

        try {
            val currentAirportTimelines = timelineSettingsStore.getTimelines(reload = true).getValueOrNull(airportIcao)
                ?: throw IllegalArgumentException("No saved timelines exist for $airportIcao")
            val updatedAirportTimelines = buildDeletedAirportTimelines(currentAirportTimelines, savedTimelineToDelete)
            timelineSettingsStore.saveAirportTimelines(airportIcao, updatedAirportTimelines)
            refreshSavedTimelineConfigs(updatedAirportTimelines)
        } catch (e: Exception) {
            showErrorMessage("Unable to delete timeline: ${e.message}")
            return
        }

        (editingTimelineConfig ?: savedTimelineToDelete).let { liveTimelineConfig ->
            liveTimelineSavedOrigins.remove(liveTimelineConfig)
            view.removeTimeline(liveTimelineConfig)
        }
        editingTimelineConfig = null
        editingSavedTimelineConfig = null
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

    private fun getKnownFixes(): Set<String> {
        if (feederFixState.availableFixes.isNotEmpty()) {
            return feederFixState.availableFixes.map { it.uppercase() }.toSet()
        }

        return settingsProvider.getAirportData()
            .find { it.icao == airportIcao }
            ?.feederFixes
            ?.map { it.uppercase() }
            ?.toSet()
            ?: emptySet()
    }

    private fun buildGeneratedFixTimelines(): List<TimelineConfig> {
        val knownFixes = getKnownFixes()
            .map { it.uppercase() }
            .sorted()
        if (knownFixes.isEmpty()) {
            return emptyList()
        }

        val airport = settingsProvider.getAirportData().find { it.icao == airportIcao } ?: return emptyList()
        val arrivalLayout = airport.feederFixTimelineArrivalLabelLayoutId
        if (arrivalLayout.isNullOrBlank()) {
            if (!hasLoggedMissingFixTimelineLayout) {
                logger.warn(
                    "Airport $airportIcao has fixes but no feederFixTimelineArrivalLabelLayoutId configured. " +
                        "Feeder fix auto-timelines are disabled."
                )
                hasLoggedMissingFixTimelineLayout = true
            }
            return emptyList()
        }

        return knownFixes.map { feederFix ->
            FeederFixTimelineConfig(
                title = feederFix,
                airportIcao = airportIcao,
                leftFixes = emptyList(),
                rightFixes = listOf(feederFix),
                arrLabelLayout = arrivalLayout
            )
        }
    }
    private fun buildUpdatedAirportTimelines(
        currentAirportTimelines: AirportTimelines?,
        updatedTimelineConfig: TimelineConfig,
        savedTimelinesToReplace: List<TimelineConfig>,
    ): AirportTimelines {
        val runwayTimelines = currentAirportTimelines?.runwayBased?.toMutableList() ?: mutableListOf()
        val feederFixTimelines = currentAirportTimelines?.feederFixBased?.toMutableList() ?: mutableListOf()

        savedTimelinesToReplace.forEach {
            removeSavedTimeline(it, runwayTimelines, feederFixTimelines)
        }
        removeSavedTimeline(updatedTimelineConfig, runwayTimelines, feederFixTimelines)
        addSavedTimeline(updatedTimelineConfig, runwayTimelines, feederFixTimelines)
        validateUniqueTimelineTitles(runwayTimelines, feederFixTimelines)

        val defaults = currentAirportTimelines?.defaults ?: defaultTimelinesFor(updatedTimelineConfig)
        return AirportTimelines(
            defaults = defaults,
            runwayBased = runwayTimelines,
            feederFixBased = feederFixTimelines,
        )
    }

    private fun buildDeletedAirportTimelines(
        currentAirportTimelines: AirportTimelines,
        savedTimelineToDelete: TimelineConfig,
    ): AirportTimelines? {
        val runwayTimelines = currentAirportTimelines.runwayBased.toMutableList()
        val feederFixTimelines = currentAirportTimelines.feederFixBased.toMutableList()

        removeSavedTimeline(savedTimelineToDelete, runwayTimelines, feederFixTimelines)
        if (runwayTimelines.isEmpty() && feederFixTimelines.isEmpty()) {
            return null
        }

        return AirportTimelines(
            defaults = currentAirportTimelines.defaults,
            runwayBased = runwayTimelines,
            feederFixBased = feederFixTimelines,
        )
    }

    private fun refreshSavedTimelineConfigs(airportTimelines: AirportTimelines?) {
        savedTimelineConfigs.clear()
        airportTimelines?.runwayBased?.forEach { savedTimelineConfigs += it.toViewConfig() }
        airportTimelines?.feederFixBased?.forEach { savedTimelineConfigs += it.toViewConfig() }
    }

    private fun findSavedTimelineConfig(timelineConfig: TimelineConfig): TimelineConfig? =
        timelineConfig.timelineId
            ?.let { timelineId -> savedTimelineConfigs.find { it.timelineId == timelineId } }
            ?: findSavedTimelineConfigByTitle(timelineConfig.title)
    private fun findSavedTimelineConfigByTitle(
        title: String,
        currentSavedTimelineConfigs: List<TimelineConfig> = savedTimelineConfigs,
    ): TimelineConfig? = currentSavedTimelineConfigs.find { it.title == title }

    private fun findConflictingSavedTimeline(
        timelineConfig: TimelineConfig,
        previousSavedTimelineConfig: TimelineConfig?,
        currentSavedTimelineConfigs: List<TimelineConfig>,
    ): TimelineConfig? {
        val conflictingTimeline = findSavedTimelineConfigByTitle(timelineConfig.title, currentSavedTimelineConfigs) ?: return null
        val previousSavedTimelineId = previousSavedTimelineConfig?.timelineId
        if (conflictingTimeline.timelineId != null && conflictingTimeline.timelineId == previousSavedTimelineId) {
            return null
        }
        return if (conflictingTimeline == previousSavedTimelineConfig) null else conflictingTimeline
    }

    private fun AirportTimelines.toSavedViewConfigs(): List<TimelineConfig> =
        runwayBased.map { it.toViewConfig() } + feederFixBased.map { it.toViewConfig() }

    private fun findLiveTimelineForSavedTimeline(savedTimelineConfig: TimelineConfig): TimelineConfig? {
        val savedTimelineId = savedTimelineConfig.timelineId
        return liveTimelineSavedOrigins.entries.firstOrNull { (_, liveOrigin) ->
            if (savedTimelineId != null && liveOrigin.timelineId != null) {
                liveOrigin.timelineId == savedTimelineId
            } else {
                liveOrigin.title == savedTimelineConfig.title
            }
        }?.key
    }

    private fun trackLiveTimelineSavedOrigin(
        timelineConfig: TimelineConfig,
        savedTimelineConfig: TimelineConfig?,
    ) {
        if (savedTimelineConfig == null) {
            liveTimelineSavedOrigins.remove(timelineConfig)
        } else {
            liveTimelineSavedOrigins[timelineConfig] = savedTimelineConfig
        }
    }

    private fun addSavedTimeline(
        timelineConfig: TimelineConfig,
        runwayTimelines: MutableList<RunwayTimeline>,
        feederFixTimelines: MutableList<FeederFixTimeline>,
    ) {
        when (timelineConfig) {
            is RunwayTimelineConfig -> runwayTimelines += timelineConfig.toDomain()
            is FeederFixTimelineConfig -> feederFixTimelines += timelineConfig.toDomain()
        }
    }

    private fun removeSavedTimeline(
        timelineConfig: TimelineConfig,
        runwayTimelines: MutableList<RunwayTimeline>,
        feederFixTimelines: MutableList<FeederFixTimeline>,
    ) {
        val timelineId = timelineConfig.timelineId
        if (timelineId != null) {
            runwayTimelines.removeAll { it.timelineId == timelineId }
            feederFixTimelines.removeAll { it.timelineId == timelineId }
            return
        }

        runwayTimelines.removeAll { it.title == timelineConfig.title }
        feederFixTimelines.removeAll { it.title == timelineConfig.title }
    }

    private fun validateUniqueTimelineTitles(
        runwayTimelines: List<RunwayTimeline>,
        feederFixTimelines: List<FeederFixTimeline>,
    ) {
        val duplicates = (runwayTimelines.map { it.title } + feederFixTimelines.map { it.title })
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("Timeline title already exists: ${duplicates.joinToString(", ")}")
        }
    }

    private fun defaultTimelinesFor(timelineConfig: TimelineConfig): TimelineDefaults = when (timelineConfig) {
        is RunwayTimelineConfig -> TimelineDefaults(
            defaultArrivalLabelLayoutId = timelineConfig.arrLabelLayout,
            defaultDepartureLabelLayoutId = timelineConfig.depLabelLayout,
        )

        is FeederFixTimelineConfig -> TimelineDefaults(
            defaultArrivalLabelLayoutId = timelineConfig.arrLabelLayout,
            defaultDepartureLabelLayoutId = null,
        )
    }

    private fun RunwayTimeline.toViewConfig(): TimelineConfig = RunwayTimelineConfig(
        title = title,
        airportIcao = airportIcao,
        leftRunways = left,
        rightRunways = right,
        depLabelLayout = departureLabelLayoutId,
        arrLabelLayout = arrivalLabelLayoutId,
        timelineId = timelineId,
    )

    private fun FeederFixTimeline.toViewConfig(): TimelineConfig = FeederFixTimelineConfig(
        title = title,
        airportIcao = airportIcao,
        leftFixes = left,
        rightFixes = right,
        arrLabelLayout = arrivalLabelLayoutId,
        timelineId = timelineId,
    )

    private fun CreateOrUpdateTimelineDto.toViewConfig(): TimelineConfig = when (this) {
        is CreateOrUpdateTimelineDto.Runway -> RunwayTimelineConfig(
            title = title,
            airportIcao = airportIcao,
            leftRunways = left,
            rightRunways = right,
            depLabelLayout = depLabelLayout,
            arrLabelLayout = arrLabelLayout,
            timelineId = timelineId,
        )

        is CreateOrUpdateTimelineDto.FeederFix -> FeederFixTimelineConfig(
            title = title,
            airportIcao = airportIcao,
            leftFixes = left,
            rightFixes = right,
            arrLabelLayout = arrLabelLayout,
            timelineId = timelineId,
        )
    }

    private fun RunwayTimelineConfig.toDomain(): RunwayTimeline = RunwayTimeline(
        title = title,
        left = leftRunways,
        right = rightRunways,
        arrivalLabelLayoutId = arrLabelLayout,
        departureLabelLayoutId = depLabelLayout,
        timelineId = timelineId ?: generateTimelineId(),
    )

    private fun FeederFixTimelineConfig.toDomain(): FeederFixTimeline = FeederFixTimeline(
        title = title,
        left = leftFixes,
        right = rightFixes,
        arrivalLabelLayoutId = arrLabelLayout,
        timelineId = timelineId ?: generateTimelineId(),
    )

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

    private fun generateTimelineId(): String = UUID.randomUUID().toString()

    private fun <K, V> Map<K, V>.getValueOrNull(key: K): V? = if (containsKey(key)) getValue(key) else null
}
