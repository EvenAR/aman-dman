package no.vaccsca.amandman.view.airport.timeline.labels

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.config.LabelItem
import no.vaccsca.amandman.model.config.LabelItemSource
import no.vaccsca.amandman.model.timeline.event.timeline.DepartureEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.view.entity.AircraftSelection
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.Color

class DepartureLabel(
    override val labelItems: List<LabelItem>,
    departureEvent: DepartureEvent,
    hBorder: Int,
    vBorder: Int,
    aircraftSelection: SharedValue<AircraftSelection?>,
) : TimelineLabel(
    departureEvent,
    defaultBackgroundColor = Color.decode("#83989B"),
    defaultForegroundColor = Color.BLACK,
    hoverBackgroundColor = Color.GRAY,
    hoverForegroundColor = Color.WHITE,
    hBorder = hBorder,
    vBorder = vBorder,
    labelItems = labelItems,
    aircraftSelection = aircraftSelection,
) {
    override fun paintBorder(g: java.awt.Graphics) {
        super.paintBorder(g)
        g.color = Color.GRAY
        g.drawRoundRect(this.visibleRect.x, visibleRect.y, visibleRect.width - 1, visibleRect.height - 1, 4, 4)
    }

    override fun decideLabelItemStyle(
        item: LabelItem,
        event: TimelineEvent
    ): LabelStyleOptions {
        val departure = event as DepartureEvent
        return when (item.source) {
            LabelItemSource.CALL_SIGN ->
                LabelStyleOptions(text = departure.callsign)

            LabelItemSource.ASSIGNED_RUNWAY ->
                LabelStyleOptions(text = departure.runway)

            LabelItemSource.AIRCRAFT_TYPE ->
                LabelStyleOptions(text = departure.icaoType)

            LabelItemSource.WAKE_CATEGORY ->
                LabelStyleOptions(text = departure.wakeCategory.toString())

            else ->
                LabelStyleOptions(text = "------")
        }
    }

    override fun getTimelinePlacement(): Instant = displayScheduledTime
}
