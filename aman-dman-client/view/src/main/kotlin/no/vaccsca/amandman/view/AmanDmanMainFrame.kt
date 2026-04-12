package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.planning.TrajectoryPoint
import no.vaccsca.amandman.model.timeline.TimelineGroup
import no.vaccsca.amandman.presenter.AirportViewInterface
import no.vaccsca.amandman.presenter.MainPresenterInterface
import no.vaccsca.amandman.presenter.MainViewInterface
import no.vaccsca.amandman.view.dialogs.LogViewerDialog
import no.vaccsca.amandman.view.dialogs.RoleSelectionDialog
import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.MainViewState
import no.vaccsca.amandman.view.visualizations.DescentProfileVisualization
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.math.roundToInt


class AmanDmanMainFrame : MainViewInterface, JFrame("AMAN") {

    override lateinit var mainPresenterInterface: MainPresenterInterface

    private val descentProfileVisualizationView = DescentProfileVisualization()

    private var descentProfileDialog: JDialog? = null
    private var airportViewsPanel: AirportViewsPanel? = null
    private var mainViewState = MainViewState()
    private val logsDialog: JDialog

    private val airportViewDelegates = mutableMapOf<String, AirportViewDelegate>()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()
        logsDialog = LogViewerDialog(this)
    }

    override fun openWindow() {
        mainPresenterInterface.onReloadSettingsRequested()
        airportViewsPanel = AirportViewsPanel(mainViewState)

        setSize(1000, 800)
        setLocationRelativeTo(null)
        add(airportViewsPanel, BorderLayout.CENTER)

        setupContextMenu()

        isVisible = true
        isAlwaysOnTop = true
    }

    private fun setupContextMenu() {
        val contextMenu = JPopupMenu()

        val startMenuItem = JMenuItem("New airport view")
        val logsMenuItem = JMenuItem("Open Logs")

        startMenuItem.addActionListener {
            RoleSelectionDialog.open(this) { icao, role ->
                mainPresenterInterface.onNewTimelineGroup(icao, role)
            }
        }

        logsMenuItem.addActionListener {
            mainPresenterInterface.onOpenLogsWindowClicked()
        }

        contextMenu.add(startMenuItem)
        contextMenu.add(logsMenuItem)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                maybeShowPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                maybeShowPopup(e)
            }

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    contextMenu.show(e.component, e.x, e.y)
                }
            }
        })
    }

    override fun showTimelineGroup(airportIcao: String) = runOnUiThread {
        mainViewState.currentTab.value = airportIcao
    }

    override fun updateTime(currentTime: Instant) = runOnUiThread {
        mainViewState.currentClock.value = currentTime
    }

    override fun updateTimelineGroups(timelineGroups: List<TimelineGroup>) = runOnUiThread {
        val existingIcaos = mainViewState.airportViewStates.value.map { it.airportIcao }.toSet()
        val newIcaos = timelineGroups.map { it.airport.icao }.toSet()

        val updatedAirportViewModels = mainViewState.airportViewStates.value.toMutableList()

        // Add new airport view models
        for (group in timelineGroups) {
            if (group.airport.icao !in existingIcaos) {
                val newViewModel = AirportViewState(
                    airportIcao = group.airport.icao,
                    userRole = group.userRole,
                )
                updatedAirportViewModels.add(newViewModel)
            }
        }

        // Remove airport view models for removed timeline groups
        updatedAirportViewModels.removeIf { it.airportIcao !in newIcaos }

        mainViewState.airportViewStates.value = updatedAirportViewModels
    }

    override fun updateDescentTrajectory(
        callsign: String,
        trajectory: List<TrajectoryPoint>
    ) = runOnUiThread {
        val currentFlightLevel = (trajectory.first().altitude / 100.0).roundToInt()
        descentProfileDialog?.title =
            "$callsign - calculated descent profile from FL$currentFlightLevel"
        descentProfileVisualizationView.setDescentSegments(trajectory)
    }

    override fun openLogsWindow() = runOnUiThread {
        logsDialog.isVisible = true
    }

    override fun openDescentProfileWindow(callsign: String) = runOnUiThread {
        if (descentProfileDialog != null) {
            descentProfileDialog?.isVisible = true
        } else {
            descentProfileDialog = JDialog(this).apply {
                add(descentProfileVisualizationView)
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                setLocationRelativeTo(this@AmanDmanMainFrame)
                preferredSize = Dimension(800, 600)
                isVisible = true
                pack()
            }
        }
        mainPresenterInterface.onAircraftSelected(callsign)
    }

    override fun showErrorMessage(message: String) = runOnUiThread {
        JOptionPane.showMessageDialog(
            this,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE
        )
    }

    override fun updateControllerInfo(controllerInfoData: ControllerInfoData) = runOnUiThread {
        if (controllerInfoData.callsign != null && controllerInfoData.facilityType != null) {
            this.title = "AMAN - ${controllerInfoData.callsign} (${controllerInfoData.facilityType})"
        } else {
            this.title = "AMAN"
        }
    }

    override fun createAirportViewDelegate(airportIcao: String, timelineGroup: TimelineGroup): AirportViewInterface {
        val airportViewState = mainViewState.airportViewStates.value.find { it.airportIcao == airportIcao }
            ?: throw IllegalStateException("No AirportViewState found for airport $airportIcao")

        val airportView = airportViewsPanel?.getAirportView(airportIcao)
            ?: throw IllegalStateException("No AirportView found for airport $airportIcao")

        val delegate = AirportViewDelegate(
            parentFrame = this,
            airportView = airportView,
            airportViewState = airportViewState
        )
        airportViewDelegates[airportIcao] = delegate
        return delegate
    }

    override fun removeAirportViewDelegate(airportIcao: String) {
        airportViewDelegates.remove(airportIcao)
    }

    private fun runOnUiThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }
}