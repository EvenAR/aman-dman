package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.airport.Footer
import no.vaccsca.amandman.view.airport.TimeRangeScrollBarVertical
import no.vaccsca.amandman.view.airport.TimelineScrollPane
import no.vaccsca.amandman.view.airport.TopBar
import no.vaccsca.amandman.view.airport.timeline.TimelineView
import no.vaccsca.amandman.view.components.BoundedDesktopManager
import no.vaccsca.amandman.view.components.ReloadButton
import no.vaccsca.amandman.view.entity.MainViewState
import no.vaccsca.amandman.view.entity.TimeRange
import no.vaccsca.amandman.view.visualizations.LandingRatesGraph
import no.vaccsca.amandman.view.visualizations.NonSeqView
import no.vaccsca.amandman.view.visualizations.VerticalWindView
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.WindowConstants
import kotlin.time.Duration

class AirportView(
    val airportIcao: String,
    mainViewState: MainViewState
) : JDesktopPane() {

    lateinit var presenter: AirportPresenterInterface

    private val airportViewState = mainViewState.airportViewStates.value.find { it.airportIcao == airportIcao }
        ?: throw IllegalStateException("No AirportViewModel found for airport ${airportIcao}")

    val timeWindowScrollbar = TimeRangeScrollBarVertical(airportViewState)
    val reloadButton = ReloadButton("Recalculate sequence for all arrivals") {
        presenter.onRecalculateSequenceClicked()
    }
    val westPanel = JPanel(BorderLayout()).apply {
        add(timeWindowScrollbar, BorderLayout.CENTER)
        add(reloadButton, BorderLayout.SOUTH)
    }

    val timelineScrollPane: TimelineScrollPane
    val topBar: TopBar
    val footer = Footer(mainViewState, airportViewState)

    private val landingRatesGraph = LandingRatesGraph(airportViewState, mainViewState)
    private var landingRatesFrame: JInternalFrame? = null

    private val nonSeqView = NonSeqView(airportViewState)
    private var nonSeqFrame: JInternalFrame? = null

    private lateinit var verticalWindView: VerticalWindView
    private var windFrame: JInternalFrame? = null

    private var minimumSpacingFrame: JInternalFrame? = null
    private val minimumSpacingModel = SpinnerNumberModel(3.0, 0.0, 100.0, 0.1)
    private var onMinimumSpacingSubmit: ((Double) -> Unit)? = null

    private var currentTime: Instant? = null

    private val contentPanel: JPanel

    init {
        timelineScrollPane = TimelineScrollPane(airportViewState) { presenter }
        topBar = TopBar(airportViewState) { presenter }

        contentPanel = JPanel(BorderLayout()).apply {
            add(topBar, BorderLayout.NORTH)
            add(westPanel, BorderLayout.WEST)
            add(timelineScrollPane, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }

        add(contentPanel)
        contentPanel.setBounds(0, 0, 800, 600)
        this.desktopManager = BoundedDesktopManager()

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                contentPanel.setSize(width, height)
                forceInternalFramesToStayInBounds()
            }
        })

        mainViewState.currentClock.addListener { currentTime ->
            updateTime(currentTime)
        }

        airportViewState.openTimelines.addListener {
            updateVisibleTimelines(it)
        }
    }

    fun initializeWindView() {
        verticalWindView = VerticalWindView(airportViewState) { presenter }
    }

    private fun updateTime(currentTime: Instant) {
        val delta = if (this.currentTime != null) {
            currentTime - this.currentTime!!
        } else {
            Duration.ZERO
        }
        airportViewState.selectedTimeRange.value = TimeRange(
            airportViewState.selectedTimeRange.value.start + delta,
            airportViewState.selectedTimeRange.value.end + delta,
        )
        airportViewState.availableTimeRange.value = TimeRange(
            currentTime - airportViewState.maxHistory,
            currentTime + airportViewState.maxFuture,
        )
        this.currentTime = currentTime
    }

    private fun updateVisibleTimelines(configs: List<TimelineConfig>) {
        val items = timelineScrollPane.viewport.view as JPanel
        items.components
            .filterIsInstance<TimelineView>()
            .forEach { component -> items.remove(component) }

        configs.forEach { timelineId ->
            timelineScrollPane.insertTimeline(timelineId)
        }
        repaint()
    }

    fun openPopupMenu(
        customizedTimelines: List<TimelineConfig>,
        generatedFixTimelines: List<TimelineConfig>,
        screenPos: Point
    ) {
        timelineScrollPane.openPopupMenu(customizedTimelines, generatedFixTimelines, screenPos)
    }

    fun openLandingRatesWindow() {
        if (landingRatesFrame == null) {
            landingRatesFrame = JInternalFrame("Traffic Load Monitoring - $airportIcao", true, true, true, true).apply {
                add(landingRatesGraph)
                setSize(500, 300)
                setLocation(50, 50)
                isVisible = true
                isIconifiable = false
                frameIcon = null
                isMaximizable = false
                defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            }
            add(landingRatesFrame)
        }
        landingRatesFrame?.isVisible = true
        landingRatesFrame?.toFront()
    }

    fun openNonSequencedWindow() {
        if (nonSeqFrame == null) {
            nonSeqFrame = JInternalFrame("Non-Sequenced Flights - $airportIcao", true, true, true, true).apply {
                add(nonSeqView)
                setSize(450, 300)
                setLocation(100, 100)
                isVisible = true
                isIconifiable = false
                frameIcon = null
                isMaximizable = false
                defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            }
            add(nonSeqFrame)
        }
        nonSeqFrame?.isVisible = true
        nonSeqFrame?.toFront()
    }

    fun openMetWindow() {
        if (!::verticalWindView.isInitialized) {
            initializeWindView()
        }
        if (windFrame == null) {
            windFrame = JInternalFrame("Vertical Wind Profile - $airportIcao", true, true, true, true).apply {
                add(verticalWindView)
                setSize(330, 700)
                setLocation(150, 150)
                isVisible = true
                isIconifiable = false
                frameIcon = null
                isMaximizable = false
                defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            }
            add(windFrame)
        }
        windFrame?.isVisible = true
        windFrame?.toFront()
    }

    fun openMinimumSpacingWindow(default: Double, onSubmit: (Double) -> Unit) {
        onMinimumSpacingSubmit = onSubmit
        minimumSpacingModel.value = default
        if (minimumSpacingFrame == null) {
            val spinner = JSpinner(minimumSpacingModel)
            val content = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("Minimum Spacing:"))
                add(spinner)
                add(JLabel("NM"))
            }
            val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(JButton("Apply").apply {
                    addActionListener {
                        onMinimumSpacingSubmit?.invoke(minimumSpacingModel.number.toDouble())
                        minimumSpacingFrame?.isVisible = false
                    }
                })
                add(JButton("Cancel").apply {
                    addActionListener {
                        minimumSpacingFrame?.isVisible = false
                    }
                })
            }

            minimumSpacingFrame = JInternalFrame("Set Minimum Spacing - $airportIcao", true, true, true, true).apply {
                layout = BorderLayout()
                add(content, BorderLayout.CENTER)
                add(buttonBar, BorderLayout.SOUTH)
                setSize(320, 120)
                setLocation(200, 120)
                isVisible = true
                isIconifiable = false
                frameIcon = null
                isMaximizable = false
                defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            }
            add(minimumSpacingFrame)
        }
        minimumSpacingFrame?.isVisible = true
        minimumSpacingFrame?.toFront()
    }

    private fun forceInternalFramesToStayInBounds() {
        for (frame in allFrames) {
            val bounds = frame.bounds
            var x = bounds.x
            var y = bounds.y

            if (bounds.x < 0) x = 0
            if (bounds.y < 0) y = 0
            if (bounds.x + bounds.width > width) x = width - bounds.width
            if (bounds.y + bounds.height > height) y = height - bounds.height

            frame.setLocation(x, y)
        }
    }
}
