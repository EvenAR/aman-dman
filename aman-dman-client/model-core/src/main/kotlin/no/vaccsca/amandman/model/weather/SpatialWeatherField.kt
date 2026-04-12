package no.vaccsca.amandman.model.weather

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.distanceTo
import no.vaccsca.amandman.model.weather.WeatherUtils.interpolateWeatherAtAltitude
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SpatialWeatherField(
    private val gridProfiles: List<VerticalWeatherProfile>
) {
    init {
        require(gridProfiles.isNotEmpty()) { "SpatialWeatherField requires at least one grid profile." }
    }

    val time: Instant = gridProfiles.first().time

    fun sampleWeather(position: LatLng, altitudeFt: Int): WeatherLayer? {
        val selection = selectProfiles(position)
        return sampleWeather(selection, altitudeFt)
    }

    fun deriveVerticalProfile(position: LatLng): DerivedWeatherProfile? {
        val selection = selectProfiles(position)
        if (selection.isExactMatch) {
            val exactProfile = selection.profiles.single().profile
            return DerivedWeatherProfile(
                profile = exactProfile.copy(position = position),
                contributingGridPointCount = 1
            )
        }

        val flightLevels = selection.profiles
            .flatMap { it.profile.weatherLayers.map { layer -> layer.flightLevelFt } }
            .distinct()
            .sorted()

        val layers = flightLevels.mapNotNull { altitudeFt ->
            sampleWeather(selection, altitudeFt)
        }

        if (layers.isEmpty()) return null

        return DerivedWeatherProfile(
            profile = VerticalWeatherProfile(
                time = time,
                position = position,
                weatherLayers = layers
            ),
            contributingGridPointCount = selection.profiles.size
        )
    }

    private fun sampleWeather(selection: ProfileSelection, altitudeFt: Int): WeatherLayer? {
        if (selection.profiles.isEmpty()) return null

        if (selection.isExactMatch) {
            return selection.profiles.single().profile.weatherLayers.interpolateWeatherAtAltitude(altitudeFt)
        }

        val weightedLayers = selection.profiles.map { profileDistance ->
            WeightedWeatherLayer(
                layer = profileDistance.profile.weatherLayers.interpolateWeatherAtAltitude(altitudeFt),
                weight = inverseDistanceSquared(profileDistance.distanceNm)
            )
        }

        return interpolateWeightedWeatherLayer(altitudeFt, weightedLayers)
    }

    private fun selectProfiles(position: LatLng): ProfileSelection {
        val profilesByDistance = gridProfiles
            .map { profile -> ProfileDistance(profile = profile, distanceNm = profile.position.distanceTo(position)) }
            .sortedBy { it.distanceNm }

        val exactMatch = profilesByDistance.firstOrNull { it.distanceNm <= EXACT_MATCH_DISTANCE_NM }
        return if (exactMatch != null) {
            ProfileSelection(profiles = listOf(exactMatch), isExactMatch = true)
        } else {
            ProfileSelection(profiles = profilesByDistance, isExactMatch = false)
        }
    }

    private fun interpolateWeightedWeatherLayer(
        altitudeFt: Int,
        weightedLayers: List<WeightedWeatherLayer>
    ): WeatherLayer {
        val totalWeight = weightedLayers.sumOf { it.weight }
        val weightedU = weightedLayers.sumOf { it.weight * it.layer.windVector.uComponentKts() } / totalWeight
        val weightedV = weightedLayers.sumOf { it.weight * it.layer.windVector.vComponentKts() } / totalWeight
        val weightedTemperature = weightedLayers.sumOf { it.weight * it.layer.temperatureC } / totalWeight

        return WeatherLayer(
            flightLevelFt = altitudeFt,
            temperatureC = weightedTemperature.roundToInt(),
            windVector = windVectorFromUvComponents(weightedU, weightedV)
        )
    }

    private fun inverseDistanceSquared(distanceNm: Double): Double = 1.0 / (distanceNm * distanceNm)

    private fun WindVector.uComponentKts(): Double {
        val radians = Math.toRadians(directionDeg.toDouble())
        return -speedKts * kotlin.math.sin(radians)
    }

    private fun WindVector.vComponentKts(): Double {
        val radians = Math.toRadians(directionDeg.toDouble())
        return -speedKts * kotlin.math.cos(radians)
    }

    private fun windVectorFromUvComponents(uComponentKts: Double, vComponentKts: Double): WindVector {
        val speedKts = sqrt(uComponentKts * uComponentKts + vComponentKts * vComponentKts)
        if (speedKts < 0.5) {
            return WindVector(directionDeg = 0, speedKts = 0)
        }

        val directionDeg = (Math.toDegrees(atan2(-uComponentKts, -vComponentKts)) + 360.0) % 360.0
        return WindVector(directionDeg.roundToInt(), speedKts.roundToInt())
    }

    data class DerivedWeatherProfile(
        val profile: VerticalWeatherProfile,
        val contributingGridPointCount: Int
    )

    private data class ProfileDistance(
        val profile: VerticalWeatherProfile,
        val distanceNm: Double
    )

    private data class ProfileSelection(
        val profiles: List<ProfileDistance>,
        val isExactMatch: Boolean
    )

    private data class WeightedWeatherLayer(
        val layer: WeatherLayer,
        val weight: Double
    )

    companion object {
        private const val EXACT_MATCH_DISTANCE_NM = 0.001
    }
}
