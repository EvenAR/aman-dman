package no.vaccsca.amandman.view.airport.timeline

import no.vaccsca.amandman.model.timeline.event.timeline.RunwayDelayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.view.airport.timeline.enums.TimelineAlignment
import java.awt.Color
import java.awt.Graphics
import javax.swing.JPanel

class SequenceStack(
    val timelineView: TimelineView,
    val alignment: TimelineAlignment
) : JPanel() {

    private val lineLength = 30
    private val lineToLabel = 10

    private var timelineEvents: List<TimelineEvent> = emptyList()

    fun updateTimelineEvents(occurrences: List<TimelineEvent>) {
        this.timelineEvents = occurrences
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val delays = timelineEvents.filterIsInstance<RunwayDelayEvent>()

        delays.forEach {
            val y = timelineView.calculateYPositionForInstant(it.scheduledTime)
            it.delay.toComponents { hours, minutes, seconds, nanoseconds ->
                "$hours:$minutes:$seconds"
            }

            val text = it.name.uppercase() + "  " + it.delay

            when (alignment) {
                TimelineAlignment.LEFT -> {
                    g.color = Color.RED
                    g.drawLine(0, y, lineLength, y)
                    g.color = Color.WHITE
                    g.drawString(text, lineLength + lineToLabel, y)
                }
                TimelineAlignment.RIGHT -> {
                    g.color = Color.RED
                    g.drawLine(width, y, width - lineLength, y)
                    g.color = Color.WHITE
                    val textWidth = g.fontMetrics.stringWidth(text)
                    g.drawString(text, width - textWidth - lineLength - lineToLabel, y)
                }
            }
        }
    }
}