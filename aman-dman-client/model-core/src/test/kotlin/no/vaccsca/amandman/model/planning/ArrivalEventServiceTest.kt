package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.atc.AtcClientArrivalData
import no.vaccsca.amandman.model.atc.ExtractedRoutePoint
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrivalEventServiceTest {

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
        )

        assertTrue(state.isActive)
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
        )

        assertFalse(state.isActive)
    }

    @Test
    fun `assignedDirectRoutingState should mark active when direct remains later in route`() {
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
        )

        assertTrue(state.isActive)
    }

    @Test
    fun `assignedDirectRoutingState should mark active when direct is first remaining waypoint`() {
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
        )

        assertTrue(state.isActive)
    }

    @Test
    fun `assignedDirectRoutingState should mark active when direct is missing from extracted route but still assigned in remaining waypoints`() {
        val state = assignedDirectRoutingState(
            arrival = arrival(
                assignedDirect = "XIVTA",
                extractedRoute = listOf(
                    routePoint("TITLA", isPassed = true),
                    routePoint("OSPAD", isPassed = false),
                ),
                remainingWaypoints = listOf(waypoint("XIVTA")),
            ),
        )

        assertTrue(state.isActive)
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
        isActive = isPassed,
    )

    private fun waypoint(id: String) = Waypoint(
        id = id,
        latLng = LatLng(60.0, 11.0),
    )
}
