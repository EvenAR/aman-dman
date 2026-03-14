import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.weather.SpatialWeatherField
import no.vaccsca.amandman.model.weather.WeatherUtils
import no.vaccsca.amandman.model.weather.WeatherUtils.interpolateWeatherAtAltitude
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.weather.WeatherLayer
import no.vaccsca.amandman.model.weather.WindVector
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun `Spatial weather returns unchanged values on exact grid point match`() {
        val exactProfile = profileAt(
            position = LatLng(60.0, 11.0),
            layers = listOf(
                WeatherLayer(flightLevelFt = 5000, temperatureC = -5, windVector = WindVector(210, 22)),
                WeatherLayer(flightLevelFt = 10000, temperatureC = -15, windVector = WindVector(220, 35)),
            )
        )
        val field = SpatialWeatherField(
            listOf(
                exactProfile,
                profileAt(
                    position = LatLng(60.5, 11.5),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 5000, temperatureC = -2, windVector = WindVector(180, 10)),
                        WeatherLayer(flightLevelFt = 10000, temperatureC = -12, windVector = WindVector(190, 20)),
                    )
                )
            )
        )

        val sampled = field.sampleWeather(exactProfile.position, 10_000)
        val derivedProfile = field.deriveVerticalProfile(exactProfile.position)

        assertEquals(exactProfile.weatherLayers.last(), sampled)
        assertEquals(exactProfile, assertNotNull(derivedProfile).profile)
        assertEquals(1, derivedProfile.contributingGridPointCount)
    }

    @Test
    fun `Spatial weather blends multiple profiles by inverse distance`() {
        val field = SpatialWeatherField(
            listOf(
                profileAt(
                    position = LatLng(0.0, -1.0),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 10000, temperatureC = 0, windVector = WindVector(0, 20))
                    )
                ),
                profileAt(
                    position = LatLng(0.0, 2.0),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 10000, temperatureC = -30, windVector = WindVector(90, 20))
                    )
                )
            )
        )

        val layer = assertNotNull(field.sampleWeather(LatLng(0.0, 0.0), 10_000))
        assertEquals(14, layer.windVector.directionDeg)
        assertEquals(16, layer.windVector.speedKts)
        assertEquals(-6, layer.temperatureC)
    }

    @Test
    fun `Spatial weather handles wind direction wrap around via components`() {
        val field = SpatialWeatherField(
            listOf(
                profileAt(
                    position = LatLng(0.0, -1.0),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 10000, temperatureC = -10, windVector = WindVector(350, 20))
                    )
                ),
                profileAt(
                    position = LatLng(0.0, 1.0),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 10000, temperatureC = -10, windVector = WindVector(10, 20))
                    )
                )
            )
        )

        val layer = assertNotNull(field.sampleWeather(LatLng(0.0, 0.0), 10_000))
        assertEquals(0, layer.windVector.directionDeg)
        assertEquals(20, layer.windVector.speedKts)
        assertEquals(-10, layer.temperatureC)
    }

    @Test
    fun `Spatial weather interpolates vertically at sampled position`() {
        val field = SpatialWeatherField(
            listOf(
                profileAt(
                    position = LatLng(0.0, 0.0),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 0, temperatureC = 10, windVector = WindVector(180, 10)),
                        WeatherLayer(flightLevelFt = 10000, temperatureC = -10, windVector = WindVector(180, 30)),
                    )
                ),
                profileAt(
                    position = LatLng(0.0, 1.0),
                    layers = listOf(
                        WeatherLayer(flightLevelFt = 0, temperatureC = 10, windVector = WindVector(180, 10)),
                        WeatherLayer(flightLevelFt = 10000, temperatureC = -10, windVector = WindVector(180, 30)),
                    )
                )
            )
        )

        val layer = assertNotNull(field.sampleWeather(LatLng(0.0, 0.5), 5_000))
        assertEquals(180, layer.windVector.directionDeg)
        assertEquals(20, layer.windVector.speedKts)
        assertEquals(0, layer.temperatureC)
    }

    private fun profileAt(position: LatLng, layers: List<WeatherLayer>) = VerticalWeatherProfile(
        time = Instant.parse("2026-03-14T12:00:00Z"),
        position = position,
        weatherLayers = layers
    )

}
