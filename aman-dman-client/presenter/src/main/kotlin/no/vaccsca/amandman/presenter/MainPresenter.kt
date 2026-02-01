package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.UserRole
import no.vaccsca.amandman.model.data.integration.AtcClientEuroScope
import no.vaccsca.amandman.model.data.integration.MasterSlaveSharedStateHttpClient
import no.vaccsca.amandman.model.data.repository.CdmClient
import no.vaccsca.amandman.model.data.repository.SettingsRepository
import no.vaccsca.amandman.model.data.repository.WeatherDataRepository
import no.vaccsca.amandman.model.domain.AirportDataSourceManager
import no.vaccsca.amandman.model.domain.TimelineGroup
import no.vaccsca.amandman.model.domain.service.DataUpdateListener
import no.vaccsca.amandman.model.domain.service.DataUpdatesServerSender
import no.vaccsca.amandman.model.domain.service.planning.LocalSequencePlanner
import no.vaccsca.amandman.model.domain.service.slave.RemoteDataMirror
import no.vaccsca.amandman.model.domain.valueobjects.NonSequencedEvent
import no.vaccsca.amandman.model.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import org.slf4j.LoggerFactory

class MainPresenter(
    private val dataSourceManager: AirportDataSourceManager,
    private val view: MainViewInterface,
) : MainPresenterInterface {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val timelineGroups = mutableListOf<TimelineGroup>()
    private val airportPresenters = mutableMapOf<String, AirportPresenter>()
    private var selectedCallsign: String? = null
    private var controllerInfo: ControllerInfoData? = null
    private val myMasterRoles = mutableSetOf<String>()

    private val euroScopeClient by lazy {
        AtcClientEuroScope(
            controllerInfoCallback = { info -> handleControllerInfoUpdate(info) },
            onVersionMismatch = { clientVersion, pluginVersion ->
                handleVersionMismatch(clientVersion, pluginVersion)
            },
            onAircraftSelectionChanged = { callsign ->
                handleAircraftSelectionChanged(callsign)
            }
        )
    }

    private val sharedState by lazy {
        MasterSlaveSharedStateHttpClient()
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

    private val guiDataHandler = AirportDataRouter()

    init {
        view.mainPresenterInterface = this

        javax.swing.Timer(1000) {
            view.updateTime(NtpClock.now())
            updateAllAirportViews()
            checkMasterRoles()
        }.start()
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
        javax.swing.SwingUtilities.invokeLater {
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
        val airport = SettingsRepository.getAirportData().find { it.icao == airportIcao }

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
                    weatherDataRepository = weatherDataRepository,
                    atcClient = euroScopeClient,
                    cdmClient = cdmClient,
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
                    weatherDataRepository = weatherDataRepository,
                    atcClient = euroScopeClient,
                    cdmClient = cdmClient,
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
            view = airportView,
            controllerInfoProvider = { controllerInfo },
            showErrorMessage = { view.showErrorMessage(it) },
            onAircraftSelectedCallback = { onAircraftSelected(it) },
            onOpenVerticalProfileCallback = { onOpenVerticalProfileWindowClicked(it) },
            onRemove = { removeAirportPresenter(airportIcao) }
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

            if (remainingPlanners.isEmpty() && euroScopeClient.isClientConnected) {
                euroScopeClient.close()
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
        // Update the local selected callsign
        selectedCallsign = callsign.ifEmpty { null }
        
        // Notify all airport presenters about the selection change
        airportPresenters.values.forEach { it.onAircraftSelectionChanged(callsign) }
        
        // Update descent profile
        updateDescentProfileForSelectedCallsign()
    }

    private fun handleControllerInfoUpdate(info: ControllerInfoData) {
        controllerInfo = info
        view.updateControllerInfo(info)
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
            listeners[airportIcao]?.onTimelineEventsUpdated(airportIcao, timelineEvents)
        }

        override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
            listeners[airportIcao]?.onRunwayModesUpdated(airportIcao, runwayStatuses)
        }

        override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
            listeners[airportIcao]?.onWeatherDataUpdated(airportIcao, data)
        }

        override fun onNonSequencedListUpdated(airportIcao: String, nonSequencedList: List<NonSequencedEvent>) {
            listeners[airportIcao]?.onNonSequencedListUpdated(airportIcao, nonSequencedList)
        }

        override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
            listeners[airportIcao]?.onMinimumSpacingUpdated(airportIcao, minimumSpacingNm)
        }
    }
}
