import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.atc.ExtractedRoutePoint
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.planning.DynamicFromTrajectoryFeederFixTimingStrategy
import no.vaccsca.amandman.model.planning.FeederFixTimingService
import no.vaccsca.amandman.model.planning.SequenceStatus
import no.vaccsca.amandman.model.planning.TrajectoryPoint
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.weather.WindVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class FeederFixTimingServiceTest {

    private val referenceEto = Instant.parse("2026-03-07T12:30:00Z")
    private val referenceSto = Instant.parse("2026-03-07T12:35:00Z")

    @Test
    fun `buildState should derive feeder fix ETO and STO from trajectory points`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1", "F2")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto)

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint("F1", 20.minutes, LatLng(60.0, 11.0)),
                    trajectoryPoint("F2", 10.minutes, LatLng(60.0, 11.5)),
                    trajectoryPoint("19L", 0.minutes, LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = { emptyList() },
        )

        val callsignTimings = state.timingsByCallsign[arrival.callsign]
        assertNotNull(callsignTimings)

        val f1 = callsignTimings["F1"]
        assertNotNull(f1)
        assertEquals(Instant.parse("2026-03-07T12:10:00Z"), f1.eto)
        assertEquals(Instant.parse("2026-03-07T12:15:00Z"), f1.sto)
        assertFalse(f1.isAbeamTime)

        val f2 = callsignTimings["F2"]
        assertNotNull(f2)
        assertEquals(Instant.parse("2026-03-07T12:20:00Z"), f2.eto)
        assertEquals(Instant.parse("2026-03-07T12:25:00Z"), f2.sto)
        assertFalse(f2.isAbeamTime)
    }

    @Test
    fun `buildState should use abeam time for bypassed feeder fix after direct`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(
            estimatedTime = referenceEto,
            scheduledTime = referenceSto,
            assignedDirect = "NEXT",
        )

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = null, remainingTime = 20.minutes, latLng = LatLng(60.0, 10.0)),
                    trajectoryPoint(fixId = "NEXT", remainingTime = 10.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "19L", remainingTime = 0.minutes, latLng = LatLng(60.0, 13.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("OLD", LatLng(60.2, 9.5), isPassed = true),
                    routePoint("F1", LatLng(61.0, 11.0), isPassed = true),
                    routePoint("NEXT", LatLng(60.0, 12.0), isPassed = false),
                )
            },
        )

        val timing = state.timingsByCallsign[arrival.callsign]?.get("F1")
        assertNotNull(timing)
        assertEquals(Instant.parse("2026-03-07T12:15:00Z"), timing.eto)
        assertEquals(Instant.parse("2026-03-07T12:20:00Z"), timing.sto)
        assertTrue(timing.isAbeamTime)
    }

    @Test
    fun `buildState should ignore fixes that are not present in trajectory or extracted route`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1", "UNKNOWN")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint("F1", 12.minutes, LatLng(60.0, 11.0)),
                    trajectoryPoint("19L", 0.minutes, LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(60.0, 11.0), isPassed = false),
                    routePoint("NEXT", LatLng(60.0, 11.5), isPassed = false),
                )
            },
        )

        val timings = state.timingsByCallsign[arrival.callsign]
        assertNotNull(timings)
        assertTrue("F1" in timings)
        assertTrue("UNKNOWN" !in timings)
    }

    @Test
    fun `buildState should not create abeam timing when no direct is assigned`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto)

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = null, remainingTime = 20.minutes, latLng = LatLng(60.0, 10.0)),
                    trajectoryPoint(fixId = "NEXT", remainingTime = 10.minutes, latLng = LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 11.0), isPassed = true),
                    routePoint("NEXT", LatLng(60.0, 12.0), isPassed = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `buildState should not create abeam timing when trajectory cannot produce a projection`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = "NEXT", remainingTime = 10.minutes, latLng = LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 11.0), isPassed = true),
                    routePoint("NEXT", LatLng(60.0, 12.0), isPassed = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `buildState should remove abeam timing after aircraft has passed the feeder fix abeam point`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = null, remainingTime = 20.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "NEXT", remainingTime = 10.minutes, latLng = LatLng(60.0, 13.0)),
                    trajectoryPoint(fixId = "19L", remainingTime = 0.minutes, latLng = LatLng(60.0, 14.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 11.0), isPassed = true),
                    routePoint("NEXT", LatLng(60.0, 13.0), isPassed = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `dynamic strategy should use first matching fix occurrence`() {
        val strategy = DynamicFromTrajectoryFeederFixTimingStrategy()
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto)

        val result = strategy.computeTimingsForArrival(
            arrival = arrival,
            trajectory = listOf(
                trajectoryPoint("F1", 18.minutes, LatLng(60.0, 11.0)),
                trajectoryPoint("F1", 8.minutes, LatLng(60.0, 11.5)),
            ),
            extractedRoute = emptyList(),
            targetFixes = setOf("F1"),
        )

        val timing = result["F1"]
        assertNotNull(timing)
        assertEquals(Instant.parse("2026-03-07T12:12:00Z"), timing.eto)
        assertEquals(Instant.parse("2026-03-07T12:17:00Z"), timing.sto)
        assertFalse(timing.isAbeamTime)
    }

    private fun airportWithFixes(vararg fixes: String): Airport = Airport(
        icao = "TEST",
        location = LatLng(60.0, 11.0),
        runways = emptyMap(),
        independentRunwaySystems = emptyList(),
        sequencingHorizon = 30.minutes,
        lockedHorizon = 10.minutes,
        feederFixes = fixes.toList(),
    )

    private fun sampleArrival(
        estimatedTime: Instant,
        scheduledTime: Instant,
        assignedDirect: String? = null,
    ): RunwayArrivalEvent = RunwayArrivalEvent(
        scheduledTime = scheduledTime,
        estimatedTime = estimatedTime,
        lastTimestamp = estimatedTime,
        runway = "19L",
        callsign = "SAS123",
        icaoType = "B738",
        wakeCategory = 'M',
        airportIcao = "TEST",
        trackingController = null,
        assignedStar = null,
        flightLevel = 120,
        pressureAltitude = 10000,
        groundSpeed = 250,
        remainingDistance = 80f,
        withinActiveAdvisoryHorizon = false,
        sequenceStatus = SequenceStatus.OK,
        landingIas = 140,
        assignedDirect = assignedDirect,
        scratchPad = null,
        assignedDirectIsIAF = false,
        assignedDirectIsIF = false,
    )

    private fun trajectoryPoint(
        fixId: String?,
        remainingTime: kotlin.time.Duration,
        latLng: LatLng,
    ): TrajectoryPoint = TrajectoryPoint(
        fixId = fixId,
        latLng = latLng,
        altitude = 10000,
        remainingDistance = 100f,
        remainingTime = remainingTime,
        groundSpeed = 250,
        tas = 260,
        ias = 240,
        windVector = WindVector(0, 0),
        heading = 180,
    )

    private fun routePoint(id: String, latLng: LatLng, isPassed: Boolean): ExtractedRoutePoint = ExtractedRoutePoint(
        id = id,
        latLng = latLng,
        isPassed = isPassed,
    )
}
