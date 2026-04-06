package no.vaccsca.amandman.model.sharedstate

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.integration.IntegrationStatus
import no.vaccsca.amandman.model.integration.IntegrationStatusState
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.timeline.event.timeline.DepartureEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayDelayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.ClientVersion
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.UUID.randomUUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun createSharedStateObjectMapper(): ObjectMapper =
    ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        registerModule(KotlinxInstantModule)
        registerModule(KotlinDurationModule)
        findAndRegisterModules()
    }

private object KotlinxInstantModule : SimpleModule() {
    init {
        addSerializer(Instant::class.java, KotlinxInstantSerializer)
        addDeserializer(Instant::class.java, KotlinxInstantDeserializer)
    }
}

private object KotlinxInstantSerializer : JsonSerializer<Instant>() {
    override fun serialize(value: Instant, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}

private object KotlinxInstantDeserializer : JsonDeserializer<Instant>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant {
        return Instant.parse(p.text)
    }
}

private object KotlinDurationModule : SimpleModule() {
    init {
        addSerializer(Duration::class.java, KotlinDurationSerializer)
        addDeserializer(Duration::class.java, KotlinDurationDeserializer)
    }
}

private object KotlinDurationSerializer : JsonSerializer<Duration>() {
    override fun serialize(value: Duration, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toIsoString())
    }
}

private object KotlinDurationDeserializer : JsonDeserializer<Duration>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Duration {
        return Duration.parseIsoString(p.text)
    }
}

