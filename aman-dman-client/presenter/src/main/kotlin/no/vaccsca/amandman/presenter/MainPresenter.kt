package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.atc.AtcClientFactory
import no.vaccsca.amandman.model.sharedstate.MasterSlaveSharedState
import no.vaccsca.amandman.model.cdm.CdmProvider
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.config.TimelineSettingsStore
import no.vaccsca.amandman.model.planning.AirportDataSourceManager
import no.vaccsca.amandman.model.timeline.TimelineGroup
import no.vaccsca.amandman.model.sharedstate.DataUpdateListener
import no.vaccsca.amandman.model.sharedstate.DataUpdatesServerSender
import no.vaccsca.amandman.model.planning.LocalSequencePlanner
import no.vaccsca.amandman.model.sharedstate.RemoteDataMirror
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.aircraft.AircraftPerformanceProvider
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.weather.WindProfileProvider
import org.slf4j.LoggerFactory

class MainPresenter(
    private val dataSourceManager: AirportDataSourceManager,
    private val view: MainViewInterface,
    private val windProfileProvider: WindProfileProvider,
    private val settingsProvider: SettingsProvider,
    private val timelineSettingsStore: TimelineSettingsStore,
    private val atcClientFactory: AtcClientFactory,
    private val sharedState: MasterSlaveSharedState,
    private val cdmProvider: CdmProvider,
    private val performanceProvider: AircraftPerformanceProvider,
    private val uiDispatcher: UiDispatcher,
) : MainPresenterInterface {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val timelineGroups = mutableListOf<TimelineGroup>()
    private val airportPresenters = mutableMapOf<String, AirportPresenter>()
    private var selectedCallsign: String? = null
    private var controllerInfo: ControllerInfoData? = null
    private val myMasterRoles = mutableSetOf<String>()

    private val dataUpdatesServerSender by lazy {
        DataUpdatesServerSender(sharedState)
    }
    private val atcClient by lazy {
        atcClientFactory.create(
            controllerInfoCallback = { info -> handleControllerInfoUpdate(info) },
            onVersionMismatch = { clientVersion, pluginVersion ->
                handleVersionMismatch(clientVersion, pluginVersion)
            },
            onAircraftSelectionChanged = { callsign ->
                handleAircraftSelectionChanged(callsign)
            }
        )
    }

    private val guiDataHandler = AirportDataRouter()

    init {
        view.mainPresenterInterface = this

        uiDispatcher.scheduleRepeating(1000) {
            view.updateTime(NtpClock.now())
            updateAllAirportViews()
            checkMasterRoles()
        }
    }

    private fun updateAllAirportViews() {
        airportPresenters.values.forEach { it.updateViewFromCache() }
        updateDescentProfileForSelectedCallsign()
    }

    private fun checkMasterRoles() {
        myMasterRoles.toList().forEach { airportIcao ->
            if (!sharedState.hasMasterRoleStatus(airportIcao)) {
                view.showErrorMessage("Lost master role for $airportIcao")
                removeAirportPresenter(airportIcao)
            }
        }
    }

    private fun handleVersionMismatch(clientVersion: String, pluginVersion: String) {
        runOnEdt {
            view.showErrorMessage(
                """
                VERSION MISMATCH DETECTED
                
                The EuroScope plugin version does not match your client version.
                
                Client Version:  $clientVersion
                Plugin Version:  $pluginVersion
                
                Please update the EuroScope plugin (dll) to version $clientVersion.
                
                Steps to update:
                1. Download the latest release from GitHub
                2. Replace the .dll file in your EuroScope plugins folder
                3. Restart EuroScope
                4. Restart this application
                
                Connection has been terminated.
                """.trimIndent()
            )
        }
    }

    private fun checkVersionCompatibility(): Boolean {
        try {
            val versionResult = sharedState.checkVersionCompatibility()

            if (!versionResult.isCompatible) {
                view.showErrorMessage(
                    """
                    Your application version is incompatible with the server.
                    
                    Your Version: ${versionResult.currentVersion}
                    Required Version: ${versionResult.requiredVersion}
                    Latest Version: ${versionResult.newestVersion}
                    
                    Please update the application to use MASTER/SLAVE modes.
                    You can still use LOCAL mode.
                    """.trimIndent()
                )
                return false
            }

            return true
        } catch (e: Exception) {
            view.showErrorMessage("Unable to verify version compatibility with server: ${e.message}\n\nYou can try again or use LOCAL mode.")
            return false
        }
    }

    override fun onReloadSettingsRequested() {
        timelineGroups.clear()
        airportPresenters.values.forEach { it.stop() }
        airportPresenters.clear()
        guiDataHandler.clear()
        view.updateTimelineGroups(timelineGroups)
    }

    override fun onNewTimelineGroup(airportIcao: String, userRole: UserRole) {
        val airport = settingsProvider.getAirportData().find { it.icao == airportIcao }

        if (airport == null) {
            view.showErrorMessage("Airport $airportIcao not found in navdata")
            return
        }

        if (timelineGroups.any { it.airport.icao == airportIcao }) {
            return
        }

        val timelineGroup = TimelineGroup(
            airport = airport,
            name = airport.icao,
            userRole = userRole
        )

        val plannerService = when (userRole) {
            UserRole.MASTER -> {
                if (!checkVersionCompatibility()) {
                    // Continue anyway for now
                }

                if (sharedState.acquireMasterRole(airport.icao)) {
                    logger.info("Acquired master role for ${airport.icao}")
                    myMasterRoles.add(airport.icao)
                } else {
                    view.showErrorMessage("Master role for ${airport.icao} is already taken by another user")
                    return
                }

                LocalSequencePlanner(
                    airport = airport,
                    windProfileProvider = windProfileProvider,
                    atcClient = atcClient,
                    cdmClient = cdmProvider,
                    sharedState = sharedState,
                    aircraftPerformanceProvider = performanceProvider,
                    dataUpdateListeners = arrayOf(guiDataHandler, dataUpdatesServerSender),
                )
            }

            UserRole.SLAVE -> {
                if (!checkVersionCompatibility()) {
                    return
                }

                RemoteDataMirror(
                    airportIcao = airport.icao,
                    sharedState = sharedState,
                    dataUpdateListener = guiDataHandler,
                )
            }

            UserRole.LOCAL -> {
                LocalSequencePlanner(
                    airport = airport,
                    windProfileProvider = windProfileProvider,
                    atcClient = atcClient,
                    cdmClient = cdmProvider,
                    aircraftPerformanceProvider = performanceProvider,
                    dataUpdateListeners = arrayOf(guiDataHandler),
                )
            }
        }

        dataSourceManager.register(plannerService)
        timelineGroups.add(timelineGroup)
        view.updateTimelineGroups(timelineGroups)

        val airportView = view.createAirportViewDelegate(airportIcao, timelineGroup)

        val airportPresenter = AirportPresenter(
            airportIcao = airportIcao,
            dataSource = plannerService,
            userRole = userRole,
            view = airportView,
            settingsProvider = settingsProvider,
            timelineSettingsStore = timelineSettingsStore,
            controllerInfoProvider = { controllerInfo },
            showErrorMessage = { view.showErrorMessage(it) },
            onAircraftSelectedCallback = { onAircraftSelected(it) },
            onOpenVerticalProfileCallback = { onOpenVerticalProfileWindowClicked(it) },
            onRemove = { removeAirportPresenter(airportIcao) },
            uiDispatcher = uiDispatcher,
        )

        airportPresenters[airportIcao] = airportPresenter
        guiDataHandler.register(airportIcao, airportPresenter)
        airportPresenter.start()

        view.showTimelineGroup(airportIcao)
    }

    private fun removeAirportPresenter(airportIcao: String) {
        airportPresenters[airportIcao]?.let { presenter ->
            presenter.stop()
            guiDataHandler.unregister(airportIcao)
            airportPresenters.remove(airportIcao)
        }

        val serviceToRemove = dataSourceManager.getForAirport(airportIcao)
        dataSourceManager.unregister(airportIcao)
        timelineGroups.removeAll { it.airport.icao == airportIcao }
        view.updateTimelineGroups(timelineGroups)
        view.removeAirportViewDelegate(airportIcao)

        if (serviceToRemove is LocalSequencePlanner) {
            val remainingPlanners = dataSourceManager.getAllSequencePlanners()
            if (remainingPlanners.isEmpty()) {
                atcClient.close()
            }
        }

        sharedState.releaseMasterRole(airportIcao)
        myMasterRoles.remove(airportIcao)

        selectedCallsign = null
    }

    override fun onOpenLogsWindowClicked() {
        view.openLogsWindow()
    }

    override fun onOpenVerticalProfileWindowClicked(callsign: String) {
        view.openDescentProfileWindow(callsign)
    }

    override fun onAircraftSelected(callsign: String) {
        selectedCallsign = callsign
        updateDescentProfileForSelectedCallsign()
    }

    private fun updateDescentProfileForSelectedCallsign() {
        selectedCallsign?.let { callsign ->
            dataSourceManager.getAllSequencePlanners().forEach { planner ->
                val descentProfile = planner.getDescentProfileForCallsign(callsign)
                if (descentProfile != null) {
                    view.updateDescentTrajectory(callsign, descentProfile)
                }
            }
        }
    }

    private fun handleAircraftSelectionChanged(callsign: String) {
        runOnEdt {
            // Update the local selected callsign
            selectedCallsign = callsign.ifEmpty { null }

            // Notify all airport presenters about the selection change
            airportPresenters.values.forEach { it.onAircraftSelectionChanged(callsign) }

            // Update descent profile
            updateDescentProfileForSelectedCallsign()
        }
    }

    private fun handleControllerInfoUpdate(info: ControllerInfoData) {
        runOnEdt {
            controllerInfo = info
            view.updateControllerInfo(info)
        }
    }

    /**
     * Routes data updates to the correct AirportPresenter based on airportIcao.
     */
    private inner class AirportDataRouter : DataUpdateListener {
        private val listeners = mutableMapOf<String, DataUpdateListener>()

        fun register(airportIcao: String, listener: DataUpdateListener) {
            listeners[airportIcao] = listener
        }

        fun unregister(airportIcao: String) {
            listeners.remove(airportIcao)
        }

        fun clear() {
            listeners.clear()
        }

        override fun onTimelineEventsUpdated(airportIcao: String, timelineEvents: List<TimelineEvent>) {
            runOnEdt {
                listeners[airportIcao]?.onTimelineEventsUpdated(airportIcao, timelineEvents)
            }
        }

        override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
            runOnEdt {
                listeners[airportIcao]?.onRunwayModesUpdated(airportIcao, runwayStatuses)
            }
        }

        override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
            runOnEdt {
                listeners[airportIcao]?.onWeatherDataUpdated(airportIcao, data)
            }
        }

        override fun onNonSequencedListUpdated(airportIcao: String, nonSequencedList: List<NonSequencedEvent>) {
            runOnEdt {
                listeners[airportIcao]?.onNonSequencedListUpdated(airportIcao, nonSequencedList)
            }
        }

        override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
            runOnEdt {
                listeners[airportIcao]?.onMinimumSpacingUpdated(airportIcao, minimumSpacingNm)
            }
        }

        override fun onFeederFixStateUpdated(airportIcao: String, feederFixState: FeederFixState) {
            runOnEdt {
                listeners[airportIcao]?.onFeederFixStateUpdated(airportIcao, feederFixState)
            }
        }
    }

    private fun runOnEdt(action: () -> Unit) {
        if (uiDispatcher.isUiThread()) {
            action()
        } else {
            uiDispatcher.dispatch(action)
        }
    }
}
