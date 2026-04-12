package no.vaccsca.amandman.model.weather

import kotlin.math.roundToInt

object WeatherUtils {

    /**
     * TODO: verify this function
     */
    fun getStandardTemperatureAt(altitudeFt: Int): Int {
        // Standard temperature lapse rate is 2°C per 1000ft
        val lapseRate = 2.0 / 1000.0
        val seaLevelTemperature = 15 // Standard sea level temperature in °C
        return (seaLevelTemperature - (lapseRate * altitudeFt)).roundToInt()
    }

    fun List<WeatherLayer>.interpolateWeatherAtAltitude(altitudeFt: Int): WeatherLayer {
        val sorted = this.sortedBy { it.flightLevelFt }
        val lower = sorted.lastOrNull { it.flightLevelFt <= altitudeFt } ?: sorted.minBy { it.flightLevelFt }
        val upper = sorted.firstOrNull { it.flightLevelFt > altitudeFt } ?: sorted.maxBy { it.flightLevelFt }

        val ratio =
            if (altitudeFt <= lower.flightLevelFt) 0f
            else if (altitudeFt >= upper.flightLevelFt) 1f
            else (altitudeFt - lower.flightLevelFt).toFloat() / (upper.flightLevelFt - lower.flightLevelFt).toFloat()

        // Interpolate wind direction with wrap-around
        val dir1 = lower.windVector.directionDeg.toFloat()
        val dir2 = upper.windVector.directionDeg.toFloat()
        val delta = ((dir2 - dir1 + 540) % 360) - 180 // shortest path
        val direction = (dir1 + ratio * delta + 360) % 360

        val speed = (1 - ratio) * lower.windVector.speedKts + ratio * upper.windVector.speedKts
        val temperature = (1 - ratio) * lower.temperatureC + ratio * upper.temperatureC

        return WeatherLayer(
            flightLevelFt = altitudeFt,
            windVector = WindVector(direction.roundToInt(), speed.roundToInt()),
            temperatureC = temperature.roundToInt()
        )
    }
}
