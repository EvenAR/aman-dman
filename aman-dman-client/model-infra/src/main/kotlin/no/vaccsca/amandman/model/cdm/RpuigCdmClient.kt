package no.vaccsca.amandman.model.cdm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.datetime.*
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.integration.IntegrationStatus
import no.vaccsca.amandman.model.integration.IntegrationStatusState
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours


class RpuigCdmClient : CdmProvider {
    // https://cdm-server-production.up.railway.app/ifps/depAirport?airport=ENGM

    private val httpClient = OkHttpClient()
    private val statusByAirport = ConcurrentHashMap<String, IntegrationStatus>()

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

    override fun fetchCdmDepartures(airportIcao: String): List<CdmData>? {
        statusByAirport[airportIcao] = IntegrationStatus(
            state = IntegrationStatusState.LOADING,
            shouldFlash = true,
            detail = "Fetching CDM data"
        )

        return runCatching {
            val request = Request.Builder()
                .url("https://cdm-server-production.up.railway.app/ifps/depAirport?airport=$airportIcao")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    statusByAirport[airportIcao] = IntegrationStatus(
                        state = IntegrationStatusState.ERROR,
                        detail = "CDM HTTP ${response.code}"
                    )
                    return emptyList()
                }

                // Parse response body
                val body = response.body.string()
                val objectMapper = ObjectMapper().registerKotlinModule()

                val reader = objectMapper.readerFor(CdmJson::class.java)
                val data = reader.readValues<CdmJson>(body).readAll().toList().map {
                    CdmData(
                        callsign = it.callsign,
                        ttot = it.cdmData.ttot?.let { parseHhMmSsTimestamp(it) },
                        ctot = it.cdmData.ctot?.let { parseHhMmSsTimestamp(it) },
                    )
                }
                statusByAirport[airportIcao] = IntegrationStatus(
                    state = IntegrationStatusState.OK,
                    detail = "CDM data received"
                )
                data
            }
        }.getOrElse {
            statusByAirport[airportIcao] = IntegrationStatus(
                state = IntegrationStatusState.ERROR,
                detail = "CDM fetch failed: ${it.message}"
            )
            null
        }
    }

    override fun getIntegrationStatus(airportIcao: String): IntegrationStatus =
        statusByAirport[airportIcao]
            ?: IntegrationStatus(IntegrationStatusState.ERROR, detail = "CDM not fetched yet")

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
