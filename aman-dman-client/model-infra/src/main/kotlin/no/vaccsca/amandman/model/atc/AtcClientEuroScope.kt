package no.vaccsca.amandman.model.atc

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.ClientVersion
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.atc.euroscope.*
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.integration.IntegrationStatus
import no.vaccsca.amandman.model.integration.IntegrationStatusState
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class AtcClientEuroScope(
    private val controllerInfoCallback: ((ControllerInfoData) -> Unit),
    private val onVersionMismatch: ((clientVersion: String, pluginVersion: String) -> Unit)? = null,
    private val onAircraftSelectionChanged: ((String) -> Unit)? = null,
    private val settingsProvider: SettingsProvider,
    private val host: String = settingsProvider.getSettings(reload = true).connectionConfig.atcClient.host,
    private val port: Int = settingsProvider.getSettings(reload = true).connectionConfig.atcClient.port ?: 12345,
) : AtcClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    private var isRunning = false
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: InputStreamReader? = null
    private var isConnected = false
    private var isVersionValidated = false
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, exception ->
        logger.error("Unhandled exception in AtcClientEuroScope coroutine: ${exception.message}", exception)
    })
    private val arrivalCallbacks = mutableMapOf<String, (List<AtcClientArrivalData>) -> Unit>()
    private val departuresCallbacks = mutableMapOf<String, (List<AtcClientDepartureData>) -> Unit>()
    private val runwayStatusCallbacks = mutableMapOf<String, (List<AtcClientRunwaySelectionData>) -> Unit>()
    private val latestMovementTimestampByAirport = ConcurrentHashMap<String, kotlinx.datetime.Instant>()
    private val loadingUntilByAirport = ConcurrentHashMap<String, kotlinx.datetime.Instant>()
    private val errorStatusByAirport = ConcurrentHashMap<String, IntegrationStatus>()

    private val objectMapper = jacksonObjectMapper().apply {
        // Configure Jackson for large messages
        factory.configure(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING, true)
    }

    val isClientConnected: Boolean
        get() = isConnected

    override fun start(onControllerInfoData: (ControllerInfoData) -> Unit) {
        if (isRunning) return

        // Reset state and create a new scope if needed
        if (scope.isActive.not()) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, exception ->
                logger.error("Unhandled exception in AtcClientEuroScope coroutine: ${exception.message}", exception)
            })
        }

        isRunning = true
        scope.launch {
            while (isRunning) {
                if (!isConnected) {
                    markAllSubscribedAirportsLoading()
                    logger.info("Attempting to connect to $host:$port")
                    try {
                        socket = Socket(host, port)

                        // Configure socket for large messages
                        socket!!.receiveBufferSize = 256 * 1024 // 256KB receive buffer
                        socket!!.sendBufferSize = 64 * 1024    // 64KB send buffer
                        socket!!.tcpNoDelay = true             // Disable Nagle's algorithm for faster small message sending

                        writer = OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8)
                        reader = InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8)
                        isConnected = true
                        logger.info("Connected to $host:$port (buffers: recv=${socket!!.receiveBufferSize}, send=${socket!!.sendBufferSize})")

                        onConnectionEstablished()
                        launch { receiveMessages() }
                    } catch (e: Exception) {
                        logger.info("Connection to EuroScope failed (${e.message}). Will try again.")
                        delay(5000)
                    }
                } else {
                    delay(5000)
                }
            }
        }
    }

    override fun collectDataFor(
        airportIcao: String,
        onArrivalsReceived: (List<AtcClientArrivalData>) -> Unit,
        onDeparturesReceived: (List<AtcClientDepartureData>) -> Unit,
        onRunwaySelectionChanged: (List<AtcClientRunwaySelectionData>) -> Unit,
    ) {
        runwayStatusCallbacks[airportIcao] = onRunwaySelectionChanged
        arrivalCallbacks[airportIcao] = onArrivalsReceived
        departuresCallbacks[airportIcao] = onDeparturesReceived
        markAirportLoading(airportIcao)

        reSubscribeToAllAirports()
    }

    override fun stopCollectingMovementsFor(airportIcao: String) {
        // Send unregister message to the server
        logger.info("Unsubscribing from EuroScope data for airport $airportIcao")
        sendMessage(UnregisterAirportJson(airportIcao))

        // Clean up local callbacks for this airport
        runwayStatusCallbacks.remove(airportIcao)
        arrivalCallbacks.remove(airportIcao)
        departuresCallbacks.remove(airportIcao)
        latestMovementTimestampByAirport.remove(airportIcao)
        loadingUntilByAirport.remove(airportIcao)
        errorStatusByAirport.remove(airportIcao)
    }

    override fun assignRunway(callsign: String, newRunway: String) {
        logger.info("Assigning runway $callsign to $newRunway")
        sendMessage(
            AssignRunwayJson(
                callsign = callsign,
                runway = newRunway
            )
        )
    }

    override fun showPolygon(
        label: String,
        boundary: List<LatLng>,
        color: String,
        lineWidth: Int,
        fillColor: String?,
        durationSeconds: Int
    ) {
        logger.info("Sending polygon '$label' to EuroScope (color=$color, lineWidth=$lineWidth, fillColor=$fillColor, duration=${durationSeconds}s)")
        sendMessage(
            ShowPolygonJson(
                label = label,
                boundary = boundary.map { CoordinateJson(it.lat, it.lon) },
                color = color,
                lineWidth = lineWidth,
                fillColor = fillColor,
                durationSeconds = durationSeconds
            )
        )
    }

    override fun getIntegrationStatus(airportIcao: String): IntegrationStatus {
        errorStatusByAirport[airportIcao]?.let { return it }

        if (airportIcao !in arrivalCallbacks.keys && airportIcao !in departuresCallbacks.keys) {
            return IntegrationStatus(
                state = IntegrationStatusState.ERROR,
                detail = "ATC not subscribed for airport $airportIcao"
            )
        }

        val now = NtpClock.now()
        val shouldFlash = loadingUntilByAirport[airportIcao]?.let { now < it } ?: false
        val latestMovement = latestMovementTimestampByAirport[airportIcao]

        val state = when {
            !isRunning -> IntegrationStatusState.ERROR
            !isConnected || !isVersionValidated -> IntegrationStatusState.LOADING
            latestMovement == null -> IntegrationStatusState.LOADING
            now - latestMovement > 5.seconds -> IntegrationStatusState.LOADING
            else -> IntegrationStatusState.OK
        }

        return IntegrationStatus(
            state = state,
            updatedAt = now,
            shouldFlash = shouldFlash,
            detail = when (state) {
                IntegrationStatusState.OK -> "ATC data received"
                IntegrationStatusState.LOADING -> "Waiting for recent ATC data"
                IntegrationStatusState.ERROR -> "ATC client not running"
            }
        )
    }

    override fun close() {
        try {
            logger.info("Closing AtcClientEuroScope...")
            isRunning = false
            isConnected = false

            // Cancel all coroutines in the scope
            scope.cancel()

            // Synchronized block to safely close the socket and streams
            synchronized(this) {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    logger.error("Error closing socket: ${e.message}", e)
                } finally {
                    socket = null
                }

                try {
                    writer?.close()
                } catch (e: Exception) {
                    logger.error("Error closing writer: ${e.message}", e)
                } finally {
                    writer = null
                }

                try {
                    reader?.close()
                } catch (e: Exception) {
                    logger.error("Error closing reader: ${e.message}", e)
                } finally {
                    reader = null
                }
            }
            markAllSubscribedAirportsError("ATC client closed")
        } catch (e: Exception) {
            logger.error("Error closing connection: ${e.message}", e)
        }
    }

    private fun onConnectionEstablished() {
        isVersionValidated = false
        // A reconnect gets a fresh socket; re-register all airport subscriptions.
        reSubscribeToAllAirports()
    }

    private fun reSubscribeToAllAirports() {
        (arrivalCallbacks + departuresCallbacks).keys.toSet().forEach { airportIcao ->
            logger.info("Requesting data from EuroScope for airport $airportIcao")
            sendMessage(
                RegisterAirportJson(
                    icao = airportIcao
                )
            )
        }
    }

    private fun sendMessage(message: MessageToEuroScopePluginJson) {
        try {
            val jsonMessage = objectMapper.writeValueAsString(message)
            writer?.write(jsonMessage + "\n")
            writer?.flush()
        } catch (e: Exception) {
            logger.error("Failed to send message: ${e.message}", e)
        }
    }

    private suspend fun receiveMessages() {
        val buffer = CharArray(1024 * 64)
        var messageBuffer = StringBuilder()

        while (isRunning) {
            try {
                val bytesRead = reader?.read(buffer) ?: -1
                if (bytesRead <= 0) {
                    isConnected = false
                    resetConnectionResources()
                    return
                }

                val messageChunk = String(buffer, 0, bytesRead)
                messageBuffer.append(messageChunk)

                val messages = messageBuffer.split("\n")
                if (messages.isNotEmpty()) {
                    for (i in 0 until messages.size - 1) {
                        val message = messages[i]
                        if (message.isNotBlank()) {
                            handleIncomingMessage(message)
                        }
                    }

                    // Keep the last partial message (if any)
                    messageBuffer = StringBuilder(messages.last())
                }
            } catch (e: SocketException) {
                logger.error("SocketException: ${e.message}")
                isConnected = false
                resetConnectionResources()
                return
            } catch (e: SocketTimeoutException) {
                logger.error("SocketTimeoutException: ${e.message}")
                isConnected = false
                resetConnectionResources()
                return
            } catch (e: Exception) {
                logger.error("Unexpected error: ${e.message}")
                isConnected = false
                resetConnectionResources()
                return
            }
        }
    }

    private fun handleIncomingMessage(message: String) {
        try {
            when (val messageObj = objectMapper.readValue(message, MessageFromEuroScopePluginJson::class.java)) {
                is PluginVersionJson -> {
                    logger.info("Received plugin version: ${messageObj.version}")
                    if (!isVersionValidated) {
                        val clientVersion = ClientVersion.value
                        if (messageObj.version != clientVersion) {
                            markAllSubscribedAirportsError("ATC version mismatch")
                            onVersionMismatch?.invoke(clientVersion, messageObj.version)
                            close()
                            return
                        }
                        isVersionValidated = true
                    }
                }

                is ArrivalsUpdateFromEuroScopePluginJson -> {
                    val arrivals = messageObj.inbounds.mapNotNull { arrival ->
                        arrival.toDomain().also {
                            if (it == null) {
                                logger.warn("Failed to parse arrival data for ${arrival.callsign}")
                            }
                        }
                    }
                    val grouped = arrivals.groupBy { it.arrivalAirportIcao }
                    grouped.forEach { (icao, list) ->
                        markAirportDataReceived(icao)
                        arrivalCallbacks[icao]?.invoke(list)
                    }
                }

                is DeparturesUpdateFromEuroScopePluginJson -> {
                    val departures = messageObj.outbounds.mapNotNull { it.toDomain() }
                    val grouped = departures.groupBy { it.departureIcao }
                    grouped.forEach { (icao, list) ->
                        markAirportDataReceived(icao)
                        departuresCallbacks[icao]?.invoke(list)
                    }
                }

                is RunwayStatusesUpdateFromEuroScopePluginJson -> {
                    messageObj.airports.forEach { (icao, runways) ->
                        val list = runways.map { (runway, status) ->
                            AtcClientRunwaySelectionData(
                                runway = runway,
                                allowArrivals = status.arrivals,
                                allowDepartures = status.departures
                            )
                        }
                        runwayStatusCallbacks[icao]?.invoke(list)
                    }
                }

                is ControllerInfoFromEuroScopePluginJson -> {
                    val info = ControllerInfoData(
                        callsign = messageObj.me.callsign,
                        positionId = messageObj.me.positionId,
                        facilityType = messageObj.me.facilityType?.toString()
                    )
                    controllerInfoCallback(info)
                }

                is AircraftSelectionFromEuroScopePluginJson -> {
                    onAircraftSelectionChanged?.invoke(messageObj.callsign)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse message: ${e.message}")
        }
    }

    private fun DepartureJson.toDomain(): AtcClientDepartureData? {
        return try {
            AtcClientDepartureData(
                departureIcao = departureAirportIcao,
                callsign = callsign,
                icaoType = icaoType,
                assignedSid = sid,
                trackingController = trackingController,
                scratchPad = scratchPad,
                assignedRunway = runway,
                wakeCategory = wakeCategory,
                recvTimestamp = NtpClock.now(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse departure data for $callsign: ${e.message}")
            null
        }
    }

    private fun markAirportLoading(airportIcao: String) {
        loadingUntilByAirport[airportIcao] = NtpClock.now() + 2.seconds
        errorStatusByAirport.remove(airportIcao)
    }

    private fun markAllSubscribedAirportsLoading() {
        (arrivalCallbacks.keys + departuresCallbacks.keys + runwayStatusCallbacks.keys).forEach { markAirportLoading(it) }
    }

    private fun markAirportDataReceived(airportIcao: String) {
        latestMovementTimestampByAirport[airportIcao] = NtpClock.now()
        errorStatusByAirport.remove(airportIcao)
    }

    private fun markAllSubscribedAirportsError(detail: String) {
        val now = NtpClock.now()
        (arrivalCallbacks.keys + departuresCallbacks.keys + runwayStatusCallbacks.keys).forEach { airportIcao ->
            errorStatusByAirport[airportIcao] = IntegrationStatus(
                state = IntegrationStatusState.ERROR,
                updatedAt = now,
                detail = detail
            )
        }
    }

    private fun resetConnectionResources() {
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        reader = null
        writer = null
        socket = null
    }
}

internal fun ArrivalJson.toDomain(receivedAt: kotlinx.datetime.Instant = NtpClock.now()): AtcClientArrivalData? {
    return try {
        val extractedRoute = route.map { point -> point.toDomain() }
        AtcClientArrivalData(
            callsign = callsign,
            icaoType = icaoType,
            assignedStar = assignedStar,
            assignedDirect = assignedDirect,
            trackingController = trackingController,
            scratchPad = scratchPad,
            currentPosition = AircraftPosition(
                latLng = LatLng(latitude, longitude),
                flightLevel = flightLevel,
                altitudeFt = pressureAltitude,
                groundspeedKts = groundSpeed,
                trackDeg = track
            ),
            extractedRoute = extractedRoute,
            remainingWaypoints = extractedRoute
                .filter { it.isActive }
                .map { Waypoint(it.id, it.latLng) },
            assignedRunway = assignedRunway,
            arrivalAirportIcao = arrivalAirportIcao,
            flightPlanTas = flightPlanTas,
            recvTimestamp = receivedAt,
        )
    } catch (_: Exception) {
        null
    }
}

internal fun FixPointJson.toDomain(): ExtractedRoutePoint = ExtractedRoutePoint(
    id = name,
    latLng = LatLng(latitude, longitude),
    isActive = isActive,
)
