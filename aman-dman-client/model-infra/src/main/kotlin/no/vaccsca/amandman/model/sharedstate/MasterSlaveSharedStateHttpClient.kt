package no.vaccsca.amandman.model.sharedstate

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.timeline.event.timeline.DepartureEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayDelayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.UUID.randomUUID
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

class MasterSlaveSharedStateHttpClient(
    private val settingsProvider: SettingsProvider,
    private val httpClient: OkHttpClient = OkHttpClient()
) : MasterSlaveSharedState {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val clientUuid = randomUUID().toString()
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        registerModule(KotlinxInstantModule)
        findAndRegisterModules()
    }

    private val SESSION_ID_HEADER = "x-session-uuid"
    private val CLIENT_VERSION_HEADER = "x-client-version"
    private val JSON = "application/json".toMediaType()
    private val BASE_URL: String = settingsProvider.getSettings(reload = true).connectionConfig.api.host
    private val clientVersion = object {}.javaClass.`package`.implementationVersion

    override fun hasMasterRoleStatus(airportIcao: String): Boolean {
        val request = baseApiRequest(airportIcao, "master-role")
            .header(SESSION_ID_HEADER, clientUuid)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false

            val body = response.body.string()

            return try {
                val masterResp = objectMapper.readValue(body, MasterRoleResponse::class.java)
                masterResp.isMaster
            } catch (ex: Exception) {
                logger.error("Failed to parse master role response: $body", ex)
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

    override fun checkVersionCompatibility(): VersionCompatibilityResult {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/compat")
            .header(CLIENT_VERSION_HEADER, clientVersion)
            .get()
            .build()

        val response = httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            objectMapper.readValue(body, CompatibilityCheckJson::class.java)
        }

        return VersionCompatibilityResult(
            isCompatible = response.status != VersionStatus.UPDATE_REQUIRED,
            requiredVersion = response.minClientVersion,
            newestVersion = response.latestClientVersion,
            currentVersion = clientVersion
        )
    }

    override fun sendNonSequencedList(
        airportIcao: String,
        nonSequencedList: List<NonSequencedEvent>
    ) {
        val sharedStateJson = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = nonSequencedList
        )
        sendStateJson(airportIcao, "non-sequenced", sharedStateJson)
    }

    override fun getNonSequencedList(airportIcao: String): List<NonSequencedEvent> {
        val typeRef = object : TypeReference<SharedStateJson<List<NonSequencedEvent>>>() {}
        val sharedState = fetchStateJson(airportIcao, "non-sequenced", typeRef)
        return sharedState.data
    }

    override fun sendTimelineEvents(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        val events = timelineEvents.map { event ->
            val type = when (event) {
                is RunwayArrivalEvent -> "runwayArrival"
                is DepartureEvent -> "runwayDeparture"
                is RunwayDelayEvent -> "runwayDelay"
                else -> throw IllegalArgumentException("Unknown event type: ${event::class}")
            }
            SharedStateTimelineEventJson(type = type, event = event)
        }

        val sharedState = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = events
        )

        sendStateJson(airportIcao, "events", sharedState)
    }

    override fun getTimelineEvents(airportIcao: String): List<TimelineEvent> {
        val typeRef = object : TypeReference<SharedStateJson<List<SharedStateTimelineEventJson>>>() {}
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

    private fun sendStateJson(airportIcao: String, endpoint: String, data: Any) {
        val jsonBody = objectMapper.writeValueAsString(data)
        val request = baseApiRequest(airportIcao, endpoint)
            .post(jsonBody.toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn("Failed to send state $endpoint to server. Status: ${response.code}")
            }
        }
    }

    private fun <T> fetchStateJson(airportIcao: String, endpoint: String, typeRef: TypeReference<T>): T {
        val request = baseApiRequest(airportIcao, endpoint)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            return objectMapper.readValue(body, typeRef)
        }
    }

    private fun baseApiRequest(airportIcao: String, endpoint: String): Request.Builder {
        return Request.Builder()
            .url("$BASE_URL/api/v1/airports/$airportIcao/$endpoint")
            .header(SESSION_ID_HEADER, clientUuid)
            .header(CLIENT_VERSION_HEADER, clientVersion)
    }

    private data class MasterRoleResponse(
        val isMaster: Boolean
    )

    private object KotlinxInstantModule : SimpleModule() {
        init {
            addSerializer(Instant::class.java, KotlinxInstantSerializer)
            addDeserializer(Instant::class.java, KotlinxInstantDeserializer)
        }
    }

    private object KotlinxInstantSerializer : com.fasterxml.jackson.databind.JsonSerializer<Instant>() {
        override fun serialize(value: Instant, gen: JsonGenerator, serializers: com.fasterxml.jackson.databind.SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private object KotlinxInstantDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<Instant>() {
        override fun deserialize(p: JsonParser, ctxt: com.fasterxml.jackson.databind.DeserializationContext): Instant {
            return Instant.parse(p.text)
        }
    }

    private data class CompatibilityCheckJson(
        val apiVersion: String,
        val latestClientVersion: String,
        val minClientVersion: String,
        val status: VersionStatus,
    )

    private enum class VersionStatus {
        OK,
        UPDATE_REQUIRED,
        UPDATE_RECOMMENDED,
    }
}
