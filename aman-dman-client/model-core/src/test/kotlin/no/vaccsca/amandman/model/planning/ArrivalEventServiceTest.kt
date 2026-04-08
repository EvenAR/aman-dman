package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.atc.AtcClientArrivalData
import no.vaccsca.amandman.model.atc.ExtractedRoutePoint
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ArrivalEventServiceTest {

    private val airport = Airport(
        icao = "ENGM",
        location = LatLng(60.2, 11.1),
        runways = emptyMap(),
        independentRunwaySystems = emptyList(),
        sequencingHorizon = 30.minutes,
        lockedHorizon = 10.minutes,
        feederFixes = listOf("TITLA", "INREX"),
    )

    @Test
    fun `assignedDirectRoutingState should mark active if direct remains in route`() {
        val state = assignedDirectRoutingState(
            arrival = arrival(
                assignedDirect = "OSPAD",
                extractedRoute = listOf(
                    routePoint("TITLA", isPassed = true),
                    routePoint("OSPAD", isPassed = false),
                ),
                remainingWaypoints = listOf(waypoint("OSPAD")),
            ),
            airport = airport,
        )

        assertTrue(state.isActive)
        assertTrue(state.isAfterFeederFix)
    }

    @Test
    fun `assignedDirectRoutingState should mark false when direct has been passed`() {
        val state = assignedDirectRoutingState(
            arrival = arrival(
                assignedDirect = "OSPAD",
                extractedRoute = listOf(
                    routePoint("TITLA", isPassed = true),
                    routePoint("OSPAD", isPassed = true),
                ),
                remainingWaypoints = listOf(waypoint("XIVTA")),
            ),
            airport = airport,
        )

        assertFalse(state.isActive)
        assertFalse(state.isAfterFeederFix)
    }

    @Test
    fun `assignedDirectRoutingState should mark active non IAF or IF direct after feeder fix in extracted route`() {
        val state = assignedDirectRoutingState(
            arrival = arrival(
                assignedDirect = "XIVTA",
                extractedRoute = listOf(
                    routePoint("INREX", isPassed = true),
                    routePoint("TITLA", isPassed = true),
                    routePoint("OSPAD", isPassed = false),
                    routePoint("XIVTA", isPassed = false),
                ),
                remainingWaypoints = listOf(waypoint("XIVTA"), waypoint("19L")),
            ),
            airport = airport,
        )

        assertTrue(state.isActive)
        assertTrue(state.isAfterFeederFix)
    }

    @Test
    fun `assignedDirectRoutingState should not mark direct before feeder fix as after feeder`() {
        val state = assignedDirectRoutingState(
            arrival = arrival(
                assignedDirect = "GM418",
                extractedRoute = listOf(
                    routePoint("GM418", isPassed = false),
                    routePoint("TITLA", isPassed = false),
                    routePoint("OSPAD", isPassed = false),
                ),
                remainingWaypoints = listOf(waypoint("GM418"), waypoint("TITLA")),
            ),
            airport = airport,
        )

        assertTrue(state.isActive)
        assertFalse(state.isAfterFeederFix)
    }

    @Test
    fun `assignedDirectRoutingState should not mark after feeder when direct is missing from extracted route`() {
        val state = assignedDirectRoutingState(
            arrival = arrival(
                assignedDirect = "XIVTA",
                extractedRoute = listOf(
                    routePoint("TITLA", isPassed = true),
                    routePoint("OSPAD", isPassed = false),
                ),
                remainingWaypoints = listOf(waypoint("XIVTA")),
            ),
            airport = airport,
        )

        assertTrue(state.isActive)
        assertFalse(state.isAfterFeederFix)
    }

    private fun arrival(
        assignedDirect: String?,
        extractedRoute: List<ExtractedRoutePoint>,
        remainingWaypoints: List<Waypoint>,
    ) = AtcClientArrivalData(
        callsign = "SAS123",
        icaoType = "B738",
        assignedStar = "INREX4M",
        assignedDirect = assignedDirect,
        trackingController = null,
        scratchPad = null,
        currentPosition = AircraftPosition(
            latLng = LatLng(60.0, 11.0),
            flightLevel = 120,
            altitudeFt = 10000,
            groundspeedKts = 250,
            trackDeg = 180,
        ),
        extractedRoute = extractedRoute,
        remainingWaypoints = remainingWaypoints,
        assignedRunway = "19L",
        arrivalAirportIcao = "ENGM",
        flightPlanTas = 450,
        recvTimestamp = Instant.parse("2026-04-08T18:00:00Z"),
    )

    private fun routePoint(id: String, isPassed: Boolean) = ExtractedRoutePoint(
        id = id,
        latLng = LatLng(60.0, 11.0),
        isPassed = isPassed,
    )

    private fun waypoint(id: String) = Waypoint(
        id = id,
        latLng = LatLng(60.0, 11.0),
    )
}
