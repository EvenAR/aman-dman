import no.vaccsca.amandman.model.weather.WeatherUtils
import no.vaccsca.amandman.model.weather.WeatherUtils.interpolateWeatherAtAltitude
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.weather.WeatherLayer
import no.vaccsca.amandman.model.weather.WindVector
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WeatherTest {

    @Test
    fun `Should calculate standard temperature correctly`() {
        val expectedTemperature = WeatherUtils.getStandardTemperatureAt(35000)
        assertEquals(-55, expectedTemperature)
    }

    @Test
    fun `Should interpolate wind direction, speed and temperature`() {
        val layers = listOf(
            WeatherLayer(flightLevelFt = 0, temperatureC = 0, windVector = WindVector(0, 0)),
            WeatherLayer(flightLevelFt = 10000, temperatureC = -20, windVector = WindVector(90, 50)),
            WeatherLayer(flightLevelFt = 20000, temperatureC = -40, windVector = WindVector(180, 100)),
        )

        layers.interpolateWeatherAtAltitude(10_000).let { interpolatedLayer ->
            assertEquals(90, interpolatedLayer.windVector.directionDeg)
            assertEquals(50, interpolatedLayer.windVector.speedKts)
            assertEquals(-20, interpolatedLayer.temperatureC)
        }

        layers.interpolateWeatherAtAltitude(15_000).let { interpolatedLayer ->
            assertEquals(135, interpolatedLayer.windVector.directionDeg)
            assertEquals(75, interpolatedLayer.windVector.speedKts)
            assertEquals(-30, interpolatedLayer.temperatureC)
        }
    }

    @Test
    fun `When wind direction wraps around 360 degrees, wind direction should be interpolated correctly`() {
        val layers = listOf(
            WeatherLayer(flightLevelFt = 0, temperatureC = 0, windVector = WindVector(350, 20)),
            WeatherLayer(flightLevelFt = 10000, temperatureC = -20, windVector = WindVector(10, 40)),
        )

        layers.interpolateWeatherAtAltitude(5_000).let { interpolatedLayer ->
            assertEquals(0, interpolatedLayer.windVector.directionDeg)
            assertEquals(30, interpolatedLayer.windVector.speedKts)
            assertEquals(-10, interpolatedLayer.temperatureC)
        }
    }

}