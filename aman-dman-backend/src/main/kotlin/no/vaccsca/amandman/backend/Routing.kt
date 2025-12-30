package no.vaccsca.amandman.backend

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.server.application.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import no.vaccsca.amandman.backend.data.dto.atcClient.*
import no.vaccsca.amandman.backend.data.dto.sharedState.SharedStateEventJson
import no.vaccsca.amandman.backend.data.integration.AtcClientDataMapper.facilityTypeToString
import no.vaccsca.amandman.backend.data.integration.AtcClientDataMapper.toArrival
import no.vaccsca.amandman.backend.data.integration.AtcClientDataMapper.toDeparture
import no.vaccsca.amandman.backend.data.integration.AtcClientDataMapper.toRunwayStatus
import no.vaccsca.amandman.backend.data.repository.SettingsRepository
import no.vaccsca.amandman.backend.domain.PlannerManager
import no.vaccsca.amandman.backend.domain.service.DataUpdateListener
import no.vaccsca.amandman.backend.domain.service.PlannerService
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayDelayEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.util.KotlinxInstantModule
import java.util.concurrent.ConcurrentHashMap

val objectMapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
    registerModule(JavaTimeModule())
    registerModule(KotlinxInstantModule)
    findAndRegisterModules()
}

fun <T> toJson(frame: T): String {
    return objectMapper.writeValueAsString(frame)
}

// Registry to track slave listeners per airport so master can close them when it disconnects
private val slaveListeners: ConcurrentHashMap<String, MutableSet<WsClientDataUpdateListener>> = ConcurrentHashMap()

fun Application.configureRouting(
    plannerManager: PlannerManager
) {

    suspend fun handleMessage(service: PlannerService, messageFromServerJson: MessageFromServerJson) {
        when (messageFromServerJson) {
            is ArrivalsUpdateFromServerJson -> {
                messageFromServerJson.inbounds.groupBy { it.arrivalAirportIcao }.forEach { (arrivalAirportIcao, arrivals) ->
                    service.onArrivalsUpdateFromAtcClient(arrivals.map { it.toArrival() })
                }
            }
            is DeparturesUpdateFromServerJson -> {
                messageFromServerJson.outbounds.groupBy { it.departureAirportIcao }.forEach { (departureAirportIcao, departures) ->
                    service.onDeparturesUpdateFromAtcClient(departures.map { it.toDeparture() })
                }
            }
            is RunwayStatusesUpdateFromServerJson -> {
                messageFromServerJson.airports.forEach { (airportIcao, statusesJson) ->
                    val statuses = statusesJson.map { (name, statusJson) -> statusJson.toRunwayStatus(name) }
                    service.onRunwaySelectionChanged(statuses)
                }
            }
            is ControllerInfoFromServerJson -> {
                val infoData = ControllerInfoData(
                    callsign = messageFromServerJson.me.callsign,
                    positionId = messageFromServerJson.me.positionId,
                    facilityType = facilityTypeToString(messageFromServerJson.me.facilityType),
                )
                // TODO
                println("TODO: handle controller info: $infoData")
            }
        }
    }

    routing {
        webSocket("/ws/airport/{code}/master") {
            val code = call.parameters["code"]?.uppercase()
            if (code == null) {
                close()
                return@webSocket
            }

            val existingService = plannerManager.getServiceForAirport(code)

            if (existingService != null) {
                println("Failed to acquire master role for airport $code")
                close()
                return@webSocket
            }

            val plannerService = plannerManager.createServiceForAirport(code)

            runCatching {
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val message: MessageFromServerJson = objectMapper.readValue(frame.readText(), MessageFromServerJson::class.java)
                            handleMessage(plannerService, message)
                        }
                        else -> {
                            // ignore other frames
                        }
                    }
                }

            }.onFailure { e ->
                when (e) {
                    is ClosedReceiveChannelException -> {
                        println("WebSocket closed normally: ${closeReason.await()}")
                        closeReason.await()
                    }
                    is Throwable -> {
                        println("WebSocket closed with error: ${e.message}")
                        closeReason.await()
                    }
                }
            }.also {
                // Master disconnected: close all slave sessions for this airport, then unregister service
                val listeners = slaveListeners.remove(code)
                if (!listeners.isNullOrEmpty()) {
                    listeners.forEach { listener ->
                        // close each slave session; do it in separate coroutines so we don't get blocked
                        launch {
                            try {
                                listener.closeSession(CloseReason(CloseReason.Codes.NORMAL, "master disconnected"))
                            } catch (e: Exception) {
                                println("Failed to close slave session: ${e.message}")
                            }
                        }
                    }
                }
                plannerManager.unregisterService(code)
            }
        }

        webSocket("/ws/airport/{code}/slave") {
            val code = call.parameters["code"]?.uppercase()
            if (code == null) {
                close()
                return@webSocket
            }

            val messageResponseFlow = MutableSharedFlow<Int>()
            val sharedFlow = messageResponseFlow.asSharedFlow()

            // Keep a reference to the service so we can remove the listener on disconnect
            val service = plannerManager.getServiceForAirport(code)
            if (service == null) {
                println("No planner service available for airport $code")
                close()
                return@webSocket
            }

            val listener = WsClientDataUpdateListener(this)
            service.addDataUpdateListener(listener)

            // register listener for master-driven closure
            slaveListeners.compute(code) { _, existing ->
                val set = existing ?: mutableSetOf()
                synchronized(set) { set.add(listener) }
                set
            }

            val job = launch {
                sharedFlow.collect { message ->
                    println("Sending message to client: $message")
                    //send(message)
                }
            }

            try {
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            // Optionally handle client messages here
                        }
                        else -> {
                            // ignore other frames
                        }
                    }
                }
            } catch (e: Throwable) {
                when (e) {
                    is ClosedReceiveChannelException -> {
                        println("WebSocket closed normally: ${closeReason.await()}")
                        closeReason.await()
                    }
                    else -> {
                        println("WebSocket closed with error: ${e.message}")
                        closeReason.await()
                    }
                }
            } finally {
                // Always cancel the job and remove the listener when the client disconnects
                job.cancel()
                try {
                    service.removeDataUpdateListener(listener)
                } catch (e: Exception) {
                    println("Failed to remove data update listener: ${e.message}")
                }

                // remove from registry
                slaveListeners.computeIfPresent(code) { _, set ->
                    synchronized(set) { set.remove(listener) }
                    if (set.isEmpty()) null else set
                }
            }
        }

        get("/airports") {
            val airports = SettingsRepository.getAirportData()
            call.respondText(toJson(airports))
        }
    }
}


