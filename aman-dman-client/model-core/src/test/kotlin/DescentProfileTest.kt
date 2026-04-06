import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.planning.DescentTrajectoryService
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.aircraft.SpeedConversionUtils
import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.NavigationUtils.dmsToDecimal
import no.vaccsca.amandman.model.navigation.NavigationUtils.interpolatePositionAlongPath
import no.vaccsca.amandman.model.navigation.Waypoint
import no.vaccsca.amandman.model.navigation.bearingTo
import no.vaccsca.amandman.model.navigation.distanceTo
import no.vaccsca.amandman.model.planning.TrajectoryPoint
import no.vaccsca.amandman.model.weather.SpatialWeatherField
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.weather.WeatherLayer
import no.vaccsca.amandman.model.weather.WindVector
import org.junit.jupiter.api.Test
import kotlin.collections.listOf
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes


class DescentProfileTest {

    val arrivalFixExpectations01L = listOf(
        arrivalFixExpectation("LUNIP", typicalSpeedIas = 250),
        arrivalFixExpectation("GM416", typicalAltitude = 11000, typicalSpeedIas = 220),
        arrivalFixExpectation("INSUV", typicalAltitude = 5000, typicalSpeedIas = 200),
        arrivalFixExpectation("NOSLA", typicalAltitude = 4000, typicalSpeedIas = 180),
        arrivalFixExpectation("XEMEN", typicalAltitude = 3500, typicalSpeedIas = 170),
        arrivalFixExpectation("ENGM", typicalAltitude = 700),
    )

    val runwayThreshold01L = RunwayThreshold(
        id = "01L",
        latLng = LatLng(60.18501045995491,11.073783755507158),
        elevation = 681f,
        trueHeading = 14f,
        arrivalFixExpectations = arrivalFixExpectations01L,
    )

    val testAirport = Airport(
        icao = "ENGM",
        location = LatLng(0.0, 0.0),
        runways = mapOf("01L" to runwayThreshold01L),
        independentRunwaySystems = listOf(setOf("01L")),
        sequencingHorizon = 30.minutes,
        lockedHorizon = 10.minutes
    )

    data class TestFlight(
        val assignedRunway: String,
        val assignedStar: String,
        val currentPosition: AircraftPosition,
        val remainingRoute: List<Waypoint>,
    )

    val testFlight = TestFlight(
        assignedRunway = "01L",
        assignedStar = "LUNIP4L",
        currentPosition = AircraftPosition(
            dmsToDecimal("""58°50'25.3"N  011°20'7.2"E"""),
            22000,
            250,
            180,
            trackDeg = 0
        ),
        remainingRoute = listOf(
            Waypoint("LUNIP", dmsToDecimal("""59°10'60.0"N  011°18'55.0"E""")),
            Waypoint("DEVKU", dmsToDecimal("""59°27'7.9"N  011°15'34.4"E""")),
            Waypoint("GM416", dmsToDecimal("""59°37'49.7"N  011°13'1.2"E""")),
            Waypoint("GM417", dmsToDecimal("""59°39'55.7"N  011°24'37.9"E""")),
            Waypoint("GM415", dmsToDecimal("""59°43'57.3"N  011°34'9.0"E""")),
            Waypoint("GM414", dmsToDecimal("""59°49'18.7"N  011°40'29.1"E""")),
            Waypoint("INSUV", dmsToDecimal("""59°55'32.2"N  011°6'50.6"E""")),
            Waypoint("NOSLA", dmsToDecimal("""59°59'1.2"N  010°59'51.2"E""")),
            Waypoint("XEMEN", dmsToDecimal("""60°2'10.4"N  011°1'39.4"E""")),
            Waypoint("ONE", dmsToDecimal("""60°10'40.6"N  011°6'41.0"E""")),
        )
    )

    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `Arrival route length matches AIRAC spec`() {
        val lunip4lRoute = listOf(
            Waypoint("LUNIP", dmsToDecimal("""59°10'60.0"N  011°18'55.0"E""")),
            Waypoint("DEVKU", dmsToDecimal("""59°27'7.9"N  011°15'34.4"E""")),
            Waypoint("GM416", dmsToDecimal("""59°37'49.7"N  011°13'1.2"E""")),
            Waypoint("GM417", dmsToDecimal("""59°39'55.7"N  011°24'37.9"E""")),
            Waypoint("GM415", dmsToDecimal("""59°43'57.3"N  011°34'9.0"E""")),
            Waypoint("GM414", dmsToDecimal("""59°49'18.7"N  011°40'29.1"E""")),
            Waypoint("INSUV", dmsToDecimal("""59°55'32.2"N  011°6'50.6"E""")),
        )

        assertEquals(64, lunip4lRoute.getRouteDistance().roundToInt())
    }

