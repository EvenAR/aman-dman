package no.vaccsca.amandman.view.airport

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.view.entity.TimeRange
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.*
import java.awt.event.MouseEvent
import kotlin.math.roundToInt
import kotlin.time.Duration

class TimeRangeScrollBarVertical(
    selectedRange: SharedValue<TimeRange>,
    availableRange: SharedValue<TimeRange>,
) : TimeRangeScrollBarAbstract(selectedRange, availableRange) {

    override fun getInitialSize(): Dimension {
        return Dimension(scrollbarWidth, 0) // Default height, can be adjusted
    }

    override fun getBarStart(): Int {
        val totalRangeSeconds = availableTimelineSeconds()
        val startPositionOffset = selectedRange.value.start.epochSeconds - availableRange.value.start.epochSeconds

        val relativeTimeStart = 1 - startPositionOffset.toFloat() / totalRangeSeconds.toFloat()

        return (relativeTimeStart * height).toInt()
    }

    override fun getBarEnd(): Int {
        val totalRangeSeconds = availableTimelineSeconds()
        val endPositionOffset = selectedRange.value.end.epochSeconds - availableRange.value.start.epochSeconds

        val relativeTimeEnd = 1 - endPositionOffset.toFloat() / totalRangeSeconds.toFloat()

        return (relativeTimeEnd * height).toInt()
    }

    override fun axisPosition(e: MouseEvent) =
        e.y

    override fun getScrollbarLength() =
        height

    override fun drawScrollBar(g2: Graphics2D) {
        val barStart = getBarStart()
        val barEnd = getBarEnd()

        timelineEvents.forEach { occurrence ->
            when (occurrence) {
                is DepartureEvent ->
                    // Hex
                    drawEvent(g2, occurrence.scheduledTime, Color(0x51BAB7))
                else ->
                    drawEvent(g2, occurrence.scheduledTime, Color.WHITE)
            }
        }

        val handleLeft = scrollHandleMargin
        val handleRight = handleLeft + scrollHandleWidth

        val gradient = GradientPaint(
            handleLeft.toFloat(), barEnd.toFloat(), Color(255, 255, 255, 40),
            handleRight.toFloat(), barEnd.toFloat(), Color(255, 255, 255, 20)
        )

        // Handle fill
        g2.paint = gradient
        g2.fillRoundRect(handleLeft, barEnd, scrollHandleWidth, barStart - barEnd, cornerRadius, cornerRadius)

        // Handle border
        g2.color = foreground
        g2.drawRoundRect(handleLeft, barEnd, scrollHandleWidth - 1, barStart - barEnd, cornerRadius, cornerRadius)

        // Resize handles
        g2.fillRoundRect(handleLeft, barEnd, scrollHandleWidth - 1, resizeHandleThickness, cornerRadius, cornerRadius)
        g2.fillRoundRect(handleLeft, barStart - resizeHandleThickness, scrollHandleWidth - 1, resizeHandleThickness, cornerRadius, cornerRadius)
    }

    override fun drawEvent(g: Graphics, instant: Instant, color: Color) {
        val etaOffset = instant.epochSeconds - availableRange.value.start.epochSeconds
        g.color = color

        val yPos = (1 - etaOffset.toFloat() /  availableTimelineSeconds().toFloat())*height
        g.drawLine(scrollHandleMargin * 2, yPos.toInt(), width - scrollHandleMargin * 2 - 1, yPos.toInt())
    }

    override fun drawHighlight(g: Graphics, instant: Instant, duration: Duration, color: Color) {
        val startY = (1 - (instant.epochSeconds - availableRange.value.start.epochSeconds).toFloat() / availableTimelineSeconds()) * height.toInt()
        val endY = (1 - (instant.plus(duration).epochSeconds - availableRange.value.start.epochSeconds).toFloat() / availableTimelineSeconds()) * height.toInt()
        g.color = color
        g.fillRect(0, endY.roundToInt(), width, startY.roundToInt() - endY.roundToInt())
    }

    override fun drawNowIndicator(g: Graphics2D) {
        val relNow = 1 - (NtpClock.now().epochSeconds - availableRange.value.start.epochSeconds).toFloat() / availableTimelineSeconds()
        val pos = (relNow * getScrollbarLength()).toInt()
        drawAxisLine(g, pos)
    }

    override fun drawAxisLine(g: Graphics2D, pos: Int) =
        g.drawLine(0, pos, width, pos)
}
