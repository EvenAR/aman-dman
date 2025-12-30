package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.TabData
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.presenter.TimelineGroup
import no.vaccsca.amandman.common.domain.valueobjects.SequenceStatus
import no.vaccsca.amandman.common.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.presenter.AirportViewInterface
import no.vaccsca.amandman.view.airport.TimeRangeScrollBarVertical
import no.vaccsca.amandman.view.airport.TimelineScrollPane
import no.vaccsca.amandman.view.airport.TopBar
import no.vaccsca.amandman.view.airport.timeline.TimelineView
import no.vaccsca.amandman.view.components.ReloadButton
import no.vaccsca.amandman.view.entity.TimeRange
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.*
import javax.swing.JPanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AirportView(
    private val presenter: AirportPresenterInterface,
    val airportIcao: String,
) : JPanel(BorderLayout()), AirportViewInterface {

    private val maxHistory = 20.minutes
    private val maxFuture = 2.hours

    private val availableTimeRange = SharedValue(
        initialValue = TimeRange(
            NtpClock.now() - maxHistory,
            NtpClock.now() + maxFuture,
        )
    )

    private val selectedTimeRange = SharedValue(
        initialValue = TimeRange(
            NtpClock.now() - 10.minutes,
            NtpClock.now() + 60.minutes,
        )
    )

    val timeWindowScrollbar = TimeRangeScrollBarVertical(selectedTimeRange, availableTimeRange)
    val reloadButton = ReloadButton("Recalculate sequence for all arrivals") {
        presenter.onRecalculateSequenceClicked()
    }
    val westPanel = JPanel(BorderLayout()).apply {
        add(timeWindowScrollbar, BorderLayout.CENTER)
        add(reloadButton, BorderLayout.SOUTH)
    }

    val timelineScrollPane = TimelineScrollPane(selectedTimeRange, availableTimeRange, presenter)
    val topBar = TopBar(presenter)

    init {
        add(topBar, BorderLayout.NORTH)
        add(westPanel, BorderLayout.WEST)
        add(timelineScrollPane, BorderLayout.CENTER)
    }

    fun updateTime(currentTime: Instant, delta: Duration) {
        selectedTimeRange.value = TimeRange(
            selectedTimeRange.value.start + delta,
            selectedTimeRange.value.end + delta,
        )
        availableTimeRange.value = TimeRange(
            currentTime - maxHistory,
            currentTime + maxFuture,
        )
    }

    fun updateAmanData(tabData: TabData) {
        timeWindowScrollbar.updateTimelineEvents(tabData.timelinesData)
        timelineScrollPane.updateTimelineEvents(tabData.timelinesData)

        val numberOfNonSeq = tabData.timelinesData
            .flatMap { it.left + it.right }
            .filterIsInstance<RunwayArrivalEvent>()
            .count { it.sequenceStatus == SequenceStatus.FOR_MANUAL_REINSERTION }

        topBar.updateNonSeqNumbers(numberOfNonSeq)
    }

    override fun updateDraggedLabel(
        timelineEvent: TimelineEvent,
        newInstant: Instant,
        isAvailable: Boolean,
    ) {
        val items = timelineScrollPane.viewport.view as JPanel
        items.components.filterIsInstance<TimelineView>().forEach { timelineView ->
            timelineView.updateDraggedLabel(timelineEvent, newInstant, isAvailable)
        }
    }

    override fun updateTab(tabData: TabData) {
        TODO("Not yet implemented")
    }

    override fun updateWeatherData(weather: VerticalWeatherProfile?) {
        TODO("Not yet implemented")
    }

    override fun updateDescentTrajectory(
        callsign: String,
        trajectory: List<TrajectoryPoint>
    ) {
        TODO("Not yet implemented")
    }

    override fun showMinimumSpacingDialog(d: Double) {
        TODO("Not yet implemented")
    }

    fun updateVisibleTimelines(timelineGroup: TimelineGroup) {
        // Clear existing timelines
        val items = timelineScrollPane.viewport.view as JPanel
        items.components
            .filterIsInstance<TimelineView>()
            .forEach { component -> items.remove(component) }

        // Add the current timelines
        timelineGroup.timelines.forEach { timelineConfig ->
            timelineScrollPane.insertTimeline(timelineConfig)
        }
        repaint()
    }

    fun updateMinSpacingNM(minSpacingNm: Double) {
        timelineScrollPane.updateMinimumSpacingSelection(minSpacingNm)
    }

    override fun updateMinimumSpacing(minimumSpacingNm: Double) {
        TODO("Not yet implemented")
    }

    override fun updateRunwayModes(runwayModes: Map<String, RunwayStatus>) {
        topBar.setRunwayModes(runwayModes)
    }

    override fun showAirportContextMenu(
        availableTimelines: List<TimelineConfig>,
        screenPos: Point
    ) {
        TODO("Not yet implemented")
    }

    fun openPopupMenu(availableTimelines: List<TimelineConfig>, screenPos: Point) {
        timelineScrollPane.openPopupMenu(availableTimelines, screenPos)
    }
}