    @Test
    fun `When aircraft is heading away from the first waypoint, the turn radius will be taken into account`() {
        // TODO: not implemented yet
    }

    @Test
    fun `Removing a fix that causes a shorter flight path should reduce time`() {
        val descentSegmentsOriginalRoute = calculateTestDescent(testFlight)
        val remainingTimeOriginalRoute = descentSegmentsOriginalRoute.first().remainingTime

        val testAircraftB = testFlight.copy(
            remainingRoute = testFlight.remainingRoute.filter { it.id != "GM415" && it.id != "GM414" }
        )
        val descentSegmentsModifiedRoute = calculateTestDescent(testAircraftB)

        val remainingTimeModifiedRoute = descentSegmentsModifiedRoute.first().remainingTime

        assertTrue { remainingTimeOriginalRoute > remainingTimeModifiedRoute }
    }

   /* @Test
    fun `Descent path adheres to altitude restrictions`() {
        val descentSegments = calculateTestDescent(testFlight)
        val altitudeViolations = descentSegments.filter { descentSegment ->
            val passingWp = testFlight.remainingRoute.find { wp -> wp.latLng == descentSegment.latLng }
            val altitudeConstraint = testFlight.assignedStar.fixes.find { wp -> wp.id == passingWp?.id }?.typicalAltitude

            if (altitudeConstraint == null) {
                return@filter false
            }

            val wrongHeight = abs(descentSegment.altitude - altitudeConstraint) > 500

            if (wrongHeight) {
                println("Violation at ${passingWp!!.id} - ${descentSegment.altitude} ft. Constraint = ${altitudeConstraint}")
                return@filter true
            }
            return@filter false
        }

        assertEquals(emptyList(), altitudeViolations)
    }*/

    @Test
    fun `Descent segments should not contain duplicates`() {
        val descentSegments = calculateTestDescent(testFlight)
        val nDistinctPoints = descentSegments.map { it.latLng.lat to it.latLng.lon }.distinct().size
        val latLngDuplicates = nDistinctPoints - descentSegments.size

        assertEquals(0, latLngDuplicates)
    }

    @Test
    fun `Descent segments should include all waypoints`() {
        val descentSegments = calculateTestDescent(testFlight)
        val waypointsNotInDescentSegments = testFlight.remainingRoute
            .filter { waypoint -> descentSegments.none { it.latLng == waypoint.latLng } }
            .map { it.id }

        assertEquals(emptyList(), waypointsNotInDescentSegments)
    }

    @Test
    fun `Descent path should start at current aircraft position and end at runway`() {
        val descentSegments = calculateTestDescent(testFlight)
        val firstPoint = descentSegments.first()
        val lastPoint = descentSegments.last()

        assertEquals(testFlight.currentPosition.latLng, firstPoint.latLng)
        //assertEquals(testFlight.assignedRunway.latLng, lastPoint.latLng)
        //assertEquals(testFlight.assignedRunway.id, lastPoint.fixId)
    }

    @Test
    fun `Calculates bearing correctly`() {
        LatLng(60.0, 0.0).bearingTo(LatLng(60.0, 1.0)).let {
            assertEquals(90, it)
        }

        LatLng(60.0, 0.0).bearingTo(LatLng(61.0, 0.0)).let {
            assertEquals(0, it)
        }

        LatLng(60.0, 0.0).bearingTo(LatLng(60.0, -1.0)).let {
            assertEquals(270, it)
        }

        LatLng(60.0, 0.0).bearingTo(LatLng(59.0, 0.0)).let {
            assertEquals(180, it)
        }
    }

    @Test
    fun `Interpolate distance along path`() {
        val origin = LatLng(60.0, 11.0)
        val newPoint = origin.interpolatePositionAlongPath(LatLng(60.0, 12.0), 0.5)
        val tolerance = 0.0001

        assertEquals(60.0, newPoint.lat, tolerance)
        assertTrue(newPoint.lon > 11.0)
        assertTrue(newPoint.lon < 12.0)
        assertEquals(0.5, origin.distanceTo(newPoint), tolerance)
    }

