package no.vaccsca.amandman.backend.data.repository

import kotlinx.datetime.*
import no.vaccsca.amandman.common.NtpClock
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import no.vaccsca.amandman.common.domain.valueobjects.LatLng
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.domain.valueobjects.weather.WeatherLayer
import no.vaccsca.amandman.common.domain.valueobjects.weather.WindVector
import no.vaccsca.amandman.common.util.NumberUtils.format
import ucar.nc2.NetcdfFile
import ucar.nc2.NetcdfFiles
import kotlin.math.round

class WindApi {

    fun getVerticalProfileAtPoint(latitude: Double, longitude: Double): VerticalWeatherProfile? {
        val gridPoints = getVerticalProfileGrid(
            BoundingBox(
                topLat = latitude + 0.5,
                bottomLat = latitude - 0.5,
                leftLon = longitude - 0.5,
                rightLon = longitude + 0.5
            )
        )

        val closestPoint =
            gridPoints.minByOrNull {
                abs(it.latitude - latitude) + abs(it.longitude - longitude)
            }

        return closestPoint?.windProfile
    }


    private fun getVerticalProfileGrid(bbox: BoundingBox): MutableList<WindProfileGridPoint> {
        val (publishTime, grib) = fetchMostRecentForecast(bbox)!!

        val latitudes = grib.findVariable("lat")!!
        val longitudes = grib.findVariable("lon")!!
        val reftimes = grib.findVariable("time")!!
        val isobarics = grib.findVariable("isobaric")!!
        val temperatures = grib.findVariable("Temperature_isobaric")!!
        val uWindComponents = grib.findVariable("u-component_of_wind_isobaric")!!
        val vWindComponents = grib.findVariable("v-component_of_wind_isobaric")!!

        // Read all data arrays once
        val latData = latitudes.read()
        val lonData = longitudes.read()
        val timeData = reftimes.read()
        val isobaricData = isobarics.read()
        val tempData = temperatures.read()
        val uWindData = uWindComponents.read()
        val vWindData = vWindComponents.read()

        val gridPoints = mutableListOf<WindProfileGridPoint>()

        val i = 0 // Only one time dimension

        for (a in 0 until isobaricData.shape[0]) {
            for (j in 0 until latData.shape[0]) {
                for (k in 0 until lonData.shape[0]) {

                    val isobaric = isobaricData.getDouble(a)
                    val gridLat = latData.getDouble(j)
                    val gridLon = lonData.getDouble(k)

                    // Wind dimension order: [time][isobaric][lat][lon]
                    val uWind = uWindData.getFloat(uWindData.index.set(i, a, j, k))
                    val vWind = vWindData.getFloat(vWindData.index.set(i, a, j, k))

                    var windDirection = Math.toDegrees(atan2(uWind, vWind).toDouble()).roundToInt() + 180
                    if (windDirection > 360) windDirection -= 360


                    val windSpeedKnots = (sqrt(uWind * uWind + vWind * vWind) * 1.94384).roundToInt()

                    // Temperature dimension order: [time][isobaric][lat][lon]
                    val temp = (tempData.getDouble(tempData.index.set(i, a, j, k)) - 272.15).roundToInt()

                    val flightLevel = pressureToAltitudeInFeet(isobaric).roundToInt()

                    val forecastTime = publishTime.plus(timeData.getLong(i).hours)

                    val currentGridPoint = gridPoints.find { it.latitude == gridLat && it.longitude == gridLon }?.windProfile
                        ?: VerticalWeatherProfile(forecastTime, LatLng(lat = gridLat, lon = gridLon), mutableListOf())

                    currentGridPoint.weatherLayers.add(
                        WeatherLayer(
                            flightLevel,
                            temp,
                            WindVector(windDirection, windSpeedKnots)
                        )
                    )

                    if (gridPoints.none { it.latitude == gridLat && it.longitude == gridLon }) {
                        gridPoints.add(WindProfileGridPoint(gridLat, gridLon, currentGridPoint))
                    }
                }
            }
        }

        return gridPoints
    }

    private fun fetchMostRecentForecast(bbox: BoundingBox): Pair<Instant, NetcdfFile>? {
        val timeNow = NtpClock.now()

        // Truncate fractional seconds by constructing a new Instant at whole seconds precision:
        val timeNowTruncated = Instant.fromEpochSeconds(timeNow.epochSeconds)

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
                return Pair(closestPublishTime, gribFile)
            } catch (e: FileNotFoundException) {
                val nextPublicationTime = closestPublishTime.minus(6.hours)
                println("Could not find a forecast publication from $closestPublishTime. Will try $nextPublicationTime")
                closestPublishTime = nextPublicationTime
            }
        }
        return null
    }

    private fun fetchGribFile(bbox: BoundingBox, productionTime: Instant, hourOffset: Int): NetcdfFile {
        val formattedProductionDate = productionTime.format("yyyyMMdd")
        val formattedProductionHour = productionTime.format("HH")
        val hoursOffsetFormatted = hourOffset.toString().padStart(3, '0')

        // Example: https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25_1hr.pl?dir=%2Fgfs.20250309%2F12%2Fatmos&file=gfs.t12z.pgrb2.0p25.f000&all_var=on&all_lev=on
        val fileUrl =
            "https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25_1hr.pl" +
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
                    "&lev_150_mb=on"
                    "&var_UGRD=on" +
                    "&var_VGRD=on"

        val tempFile = Files.createTempFile("wind_data", ".grib2").toFile()
        tempFile.deleteOnExit()

        URI(fileUrl).toURL().openStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return NetcdfFiles.open(tempFile.path) // now it's a real file
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
}
