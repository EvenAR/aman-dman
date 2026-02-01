package no.vaccsca.amandman.view.airport.timeline.labels

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.valueobjects.LabelItem
import no.vaccsca.amandman.model.domain.valueobjects.LabelItemSource
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.AmanPopupMenu
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ArrivalLabel(
    override val labelItems: List<LabelItem>,
    val arrivalEvent: RunwayArrivalEvent,
    val presenter: AirportPresenterInterface,
    hBorder: Int,
    vBorder: Int
) : TimelineLabel(arrivalEvent, labelItems, hBorder = hBorder, vBorder = vBorder) {

    private val TTL_TTG_THRESHOLD = 10.seconds

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { maybeShowPopup(e) }
            override fun mouseReleased(e: MouseEvent) { maybeShowPopup(e) }

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) showPopupMenu(e)
            }
        })
    }

    override fun getTimelinePlacement(): Instant = timelineEvent.scheduledTime

    override fun decideLabelItemStyle(item: LabelItem, event: TimelineEvent): LabelStyleOptions {
        val arrival = event as RunwayArrivalEvent
        val borderColor = if (arrival.isSelected) Color.WHITE else null
        
        return when (item.source) {
            LabelItemSource.CALL_SIGN ->
                LabelStyleOptions(text = arrival.callsign, borderColor = borderColor)

            LabelItemSource.ASSIGNED_RUNWAY ->
                LabelStyleOptions(text = arrival.runway, borderColor = borderColor)

            LabelItemSource.ASSIGNED_STAR ->
                LabelStyleOptions(text = arrival.assignedStar ?: "", borderColor = borderColor)

            LabelItemSource.AIRCRAFT_TYPE ->
                LabelStyleOptions(text = arrival.icaoType, borderColor = borderColor)

            LabelItemSource.WAKE_CATEGORY ->
                LabelStyleOptions(text = arrival.wakeCategory.toString(), textColor = wakeCatColor(arrival.wakeCategory), borderColor = borderColor)

            LabelItemSource.TTL_TTG ->
                LabelStyleOptions(text = formatTtlTtgValue(arrival), textColor = ttlTtgColor(arrival.scheduledTime - arrival.estimatedTime), borderColor = borderColor)

            LabelItemSource.TIME_BEHIND_PRECEDING -> {
                val text = arrival.timeToPreceding?.let { toHhMm(it) } ?: "--:--"
                LabelStyleOptions(text = text, borderColor = borderColor)
            }

            LabelItemSource.TIME_BEHIND_PRECEDING_ROUNDED -> {
                val text = arrival.timeToPreceding?.let { toNormalizedMinutes(it).toString() } ?: "0"
                LabelStyleOptions(text = text, borderColor = borderColor)
            }

            LabelItemSource.REMAINING_DISTANCE ->
                LabelStyleOptions(text = arrival.remainingDistance.roundToInt().toString(), borderColor = borderColor)

            LabelItemSource.DISTANCE_BEHIND_PRECEDING ->
                LabelStyleOptions(text = (arrival.distanceToPreceding ?: arrival.remainingDistance).roundToInt().toString(), borderColor = borderColor)

            LabelItemSource.DIRECT_ROUTING ->
                LabelStyleOptions(text = arrival.assignedDirect ?: "", borderColor = borderColor)

            LabelItemSource.SCRATCH_PAD ->
                LabelStyleOptions(text = arrival.scratchPad ?: "", borderColor = borderColor)

            LabelItemSource.ESTIMATED_LANDING_TIME ->
                LabelStyleOptions(text = SimpleDateFormat("HH:mm").format(arrival.estimatedTime.epochSeconds * 1000), borderColor = borderColor)

            LabelItemSource.GROUND_SPEED ->
                LabelStyleOptions(text = arrival.groundSpeed.toString(), borderColor = borderColor)

            LabelItemSource.GROUND_SPEED_10 ->
                LabelStyleOptions(text = ((arrival.groundSpeed / 10) * 10).toString(), borderColor = borderColor)

            LabelItemSource.ALTITUDE ->
                LabelStyleOptions(text = arrival.pressureAltitude.toString(), borderColor = borderColor)
        }
    }

    private fun showPopupMenu(e: MouseEvent) {
        val popup = AmanPopupMenu("Flight Options") {
            item("Re-schedule", action = {
                presenter.onRecalculateSequenceClicked(arrivalEvent.callsign)
            })
            item("Show vertical profile", action = {
                presenter.onOpenVerticalProfileWindowClicked(arrivalEvent.callsign)
            })
        }
        popup.show(e.component, e.x, e.y)
    }

    private fun wakeCatColor(wakeCategory: Char): Color? =
        when (wakeCategory) {
            'L' -> Color.ORANGE
            'H', 'J' -> Color.YELLOW
            else -> null
        }

    private fun ttlTtgColor(timeToLoseOrGain: Duration): Color? =
        when {
            timeToLoseOrGain > TTL_TTG_THRESHOLD -> Color.YELLOW
            timeToLoseOrGain < -TTL_TTG_THRESHOLD -> Color.GREEN
            else -> null
        }

    private fun formatTtlTtgValue(flight: RunwayArrivalEvent): String {
        val timeToLoseOrGain = flight.scheduledTime - flight.estimatedTime
        val minutesToLoseOrGain = toNormalizedMinutes(timeToLoseOrGain)
        return when {
            timeToLoseOrGain > TTL_TTG_THRESHOLD -> "+$minutesToLoseOrGain"
            timeToLoseOrGain < -TTL_TTG_THRESHOLD -> minutesToLoseOrGain.toString()
            else -> ""
        }
    }

    private fun toNormalizedMinutes(seconds: Duration): Int {
        val minutes = seconds.inWholeSeconds.toDouble() / 60.0
        return when {
            minutes > 0 -> ceil(minutes).toInt()
            minutes < 0 -> floor(minutes).toInt()
            else -> 0
        }
    }

    private fun toHhMm(duration: Duration): String {
        val minutes = duration.inWholeSeconds / 60
        val seconds = duration.inWholeSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
