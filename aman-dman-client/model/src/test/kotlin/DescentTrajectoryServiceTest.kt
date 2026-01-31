import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.domain.service.planning.DescentTrajectoryService
import no.vaccsca.amandman.model.domain.valueobjects.*
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientArrivalData
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DescentTrajectoryServiceTest {

    val testArrival1 =
        AtcClientArrivalData(
            callsign="SAS4411",
            icaoType="A20N",
            assignedRunway="19L",
            assignedStar="INREX4M",
            assignedDirect=null,
            trackingController=null,
            scratchPad=null,
            currentPosition = AircraftPosition(
                latLng = LatLng(62.09387969970703, 11.931380271911621),
                flightLevel=38000,
                altitudeFt=38195,
                groundspeedKts=449,
                trackDeg=0
            ),
            remainingWaypoints = listOf(
                /*Waypoint(id="ENTC", latLng=LatLng(69.681389, 18.917778000000002)),
                Waypoint(id="TC707", latLng=LatLng(69.57579388888888, 18.847716944444443)),
                Waypoint(id="IPTUK", latLng=LatLng(69.33083277777777, 18.486110833333335)),
                Waypoint(id="KAMPE", latLng=LatLng(68.89629694444446, 17.955566944444445)),
                Waypoint(id="AMIMO", latLng=LatLng(67.875, 16.441388888888888)),
                Waypoint(id="GUBAV", latLng=LatLng(65.773333, 13.944721999999999)),
                Waypoint(id="TUXOT", latLng=LatLng(63.977582999999996, 12.321442000000001)),
                Waypoint(id="SOMUB", latLng=LatLng(63.349166999999994, 11.805000000000001)),*/
                Waypoint(id="ABUNO", latLng=LatLng(62.0, 11.940256)),
                Waypoint(id="INREX", latLng=LatLng(61.01388899999999, 12.216667)),
                Waypoint(id="IXUMA", latLng=LatLng(60.819027777777784, 11.989721944444444)),
                Waypoint(id="GM418", latLng=LatLng(60.65119388888889, 11.7955)),
                Waypoint(id="TITLA", latLng=LatLng(60.42136083333333, 11.402721944444444)),
                Waypoint(id="OSPAD", latLng=LatLng(60.40099194444444, 11.238730833333333)),
                Waypoint(id="XIVTA", latLng=LatLng(60.34008277777778, 11.203132777777776)),
                Waypoint(id="GME40", latLng=LatLng(60.26238277777778, 11.157691944444444)),
                Waypoint(id="19L", latLng=rwy19L.latLng),
            ),
            arrivalAirportIcao="ENGM",
            flightPlanTas=450,
            recvTimestamp = NtpClock.now(),
        )

    val testArrival2 =
        AtcClientArrivalData(
            callsign="EZY9",
            icaoType="A320",
            assignedRunway="19L",
            assignedStar="ESEBA4M",
            assignedDirect=null,
            trackingController=null,
            scratchPad=null,
            currentPosition = AircraftPosition(
                latLng = LatLng(59.77381134033203, 14.915430068969727),
                flightLevel=31222,
                altitudeFt=31476,
                groundspeedKts=407,
                trackDeg=0
            ),
            remainingWaypoints = listOf(
                Waypoint(id="ESSA", latLng=LatLng(59.65194399999999, 17.918611)),
                Waypoint(id="ARS", latLng=LatLng(59.586222, 16.650333)),
                Waypoint(id="BEDLA", latLng=LatLng(59.628944000000004, 16.225028)),
                Waypoint(id="IBGAX", latLng=LatLng(59.72222200000001, 15.395833000000001)),
                Waypoint(id="EBURI", latLng=LatLng(59.8, 14.660556000000001)),
                Waypoint(id="TEKVA", latLng=LatLng(59.984722, 12.719444000000001)),
                Waypoint(id="ESEBA", latLng=LatLng(60.012778000000004, 12.392222)),
                Waypoint(id="KEGET", latLng=LatLng(60.08972194444445, 12.254888888888889)),
                Waypoint(id="GM422", latLng=LatLng(60.26261083333333, 11.954832777777778)),
                Waypoint(id="GM423", latLng=LatLng(60.36525, 12.033749722222222)),
                Waypoint(id="TITLA", latLng=LatLng(60.42136083333333, 11.402721944444444)),
                Waypoint(id="OSPAD", latLng=LatLng(60.40099194444444, 11.238730833333333)),
                Waypoint(id="XIVTA", latLng=LatLng(60.34008277777778, 11.203132777777776)),
                Waypoint(id="GME40", latLng=LatLng(60.26238277777778, 11.157691944444444)),
                Waypoint(id="19L", latLng=rwy19L.latLng),
            ),
            arrivalAirportIcao="ENGM",
            flightPlanTas=450,
            recvTimestamp = NtpClock.now(),
        )


    val arrivalWithDirectRouting = AtcClientArrivalData(
        callsign="SRR22X",
        icaoType="B77L",
        assignedRunway="19L",
        assignedStar="ADOPI3M",
        assignedDirect="BAVAD",
        trackingController="GWR",
        scratchPad=null,
        currentPosition = AircraftPosition(
            latLng = LatLng(60.36001968383789, 9.892060279846191),
            flightLevel=15670,
            altitudeFt = 15851,
            groundspeedKts = 402,
            trackDeg = 0
        ),
        remainingWaypoints = listOf(
            Waypoint(id="BAVAD", latLng=LatLng(60.46611083333334, 11.08444388888889)),
            Waypoint(id="OSPAD", latLng=LatLng(60.40099194444444, 11.238730833333333)),
            Waypoint(id="XIVTA", latLng=LatLng(60.34008277777778, 11.203132777777776)),
            Waypoint(id="GME40", latLng=LatLng(60.26238277777778, 11.157691944444444)),
            Waypoint(id="19L", latLng=rwy19L.latLng),
        ),
        arrivalAirportIcao="ENGM",
        flightPlanTas = 450,
        recvTimestamp = NtpClock.now(),
    )

    val testAirport = Airport(
        icao="ENGM",
        location=LatLng(60.20116653568569,11.12244616482607),
        runways=mapOf(
            "19L" to rwy19L,
        ),
    )

    @Test
    fun `When route does not end with destination airport, append runway threshold to descent trajectory`() {
        val arrivalWithoutAirportInWaypointsList = testArrival1.copy(
            remainingWaypoints = testArrival1.remainingWaypoints.filter { it.id != testArrival1.arrivalAirportIcao }
        )

        val descentTrajectory = calculateDescentTrajectoryFor(arrivalWithoutAirportInWaypointsList)

        // Ensure that the descent trajectory does not contain the airport ICAO as a fixId
        assertTrue { arrivalWithoutAirportInWaypointsList.remainingWaypoints.none { it.id == arrivalWithoutAirportInWaypointsList.arrivalAirportIcao } }
        assertTrue { descentTrajectory.none { it.fixId == arrivalWithoutAirportInWaypointsList.arrivalAirportIcao } }

        // Ensure that the last point in the descent trajectory is the runway threshold
        assertNotEquals(illegal = testArrival1.arrivalAirportIcao, actual = arrivalWithoutAirportInWaypointsList.remainingWaypoints.last().id)
        assertEquals(expected = rwy19L.latLng.lat, actual = descentTrajectory.last().latLng.lat)
        assertEquals(expected = rwy19L.latLng.lon, actual = descentTrajectory.last().latLng.lon)
        assertEquals(expected = rwy19L.elevation.toInt(), actual = descentTrajectory.last().altitude)
    }

    @Test
    fun `When direct routing, use preferred speed until next typical speed`() {
        val descentTrajectory = calculateDescentTrajectoryFor(arrivalWithDirectRouting)

        val directRoutingIndex = descentTrajectory.indexOfFirst { it.fixId == arrivalWithDirectRouting.assignedDirect }
        val expectedSpeedAtDirectRouting = star19LAdopi3M.fixes.find { it.id == arrivalWithDirectRouting.assignedDirect }!!.typicalSpeedIas!!

        descentTrajectory.subList(0, directRoutingIndex).forEach {
            assertTrue { it.ias > expectedSpeedAtDirectRouting }
        }
    }

    @Test
    fun `Descent trajectory should contain all fixes in route, including runway`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival1)

        val expectedFixes = testArrival1.remainingWaypoints.map { it.id } + listOf(rwy19L.id)

        val fixesOnDescentTrajectory = descentTrajectory.mapNotNull { it.fixId }

        assertEquals(
            expected = expectedFixes,
            actual = fixesOnDescentTrajectory,
            message = "Descent trajectory should contain all fixes that has not been passed"
        )
    }

    @Test
    fun `Estimated TAS should not jump by more than 10 knots`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival2)

        descentTrajectory.forEach {
            println(
                "FixId: ${it.fixId}, ias: ${it.ias}, altitude: ${it.altitude}, remainingDistance: ${it.remainingDistance}, remainingTime: ${it.remainingTime}, groundSpeed: ${it.groundSpeed}, tas: ${it.tas}"
            )
        }

        val tasList = descentTrajectory.map { it.tas }
        val isJumping = tasList.zipWithNext().any { (prev, next) -> abs(prev - next) > 10 }
        assertEquals(false, isJumping, "TAS should not jump by more than 10 knots")
    }

    @Test
    fun `Estimated IAS should not jump by more than 5 knots`() {
        val descentTrajectoryNew = calculateDescentTrajectoryFor(testArrival1)

        val iasList = descentTrajectoryNew.map { it.ias }
        val isJumping = iasList.zipWithNext().any { (prev, next) -> abs(prev - next) > 5 }
        assertEquals(false, isJumping, "IAS should not jump by more than 5 knots")
    }

    @Test
    fun `Estimated IAS should never exceed typical speed on STAR point`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival1)
        val fixesInTrajectory = descentTrajectory.filter { it.fixId != null }

        assertEquals(fixesInTrajectory.size, 10)

        val isExceeding = fixesInTrajectory
            .any { point ->
                val starFix = star19LInrex4M.fixes.find { it.id == point.fixId }
                if (starFix?.typicalSpeedIas == null) return@any false
                point.ias > starFix.typicalSpeedIas
            }

        assertEquals(false, isExceeding, "IAS should not exceed typical speed on STAR point")
    }

    @Test
    fun `Trajectory points that have a fix id should have the same coordinates as the corresponding fix on the aircraft's route`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival1)

        val allAreMatching = descentTrajectory
            .filter { it.fixId != null }
            .all { point ->
                if (point.fixId == testArrival1.assignedRunway) return@all true // Skip runway, as it is not part of the route waypoints
                val routeFix = testArrival1.remainingWaypoints.find { it.id == point.fixId || it.id == "19L" }!!
                point.latLng.lat == routeFix.latLng.lat && point.latLng.lon == routeFix.latLng.lon
            }

        assertEquals(true, allAreMatching, "Coordinates should match the fix coordinates")
    }

    @Test
    fun `Estimated IAS should not be more than 250 below FL100`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival1)
        val descentTrajectory2 = calculateDescentTrajectoryFor(arrivalWithDirectRouting)

        val isExceeding = descentTrajectory.any { it.altitude < 10_000 && it.ias > 250 }
        assertEquals(false, isExceeding, "IAS should not exceed 250 below FL100")


        val isExceeding2 = descentTrajectory2.any { it.altitude < 10_000 && it.ias > 250 }
        assertEquals(false, isExceeding2, "IAS should not exceed 250 below FL100")
    }

    @Test
    fun `Altitude should never be increasing`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival1)

        val altitudeList = descentTrajectory.map { it.altitude }
        val isIncreasing = altitudeList.zipWithNext().any { (prev, next) -> prev < next }
        assertEquals(false, isIncreasing, "Altitude should never be increasing")
    }

    @Test
    fun `Removing waypoint along a straight line should not affect ETA`() {
        val originalTrajectory = calculateDescentTrajectoryFor(testArrival2)

        val modifiedRoute = testArrival2.copy(
            remainingWaypoints = testArrival2.remainingWaypoints.filter { it.id != "TEKVA" }
        )

        val newTrajectory = calculateDescentTrajectoryFor(modifiedRoute)

        assertEquals(
            expected = originalTrajectory.first().remainingTime.inWholeSeconds.toDouble(),
            actual = newTrajectory.first().remainingTime.inWholeSeconds.toDouble(),
            absoluteTolerance = 5.0,
            "ETA should not change when removing a waypoint along a straight line"
        )
    }

    @Test
    fun `Removing waypoint along a curve should affect ETA`() {
        val originalTrajectory = calculateDescentTrajectoryFor(testArrival2)

        val modifiedRoute = testArrival2.copy(
            remainingWaypoints = testArrival2.remainingWaypoints.filter { it.id != "GM423" }
        )

        val newTrajectory = calculateDescentTrajectoryFor(modifiedRoute)

        val timeGained = originalTrajectory.first().remainingTime - newTrajectory.first().remainingTime

        assertTrue { timeGained > 30.seconds && timeGained < 2.minutes }

        assertNotEquals(
            illegal = originalTrajectory.first().remainingTime.inWholeSeconds.toDouble(),
            actual = newTrajectory.first().remainingTime.inWholeSeconds.toDouble(),
            absoluteTolerance = 45.0,
            "ETA should change when removing a waypoint along a curve"
        )
    }

    @Test
    fun `Descent trajectory should have same length as the remaining route`() {
        val descentTrajectory = calculateDescentTrajectoryFor(testArrival1)

        val descentTrajectoryLength =
            descentTrajectory
                .zipWithNext()
                .sumOf { (previous, next) -> previous.latLng.distanceTo(next.latLng) }

        val remainingRouteLength = testArrival1.remainingWaypoints.zipWithNext()
            .sumOf { (current, next) ->
                current.latLng.distanceTo(next.latLng)
            }

        val distanceToFirstFix =
            testArrival1.currentPosition.latLng
                .distanceTo(testArrival1.remainingWaypoints.first().latLng)

        assertEquals(
            expected = descentTrajectoryLength,
            actual = remainingRouteLength + distanceToFirstFix,
            absoluteTolerance = 0.001,
            "Descent trajectory should have same length as the remaining route"
        )
    }

    private fun calculateDescentTrajectoryFor(arrival: AtcClientArrivalData) =
        DescentTrajectoryService.calculateDescentTrajectory(
            currentPosition = arrival.currentPosition,
            assignedRunway = arrival.assignedRunway!!,
            remainingWaypoints = arrival.remainingWaypoints,
            assignedStar = arrival.assignedStar,
            verticalWeatherProfile = null,
            flightPlanTas = 450,
            aircraftPerformance = b738performance,
            airport = testAirport
        )!!.trajectoryPoints

}