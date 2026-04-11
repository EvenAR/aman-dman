package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.navigation.NavdataUtils.getInterpolatedSpeedExpectation
import no.vaccsca.amandman.model.navigation.NavigationUtils.interpolatePositionAlongPath
import no.vaccsca.amandman.model.navigation.NavigationUtils.isBehind
import no.vaccsca.amandman.model.aircraft.SpeedConversionUtils
import no.vaccsca.amandman.model.weather.WeatherUtils
import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.aircraft.AircraftPerformance
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import no.vaccsca.amandman.model.navigation.bearingTo
import no.vaccsca.amandman.model.navigation.distanceTo
import no.vaccsca.amandman.model.weather.SpatialWeatherField
import no.vaccsca.amandman.model.weather.WindVector
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

object DescentTrajectoryService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val CURRENT_ID = "CURRENT"
    private val DECELERATION_RATE = 0.5
    private val calmWindVector = WindVector(0, 0)

    /**
     * Calculates a descent trajectory from the current position to the runway using
     * runway plus arrival-name-scoped arrival fix expectations and aircraft performance data.
     *
     * @param currentPosition The current position of the aircraft.
     * @param assignedRunway The runway assigned to the aircraft.
     * @param remainingWaypoints The remaining waypoints in the flight plan leading to the runway
     * @param spatialWeatherField The spatial weather field, if available.
     * @param assignedStar The arrival name assigned to the aircraft, if any.
     * @param aircraftPerformance The performance characteristics of the aircraft.
     * @param flightPlanTas The true airspeed (TAS) from the flight plan
     *
     * @return A list of TrajectoryPoint objects representing the descent trajectory.
     */
    fun calculateDescentTrajectory(
        currentPosition: AircraftPosition,
        assignedRunway: String,
        remainingWaypoints: List<Waypoint>,
        spatialWeatherField: SpatialWeatherField?,
        assignedStar: String?,
        aircraftPerformance: AircraftPerformance,
        flightPlanTas: Int?,
        airport: Airport,
    ): DescentTrajectoryResult? {
        val runwayInfo = airport.runways[assignedRunway]
        if (runwayInfo == null) {
            logger.warn("No runway info for $assignedRunway at ${airport.icao}. Cannot compute descent trajectory.")
            return null
        }

        val routeFixIds = remainingWaypoints.map { it.id.uppercase() }.toSet()
        val routeArrivalFixExpectations = runwayInfo.arrivalFixExpectationsFor(assignedStar).filter {
            it.fixName in routeFixIds
        }
        val arrivalFixExpectationByName = routeArrivalFixExpectations.associateBy { it.fixName }
        val trajectoryPoints = mutableListOf<TrajectoryPoint>()

        // Starts at the airports and works backwards
        var probePosition = runwayInfo.latLng
        var probeAltitude = runwayInfo.elevation.roundToInt()
        var probingDistance = 0f
        var accumulatedTimeFromDestination = 0.seconds

        val remainingRoute =
            listOf(
                Waypoint(
                    id = CURRENT_ID,
                    latLng = currentPosition.latLng,
                )
            ) + remainingWaypoints.filter { it.id != airport.icao } + listOf(
                Waypoint(
                    id = assignedRunway,
                    latLng = runwayInfo.latLng,
                )
            )

        val isAtEndOfRoute = remainingRoute.size == 2 && remainingRoute.last().latLng.isBehind(currentPosition.latLng, currentPosition.trackDeg)
        if (isAtEndOfRoute) {
            return null
        }

        // Add the last point (the airport) to the profile
        trajectoryPoints +=
                TrajectoryPoint(
                    fixId = assignedRunway,
                    latLng = probePosition,
                    altitude = probeAltitude,
                    remainingDistance = probingDistance,
                    remainingTime = accumulatedTimeFromDestination,
                    groundSpeed = aircraftPerformance.landingVat, // TODO: convert to ground speed
                    tas = aircraftPerformance.landingVat,
                    windVector = calmWindVector, // TODO: use wind from METAR
                    ias = aircraftPerformance.landingVat,
                    heading = runwayInfo.trueHeading.roundToInt(),
                )

        // Start from the last waypoint (the runway threshold) and work backward
        for (i in remainingRoute.lastIndex downTo 1) {
            val laterPoint = remainingRoute[i]
            val earlierPoint = remainingRoute[i-1]
            val remainingRouteReversed = remainingRoute.subList(0, i).reversed()

            val nextAltitudeExpectation = routeArrivalFixExpectations.nextAltitudeExpectation(
                remainingRouteReversed
            )
                ?.let { arrivalFixExpectationByName[it.id]?.typicalAltitude }
                ?: currentPosition.altitudeFt

            val earlierSpeedExpectation =
                if (routeArrivalFixExpectations.isNotEmpty()) {
                    remainingWaypoints.getInterpolatedSpeedExpectation(
                        arrivalFixExpectations = routeArrivalFixExpectations,
                        atWaypoint = earlierPoint
                    )
                } else {
                    aircraftPerformance.getPreferredIas(
                        altitudeFt = nextAltitudeExpectation,
                        temperatureC = spatialWeatherField?.sampleWeather(earlierPoint.latLng, nextAltitudeExpectation)?.temperatureC,
                        flightPlanTas = flightPlanTas
                    )
                }

            val descentSteps = aircraftPerformance.computeDescentPathBackward(
                lowerAltitude = probeAltitude,
                higherAltitude = nextAltitudeExpectation,
                laterPoint = probePosition,
                earlierPoint = earlierPoint.latLng,
                spatialWeatherField = spatialWeatherField,
                earlierExpectedSpeed = earlierSpeedExpectation,
                laterExpectedSpeed = trajectoryPoints.last().ias,
                flightPlanTas = flightPlanTas
            )

            for (step in descentSteps) {
                val isLastStep = step == descentSteps.last()
                val stepLength = probePosition.distanceTo(step.position).toFloat()
                probingDistance += stepLength
                accumulatedTimeFromDestination += (stepLength / step.groundSpeed * 3600.0).roundToInt().seconds

                trajectoryPoints +=
                        TrajectoryPoint(
                            latLng = step.position,
                            altitude = step.altitudeFt,
                            remainingDistance = probingDistance,
                            remainingTime = accumulatedTimeFromDestination,
                            groundSpeed = step.groundSpeed,
                            tas = step.tas,
                            windVector = step.windVector,
                            heading = step.position.bearingTo(probePosition),
                            ias = step.ias,
                            fixId = if (isLastStep && earlierPoint.id != CURRENT_ID) earlierPoint.id else null,
                        )

                probeAltitude = step.altitudeFt
                probePosition = step.position
            }
        }

        return DescentTrajectoryResult(
            trajectoryPoints = trajectoryPoints.reversed(),
            runwayThreshold = runwayInfo,
        )
    }

    /**
     * Computes the descent path backward from a given altitude to a target altitude.
     *
     * @param lowerAltitude The altitude we are descending to in feet.
     * @param higherAltitude The altitude we are descending from in feet.
     * @param laterPoint The point we are descending towards as a LatLng.
     * @param earlierPoint The point we are descending from as a LatLng.
     * @param spatialWeatherField The spatial weather field, if available.
     * @param earlierExpectedSpeed The expected speed at the earlier point in IAS, if available
     * @param laterExpectedSpeed The expected speed at the later point in IAS, if available
     * @param flightPlanTas The true airspeed from the flight plan, if available.
     *
     * @return A list of DescentStep objects representing the descent path.
     */
    private fun AircraftPerformance.computeDescentPathBackward(
        lowerAltitude: Int,
        higherAltitude: Int,
        laterPoint: LatLng,
        earlierPoint: LatLng,
        spatialWeatherField: SpatialWeatherField?,
        earlierExpectedSpeed: Int?,
        laterExpectedSpeed: Int?,
        flightPlanTas: Int?
    ): List<DescentStep> {
        val descentPath = mutableListOf<DescentStep>()
        var probeAltitude = lowerAltitude
        var probePosition = laterPoint

        val deltaTime = 10.seconds
        val descentRateFpm = this.estimateDescentRate(probeAltitude)
        val verticalSpeed = descentRateFpm / 60.0 // ft/sec

        var remainingProbingDistance = laterPoint.distanceTo(earlierPoint)
        var currentExpectedSpeed = laterExpectedSpeed
            ?: this.getPreferredIas(
                probeAltitude,
                spatialWeatherField?.sampleWeather(probePosition, probeAltitude)?.temperatureC,
                flightPlanTas
            )

        while (true) {
            val probeWeather = spatialWeatherField?.sampleWeather(probePosition, probeAltitude)
            val estimatedOutsideTemperature = WeatherUtils.getStandardTemperatureAt(probeAltitude)

            val targetSpeed = earlierExpectedSpeed ?: getPreferredIas(probeAltitude, probeWeather?.temperatureC, flightPlanTas)
            val maxSpeedChange = deltaTime.inWholeSeconds.toInt() * DECELERATION_RATE
            val speedDifference = (targetSpeed - currentExpectedSpeed).toDouble()
            val speedAdjustment = speedDifference.coerceIn(-maxSpeedChange, maxSpeedChange)

            currentExpectedSpeed += speedAdjustment.roundToInt()

            val stepTas = SpeedConversionUtils.iasToTAS(currentExpectedSpeed, probeAltitude, probeWeather?.temperatureC ?: estimatedOutsideTemperature)
            val stepGroundSpeedKts = SpeedConversionUtils.tasToGS(stepTas, probeWeather?.windVector ?: calmWindVector, earlierPoint.bearingTo(probePosition))
            val stepDistanceNm = (stepGroundSpeedKts * deltaTime.inWholeSeconds) / 3600.0

            val newAltitude = probeAltitude + (verticalSpeed * deltaTime.inWholeSeconds).toInt()
            val reachingTargetAltitude = newAltitude > higherAltitude
            val reachingTargetPoint = remainingProbingDistance - stepDistanceNm < 0

            probeAltitude =
                if (reachingTargetAltitude)
                    higherAltitude
                else
                    newAltitude

            probePosition =
                if (reachingTargetPoint)
                    earlierPoint
                else
                    probePosition.interpolatePositionAlongPath(earlierPoint, stepDistanceNm)

            remainingProbingDistance -=
                if (reachingTargetPoint)
                    probePosition.distanceTo(earlierPoint)
                else
                    stepDistanceNm

            descentPath.add(
                DescentStep(
                    position = probePosition,
                    altitudeFt = probeAltitude,
                    groundSpeed = stepGroundSpeedKts,
                    tas = stepTas,
                    windVector = probeWeather?.windVector ?: calmWindVector,
                    ias = currentExpectedSpeed
                )
            )

            if (reachingTargetPoint) {
                break
            }
        }

        return descentPath
    }

    /**
     * Estimate a suitable descent rate based on altitude and aircraft performance data.
     *
     * @param altitudeFt The current altitude in feet.
     * @return The estimated descent rate in feet per minute (fpm).
     */
    private fun AircraftPerformance.estimateDescentRate(altitudeFt: Int): Int {
        // Example: https://contentzone.eurocontrol.int/aircraftperformance/details.aspx?ICAO=A321
        val vsAbove24k = initialDescentROD ?: descentROD ?: approachROD ?: 1000
        val vs24kTo10k = descentROD ?: approachROD ?: initialDescentROD ?: 1000
        val vs10kToGnd = approachROD ?: descentROD ?: initialDescentROD ?: 1000

        // Interpolate between the values based on altitude
        return when {
            altitudeFt < 10_000 -> {
                vs10kToGnd
            }
            altitudeFt in 10_000 until 24_000 -> {
                val ratio = (altitudeFt - 10_000).toDouble() / (24_000 - 10_000)
                ((1 - ratio) * vs10kToGnd + ratio * vs24kTo10k).toInt()
            }
            else -> {
                val ratio = (altitudeFt - 24_000).coerceAtMost(10_000).toDouble() / 10_000
                ((1 - ratio) * vs24kTo10k + ratio * vsAbove24k).toInt()
            }
        }
    }

    /**
     * Determine the preferred indicated airspeed (IAS) based on altitude, temperature, and flight plan true airspeed (TAS).
     *
     * @param altitudeFt The current altitude in feet.
     * @param temperatureC The current temperature in degrees Celsius, if available.
     * @param flightPlanTas The true airspeed from the flight plan, if available.
     * @return The preferred IAS in knots.
     */
    private fun AircraftPerformance.getPreferredIas(altitudeFt: Int, temperatureC: Int?, flightPlanTas: Int?): Int {
        val tempOrStandardTemp = temperatureC ?: WeatherUtils.getStandardTemperatureAt(altitudeFt)

        val machIas =
            flightPlanTas?.let { SpeedConversionUtils.tasToIAS(it, altitudeFt, tempOrStandardTemp) }
                ?: initialDescentMACH?.let { SpeedConversionUtils.machToIAS(it, altitudeFt, tempOrStandardTemp) }
                ?: descentIAS

        return when {
            altitudeFt > 30000 -> {
                // High cruise descent: Mach hold
                machIas
            }
            altitudeFt in 28000..30000 -> {
                // Blend Mach to IAS between FL280-FL300
                val ratio = (30000 - altitudeFt) / 2000.0
                val blendedIas = machIas * ratio + descentIAS * (1 - ratio)
                blendedIas.toInt()
            }
            altitudeFt in 10000..28000 -> {
                // Constant descent IAS below Mach hold
                descentIAS
            }
            altitudeFt in 5000 until 10000 -> {
                // From 10k to 5k, interpolate approach IAS from descent IAS
                val ratio = (altitudeFt - 5000) / 5000.0
                val interpolatedIas = (1 - ratio) * approachIAS + ratio * descentIAS
                interpolatedIas.coerceAtMost(240.0).toInt() // Cap at 250 kt
            }
            else -> {
                // From 5k to ground, interpolate landing speed
                val ratio = (altitudeFt / 5000.0)
                val interpolatedIas = (1 - ratio) * landingVat + ratio * approachIAS
                interpolatedIas.coerceAtMost(240.0).toInt() // Still cap at 250 kt
            }
        }
    }

    private fun List<Waypoint>.routeToNextAltitudeExpectation(
        arrivalFixExpectations: List<ArrivalFixExpectation>
    ): List<Waypoint> {
        val i = this.indexOfFirst { waypoint ->
            arrivalFixExpectations.any { fix ->
                fix.fixName == waypoint.id.uppercase() && fix.typicalAltitude != null
            }
        }
        return if (i == -1) emptyList() else this.subList(0, i + 1)
    }

    private fun List<ArrivalFixExpectation>.nextAltitudeExpectation(route: List<Waypoint>): Waypoint? =
        route.routeToNextAltitudeExpectation(this).lastOrNull()

}
