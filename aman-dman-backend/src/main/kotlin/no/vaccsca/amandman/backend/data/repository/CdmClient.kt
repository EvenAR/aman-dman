package no.vaccsca.amandman.backend.data.repository

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.domain.valueobjects.CdmData
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours


class CdmClient {
    // https://cdm-server-production.up.railway.app/ifps/depAirport?airport=ENGM

    private val httpClient = OkHttpClient()

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CdmJson(
        val callsign: String,
        val cdmData: CdmDataJson,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CdmDataJson(
        val tobt: String?,
        val tsat: String?,
        val ttot: String?,
        val ctot: String?,
    )

    fun fetchCdmDepartures(airportIcao: String): List<CdmData>? {
        val request = Request.Builder()
            .url("https://cdm-server-production.up.railway.app/ifps/depAirport?airport=$airportIcao")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()

            // Parse response body
            val body = response.body?.string()
            val objectMapper = ObjectMapper().registerKotlinModule()

            return try {
                val reader = objectMapper.readerFor(CdmJson::class.java)
                reader.readValues<CdmJson>(body).readAll().toList().map {
                    CdmData(
                        callsign = it.callsign,
                        ttot = it.cdmData.ttot?.let { parseHhMmSsTimestamp(it) },
                        ctot = it.cdmData.ctot?.let { parseHhMmSsTimestamp(it) },
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Parses a UTC "HHMMSS" timestamp into an Instant,
     * assuming today's date in UTC (or tomorrow if already passed >1h ago).
     */
    private fun parseHhMmSsTimestamp(value: String): Instant? {
        return try {
            val hour = value.substring(0, 2).toInt()
            val minute = value.substring(2, 4).toInt()
            val second = value.substring(4, 6).toInt()

            val nowUtc = NtpClock.now().toLocalDateTime(TimeZone.UTC)
            val todayDate = nowUtc.date

            val parsed = LocalDateTime(
                year = todayDate.year,
                monthNumber = todayDate.monthNumber,
                dayOfMonth = todayDate.dayOfMonth,
                hour = hour,
                minute = minute,
                second = second,
                nanosecond = 0
            )

            // If the time is more than 1 hour in the past, assume it's tomorrow UTC
            var parsedInstant = parsed.toInstant(TimeZone.UTC)
            val nowInstant = NtpClock.now()

            if (parsedInstant < nowInstant.minus(1.hours)) {
                parsedInstant = parsedInstant.plus(1.days)
            }

            parsedInstant
        } catch (_: Throwable) {
            null
        }
    }
}