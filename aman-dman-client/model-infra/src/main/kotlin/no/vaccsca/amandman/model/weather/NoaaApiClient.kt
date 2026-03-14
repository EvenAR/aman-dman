package no.vaccsca.amandman.model.weather

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.util.NumberUtils.format
import no.vaccsca.amandman.model.integration.IntegrationStatus
import no.vaccsca.amandman.model.integration.IntegrationStatusState
import no.vaccsca.amandman.model.navigation.LatLng
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import ucar.nc2.NetcdfFile
import ucar.nc2.NetcdfFiles
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
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
    private val gridPaddingDeg: Double = 0.25
) : WindProfileProvider {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val statusByAirport = ConcurrentHashMap<String, IntegrationStatus>()

    override fun getWeatherDataAroundAirport(
        airportIcao: String,
        latitude: Double,
        longitude: Double,
        weatherFetchRadiusNm: Double
    ): WindProfileResult {
        statusByAirport[airportIcao] = IntegrationStatus(
            state = IntegrationStatusState.LOADING,
            shouldFlash = true,
            detail = "Loading NOAA wind profile"
        )

        logger.info(
            "Fetching NOAA weather for {} with fetch radius {} NM around airport center",
            airportIcao,
            weatherFetchRadiusNm.format(1)
        )

        val latitudeSpanDeg = weatherFetchRadiusNm / 60.0 + gridPaddingDeg
        val longitudeSpanDeg =
            weatherFetchRadiusNm / (60.0 * max(cos(Math.toRadians(latitude)), 0.01)) + gridPaddingDeg
        val bbox = BoundingBox(
            topLat = latitude + latitudeSpanDeg,
            bottomLat = latitude - latitudeSpanDeg,
            leftLon = longitude - longitudeSpanDeg,
            rightLon = longitude + longitudeSpanDeg
        )

        val forecast = fetchMostRecentForecast(bbox)
        return when (forecast) {
            is ForecastResult.Failure -> {
                logger.warn(
                    "Failed to fetch NOAA weather for {} with fetch radius {} NM: {}",
                    airportIcao,
                    weatherFetchRadiusNm.format(1),
                    forecast.error.message
                )
                statusByAirport[airportIcao] = IntegrationStatus(
                    state = IntegrationStatusState.ERROR,
                    detail = forecast.error.message
                )
                WindProfileResult.Failure(forecast.error)
            }

            is ForecastResult.Success -> {
                try {
                    forecast.grib.use { grib ->
                        val gridProfiles = getVerticalProfileGrid(grib, forecast.publishTime)
                        logger.info(
                            "Parsed {} NOAA grid points for {} with fetch radius {} NM",
                            gridProfiles.size,
                            airportIcao,
                            weatherFetchRadiusNm.format(1)
                        )

                        if (gridProfiles.isEmpty()) {
                            statusByAirport[airportIcao] = IntegrationStatus(
                                state = IntegrationStatusState.ERROR,
                                detail = "No NOAA profile for area"
                            )
                            return WindProfileResult.Failure(
                                WindProfileError.Parse("No NOAA grid points available for requested area.")
                            )
                        }

                        val weatherField = SpatialWeatherField(gridProfiles)
                        val airportDisplayProfile = weatherField.deriveVerticalProfile(LatLng(latitude, longitude))
                            ?: return WindProfileResult.Failure(
                                WindProfileError.Parse("Failed to derive airport weather profile from NOAA grid.")
                            ).also {
                                statusByAirport[airportIcao] = IntegrationStatus(
                                    state = IntegrationStatusState.ERROR,
                                    detail = "No NOAA profile for point"
                                )
                            }

                        statusByAirport[airportIcao] = IntegrationStatus(
                            IntegrationStatusState.OK,
                            detail = "NOAA profile loaded"
                        )

                        WindProfileResult.Success(
                            weatherField = weatherField,
                            displayProfile = airportDisplayProfile.profile
                        )
                    }
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to parse NOAA forecast for {} with fetch radius {} NM: {}",
                        airportIcao,
                        weatherFetchRadiusNm.format(1),
                        e.message
                    )
                    statusByAirport[airportIcao] = IntegrationStatus(
                        state = IntegrationStatusState.ERROR,
                        detail = "Failed to parse GRIB forecast: ${e.message}"
                    )
                    WindProfileResult.Failure(
                        WindProfileError.Parse("Failed to parse GRIB forecast: ${e.message}")
                    )
                }
            }
        }
    }

    override fun getIntegrationStatus(airportIcao: String): IntegrationStatus =
        statusByAirport[airportIcao]
            ?: IntegrationStatus(IntegrationStatusState.ERROR, detail = "MET not loaded yet")

    private fun getVerticalProfileGrid(grib: NetcdfFile, publishTime: Instant): List<VerticalWeatherProfile> {
        val latitudes = grib.findVariable("lat") ?: throw IllegalStateException("Missing variable: lat")
        val longitudes = grib.findVariable("lon") ?: throw IllegalStateException("Missing variable: lon")
        val reftimes = grib.findVariable("time") ?: throw IllegalStateException("Missing variable: time")
        val isobarics = grib.findVariable("isobaric") ?: throw IllegalStateException("Missing variable: isobaric")
        val temperatures = grib.findVariable("Temperature_isobaric")
            ?: throw IllegalStateException("Missing variable: Temperature_isobaric")
        val uWindComponents = grib.findVariable("u-component_of_wind_isobaric")
            ?: throw IllegalStateException("Missing variable: u-component_of_wind_isobaric")
        val vWindComponents = grib.findVariable("v-component_of_wind_isobaric")
            ?: throw IllegalStateException("Missing variable: v-component_of_wind_isobaric")

        val latData = latitudes.read()
        val lonData = longitudes.read()
        val timeData = reftimes.read()
        val isobaricData = isobarics.read()
        val tempData = temperatures.read()
        val uWindData = uWindComponents.read()
        val vWindData = vWindComponents.read()

        val gridPointLayers = mutableMapOf<Pair<Double, Double>, MutableList<WeatherLayer>>()

        val timeIndex = 0
        val forecastTime = publishTime.plus(timeData.getLong(timeIndex).hours)

        for (a in 0 until isobaricData.shape[0]) {
            for (j in 0 until latData.shape[0]) {
                for (k in 0 until lonData.shape[0]) {
                    val isobaric = isobaricData.getDouble(a)
                    val gridLat = latData.getDouble(j)
                    val gridLon = lonData.getDouble(k)

                    val uWind = uWindData.getFloat(uWindData.index.set(timeIndex, a, j, k))
                    val vWind = vWindData.getFloat(vWindData.index.set(timeIndex, a, j, k))

                    var windDirection = Math.toDegrees(atan2(uWind, vWind).toDouble()).roundToInt() + 180
                    if (windDirection > 360) windDirection -= 360

                    val windSpeedKnots = (sqrt(uWind * uWind + vWind * vWind) * 1.94384).roundToInt()
                    val temp = (tempData.getDouble(tempData.index.set(timeIndex, a, j, k)) - 273.15).roundToInt()
                    val flightLevel = pressureToAltitudeInFeet(isobaric).roundToInt()

                    val layers = gridPointLayers.getOrPut(gridLat to gridLon) { mutableListOf() }
                    layers.add(
                        WeatherLayer(
                            flightLevelFt = flightLevel,
                            temperatureC = temp,
                            windVector = WindVector(windDirection, windSpeedKnots)
                        )
                    )
                }
            }
        }

        return gridPointLayers.map { (key, layers) ->
            VerticalWeatherProfile(
                time = forecastTime,
                position = LatLng(lat = key.first, lon = key.second),
                weatherLayers = layers.sortedBy { it.flightLevelFt }
            )
        }
    }

    private fun fetchMostRecentForecast(bbox: BoundingBox): ForecastResult {
        val timeNow = clock.now()
        val timeNowTruncated = Instant.fromEpochSeconds(timeNow.epochSeconds)
        var closestPublishTime = timeNowTruncated.minus((timeNowTruncated.epochSeconds % (6 * 60 * 60)).seconds)

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
                "&var_VGRD=on" +
                "&var_TMP=on"

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

    private fun pressureToAltitudeInFeet(pascal: Double): Double =
        145366.45 * (1 - Math.pow(pascal / 101325.0, 0.190284))

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
