package no.vaccsca.amandman.view.airport.timeline

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.util.NumberUtils.format
import no.vaccsca.amandman.model.timeline.TimelineData
import no.vaccsca.amandman.model.timeline.TimelineDisplayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayDelayEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.AmanMenuItemData
import no.vaccsca.amandman.view.AmanPopupMenu
import no.vaccsca.amandman.view.airport.timeline.utils.GraphicUtils.drawStringAdvanced
import no.vaccsca.amandman.view.entity.SharedValue
import no.vaccsca.amandman.view.entity.TimeRange
import java.awt.Color
import java.awt.Graphics
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel

class TimeScale(
    private val timelineView: TimelineView,
    private val selectedRange: SharedValue<TimeRange>,
    private val scaleOnRightSideOnly: Boolean,
    private val presenterProvider: () -> AirportPresenterInterface
) : JPanel(null) {

    private val presenter: AirportPresenterInterface get() = presenterProvider()
    private val TICK_WIDTH_1_MIN = 5
    private val TICK_WIDTH_5_MIN = 10

    private val lineColor = Color.decode("#C8C8C8")
    private val pastColor = Color.decode("#4B4B4B")

    private var leftEvents: List<TimelineDisplayEvent>? = null
    private var rightEvents: List<TimelineDisplayEvent>? = null

    init {
        background = Color.decode("#646464")
        border = BorderFactory.createMatteBorder(0, 1, 0, 1, lineColor)

        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }
        })
    }

    fun updateTimelineData(timelineData: TimelineData) {
        leftEvents = timelineData.left
        rightEvents = timelineData.right
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val timespanSeconds = selectedRange.value.end.epochSeconds - selectedRange.value.start.epochSeconds

        val timeNow = NtpClock.now()

        // Set background color of time that has passed
        val currentTimeYpos = timelineView.calculateYPositionForInstant(timeNow)
        g.color = pastColor
        g.fillRect(0, currentTimeYpos, width, height - currentTimeYpos)

        g.color = lineColor
        for (timestep in 0 .. timespanSeconds) {
            val accInstant = Instant.fromEpochSeconds(selectedRange.value.start.epochSeconds + timestep)
            val accSeconds = accInstant.epochSeconds
            val yPos = timelineView.calculateYPositionForInstant(Instant.fromEpochSeconds(accSeconds))

            if (accSeconds % (60L * 5L) == 0L) {
                if (!scaleOnRightSideOnly) {
                    g.drawLine(0, yPos, TICK_WIDTH_5_MIN, yPos)
                }
                g.drawLine(width, yPos, width - TICK_WIDTH_5_MIN, yPos)
                val scaleCenter = width / 2
                if (accSeconds % (60L * 10L) == 0L) {
                    g.drawStringAdvanced(accInstant.format("HH:mm"), scaleCenter, yPos)
                } else {
                    g.drawStringAdvanced(accInstant.format("mm"), scaleCenter, yPos)
                }
            } else if (accSeconds % 60L == 0L) {
                if (!scaleOnRightSideOnly) {
                    g.drawLine(0, yPos, TICK_WIDTH_1_MIN, yPos)
                }
                g.drawLine(width, yPos, width - TICK_WIDTH_1_MIN, yPos)
            }
        }

        leftEvents?.let {
            drawDelays(g, it.filter { sideEvent -> sideEvent.event is RunwayDelayEvent })
        }
        rightEvents?.let {
            drawDelays(g, it.filter { sideEvent -> sideEvent.event is RunwayDelayEvent })
        }
    }

    private fun drawDelays(g: Graphics, delays: List<TimelineDisplayEvent>) {
        delays.forEach { sideEvent ->
            val event = sideEvent.event as? RunwayDelayEvent ?: return@forEach
            val topY = timelineView.calculateYPositionForInstant(sideEvent.displayScheduledTime + event.delay)
            val height = timelineView.calculateYPositionForInstant(sideEvent.displayScheduledTime) - topY
            g.color = Color.RED
            g.fillRect(0, topY, 2, height)
        }
    }

    private fun showPopupMenu(e: MouseEvent) {
        val popup = buildPopupMenu()
        popup.show(e.component, e.x, e.y)
    }

    internal fun buildPopupMenu(): AmanPopupMenu {
        return buildTimelinePopupMenu(presenter, timelineView.timelineConfig)
    }
}

fun buildTimelinePopupMenu(
    presenter: AirportPresenterInterface,
    timelineConfig: TimelineConfig
): AmanPopupMenu {
    return AmanPopupMenu(
        "Timeline Actions",
        AmanMenuItemData("Edit timeline", action = { presenter.onEditTimelineRequested(timelineConfig) }),
        AmanMenuItemData("Move left", action = { presenter.onMoveTimelineLeftRequested(timelineConfig) }),
        AmanMenuItemData("Move right", action = { presenter.onMoveTimelineRightRequested(timelineConfig) }),
        AmanMenuItemData("Remove timeline", action = { presenter.onRemoveTimelineClicked(timelineConfig) }),
    )
}
