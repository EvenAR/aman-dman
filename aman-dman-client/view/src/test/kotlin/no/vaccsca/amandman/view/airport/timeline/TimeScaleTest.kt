package no.vaccsca.amandman.view.airport.timeline

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.RunwayTimelineConfig
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import java.awt.Point
import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TimeScaleTest {

    @Test
    fun `timeline popup contains edit and remove and triggers presenter actions`() {
        val timelineConfig: TimelineConfig = RunwayTimelineConfig(
            title = "T1",
            airportIcao = "TEST",
            leftRunways = emptyList(),
            rightRunways = listOf("19L"),
            depLabelLayout = "DEP",
            arrLabelLayout = "osloArr",
        )
        val presenter = CapturingPresenter()

        val popup = buildTimelinePopupMenu(presenter, timelineConfig)
        val menuItems = popup.components.filterIsInstance<JMenuItem>()

        assertEquals(listOf("Edit timeline", "Remove timeline"), menuItems.map { it.text })

        val editItem = menuItems.firstOrNull { it.text == "Edit timeline" }
        assertNotNull(editItem)
        editItem.doClick()
        assertEquals(listOf(timelineConfig), presenter.editRequests)

        val removeItem = menuItems.firstOrNull { it.text == "Remove timeline" }
        assertNotNull(removeItem)
        removeItem.doClick()
        assertEquals(listOf(timelineConfig), presenter.removeRequests)
    }

    private class CapturingPresenter : AirportPresenterInterface {
        override val airportIcao: String = "TEST"
        val editRequests = mutableListOf<TimelineConfig>()
        val removeRequests = mutableListOf<TimelineConfig>()

        override fun onLabelDrag(timelineEvent: TimelineEvent, newInstant: Instant) {}
        override fun onLabelDragEnd(timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String?) {}
        override fun onRecalculateSequenceClicked(callSign: String?) {}
        override fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double) {}
        override fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?) {}
        override fun onOpenMetWindowClicked() {}
        override fun onOpenLandingRatesWindow() {}
        override fun onOpenNonSequencedWindow() {}
        override fun onOpenVerticalProfileWindowClicked(callsign: String) {}
        override fun onAircraftSelected(callsign: String) {}
        override fun beginRunwaySelection(runwayEvent: RunwayEvent, onSubmit: (runway: String?) -> Unit, onCancel: () -> Unit) {}
        override fun onToggleShowDepartures(selected: Boolean) {}
        override fun onReloadWindsClicked() {}
        override fun onTabMenu(screenPos: Point) {}
        override fun onCreateNewTimelineClicked() {}
        override fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig) {}
        override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {
            removeRequests += timelineConfig
        }
        override fun onEditTimelineRequested(timelineConfig: TimelineConfig) {
            editRequests += timelineConfig
        }
        override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {}
        override fun onRemoveTab() {}
    }
}
