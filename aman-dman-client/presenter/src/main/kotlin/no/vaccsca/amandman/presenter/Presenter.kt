package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.UserRole
import no.vaccsca.amandman.model.data.dto.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.data.dto.TabData
import no.vaccsca.amandman.model.data.integration.AtcClientEuroScope
import no.vaccsca.amandman.model.data.integration.SharedStateHttpClient
import no.vaccsca.amandman.model.data.repository.CdmClient
import no.vaccsca.amandman.model.data.repository.SettingsRepository
import no.vaccsca.amandman.model.data.repository.WeatherDataRepository
import no.vaccsca.amandman.model.domain.PlannerManager
import no.vaccsca.amandman.model.domain.TimelineGroup
import no.vaccsca.amandman.model.domain.exception.UnsupportedInSlaveModeException
import no.vaccsca.amandman.model.domain.service.DataUpdateListener
import no.vaccsca.amandman.model.domain.service.DataUpdatesServerSender
import no.vaccsca.amandman.model.domain.service.PlannerServiceMaster
import no.vaccsca.amandman.model.domain.service.PlannerServiceSlave
import no.vaccsca.amandman.model.domain.service.WeatherFetchStatus
import no.vaccsca.amandman.model.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.model.domain.valueobjects.TimelineData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayFlightEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

