package integration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.util.KotlinxInstantModule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocketListener
import kotlin.time.Duration.Companion.milliseconds

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = WeatherProfileJson::class, name = "weather"),
    JsonSubTypes.Type(value = TimelineEventsJson::class, name = "events"),
    JsonSubTypes.Type(value = RunwayStatusesJson::class, name = "runwayStatuses"),
    JsonSubTypes.Type(value = MinimumSpacingJson::class, name = "minimumSpacing"),
)
sealed class MessageFromServerJson()

data class TimelineEventsJson(
    val data: List<TimelineEvent>,
    val time: Instant,
) : MessageFromServerJson()

data class WeatherProfileJson(
    val data: VerticalWeatherProfile,
    val time: Instant,
) : MessageFromServerJson()

data class RunwayStatusesJson(
    val data: Map<String, RunwayStatus>,
    val time: Instant,
) : MessageFromServerJson()

data class MinimumSpacingJson(
    val data: Double,
    val time: Instant,
) : MessageFromServerJson()

class WebsocketNetworkClient(
    val airportIcao: String,
    val networkEventHandler: NetworkEventHandlerInterface
) : NetworkClientInterface {

    val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        registerModule(KotlinxInstantModule)
        findAndRegisterModules()
    }

    private var socket: okhttp3.WebSocket? = null

    val webSocketListener: WebSocketListener = object : WebSocketListener() {
        override  fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
            networkEventHandler.onConnected()
            socket = webSocket
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
            val message: MessageFromServerJson = objectMapper.readValue(
                text,
                MessageFromServerJson::class.java
            )
            suspend {
                when (message) {
                    is TimelineEventsJson ->  networkEventHandler.onLiveData(message.data)
                    is WeatherProfileJson -> networkEventHandler.onWeatherDataUpdated(message.data)
                    is RunwayStatusesJson -> networkEventHandler.onRunwayModesUpdated(message.data)
                    is MinimumSpacingJson -> networkEventHandler.onMinimumSpacingUpdated(message.data)
                }
            }
        }

        override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
             networkEventHandler.onDisconnected()
        }

        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
            networkEventHandler.onError(t)
        }
    }

    val client = OkHttpClient.Builder()
        .readTimeout(0.milliseconds)
        .build()

    fun run() {
        val request: Request = Request.Builder()
            .url("ws://127.0.0.1:3000/ws/airport/$airportIcao/slave")
            .build()

        client.newWebSocket(request, webSocketListener)

        client.dispatcher.executorService.shutdown()
    }

    override fun monitorDescentTrajectoryForAircraft(callSign: String): String {
        TODO("Not yet implemented")
    }

    override fun checkIfScheduledTimeIsAvailable(
        airportIcao: String,
        timelineEvent: TimelineEvent,
        newInstant: Instant
    ) {
        TODO("Not yet implemented")
    }

    override fun setMinimumSpacingForAirport(airportIcao: String, minimumSpacingDistanceNm: Double) {
        TODO("Not yet implemented")
    }

    override fun setSubscribeForDeparturesOption(airportIcao: String, selected: Boolean) {
        TODO("Not yet implemented")
    }
}