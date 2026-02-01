package no.vaccsca.amandman.view.airport.timeline.labels

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.valueobjects.LabelItem
import no.vaccsca.amandman.model.domain.valueobjects.LabelItemSource
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.AmanPopupMenu
import no.vaccsca.amandman.view.entity.SharedValue
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
    vBorder: Int,
    selectedAircraftCallsign: SharedValue<String>? = null,
    selectedAircraftTimestamp: SharedValue<Instant?>? = null,
) : TimelineLabel(arrivalEvent, labelItems, hBorder = hBorder, vBorder = vBorder, selectedAircraftCallsign = selectedAircraftCallsign, selectedAircraftTimestamp = selectedAircraftTimestamp) {

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
        
        return when (item.source) {
            LabelItemSource.CALL_SIGN ->
                LabelStyleOptions(text = arrival.callsign)

            LabelItemSource.ASSIGNED_RUNWAY ->
                LabelStyleOptions(text = arrival.runway)

            LabelItemSource.ASSIGNED_STAR ->
                LabelStyleOptions(text = arrival.assignedStar ?: "")

            LabelItemSource.AIRCRAFT_TYPE ->
                LabelStyleOptions(text = arrival.icaoType)

            LabelItemSource.WAKE_CATEGORY ->
                LabelStyleOptions(text = arrival.wakeCategory.toString(), textColor = wakeCatColor(arrival.wakeCategory))

            LabelItemSource.TTL_TTG ->
                LabelStyleOptions(text = formatTtlTtgValue(arrival), textColor = ttlTtgColor(arrival.scheduledTime - arrival.estimatedTime))

            LabelItemSource.TIME_BEHIND_PRECEDING -> {
                val text = arrival.timeToPreceding?.let { toHhMm(it) } ?: "--:--"
                LabelStyleOptions(text = text)
            }

            LabelItemSource.TIME_BEHIND_PRECEDING_ROUNDED -> {
                val text = arrival.timeToPreceding?.let { toNormalizedMinutes(it).toString() } ?: "0"
                LabelStyleOptions(text = text)
            }

            LabelItemSource.REMAINING_DISTANCE ->
                LabelStyleOptions(text = arrival.remainingDistance.roundToInt().toString())

            LabelItemSource.DISTANCE_BEHIND_PRECEDING ->
                LabelStyleOptions(text = (arrival.distanceToPreceding ?: arrival.remainingDistance).roundToInt().toString())

            LabelItemSource.DIRECT_ROUTING ->
                LabelStyleOptions(text = arrival.assignedDirect ?: "")

            LabelItemSource.SCRATCH_PAD ->
                LabelStyleOptions(text = arrival.scratchPad ?: "")

            LabelItemSource.ESTIMATED_LANDING_TIME ->
                LabelStyleOptions(text = SimpleDateFormat("HH:mm").format(arrival.estimatedTime.epochSeconds * 1000))

            LabelItemSource.GROUND_SPEED ->
                LabelStyleOptions(text = arrival.groundSpeed.toString())

            LabelItemSource.GROUND_SPEED_10 ->
                LabelStyleOptions(text = ((arrival.groundSpeed / 10) * 10).toString())

            LabelItemSource.ALTITUDE ->
                LabelStyleOptions(text = arrival.pressureAltitude.toString())
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
