package no.vaccsca.amandman.model.atc

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.ClientVersion
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.atc.euroscope.*
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

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
        } catch (e: Exception) {
            logger.error("Error closing connection: ${e.message}", e)
        }
    }

    private fun onConnectionEstablished() {
        isVersionValidated = false
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
                return
            } catch (e: SocketTimeoutException) {
                logger.error("SocketTimeoutException: ${e.message}")
                isConnected = false
                return
            } catch (e: Exception) {
                logger.error("Unexpected error: ${e.message}")
                isConnected = false
                return
            }
        }
    }

    private fun handleIncomingMessage(message: String) {
        try {
            val messageObj = objectMapper.readValue(message, MessageFromEuroScopePluginJson::class.java)

            when (messageObj) {
                is PluginVersionJson -> {
                    logger.info("Received plugin version: ${messageObj.version}")
                    if (!isVersionValidated) {
                        val clientVersion = ClientVersion.value
                        if (messageObj.version != clientVersion) {
                            onVersionMismatch?.invoke(clientVersion, messageObj.version)
                            close()
                            return
                        }
                        isVersionValidated = true
                    }
                }

                is ArrivalsUpdateFromEuroScopePluginJson -> {
                    val arrivals = messageObj.inbounds.mapNotNull { it.toDomain() }
                    val grouped = arrivals.groupBy { it.arrivalAirportIcao }
                    grouped.forEach { (icao, list) ->
                        arrivalCallbacks[icao]?.invoke(list)
                    }
                }

                is DeparturesUpdateFromEuroScopePluginJson -> {
                    val departures = messageObj.outbounds.mapNotNull { it.toDomain() }
                    val grouped = departures.groupBy { it.departureIcao }
                    grouped.forEach { (icao, list) ->
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

    private fun ArrivalJson.toDomain(): AtcClientArrivalData? {
        return try {
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
                remainingWaypoints = route.filter { !it.isPassed }.map { Waypoint(it.name, LatLng(it.latitude, it.longitude)) },
                assignedRunway = assignedRunway,
                arrivalAirportIcao = arrivalAirportIcao,
                flightPlanTas = flightPlanTas,
                recvTimestamp = NtpClock.now(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse arrival data for $callsign: ${e.message}")
            null
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
}
