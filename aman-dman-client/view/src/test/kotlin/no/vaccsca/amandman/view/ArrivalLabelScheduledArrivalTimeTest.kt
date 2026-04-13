package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.config.LabelItem
import no.vaccsca.amandman.model.config.LabelItemSource
import no.vaccsca.amandman.model.planning.SequenceStatus
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.airport.timeline.labels.ArrivalLabel
import no.vaccsca.amandman.view.entity.AircraftSelection
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.Color
import java.awt.Point
import javax.swing.JLabel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ArrivalLabelScheduledArrivalTimeTest {

    @BeforeTest
    fun setupHeadless() {
        System.setProperty("java.awt.headless", "true")
    }

    @Test
    fun `scheduledArrivalTime should render runway STO in UTC by default`() {
        val label = createLabel(scheduledTime = Instant.parse("2026-03-07T12:34:56Z"))

        label.updateText()

        assertEquals("12:34:56", labelText(label))
    }

    @Test
    fun `scheduledArrivalTime should honor configured time format`() {
        val label = createLabel(
            scheduledTime = Instant.parse("2026-03-07T12:34:56Z"),
            labelItems = listOf(
                LabelItem(
                    source = LabelItemSource.SCHEDULED_ARRIVAL_TIME,
                    width = 5,
                    timeFormat = "HH:mm",
                )
            ),
        )

        label.updateText()

        assertEquals("12:34", labelText(label))
    }

    @Test
    fun `scheduledArrivalTime should support minute only time format`() {
        val label = createLabel(
            scheduledTime = Instant.parse("2026-03-07T12:34:56Z"),
            labelItems = listOf(
                LabelItem(
                    source = LabelItemSource.SCHEDULED_ARRIVAL_TIME,
                    width = 2,
                    timeFormat = "mm",
                )
            ),
        )

        label.updateText()

        assertEquals("34", labelText(label))
    }

    @Test
    fun `scheduledArrivalTime should render feeder fix STO when display scheduled time is overridden`() {
        val label = createLabel(scheduledTime = Instant.parse("2026-03-07T12:34:56Z"))
        label.applyDisplayTimes(
            scheduledTime = Instant.parse("2026-03-07T11:22:33Z"),
            estimatedTime = Instant.parse("2026-03-07T11:00:00Z"),
        )

        label.updateText()

        assertEquals("11:22:33", labelText(label))
    }

    @Test
    fun `estimatedArrivalTime should honor configured time format`() {
        val label = createLabel(
            scheduledTime = Instant.parse("2026-03-07T12:34:56Z"),
            labelItems = listOf(
                LabelItem(
                    source = LabelItemSource.ESTIMATED_ARRIVAL_TIME,
                    width = 2,
                    timeFormat = "HH",
                )
            ),
        )

        label.updateText()

        assertEquals("12", labelText(label))
    }

    @Test
    fun `label item should still respect maxLength before width padding`() {
        val label = createLabel(
            scheduledTime = Instant.parse("2026-03-07T12:34:56Z"),
            labelItems = listOf(
                LabelItem(
                    source = LabelItemSource.CALL_SIGN,
                    width = 6,
                    maxLength = 4,
                )
            ),
        )

        label.updateText()

        assertEquals("SAS1  ", labelText(label))
    }

    @Test
    fun `distance behind preceding should show countdown in green when turn advisory is active`() {
        val label = createLabel(
            scheduledTime = Instant.parse("2026-03-07T12:34:56Z"),
            labelItems = listOf(LabelItem(source = LabelItemSource.DISTANCE_BEHIND_PRECEDING, width = 4)),
            distanceToPreceding = 14f,
            turnToIafAdvisoryTime = NtpClock.now() + 9.seconds,
        )

        label.updateText()

        val text = labelText(label).trim()
        assertTrue(text.startsWith("T"))
        val countdownValue = text.removePrefix("T").toInt()
        assertTrue(countdownValue in 8..9)
        assertEquals(Color.GREEN, labelTextColor(label))
    }

    @Test
    fun `distance behind preceding should show T in green once turn advisory time is reached`() {
        val label = createLabel(
            scheduledTime = Instant.parse("2026-03-07T12:34:56Z"),
            labelItems = listOf(LabelItem(source = LabelItemSource.DISTANCE_BEHIND_PRECEDING, width = 4)),
            distanceToPreceding = 14f,
            turnToIafAdvisoryTime = NtpClock.now() - 1.seconds,
        )

        label.updateText()

        assertEquals("T", labelText(label).trim())
        assertEquals(Color.GREEN, labelTextColor(label))
    }

    private fun labelText(label: ArrivalLabel): String = (label.components[0] as JLabel).text
    private fun labelTextColor(label: ArrivalLabel): Color? = (label.components[0] as JLabel).foreground

    private fun createLabel(
        scheduledTime: Instant,
        labelItems: List<LabelItem> = listOf(LabelItem(source = LabelItemSource.SCHEDULED_ARRIVAL_TIME, width = 8)),
        distanceToPreceding: Float? = null,
        turnToIafAdvisoryTime: Instant? = null,
    ): ArrivalLabel {
        val arrivalEvent = RunwayArrivalEvent(
            scheduledTime = scheduledTime,
            estimatedTime = Instant.parse("2026-03-07T12:20:00Z"),
            lastTimestamp = Instant.parse("2026-03-07T12:00:00Z"),
            runway = "01L",
            callsign = "SAS123",
            icaoType = "B738",
            wakeCategory = 'M',
            airportIcao = "ENGM",
            trackingController = null,
            assignedStar = "GODOS",
            flightLevel = 100,
            pressureAltitude = 5000,
            groundSpeed = 230,
            remainingDistance = 65f,
            withinActiveAdvisoryHorizon = true,
            sequenceStatus = SequenceStatus.OK,
            landingIas = 145,
            distanceToPreceding = distanceToPreceding,
            assignedDirect = null,
            scratchPad = null,
            assignedDirectIsIAF = false,
            assignedDirectIsIF = false,
            turnToIafAdvisoryTime = turnToIafAdvisoryTime,
        )

        return ArrivalLabel(
            labelItems = labelItems,
            arrivalEvent = arrivalEvent,
            presenter = NoopAirportPresenter,
            hBorder = 0,
            vBorder = 0,
            aircraftSelection = SharedValue<AircraftSelection?>(null),
        )
    }

    private object NoopAirportPresenter : AirportPresenterInterface {
        override val airportIcao: String = "ENGM"

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
        override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {}
        override fun onEditTimelineRequested(timelineConfig: TimelineConfig) {}
        override fun onMoveTimelineLeftRequested(timelineConfig: TimelineConfig) {}
        override fun onMoveTimelineRightRequested(timelineConfig: TimelineConfig) {}
        override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {}
        override fun onDeleteEditedTimeline() {}
        override fun onRemoveTab() {}
    }
}
