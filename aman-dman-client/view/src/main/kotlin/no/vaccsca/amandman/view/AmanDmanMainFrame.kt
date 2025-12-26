package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.domain.TimelineGroup
import no.vaccsca.amandman.presenter.PresenterInterface
import no.vaccsca.amandman.presenter.ViewInterface
import no.vaccsca.amandman.model.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.model.data.dto.TabData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.view.airport.Footer
import no.vaccsca.amandman.view.visualizations.LandingRatesGraph
import no.vaccsca.amandman.view.forms.NewTimelineForm
import no.vaccsca.amandman.view.visualizations.NonSeqView
import no.vaccsca.amandman.view.visualizations.VerticalWindView
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import kotlin.math.roundToInt
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.view.dialogs.RunwayDialog
import no.vaccsca.amandman.view.dialogs.SpacingDialog
import no.vaccsca.amandman.view.visualizations.DescentProfileVisualization
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

class AmanDmanMainFrame : ViewInterface, JFrame("AMAN") {

    override lateinit var presenterInterface: PresenterInterface

    private val nonSeqView = NonSeqView()
    private val descentProfileVisualizationView = DescentProfileVisualization()
    private val landingRatesGraph = LandingRatesGraph()

    private var newTimelineForm: JDialog? = null
    private var windDialog: JDialog? = null
    private var descentProfileDialog: JDialog? = null
    private var landingRatesDialog: JDialog? = null
    private var nonSequencedDialog: JDialog? = null
    private var footer: Footer? = null
    private var airportViewsPanel: AirportViewsPanel? = null
    private var currentTime: Instant? = null
    private var verticalWindView: VerticalWindView? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()
    }

    override fun openWindow() {

        presenterInterface.onReloadSettingsRequested()
        footer = Footer(presenterInterface, this)
        airportViewsPanel = AirportViewsPanel(presenterInterface)
        verticalWindView = VerticalWindView(presenterInterface)

        setSize(1000, 800)
        setLocationRelativeTo(null) // Center the window
        add(airportViewsPanel, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)

        isVisible = true // Show the frame
        isAlwaysOnTop = true
    }

    override fun updateMinimumSpacing(airportIcao: String, minimumSpacingNm: Double) = runOnUiThread {
        airportViewsPanel?.updateMinimumSpacing(airportIcao, minimumSpacingNm)
    }

    override fun openSelectRunwayDialog(
        runwayEvent: RunwayEvent,
        runwayOptions: Set<String>,
        onClose: (String) -> Unit
    ) = runOnUiThread {
        RunwayDialog.open(
            parent = this,
            runwayEvent = runwayEvent,
            runwayOptions = runwayOptions,
            onSubmit = onClose
        )
    }

    override fun showTimelineGroup(airportIcao: String) = runOnUiThread {
        airportViewsPanel?.changeVisibleGroup(airportIcao)
    }

    override fun updateTime(currentTime: Instant) = runOnUiThread {
        val previousTime = this.currentTime
        this.currentTime = currentTime
        val delta = if (previousTime != null) {
            currentTime - previousTime
        } else {
            0.seconds
        }
        airportViewsPanel?.updateTime(currentTime, delta)
    }

    override fun showAirportContextMenu(
        airportIcao: String,
        availableTimelines: List<TimelineConfig>,
        screenPos: Point
    ) = runOnUiThread {
        airportViewsPanel?.openPopupMenu(airportIcao, availableTimelines, screenPos)
    }

    override fun updateTab(airportIcao: String, tabData: TabData) = runOnUiThread {
        airportViewsPanel?.updateTab(airportIcao, tabData)
        val allArrivalEvents = tabData.timelinesData.flatMap { it.left + it.right }
        landingRatesGraph.updateData(airportIcao, allArrivalEvents)
        nonSeqView.updateNonSeqData(
            tabData.timelinesData.flatMap { it.left + it.right }
        )
    }

    override fun updateTimelineGroups(timelineGroups: List<TimelineGroup>) = runOnUiThread {
        airportViewsPanel?.updateTimelineGroups(timelineGroups)
    }

    override fun updateDraggedLabel(
        timelineEvent: TimelineEvent,
        newInstant: Instant,
        isAvailable: Boolean
    ) = runOnUiThread {
        airportViewsPanel?.updateDraggedLabel(timelineEvent, newInstant, isAvailable)
    }

    override fun updateRunwayModes(
        airportIcao: String,
        runwayModes: List<Pair<String, Boolean>>
    ) = runOnUiThread {
        airportViewsPanel?.updateRunwayModes(airportIcao, runwayModes)
    }

    override fun openTimelineConfigForm(
        groupId: String,
        availableTagLayoutsDep: Set<String>,
        availableTagLayoutsArr: Set<String>,
        existingConfig: TimelineConfig?
    ) = runOnUiThread {
        if (newTimelineForm != null) {
            newTimelineForm?.isVisible = true
        } else {
            newTimelineForm = JDialog(this, "New timeline for $groupId").apply {
                defaultCloseOperation = DISPOSE_ON_CLOSE
                contentPane = NewTimelineForm(presenterInterface, groupId, existingConfig)
                pack()
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
        val timelineForm = newTimelineForm?.contentPane as? NewTimelineForm
        timelineForm?.update(
            arrLayouts = availableTagLayoutsArr,
            depLayouts = availableTagLayoutsDep
        )
    }

    override fun closeTimelineForm() = runOnUiThread {
        newTimelineForm?.isVisible = false
        newTimelineForm?.dispose()
        newTimelineForm = null
    }

    override fun showMinimumSpacingDialog(icao: String, default: Double) = runOnUiThread {
        SpacingDialog.open(this, icao, default) { newValue ->
            presenterInterface.onMinimumSpacingDistanceSet(icao, newValue)
        }
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

    override fun openMetWindow(airportIcao: String) = runOnUiThread {
        if (windDialog != null) {
            windDialog?.isVisible = true
        } else {
            windDialog = JDialog(this, "Vertical wind profile").apply {
                add(verticalWindView)
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                setLocationRelativeTo(this@AmanDmanMainFrame)
                preferredSize = Dimension(330, 700)
                isVisible = true
                pack()
            }
        }
        verticalWindView?.showAirport(airportIcao)
    }

    override fun openLandingRatesWindow() = runOnUiThread {
        if (landingRatesDialog != null) {
            landingRatesDialog?.isVisible = true
        } else {
            landingRatesDialog = JDialog(this, "Landing Rates").apply {
                add(landingRatesGraph)
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                setLocationRelativeTo(this@AmanDmanMainFrame)
                preferredSize = Dimension(500, 300)
                isVisible = true
                pack()
            }
        }
    }

    override fun openNonSequencedWindow() = runOnUiThread {
        if (nonSequencedDialog != null) {
            nonSequencedDialog?.isVisible = true
        } else {
            nonSequencedDialog = JDialog(this, "Non-Sequenced Flights").apply {
                add(nonSeqView)
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                setLocationRelativeTo(this@AmanDmanMainFrame)
                preferredSize = Dimension(300, 300)
                isVisible = true
                pack()
            }
        }
    }

    override fun updateWeatherData(
        airportIcao: String,
        weather: VerticalWeatherProfile?
    ) = runOnUiThread {
        verticalWindView?.update(airportIcao, weather)
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
        presenterInterface.onAircraftSelected(callsign)
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

    override fun updateAtcClientConnectionStatus(isConnected: Boolean, hasStarted: Boolean, lastReceivedTimestamp: Instant?) = runOnUiThread {
        val status = if (!hasStarted) {
            // In slave mode, data comes from the server, not EuroScope
            Footer.TrafficLightPanel.Status.GREY
        } else {
            val now = currentTime ?: return@runOnUiThread

            when {
                // If started but not connected, connection has failed - show RED
                !isConnected -> Footer.TrafficLightPanel.Status.RED
                // If never received data and not started, show YELLOW (waiting/initializing)
                lastReceivedTimestamp == null -> Footer.TrafficLightPanel.Status.YELLOW
                // If data is very stale (>30 seconds), show RED
                now - lastReceivedTimestamp > 30.seconds -> Footer.TrafficLightPanel.Status.RED
                // If data is somewhat stale (10-30 seconds), show YELLOW
                now - lastReceivedTimestamp > 10.seconds -> Footer.TrafficLightPanel.Status.YELLOW
                // If receiving fresh data (<10 seconds), show GREEN
                else -> Footer.TrafficLightPanel.Status.GREEN
            }
        }

        footer?.setAtcClientStatus(status)
    }

    override fun updateMetStatus(airportIcao: String, status: ViewInterface.MetStatus) = runOnUiThread {
        val footerStatus = when (status) {
            ViewInterface.MetStatus.GREY -> Footer.TrafficLightPanel.Status.GREY
            ViewInterface.MetStatus.YELLOW -> Footer.TrafficLightPanel.Status.YELLOW
            ViewInterface.MetStatus.GREEN -> Footer.TrafficLightPanel.Status.GREEN
            ViewInterface.MetStatus.RED -> Footer.TrafficLightPanel.Status.RED
        }
        footer?.setMetStatus(footerStatus)
    }

    private fun runOnUiThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater {
                block()
            }
        }
    }
}