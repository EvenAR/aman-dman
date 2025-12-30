package no.vaccsca.amandman.view.airport.timeline

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.valueobjects.LabelItem
import no.vaccsca.amandman.presenter.PresenterInterface
import no.vaccsca.amandman.common.domain.valueobjects.TimelineData
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.entity.TimeRange
import no.vaccsca.amandman.view.airport.timeline.enums.TimelineAlignment
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

class TimelineView(
    val timelineConfig: TimelineConfig,
    private val selectedTimeRange: SharedValue<TimeRange>,
    private val presenterInterface: AirportPresenterInterface,
) : JLayeredPane() {
    private val scaleWidth = 60
    private val isDual = timelineConfig.runwaysLeft.isNotEmpty() && timelineConfig.runwaysRight.isNotEmpty()

    private val basePanel = JPanel(null)
    private val labelLayout = emptyList<LabelItem>()// SettingsRepository.getSettings().arrivalLabelLayouts[timelineConfig.arrLabelLayout]!!

    private val labelContainer = TimelineOverlay(timelineConfig, this, presenterInterface, labelLayout, labelLayout)
    private val timeScale = TimeScale(this, selectedTimeRange, !isDual, presenterInterface)

    private val leftSequence = if (isDual) SequenceStack(this, TimelineAlignment.RIGHT) else null
    private val rightSequence = if (isDual) SequenceStack(this, TimelineAlignment.LEFT) else null
    private val singleSequence = if (!isDual) SequenceStack(this, TimelineAlignment.RIGHT) else null

    init {
        layout = null
        add(basePanel)
        add(labelContainer)
        setLayer(basePanel, DEFAULT_LAYER)
        setLayer(labelContainer, PALETTE_LAYER)

        if (isDual) {
            basePanel.add(leftSequence)
            basePanel.add(timeScale)
            basePanel.add(rightSequence)
        } else {
            basePanel.add(timeScale)
            basePanel.add(singleSequence)
        }

        selectedTimeRange.addListener { labelContainer.repaint() }
    }

    private fun computedListWidth(): Int {
        val overlayPref = labelContainer.preferredSize.width
        if (overlayPref <= scaleWidth) return 280 // fallback
        val contentWidth = overlayPref - scaleWidth
        return if (isDual) (contentWidth / 2).coerceAtLeast(1) else contentWidth
    }

    override fun doLayout() {
        super.doLayout()
        basePanel.setBounds(0, 0, width, height)
        val listWidthDynamic = computedListWidth()
        if (isDual) {
            leftSequence?.setBounds(0, 0, listWidthDynamic, height)
            timeScale.setBounds(listWidthDynamic, 0, scaleWidth, height)
            rightSequence?.setBounds(listWidthDynamic + scaleWidth, 0, listWidthDynamic, height)
        } else {
            timeScale.setBounds(0, 0, scaleWidth, height)
            singleSequence?.setBounds(scaleWidth, 0, listWidthDynamic, height)
        }
        labelContainer.setBounds(0, 0, width, height)
    }

    override fun getPreferredSize(): Dimension {
        val fallback = if (isDual) scaleWidth + 280 * 2 else scaleWidth + 280
        val overlayWidth = labelContainer.preferredSize.width.takeIf { it > 0 } ?: fallback
        return Dimension(overlayWidth, super.getPreferredSize().height)
    }

    fun updateTimelineData(timelineData: TimelineData) {
        labelContainer.updateTimelineData(timelineData)
        timeScale.updateTimelineData(timelineData)
    }

    fun calculateYPositionForInstant(instant: Instant): Int {
        val timespanSeconds = selectedTimeRange.value.end.epochSeconds - selectedTimeRange.value.start.epochSeconds
        val pixelsPerSecond = height.toFloat() / timespanSeconds.toFloat()
        return (height - pixelsPerSecond * (instant.epochSeconds - selectedTimeRange.value.start.epochSeconds)).toInt()
    }

    fun calculateInstantForYPosition(y: Int): Instant {
        val timespanSeconds = selectedTimeRange.value.end.epochSeconds - selectedTimeRange.value.start.epochSeconds
        val pixelsPerSecond = height.toFloat() / timespanSeconds.toFloat()
        val secondsFromStart = (height - y) / pixelsPerSecond
        return selectedTimeRange.value.start + secondsFromStart.toLong().seconds
    }

    fun getScaleBounds(): Rectangle {
        return timeScale.bounds
    }


    fun updateDraggedLabel(timelineEvent: TimelineEvent, proposedTime: Instant, available: Boolean) {
        labelContainer.updateDraggedLabel(timelineEvent, proposedTime, available)
    }

    fun getScaleWidth(): Int = scaleWidth
}