class MasterSlaveSharedStateHttpClient(
    private val settingsProvider: SettingsProvider,
    private val httpClient: OkHttpClient = OkHttpClient()
) : MasterSlaveSharedState {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val clientUuid = randomUUID().toString()
    private val objectMapper = createSharedStateObjectMapper()

    private val SESSION_ID_HEADER = "x-session-uuid"
    private val CLIENT_VERSION_HEADER = "x-client-version"
    private val JSON = "application/json".toMediaType()
    private val BASE_URL: String = settingsProvider.getSettings(reload = true).connectionConfig.api.host
    private val clientVersion = ClientVersion.value
    private val statusByAirport = ConcurrentHashMap<String, IntegrationStatus>()
    private val loadingUntilByAirport = ConcurrentHashMap<String, Instant>()

    override fun hasMasterRoleStatus(airportIcao: String): Boolean {
        markLoading(airportIcao)
        val request = baseApiRequest(airportIcao, "master-role")
            .header(SESSION_ID_HEADER, clientUuid)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                markError(airportIcao, "Master role status HTTP ${response.code}")
                return false
            }

            val body = response.body.string()

            return try {
                val masterResp = objectMapper.readValue(body, MasterRoleResponse::class.java)
                markOk(airportIcao, "Master role status received")
                masterResp.isMaster
            } catch (ex: Exception) {
                logger.error("Failed to parse master role response: $body", ex)
                markError(airportIcao, "Failed to parse master role response")
                false
            }
        }
    }

    override fun acquireMasterRole(airportIcao: String): Boolean {
        markLoading(airportIcao, flash = true)
        val request = baseApiRequest(airportIcao, "master-role")
            .post("".toRequestBody()) // Empty body
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                markOk(airportIcao, "Master role acquired")
                return true
            }
            markError(airportIcao, "Acquire master role failed HTTP ${response.code}")
            return false
        }
    }

    override fun releaseMasterRole(airportIcao: String) {
        markLoading(airportIcao, flash = true)
        val request = baseApiRequest(airportIcao, "master-role")
            .delete()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                markOk(airportIcao, "Master role released")
            } else {
                markError(airportIcao, "Release master role failed HTTP ${response.code}")
            }
        }
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

    override fun sendFeederFixState(airportIcao: String, feederFixState: FeederFixState) {
        val sharedStateJson = SharedStateJson(
            lastUpdate = NtpClock.now(),
            data = feederFixState
        )
        sendStateJson(airportIcao, "feeder-fixes", sharedStateJson)
    }

    override fun getFeederFixState(airportIcao: String): FeederFixState {
        val typeRef = object : TypeReference<SharedStateJson<FeederFixState>>() {}
        val sharedState = fetchStateJsonOrNull(airportIcao, "feeder-fixes", typeRef)
        return sharedState?.data ?: FeederFixState()
    }

    override fun getIntegrationStatus(airportIcao: String): IntegrationStatus {
        val now = NtpClock.now()
        val base = statusByAirport[airportIcao]
            ?: IntegrationStatus(IntegrationStatusState.ERROR, detail = "No server status yet")
        val shouldFlash = loadingUntilByAirport[airportIcao]?.let { now < it } ?: false
        return base.copy(shouldFlash = shouldFlash)
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
        markLoading(airportIcao)
        val jsonBody = objectMapper.writeValueAsString(data)
        val request = baseApiRequest(airportIcao, endpoint)
            .post(jsonBody.toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn("Failed to send state $endpoint to server. Status: ${response.code}")
                markError(airportIcao, "Send $endpoint failed HTTP ${response.code}")
            } else {
                markOk(airportIcao, "Sent $endpoint")
            }
        }
    }

    private fun <T> fetchStateJson(airportIcao: String, endpoint: String, typeRef: TypeReference<T>): T {
        markLoading(airportIcao)
        val request = baseApiRequest(airportIcao, endpoint)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                markError(airportIcao, "Fetch $endpoint failed HTTP ${response.code}")
                throw IllegalStateException("Fetch $endpoint failed HTTP ${response.code}")
            }
            val body = response.body.string()
            return try {
                val parsed = objectMapper.readValue(body, typeRef)
                markOk(airportIcao, "Fetched $endpoint")
                parsed
            } catch (e: Exception) {
                markError(airportIcao, "Parse $endpoint failed: ${e.message}")
                throw e
            }
        }
    }

    private fun <T> fetchStateJsonOrNull(airportIcao: String, endpoint: String, typeRef: TypeReference<T>): T? {
        markLoading(airportIcao)
        val request = baseApiRequest(airportIcao, endpoint)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // Backward compatibility: old servers don't expose this endpoint.
                    markOk(airportIcao, "Endpoint $endpoint not available")
                    return null
                }
                markError(airportIcao, "Fetch $endpoint failed HTTP ${response.code}")
                throw IllegalStateException("Fetch $endpoint failed HTTP ${response.code}")
            }
            val body = response.body.string()
            return try {
                val parsed = objectMapper.readValue(body, typeRef)
                markOk(airportIcao, "Fetched $endpoint")
                parsed
            } catch (e: Exception) {
                markError(airportIcao, "Parse $endpoint failed: ${e.message}")
                throw e
            }
        }
    }

    private fun baseApiRequest(airportIcao: String, endpoint: String): Request.Builder {
        return Request.Builder()
            .url("$BASE_URL/api/v1/airports/$airportIcao/$endpoint")
            .header(SESSION_ID_HEADER, clientUuid)
            .header(CLIENT_VERSION_HEADER, clientVersion)
    }

    private fun markLoading(airportIcao: String, flash: Boolean = false) {
        if (flash) {
            loadingUntilByAirport[airportIcao] = NtpClock.now() + 2.seconds
        }
        statusByAirport[airportIcao] = IntegrationStatus(
            state = IntegrationStatusState.LOADING,
            detail = "Contacting server"
        )
    }

    private fun markOk(airportIcao: String, detail: String) {
        statusByAirport[airportIcao] = IntegrationStatus(
            state = IntegrationStatusState.OK,
            detail = detail
        )
    }

    private fun markError(airportIcao: String, detail: String) {
        statusByAirport[airportIcao] = IntegrationStatus(
            state = IntegrationStatusState.ERROR,
            detail = detail
        )
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
