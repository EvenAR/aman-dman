package no.vaccsca.amandman.backend.data.integration

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.vaccsca.amandman.backend.data.dto.sharedState.SharedStateEventJson
import no.vaccsca.amandman.backend.data.dto.sharedState.SharedStateJson
import no.vaccsca.amandman.backend.data.repository.SettingsRepository
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayDelayEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.util.KotlinxInstantModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID.randomUUID

class SharedStateHttpClient : SharedState {
    private val clientUuid = randomUUID().toString()
    private val httpClient = OkHttpClient()
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        registerModule(KotlinxInstantModule)
        findAndRegisterModules()
    }

    private val SESSION_ID_HEADER = "x-session-uuid"
    private val CLIENT_VERSION_HEADER = "x-client-version"
    private val JSON = "application/json".toMediaType()
    private val BASE_URL: String = SettingsRepository.getSettings(reload = true).connectionConfig.api.host
    private val clientVersion = object {}.javaClass.`package`.implementationVersion ?: "DEV-SNAPSHOT"

    override fun checkMasterRoleStatus(airportIcao: String): Boolean {
        val request = baseApiRequest(airportIcao, "master-role")
            .header(SESSION_ID_HEADER, clientUuid)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false

            val body = response.body?.string()

            return try {
                val masterResp = objectMapper.readValue(body, MasterRoleResponse::class.java)
                masterResp.isMaster
            } catch (ex: Exception) {
                false
            }
        }
    }

    override fun acquireMasterRole(airportIcao: String): Boolean {
        val request = baseApiRequest(airportIcao, "master-role")
            .post("".toRequestBody()) // Empty body
            .build()

        val response = httpClient.newCall(request).execute()

        return response.isSuccessful
    }

    override fun releaseMasterRole(airportIcao: String) {
        val request = baseApiRequest(airportIcao, "master-role")
            .delete()
            .build()

        httpClient.newCall(request).execute().close()
    }

    override fun sendTimelineEvents(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        val events = timelineEvents.map { event ->
            val type = when (event) {
                is RunwayArrivalEvent -> "runwayArrival"
                is DepartureEvent -> "runwayDeparture"
                is RunwayDelayEvent -> "runwayDelay"
            }
            SharedStateEventJson(type = type, event = event)
        }

        val sharedState = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = events
        )

        sendStateJson(airportIcao, "events", sharedState)
    }

    override fun getTimelineEvents(airportIcao: String): List<TimelineEvent> {
        val typeRef = object : TypeReference<SharedStateJson<List<SharedStateEventJson>>>() {}
        val timelineEvents = fetchStateJson(airportIcao, "events", typeRef)

        return timelineEvents.data.map {
            when (it.type) {
                "runwayArrival" -> it.event as RunwayArrivalEvent
                "runwayDeparture" -> it.event as DepartureEvent
                "runwayDelay" -> it.event as RunwayDelayEvent
                else -> throw IllegalArgumentException("Unknown event type: ${it.type}")
            }
        }
    }

    override fun getRunwayStatuses(airportIcao: String): Map<String, RunwayStatus> {
        val typeRef = object : TypeReference<SharedStateJson<Map<String, RunwayStatus>>>() {}
        val runwayStatuses = fetchStateJson(airportIcao, "runway-modes", typeRef)
        return runwayStatuses.data
    }

    override fun sendRunwayStatuses(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
        val sharedStateJson = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = runwayStatuses
        )
        sendStateJson(airportIcao, "runway-modes", sharedStateJson)
    }

    override fun sendWeatherData(airportIcao: String, weatherData: VerticalWeatherProfile?) {
        val sharedStateJson = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = weatherData
        )
        sendStateJson(airportIcao, "weather", sharedStateJson)
    }

    override fun getWeatherData(airportIcao: String): VerticalWeatherProfile? {
        val typeRef = object : TypeReference<SharedStateJson<VerticalWeatherProfile?>>() {}
        val weather = fetchStateJson(airportIcao, "weather", typeRef)
        return weather.data
    }

    override fun getMinimumSpacing(airportIcao: String): Double {
        val typeRef = object : TypeReference<SharedStateJson<Double>>() {}
        val minimumSpacing = fetchStateJson(airportIcao, "minimum-spacing", typeRef)
        return minimumSpacing.data
    }

    override fun sendMinimumSpacing(airportIcao: String, minimumSpacingNm: Double) {
        val sharedStateJson = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = minimumSpacingNm
        )
        sendStateJson(airportIcao, "minimum-spacing", sharedStateJson)
    }

    // Primary fetchStateJson method that uses TypeReference for complete type safety
    private fun <T> fetchStateJson(airportIcao: String, resource: String, typeRef: TypeReference<SharedStateJson<T>>): SharedStateJson<T> {
        val request = baseApiRequest(airportIcao, resource)
            .get()
            .build()

        val response = httpClient.newCall(request).execute().use { response ->
            response.body?.string()
        }

        return objectMapper.readValue(response, typeRef)
    }

    // Convenience overload for simple types (when type erasure isn't an issue)
    private inline fun <reified T> fetchStateJson(airportIcao: String, dataType: String): SharedStateJson<T> {
        val typeRef = object : TypeReference<SharedStateJson<T>>() {}
        return fetchStateJson(airportIcao, dataType, typeRef)
    }

    private fun sendStateJson(airportIcao: String, resource: String, sharedStateJson: SharedStateJson<*>) {
        val json = objectMapper.writeValueAsString(sharedStateJson)

        val request = baseApiRequest(airportIcao, resource)
            .post(json.toRequestBody(JSON))
            .build()

        val response = httpClient.newCall(request).execute().use { response ->
            response.body?.string()
        }

        println(response)
    }

    private data class MasterRoleResponse(
        val isMaster: Boolean,
        val currentMaster: String? = null,
        val sessionId: String? = null
    )

    private fun baseApiRequest(airportIcao: String, resource: String): Request.Builder {
        return Request.Builder()
            .url("$BASE_URL/api/v1/airports/$airportIcao/$resource")
            .header(SESSION_ID_HEADER, clientUuid)
            .header(CLIENT_VERSION_HEADER, clientVersion)
    }
}