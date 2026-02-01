package no.vaccsca.amandman.view.airport.timeline.labels

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.valueobjects.LabelItem
import no.vaccsca.amandman.model.domain.valueobjects.LabelItemSource
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.Color

class DepartureLabel(
    override val labelItems: List<LabelItem>,
    departureEvent: DepartureEvent,
    hBorder: Int,
    vBorder: Int,
    selectedAircraftCallsign: SharedValue<String>? = null,
    selectedAircraftTimestamp: SharedValue<Instant?>? = null,
) : TimelineLabel(
    departureEvent,
    defaultBackgroundColor = Color.decode("#83989B"),
    defaultForegroundColor = Color.BLACK,
    hoverBackgroundColor = Color.GRAY,
    hoverForegroundColor = Color.WHITE,
    hBorder = hBorder,
    vBorder = vBorder,
    labelItems = labelItems,
    selectedAircraftCallsign = selectedAircraftCallsign,
    selectedAircraftTimestamp = selectedAircraftTimestamp,
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

    override fun getTimelinePlacement(): Instant {
        return (timelineEvent as DepartureEvent).scheduledTime
    }

}