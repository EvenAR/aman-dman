package no.vaccsca.amandman.model.weather.data

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.util.NumberUtils.format
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.weather.WeatherLayer
import no.vaccsca.amandman.model.weather.WindProfileError
import no.vaccsca.amandman.model.weather.WindProfileProvider
import no.vaccsca.amandman.model.weather.WindProfileResult
import no.vaccsca.amandman.model.weather.WindVector
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import ucar.nc2.NetcdfFile
import ucar.nc2.NetcdfFiles
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class NoaaApiClient(
    private val clock: Clock = object : Clock {
        override fun now(): Instant = NtpClock.now()
    },
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25_1hr.pl",
    private val bboxRadiusDeg: Double = 0.5
) : WindProfileProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getVerticalProfileAtPoint(latitude: Double, longitude: Double): WindProfileResult {
        val bbox = BoundingBox(
            topLat = latitude + bboxRadiusDeg,
            bottomLat = latitude - bboxRadiusDeg,
            leftLon = longitude - bboxRadiusDeg,
            rightLon = longitude + bboxRadiusDeg
        )

        val forecast = fetchMostRecentForecast(bbox)
        return when (forecast) {
            is ForecastResult.Failure -> WindProfileResult.Failure(forecast.error)
            is ForecastResult.Success -> {
                try {
                    forecast.grib.use {
                        val gridPoints = getVerticalProfileGrid(it, forecast.publishTime)
                        val closestPoint = gridPoints.minByOrNull { point ->
                            abs(point.latitude - latitude) + abs(point.longitude - longitude)
                        }
                        WindProfileResult.Success(closestPoint?.windProfile)
                    }
                } catch (e: Exception) {
                    WindProfileResult.Failure(
                        WindProfileError.Parse("Failed to parse GRIB forecast: ${e.message}")
                    )
                }
            }
        }
    }

    private fun getVerticalProfileGrid(grib: NetcdfFile, publishTime: Instant): List<WindProfileGridPoint> {
        val latitudes = grib.findVariable("lat") ?: throw IllegalStateException("Missing variable: lat")
        val longitudes = grib.findVariable("lon") ?: throw IllegalStateException("Missing variable: lon")
        val reftimes = grib.findVariable("time") ?: throw IllegalStateException("Missing variable: time")
        val isobarics = grib.findVariable("isobaric") ?: throw IllegalStateException("Missing variable: isobaric")
        val temperatures = grib.findVariable("Temperature_isobaric") ?: throw IllegalStateException("Missing variable: Temperature_isobaric")
        val uWindComponents = grib.findVariable("u-component_of_wind_isobaric")
            ?: throw IllegalStateException("Missing variable: u-component_of_wind_isobaric")
        val vWindComponents = grib.findVariable("v-component_of_wind_isobaric")
            ?: throw IllegalStateException("Missing variable: v-component_of_wind_isobaric")

        // Read all data arrays once
        val latData = latitudes.read()
        val lonData = longitudes.read()
        val timeData = reftimes.read()
        val isobaricData = isobarics.read()
        val tempData = temperatures.read()
        val uWindData = uWindComponents.read()
        val vWindData = vWindComponents.read()

        val gridPointLayers = mutableMapOf<Pair<Double, Double>, MutableList<WeatherLayer>>()

        val timeIndex = 0 // Only one time dimension
        val forecastTime = publishTime.plus(timeData.getLong(timeIndex).hours)

        for (a in 0 until isobaricData.shape[0]) {
            for (j in 0 until latData.shape[0]) {
                for (k in 0 until lonData.shape[0]) {
                    val isobaric = isobaricData.getDouble(a)
                    val gridLat = latData.getDouble(j)
                    val gridLon = lonData.getDouble(k)

                    // Wind dimension order: [time][isobaric][lat][lon]
                    val uWind = uWindData.getFloat(uWindData.index.set(timeIndex, a, j, k))
                    val vWind = vWindData.getFloat(vWindData.index.set(timeIndex, a, j, k))

                    var windDirection = Math.toDegrees(atan2(uWind, vWind).toDouble()).roundToInt() + 180
                    if (windDirection > 360) windDirection -= 360

                    val windSpeedKnots = (sqrt(uWind * uWind + vWind * vWind) * 1.94384).roundToInt()

                    // Temperature dimension order: [time][isobaric][lat][lon]
                    val temp = (tempData.getDouble(tempData.index.set(timeIndex, a, j, k)) - 273.15).roundToInt()

                    val flightLevel = pressureToAltitudeInFeet(isobaric).roundToInt()

                    val key = gridLat to gridLon
                    val layers = gridPointLayers.getOrPut(key) { mutableListOf() }
                    layers.add(
                        WeatherLayer(
                            flightLevel,
                            temp,
                            WindVector(windDirection, windSpeedKnots)
                        )
                    )
                }
            }
        }

        return gridPointLayers.map { (key, layers) ->
            WindProfileGridPoint(
                latitude = key.first,
                longitude = key.second,
                windProfile = VerticalWeatherProfile(
                    time = forecastTime,
                    position = LatLng(lat = key.first, lon = key.second),
                    weatherLayers = layers.toList()
                )
            )
        }
    }

    private fun fetchMostRecentForecast(bbox: BoundingBox): ForecastResult {
        val timeNow = clock.now()

        // Truncate fractional seconds by constructing a new Instant at whole seconds precision:
        val timeNowTruncated = Instant.Companion.fromEpochSeconds(timeNow.epochSeconds)

        // Compute closest publish time by subtracting remainder seconds:
        var closestPublishTime = timeNowTruncated.minus((timeNowTruncated.epochSeconds % (6 * 60 * 60)).seconds)

        // Go backwards in time until we find a forecast
        for (i in 0 until 5) {
            try {
                val secondsSincePublish = timeNow.epochSeconds - closestPublishTime.epochSeconds
                val hoursSincePublishTimeRounded = round(secondsSincePublish / 3600.0).toInt()

                val gribFile = fetchGribFile(
                    bbox = bbox,
                    productionTime = closestPublishTime,
                    hourOffset = hoursSincePublishTimeRounded
                )
                return ForecastResult.Success(closestPublishTime, gribFile)
            } catch (e: FileNotFoundException) {
                val nextPublicationTime = closestPublishTime.minus(6.hours)
                logger.info("Could not find a forecast publication from $closestPublishTime. Will try $nextPublicationTime")
                closestPublishTime = nextPublicationTime
            } catch (e: IOException) {
                return ForecastResult.Failure(
                    WindProfileError.Network("Failed to download GRIB forecast: ${e.message}")
                )
            } catch (e: Exception) {
                return ForecastResult.Failure(
                    WindProfileError.Parse("Failed to open GRIB forecast: ${e.message}")
                )
            }
        }

        return ForecastResult.Failure(
            WindProfileError.NoForecastAvailable("No recent GRIB forecast found for requested bounding box.")
        )
    }

    private fun fetchGribFile(bbox: BoundingBox, productionTime: Instant, hourOffset: Int): NetcdfFile {
        val formattedProductionDate = productionTime.format("yyyyMMdd")
        val formattedProductionHour = productionTime.format("HH")
        val hoursOffsetFormatted = hourOffset.toString().padStart(3, '0')

        val fileUrl =
            "$baseUrl" +
                "?dir=%2Fgfs.${formattedProductionDate}%2F${formattedProductionHour}%2Fatmos" +
                "&file=gfs.t${formattedProductionHour}z.pgrb2.0p25.f$hoursOffsetFormatted" +
                "&subregion=" +
                "&toplat=${bbox.topLat}" +
                "&leftlon=${bbox.leftLon}" +
                "&rightlon=${bbox.rightLon}" +
                "&bottomlat=${bbox.bottomLat}" +
                "&lev_900_mb=on" +
                "&lev_850_mb=on" +
                "&lev_800_mb=on" +
                "&lev_750_mb=on" +
                "&lev_700_mb=on" +
                "&lev_650_mb=on" +
                "&lev_600_mb=on" +
                "&lev_550_mb=on" +
                "&lev_500_mb=on" +
                "&lev_450_mb=on" +
                "&lev_400_mb=on" +
                "&lev_350_mb=on" +
                "&lev_300_mb=on" +
                "&lev_250_mb=on" +
                "&lev_200_mb=on" +
                "&lev_150_mb=on" +
                "&var_UGRD=on" +
                "&var_VGRD=on"

        val request = Request.Builder().url(fileUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw FileNotFoundException("No GRIB at $fileUrl (status ${response.code})")
            }
            val body = response.body ?: throw IOException("Empty GRIB response body")

            val tempFile = Files.createTempFile("wind_data", ".grib2").toFile()
            tempFile.deleteOnExit()

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return NetcdfFiles.open(tempFile.path)
        }
    }

    private fun pressureToAltitudeInFeet(pascal: Double): Double {
        return 145366.45 * (1 - Math.pow(pascal / 101325.0, 0.190284))
    }

    private data class WindProfileGridPoint(
        val latitude: Double,
        val longitude: Double,
        val windProfile: VerticalWeatherProfile
    )

    private data class BoundingBox(
        val topLat: Double,
        val bottomLat: Double,
        val leftLon: Double,
        val rightLon: Double
    )

    private sealed class ForecastResult {
        data class Success(val publishTime: Instant, val grib: NetcdfFile) : ForecastResult()
        data class Failure(val error: WindProfileError) : ForecastResult()
    }
}