class Presenter(
    private val plannerManager: PlannerManager,
    private val view: ViewInterface,
    private val guiUpdater: DataUpdateListener,
) : PresenterInterface, DataUpdateListener {

    private val timelineGroups = mutableListOf<TimelineGroup>()
    private var selectedCallsign: String? = null

    private var timelineConfigs = mutableMapOf<String, TimelineConfig>()

    private val cachedAmanData = mutableMapOf<String, CachedEvent>()

    // Replace the simple currentMinimumSpacingNm with a reactive state manager
    private val runwayModeStateManager = RunwayModeStateManager(view)
    private var minimumSpacingNm: Double = 3.0
    private var availableRunways = setOf<String>()
    private var controllerInfo: ControllerInfoData? = null
    private val myMasterRoles = mutableSetOf<String>()

    private val atcClient by lazy {
        AtcClientEuroScope(
            controllerInfoCallback = { info -> handleControllerInfoUpdate(info) }
        )
    }

    private val sharedState by lazy {
        SharedStateHttpClient()
    }

    private val dataUpdatesServerSender by lazy {
        DataUpdatesServerSender(sharedState)
    }

    private val weatherDataRepository by lazy {
        WeatherDataRepository()
    }

    private val cdmClient by lazy {
        CdmClient()
    }

    init {
        view.presenterInterface = this

        javax.swing.Timer(1000) {
            view.updateTime(NtpClock.now())

            updateViewFromCachedData()

            // Update EuroScope connection status
            val lastReceivedTimestamp = atcClient.getLastReceivedTimestamp()
            view.updateAtcClientConnectionStatus(
                isConnected = atcClient.isClientConnected,
                hasStarted = atcClient.hasStarted,
                lastReceivedTimestamp = lastReceivedTimestamp,
            )

            myMasterRoles.forEach { airportIcao ->
                if (!sharedState.checkMasterRoleStatus(airportIcao)) {
                    view.showErrorMessage("Lost master role for $airportIcao")
                    plannerManager.unregisterService(airportIcao)
                    runwayModeStateManager.cleanupAirportState(airportIcao)
                    timelineGroups.removeAll { it.airport.icao == airportIcao }
                    view.updateTimelineGroups(timelineGroups)
                    sharedState.releaseMasterRole(airportIcao)
                    myMasterRoles.remove(airportIcao)
                }
            }
        }.start()
    }

    override fun onReloadSettingsRequested() {
        timelineGroups.clear()
        timelineConfigs.clear()
        view.updateTimelineGroups(timelineGroups)
        runwayModeStateManager.refreshAllStates() // Refresh runway mode states when settings change
        loadSettingsAndOpenTabs()
    }

    private fun loadSettingsAndOpenTabs() {
        SettingsRepository.getSettings(reload = true).timelines.forEach { (airportIcao, timelines) ->
            timelines.forEach { timeline ->
                TimelineConfig(
                    title = timeline.title,
                    runwaysLeft = timeline.left?.runways ?: emptyList(),
                    runwaysRight = timeline.right.runways,
                    airportIcao = airportIcao,
                    depLabelLayout = timeline.departureLabelLayoutId,
                    arrLabelLayout = timeline.arrivalLabelLayoutId,
                ).also { newTimelineConfig ->
                    timelineConfigs[timeline.title] = newTimelineConfig
                }
            }
        }
    }

    override fun onOpenMetWindowClicked(airportIcao: String) {
        view.openMetWindow(airportIcao)
    }

    override fun onOpenVerticalProfileWindowClicked(callsign: String) {
        view.openDescentProfileWindow(callsign)
    }

    override fun onAircraftSelected(callsign: String) {
        selectedCallsign = callsign
    }

    override fun onLiveData(airportIcao: String, timelineEvents: List<TimelineEvent>) {
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

    override fun onRunwayModesUpdated(
        airportIcao: String,
        runwayStatuses: Map<String, RunwayStatus>,
    ) {
        availableRunways = runwayStatuses.keys
        runwayModeStateManager.updateRunwayStatuses(airportIcao, runwayStatuses, minimumSpacingNm)
    }

    override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        this.minimumSpacingNm = minimumSpacingNm
        runwayModeStateManager.updateMinimumSpacing(this.minimumSpacingNm)
        view.updateMinimumSpacing(airportIcao, minimumSpacingNm)
    }

    override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
        view.updateWeatherData(airportIcao, data)
    }

    override fun onWeatherFetchStatusUpdated(airportIcao: String, status: WeatherFetchStatus) {
        val metStatus = when (status) {
            WeatherFetchStatus.NOT_STARTED -> ViewInterface.MetStatus.GREY
            WeatherFetchStatus.FETCHING -> ViewInterface.MetStatus.YELLOW
            WeatherFetchStatus.SUCCESS -> ViewInterface.MetStatus.GREEN
            WeatherFetchStatus.FAILED -> ViewInterface.MetStatus.RED
        }
        view.updateMetStatus(airportIcao, metStatus)
    }

    private fun updateViewFromCachedData() {
        val snapshot: List<TimelineEvent> = cachedAmanData.values.toList().map { it.timelineEvent }

        timelineGroups.forEach { group ->
            val relevantDataForTab = snapshot.filter { occurrence ->
                group.timelines.any { it.airportIcao == occurrence.airportIcao }
            }
            view.updateTab(group.airport.icao, TabData(
                timelinesData = group.timelines.map { timeline ->
                    TimelineData(
                        timelineId = timeline.title,
                        left = relevantDataForTab.filter { (it is RunwayFlightEvent) && timeline.runwaysLeft.contains(it.runway) },
                        right = relevantDataForTab.filter { (it is RunwayFlightEvent) && timeline.runwaysRight.contains(it.runway) }
                    )
                }
            ))
        }

        selectedCallsign?.let { callsign ->
            plannerManager.getAllServices().forEach { plannerService ->
                plannerService.getDescentProfileForCallsign(callsign)
                    .onSuccess { selectedDescentProfile ->
                        if (selectedDescentProfile != null)
                            view.updateDescentTrajectory(callsign, selectedDescentProfile)
                    }
                    .onFailure {
                        selectedCallsign = null
                        when (it) {
                            is UnsupportedInSlaveModeException -> view.showErrorMessage(it.msg)
                            else -> view.showErrorMessage("Failed to fetch descent profile")
                        }
                    }
            }
        }
    }

    override fun onTabMenu(airportIcao: String, screenPos: Point) {
        val availableTimelinesForIcao =
            timelineConfigs.values
                .filter { it.airportIcao == airportIcao }
                .toSet()
                .toList()

        view.showAirportContextMenu(airportIcao, availableTimelinesForIcao, screenPos)
    }

    override fun onNewTimelineGroup(airportIcao: String, userRole: UserRole) {
        val airport = SettingsRepository.getAirportData().find { it.icao == airportIcao }

        if (airport == null) {
            view.showErrorMessage("Airport $airportIcao not found in navdata")
            return
        }

        registerNewTimelineGroup(
            TimelineGroup(
                airport = airport,
                name = airport.icao,
                timelines = mutableListOf(),
                userRole = userRole
            )
        )
        view.closeTimelineForm()
    }

    override fun onAddTimelineButtonClicked(airportIcao: String, timelineConfig: TimelineConfig) {
        registerTimeline(
            airportIcao,
            timelineConfig
        )
    }

    override fun onRemoveTab(airportIcao: String) {
        // Clean up cached data for this airport
        cachedAmanData.entries.removeIf { entry ->
            entry.value.timelineEvent.airportIcao == airportIcao
        }

        // Clear selected callsign if it belongs to the removed airport
        selectedCallsign?.let { callsign ->
            val selectedEvent = cachedAmanData[callsign]?.timelineEvent
            if (selectedEvent?.airportIcao == airportIcao) {
                selectedCallsign = null
            }
        }

        // Clean up runway mode state for this airport
        runwayModeStateManager.cleanupAirportState(airportIcao)

        // Get the service before unregistering to check if it's a Master service
        val serviceToRemove = plannerManager.getServiceForAirport(airportIcao)

        // Remove from view and unregister service (this calls service.stop())
        plannerManager.unregisterService(airportIcao)
        timelineGroups.removeAll { it.airport.icao == airportIcao }
        view.updateTimelineGroups(timelineGroups)

        // Check if we need to clean up the shared AtcClient
        // Only close it if no more Master/Local services are using it
        if (serviceToRemove is PlannerServiceMaster) {
            val remainingMasterServices = plannerManager.getAllServices()
                .filterIsInstance<PlannerServiceMaster>()

            if (remainingMasterServices.isEmpty()) {
                // No more services using the AtcClient, clean it up
                atcClient.close()
            }
        }

        sharedState.releaseMasterRole(airportIcao)
        myMasterRoles.remove(airportIcao)
    }

    override fun onOpenLandingRatesWindow() {
        view.openLandingRatesWindow()
    }

    override fun onOpenNonSequencedWindow() {
        view.openNonSequencedWindow()
    }

    override fun onLabelDragEnd(airportIcao: String, timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String?) {
        plannerManager.getServiceForAirport(airportIcao).suggestScheduledTime(timelineEvent, newScheduledTime, newRunway)
            .onFailure {
                when (it) {
                    is UnsupportedInSlaveModeException -> view.showErrorMessage(it.msg)
                    else -> view.showErrorMessage("Failed to move aircraft: ${it.message}")
                }
            }
    }

    override fun onRecalculateSequenceClicked(airportIcao: String, callSign: String?) {
        plannerManager.getServiceForAirport(airportIcao).reSchedule(callSign)
            .onFailure {
                when (it) {
                    is UnsupportedInSlaveModeException -> view.showErrorMessage(it.msg)
                    else -> view.showErrorMessage("Failed to re-schedule: ${it.message}")
                }
            }
    }

    override fun onMinimumSpacingDistanceSet(airportIcao: String, minimumSpacingDistanceNm: Double) {
        plannerManager.getServiceForAirport(airportIcao).setMinimumSpacing(minimumSpacingDistanceNm)
            .onFailure {
                when (it) {
                    is UnsupportedInSlaveModeException -> {
                        view.showErrorMessage(it.msg)
                    }
                    else -> view.showErrorMessage("Failed to set minimum spacing: ${it.message}")
                }
            }
    }

    override fun beginRunwaySelection(runwayEvent: RunwayEvent, onClose: (runway: String?) -> Unit) {
        if (runwayEvent is RunwayArrivalEvent) {
            val imTheTrackingController = controllerInfo?.callsign != null && runwayEvent.trackingController == controllerInfo?.positionId
            if (imTheTrackingController) {
                view.openSelectRunwayDialog(runwayEvent, availableRunways, onClose)
            } else {
                onClose(null)
                println("Not the tracking controller for ${runwayEvent.callsign}, cannot select runway. Tracking controller is ${runwayEvent.trackingController}, my positionId is ${controllerInfo?.positionId}")
            }
        } else {
            println("selectRunway called with unsupported event type")
        }
    }

    override fun onToggleShowDepartures(airportIcao: String, selected: Boolean) {
        plannerManager.getServiceForAirport(airportIcao).setShowDepartures(selected)
    }

    override fun onLabelDrag(airportIcao: String, timelineEvent: TimelineEvent, newInstant: Instant) {
        if (timelineEvent !is RunwayArrivalEvent) {
            return
        }
        plannerManager.getServiceForAirport(airportIcao).isTimeSlotAvailable(timelineEvent, newInstant)
            .onSuccess { view.updateDraggedLabel(timelineEvent, newInstant, it) }
            .onFailure {
                when (it) {
                    is UnsupportedInSlaveModeException -> view.showErrorMessage(it.msg)
                    else -> view.showErrorMessage("Failed to check time slot availability: ${it.message}")
                }
            }
    }

    override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {
        registerTimeline(
            config.airportIcao,
            TimelineConfig(
                title = config.title,
                runwaysLeft = config.left.targetRunways,
                runwaysRight = config.right.targetRunways,
                airportIcao = config.airportIcao,
                depLabelLayout = config.depLabelLayout,
                arrLabelLayout = config.arrLabelLayout
            )
        )
    }

    override fun onReloadWindsClicked(airportIcao: String) {
        plannerManager.getServiceForAirport(airportIcao).refreshWeatherData()
            .onFailure {
                when (it) {
                    is UnsupportedInSlaveModeException -> view.showErrorMessage(it.msg)
                    else -> view.showErrorMessage("Failed to reload winds: ${it.message}")
                }
            }
    }

    override fun onSetMinSpacingSelectionClicked(icao: String, minSpacingSelectionNm: Double?) {
        view.showMinimumSpacingDialog(icao, minSpacingSelectionNm ?: minimumSpacingNm)
    }

    override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {
        timelineGroups.forEach { group ->
            group.timelines.removeIf { it.title == timelineConfig.title }
        }
        view.updateTimelineGroups(timelineGroups)
        // TODO: unsubscribe from inbounds if no timelines left
    }

    private fun registerNewTimelineGroup(timelineGroup: TimelineGroup) {
        if (timelineGroups.any { it.airport == timelineGroup.airport }) {
            return // Group already exists
        }

        val airport = SettingsRepository.getAirportData().find { it == timelineGroup.airport }

        if (airport == null) {
            view.showErrorMessage("Airport ${timelineGroup.airport} not found in navdata")
            return
        }

        val plannerService = when(timelineGroup.userRole) {
            UserRole.MASTER -> {
                if (sharedState.acquireMasterRole(airport.icao)) {
                    println("Acquired master role for ${airport.icao}")
                    myMasterRoles.add(airport.icao)
                } else {
                    view.showErrorMessage("Master role for ${airport.icao} is already taken by another user")
                    return
                }

                PlannerServiceMaster(
                    airport = airport,
                    weatherDataRepository = weatherDataRepository,
                    atcClient = atcClient,
                    cdmClient = cdmClient,
                    dataUpdateListeners = arrayOf(guiUpdater, dataUpdatesServerSender),
                )
            }
            UserRole.LOCAL ->
                PlannerServiceMaster(
                    airport = airport,
                    weatherDataRepository = weatherDataRepository,
                    atcClient = atcClient,
                    cdmClient = cdmClient,
                    dataUpdateListeners = arrayOf(guiUpdater),
                )
            UserRole.SLAVE ->
                PlannerServiceSlave(
                    airportIcao = timelineGroup.airport.icao,
                    sharedState = sharedState,
                    dataUpdateListener = guiUpdater,
                )
        }

        plannerManager.registerService(plannerService)
        plannerManager.getServiceForAirport(timelineGroup.airport.icao).startDataCollection()
        timelineGroups.add(timelineGroup)
        view.updateTimelineGroups(timelineGroups)
        view.showTimelineGroup(timelineGroup.airport.icao)

        plannerService.start()
    }

    private fun registerTimeline(airportIcao: String, timelineConfig: TimelineConfig) {
        val group = timelineGroups.find { it.airport.icao == airportIcao }
        if (group != null) {
            group.timelines += timelineConfig
            view.updateTimelineGroups(timelineGroups)
            view.closeTimelineForm()
        }
    }

    override fun onEditTimelineRequested(groupId: String, timelineTitle: String) {
        val group = timelineGroups.find { it.airport.icao == groupId }
        if (group != null) {
            val existingConfig = group.timelines.find { it.title == timelineTitle }
            if (existingConfig != null) {
                view.openTimelineConfigForm(
                    groupId = groupId,
                    availableTagLayoutsDep = SettingsRepository.getSettings().departureLabelLayouts.keys,
                    availableTagLayoutsArr = SettingsRepository.getSettings().arrivalLabelLayouts.keys,
                    existingConfig = existingConfig
                )
            }
        }
    }

    override fun onCreateNewTimelineClicked(groupId: String) {
        view.openTimelineConfigForm(
            groupId = groupId,
            availableTagLayoutsDep = SettingsRepository.getSettings().departureLabelLayouts.keys,
            availableTagLayoutsArr = SettingsRepository.getSettings().arrivalLabelLayouts.keys,
        )
    }

    private fun handleControllerInfoUpdate(info: ControllerInfoData) {
        controllerInfo = info
        view.updateControllerInfo(info)
    }

    private data class CachedEvent(
        val lastTimestamp: Instant,
        val timelineEvent: TimelineEvent
    )
}