// Update listener to expose closeSession; keep Jackson usage
class WsClientDataUpdateListener(
    private val session: DefaultWebSocketSession
) : DataUpdateListener {

    companion object {
        private val json = objectMapper
    }

    override suspend fun onLiveData(
        airportIcao: String,
        timelineEvents: List<TimelineEvent>
    ) {
        val payload = timelineEvents.map {
            val type = when (it) {
                is RunwayArrivalEvent -> "runwayArrival"
                is DepartureEvent -> "runwayDeparture"
                is RunwayDelayEvent -> "runwayDelay"
            }
            SharedStateEventJson(type = type, event = it)
        }
        session.sendAsJson("events", payload)
    }

    override suspend fun onRunwayModesUpdated(
        airportIcao: String,
        runwayStatuses: Map<String, RunwayStatus>
    ) {
        session.sendAsJson("runwayStatuses", runwayStatuses)
    }

    override suspend fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        session.sendAsJson("minimumSpacing", minimumSpacingNm)
    }

    override suspend fun onWeatherDataUpdated(
        airportIcao: String,
        data: VerticalWeatherProfile?
    ) {
        session.sendAsJson("weather", data)
    }

    // allow master to close slave sessions
    suspend fun closeSession(reason: CloseReason) {
        try {
            session.close(reason)
        } catch (e: Exception) {
            // ignore
        }
    }

    private suspend fun <T> WebSocketSession.sendAsJson(type: String, frame: T) {
        json.writeValueAsString(MessageWrapper(type = type, data = frame)).let {
            try {
                send(Frame.Text(it))
            } catch (e: Exception) {
                println("Failed to send websocket message: ${e.message}")
            }
        }
    }

    private data class MessageWrapper<T>(
        val type: String,
        val time: Instant = NtpClock.now(),
        val data: T
    )
}
