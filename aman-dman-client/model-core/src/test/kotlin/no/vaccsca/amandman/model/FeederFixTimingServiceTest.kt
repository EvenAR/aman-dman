package no.vaccsca.amandman.model

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

    val strategy = DynamicFromTrajectoryFeederFixTimingStrategy(maxAbeamDistanceNm = 15.0)
    val service = FeederFixTimingService(strategy)

    @Test
    fun `buildState should derive feeder fix ETO and STO from trajectory points`() {
        val airport = airportWithFixes("F1", "F2")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto)

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint("F1", referenceEto - 20.minutes, LatLng(60.0, 11.0)),
                    trajectoryPoint("F2", referenceEto - 10.minutes, LatLng(60.0, 11.5)),
                    trajectoryPoint("19L", referenceEto, LatLng(60.0, 12.0)),
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
                    trajectoryPoint(fixId = null, time = referenceEto - 20.minutes, latLng = LatLng(60.0, 10.0)),
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "19L", time = referenceEto, latLng = LatLng(60.0, 13.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("OLD", LatLng(60.2, 9.5), isActive = false),
                    routePoint("F1", LatLng(60.08, 11.0), isActive = false),
                    routePoint("NEXT", LatLng(60.0, 12.0), isActive = true),
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
        val airport = airportWithFixes("F1", "UNKNOWN")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint("F1", referenceEto - 12.minutes, LatLng(60.0, 11.0)),
                    trajectoryPoint("19L", referenceEto, LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(60.0, 11.0), isActive = false),
                    routePoint("NEXT", LatLng(60.0, 11.5), isActive = false),
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
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto)

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = null, time = referenceEto - 20.minutes, latLng = LatLng(60.0, 10.0)),
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 11.0), isActive = true),
                    routePoint("NEXT", LatLng(60.0, 12.0), isActive = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `buildState should not create abeam timing when trajectory cannot produce a projection`() {
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(60.0, 12.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 11.0), isActive = true),
                    routePoint("NEXT", LatLng(60.0, 12.0), isActive = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `buildState should remove abeam timing after aircraft has passed the feeder fix abeam point`() {
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = null, time = referenceEto - 20.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(60.0, 13.0)),
                    trajectoryPoint(fixId = "19L", time = referenceEto, latLng = LatLng(60.0, 14.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 11.0), isActive = true),
                    routePoint("NEXT", LatLng(60.0, 13.0), isActive = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `buildState should not recreate abeam timing from a later route bend after current abeam point`() {
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint(fixId = null, time = referenceEto - 20.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "TURN", time = referenceEto - 15.minutes, latLng = LatLng(60.0, 13.0)),
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(61.0, 12.0)),
                    trajectoryPoint(fixId = "19L", time = referenceEto, latLng = LatLng(61.0, 13.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(61.0, 12.0), isActive = true),
                    routePoint("NEXT", LatLng(61.0, 12.0), isActive = false),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `dynamic strategy should use first matching fix occurrence`() {
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto)

        val result = strategy.computeTimingsForArrival(
            arrival = arrival,
            trajectory = listOf(
                trajectoryPoint("F1", referenceEto - 18.minutes, LatLng(60.0, 11.0)),
                trajectoryPoint("F1", referenceEto - 8.minutes, LatLng(60.0, 11.5)),
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

    @Test
    fun `buildState should not create abeam timing when feeder fix is beyond max abeam distance`() {
        val service = FeederFixTimingService(DynamicFromTrajectoryFeederFixTimingStrategy(maxAbeamDistanceNm = 5.0))
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    // Straight east track at lat 60.0; F1 is ~120 NM north — well beyond 5 NM limit
                    trajectoryPoint(fixId = null, time = referenceEto - 20.minutes, latLng = LatLng(60.0, 10.0)),
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "19L", time = referenceEto, latLng = LatLng(60.0, 14.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(62.0, 11.0), isActive = false),
                    routePoint("NEXT", LatLng(60.0, 12.0), isActive = true),
                )
            },
        )

        assertNull(state.timingsByCallsign[arrival.callsign]?.get("F1"))
    }

    @Test
    fun `buildState should create abeam timing when feeder fix is within max abeam distance`() {
        val service = FeederFixTimingService(DynamicFromTrajectoryFeederFixTimingStrategy(maxAbeamDistanceNm = 5.0))
        val airport = airportWithFixes("F1")
        val arrival = sampleArrival(estimatedTime = referenceEto, scheduledTime = referenceSto, assignedDirect = "NEXT")

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    // Straight east track at lat 60.0; F1 is ~3 NM north of the midpoint — within 5 NM limit
                    trajectoryPoint(fixId = null, time = referenceEto - 20.minutes, latLng = LatLng(60.0, 10.0)),
                    trajectoryPoint(fixId = "NEXT", time = referenceEto - 10.minutes, latLng = LatLng(60.0, 12.0)),
                    trajectoryPoint(fixId = "19L", time = referenceEto, latLng = LatLng(60.0, 14.0)),
                )
            },
            extractedRouteProvider = {
                listOf(
                    routePoint("F1", LatLng(60.05, 11.0), isActive = false),
                    routePoint("NEXT", LatLng(60.0, 12.0), isActive = true),
                )
            },
        )

        val timing = state.timingsByCallsign[arrival.callsign]?.get("F1")
        assertNotNull(timing)
        assertTrue(timing.isAbeamTime)
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
        time: Instant,
        latLng: LatLng,
    ): TrajectoryPoint = TrajectoryPoint(
        fixId = fixId,
        latLng = latLng,
        altitude = 10000,
        remainingDistance = 100f,
        time = time,
        groundSpeed = 250,
        tas = 260,
        ias = 240,
        windVector = WindVector(0, 0),
        heading = 180,
    )

    private fun routePoint(id: String, latLng: LatLng, isActive: Boolean): ExtractedRoutePoint = ExtractedRoutePoint(
        id = id,
        latLng = latLng,
        isActive = isActive,
    )
}