    @Test
    fun `Wind direction should affect ground speed`() {
        val windVectorFromNorth = WindVector(speedKts = 20, directionDeg = 360)
        val aircraftTas = 220

        val gsWithHeadwind = SpeedConversionUtils.tasToGS(
            tas = aircraftTas,
            windVector = windVectorFromNorth,
            track = 360
        )

        val gsWithTailwind = SpeedConversionUtils.tasToGS(
            tas = aircraftTas,
            windVector = windVectorFromNorth,
            track = 180
        )

        val gsWithCrosswind = SpeedConversionUtils.tasToGS(
            tas = aircraftTas,
            windVector = windVectorFromNorth,
            track = 90
        )


        assertEquals(200, gsWithHeadwind)
        assertEquals(240, gsWithTailwind)
        assertEquals(221, gsWithCrosswind)
    }

    @Test
    fun `Descent sampling uses aircraft position when weather varies laterally`() {
        val lateralGradientField = SpatialWeatherField(
            listOf(
                VerticalWeatherProfile(
                    NtpClock.now(),
                    testFlight.currentPosition.latLng,
                    listOf(
                        WeatherLayer(0, 0, windVector = WindVector(180, 15)),
                        WeatherLayer(10000, -10, windVector = WindVector(180, 15)),
                        WeatherLayer(20000, -20, windVector = WindVector(180, 15)),
                        WeatherLayer(30000, -30, windVector = WindVector(180, 15)),
                    )
                ),
                VerticalWeatherProfile(
                    NtpClock.now(),
                    runwayThreshold01L.latLng,
                    listOf(
                        WeatherLayer(0, 0, windVector = WindVector(0, 15)),
                        WeatherLayer(10000, -10, windVector = WindVector(0, 15)),
                        WeatherLayer(20000, -20, windVector = WindVector(0, 15)),
                        WeatherLayer(30000, -30, windVector = WindVector(0, 15)),
                    )
                )
            )
        )

        val descentTrajectoryResult = DescentTrajectoryService.calculateDescentTrajectory(
            currentPosition = testFlight.currentPosition,
            remainingWaypoints = testFlight.remainingRoute,
            assignedRunway = testFlight.assignedRunway,
            assignedStar = testFlight.assignedStar,
            spatialWeatherField = lateralGradientField,
            aircraftPerformance = b738performance,
            flightPlanTas = 450,
            airport = testAirport
        )

        val sampledWindVectors = descentTrajectoryResult!!.trajectoryPoints
            .dropLast(1)
            .map { it.windVector }
            .distinct()

        assertTrue(sampledWindVectors.size > 1)
    }

    private fun calculateTestDescent(testFlight: TestFlight): List<TrajectoryPoint> {
        val weatherData = listOf(
            WeatherLayer(0, 0, windVector = WindVector(180, 0)),
            WeatherLayer(10000, -10, windVector = WindVector(180, 10)),
            WeatherLayer(20000, -20, windVector = WindVector(180, 20)),
            WeatherLayer(30000, -30, windVector = WindVector(180, 30)),
        )

        val weatherProfile = VerticalWeatherProfile(
            NtpClock.now(),
            testFlight.currentPosition.latLng,
            weatherData.toMutableList()
        )
        val weatherField = SpatialWeatherField(listOf(weatherProfile))

        val descentTrajectoryResult = DescentTrajectoryService.calculateDescentTrajectory(
            currentPosition = testFlight.currentPosition,
            remainingWaypoints = testFlight.remainingRoute,
            assignedRunway = testFlight.assignedRunway,
            assignedStar = testFlight.assignedStar,
            spatialWeatherField = weatherField,
            aircraftPerformance = b738performance,
            flightPlanTas = 450,
            airport = testAirport
        )

        println("Descent segments for route:")
        descentTrajectoryResult!!.trajectoryPoints.forEach { println(it) }

        return descentTrajectoryResult.trajectoryPoints
    }

    private fun List<Waypoint>.getRouteDistance() =
        this.zipWithNext().sumOf { (from, to) ->
            from.latLng.distanceTo(to.latLng)
        }

    private fun arrivalFixExpectation(
        fixName: String,
        typicalAltitude: Int? = null,
        typicalSpeedIas: Int? = null,
    ) = ArrivalFixExpectation(
        fixName = fixName,
        role = null,
        typicalAltitude = typicalAltitude,
        typicalSpeedIas = typicalSpeedIas,
    )
}
