package no.vaccsca.amandman.view.airport.timeline

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.planning.SequenceStatus
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineOverlayDirectRoutingIndicatorTest {

    @Test
    fun `directRoutingIndicatorFor should return triangle for active IAF or IF direct`() {
        val indicator = directRoutingIndicatorFor(
            arrivalEvent(
                assignedDirectIsActive = true,
                assignedDirectIsIF = true,
                assignedDirectIsAfterFeederFix = true,
            )
        )

        assertEquals(DirectRoutingIndicator.TRIANGLE, indicator)
    }

    @Test
    fun `directRoutingIndicatorFor should return circle for active non IAF or IF direct after feeder`() {
        val indicator = directRoutingIndicatorFor(
            arrivalEvent(
                assignedDirectIsActive = true,
                assignedDirectIsAfterFeederFix = true,
            )
        )

        assertEquals(DirectRoutingIndicator.CIRCLE, indicator)
    }

    @Test
    fun `directRoutingIndicatorFor should return no symbol when direct is inactive`() {
        val indicator = directRoutingIndicatorFor(
            arrivalEvent(
                assignedDirectIsActive = false,
                assignedDirectIsIF = true,
                assignedDirectIsAfterFeederFix = true,
            )
        )

        assertNull(indicator)
    }

    @Test
    fun `directRoutingIndicatorFor should return no symbol when active direct is not after feeder`() {
        val indicator = directRoutingIndicatorFor(
            arrivalEvent(
                assignedDirectIsActive = true,
                assignedDirectIsAfterFeederFix = false,
            )
        )

        assertNull(indicator)
    }

    @Test
    fun `directRoutingIndicatorAnchorX should place circle on label edge`() {
        assertEquals(97, directRoutingIndicatorAnchorX(100, 40, isOnRightSide = true, indicator = DirectRoutingIndicator.CIRCLE))
        assertEquals(143, directRoutingIndicatorAnchorX(100, 40, isOnRightSide = false, indicator = DirectRoutingIndicator.CIRCLE))
    }

    @Test
    fun `directRoutingIndicatorAnchorX should keep triangle offset from label edge`() {
        assertEquals(94, directRoutingIndicatorAnchorX(100, 40, isOnRightSide = true, indicator = DirectRoutingIndicator.TRIANGLE))
        assertEquals(146, directRoutingIndicatorAnchorX(100, 40, isOnRightSide = false, indicator = DirectRoutingIndicator.TRIANGLE))
    }

    private fun arrivalEvent(
        assignedDirectIsActive: Boolean,
        assignedDirectIsIAF: Boolean = false,
        assignedDirectIsIF: Boolean = false,
        assignedDirectIsAfterFeederFix: Boolean = false,
    ) = RunwayArrivalEvent(
        scheduledTime = Instant.parse("2026-04-08T18:05:00Z"),
        estimatedTime = Instant.parse("2026-04-08T18:03:00Z"),
        lastTimestamp = Instant.parse("2026-04-08T18:00:00Z"),
        runway = "19L",
        callsign = "SAS123",
        icaoType = "B738",
        wakeCategory = 'M',
        airportIcao = "ENGM",
        trackingController = null,
        assignedStar = "INREX4M",
        flightLevel = 120,
        pressureAltitude = 10000,
        groundSpeed = 250,
        remainingDistance = 80f,
        withinActiveAdvisoryHorizon = true,
        sequenceStatus = SequenceStatus.OK,
        landingIas = 140,
        assignedDirect = "OSPAD",
        scratchPad = null,
        assignedDirectIsIAF = assignedDirectIsIAF,
        assignedDirectIsIF = assignedDirectIsIF,
        assignedDirectIsActive = assignedDirectIsActive,
        assignedDirectIsAfterFeederFix = assignedDirectIsAfterFeederFix,
    )
}
