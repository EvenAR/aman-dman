package no.vaccsca.amandman.model.data.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.JsonFactory
import kotlinx.coroutines.*
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.data.dto.euroscope.AircraftSelectionFromEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.ArrivalJson
import no.vaccsca.amandman.model.data.dto.euroscope.ArrivalsUpdateFromEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.AssignRunwayJson
import no.vaccsca.amandman.model.data.dto.euroscope.ControllerInfoFromEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.DepartureJson
import no.vaccsca.amandman.model.data.dto.euroscope.DeparturesUpdateFromEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.MessageFromEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.MessageToEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.PluginVersionJson
import no.vaccsca.amandman.model.data.dto.euroscope.RegisterAirportJson
import no.vaccsca.amandman.model.data.dto.euroscope.RunwayStatusJson
import no.vaccsca.amandman.model.data.dto.euroscope.RunwayStatusesUpdateFromEuroScopePluginJson
import no.vaccsca.amandman.model.data.dto.euroscope.UnregisterAirportJson
import no.vaccsca.amandman.model.data.repository.SettingsRepository
import no.vaccsca.amandman.model.domain.valueobjects.AircraftPosition
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientArrivalData
import no.vaccsca.amandman.model.domain.valueobjects.LatLng
import no.vaccsca.amandman.model.domain.valueobjects.Waypoint
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientDepartureData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientRunwaySelectionData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import org.slf4j.LoggerFactory
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.minutes

class AtcClientEuroScope(
    private val controllerInfoCallback: ((ControllerInfoData) -> Unit),
    private val onVersionMismatch: ((clientVersion: String, pluginVersion: String) -> Unit)? = null,
    private val onAircraftSelectionChanged: ((String) -> Unit)? = null,
    private val host: String = SettingsRepository.getSettings(reload = true).connectionConfig.atcClient.host,
    private val port: Int = SettingsRepository.getSettings(reload = true).connectionConfig.atcClient.port ?: 12345,
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
            logger.error("Error sending message to EuroScope: ${e.message}", e)
        }
    }

    private suspend fun receiveMessages() {
        var bufferedReader: BufferedReader? = null
        try {
            // Use larger buffer for reading large messages (256KB buffer)
            bufferedReader = BufferedReader(reader, 256 * 1024)

            // Set socket timeout for read operations (60 seconds for better stability)
            socket?.soTimeout = 60000

            logger.info("Starting message receive loop...")
            var messageCount = 0
            var lastMessageTime = NtpClock.now()

            while (isConnected && isRunning) {
                try {
                    val message = bufferedReader.readLine()
                    if (message == null) {
                        logger.warn("readLine() returned null - server closed connection")
                        break
                    }
                    
                    messageCount++
                    lastMessageTime = NtpClock.now()

                    try {
                        val dataPackage = objectMapper.readValue(message, MessageFromEuroScopePluginJson::class.java)
                        handleMessage(dataPackage)
                    } catch (e: Exception) {
                        logger.error("Error parsing JSON message #$messageCount from EuroScope (length: ${message.length}): $message", e)
                        // Continue processing other messages even if one fails
                        continue
                    }
                } catch (e: SocketTimeoutException) {
                    val timeSinceLastMessage = NtpClock.now() - lastMessageTime
                    logger.error("Socket read timeout after ${timeSinceLastMessage}ms - checking connection...")

                    // If no message for more than 2 minutes, consider connection dead
                    if (timeSinceLastMessage > 2.minutes) {
                        logger.error("No messages received for over 2 minutes - connection to EuroScope may be dead")
                        break
                    }
                    continue
                } catch (e: SocketException) {
                    if (isRunning && isConnected) {
                        logger.error("Socket exception while reading from EuroScope", e)
                    } else {
                        logger.info("Socket closed during shutdown (expected)")
                    }
                    break
                } catch (e: IOException) {
                    logger.error("IOException while reading from EuroScope: ${e.message}", e)
                    break
                } catch (e: Exception) {
                    logger.error("Unexpected error while reading from EuroScope: ${e.message}", e)
                    break
                }
            }
        } catch (e: Exception) {
            logger.error("Error receiving message: ${e.message}", e)
        } finally {
            logger.info("Disconnected from server.")
            // Only mark as disconnected, don't call close() to avoid recursion
            isConnected = false

            // Clean up the buffered reader
            try {
                bufferedReader?.close()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
        }
    }

    private fun handleMessage(messageFromEuroScopePluginJson: MessageFromEuroScopePluginJson) {
        when (messageFromEuroScopePluginJson) {
            is PluginVersionJson -> {
                val clientVersion = object {}.javaClass.`package`.implementationVersion
                val pluginVersion = messageFromEuroScopePluginJson.version
                
                logger.info("Plugin version: $pluginVersion, Client version: $clientVersion")
                
                if (clientVersion != "DEVELOPMENT" && pluginVersion != clientVersion) {
                    logger.error("VERSION MISMATCH! Plugin: $pluginVersion, Client: $clientVersion")
                    // Disconnect and notify about version mismatch
                    isVersionValidated = false
                    onVersionMismatch?.invoke(clientVersion, pluginVersion)
                    close()
                } else {
                    logger.info("Version check passed or in development mode")
                    isVersionValidated = true
                    // Continue with normal operation after version is validated
                    reSubscribeToAllAirports()
                }
            }
            is ArrivalsUpdateFromEuroScopePluginJson -> {
                messageFromEuroScopePluginJson.inbounds.groupBy { it.arrivalAirportIcao }.forEach { (arrivalAirportIcao, arrivals) ->
                    arrivalCallbacks[arrivalAirportIcao]?.invoke(arrivals.map { it.toArrival() })
                }
            }
            is DeparturesUpdateFromEuroScopePluginJson -> {
                messageFromEuroScopePluginJson.outbounds.groupBy { it.departureAirportIcao }.forEach { (departureAirportIcao, departures) ->
                    departuresCallbacks[departureAirportIcao]?.invoke(departures.map { it.toDeparture() })
                }
            }
            is RunwayStatusesUpdateFromEuroScopePluginJson -> {
                messageFromEuroScopePluginJson.airports.forEach { (airportIcao, statusesJson) ->
                    val statuses = statusesJson.map { (name, statusJson) -> statusJson.toRunwayStatus(name) }
                    runwayStatusCallbacks[airportIcao]?.invoke(statuses)
                }
            }
            is ControllerInfoFromEuroScopePluginJson -> {
                val infoData = ControllerInfoData(
                    callsign = messageFromEuroScopePluginJson.me.callsign,
                    positionId = messageFromEuroScopePluginJson.me.positionId,
                    facilityType = facilityTypeToString(messageFromEuroScopePluginJson.me.facilityType),
                )
                controllerInfoCallback(infoData)
            }
            is AircraftSelectionFromEuroScopePluginJson -> {
                onAircraftSelectionChanged?.invoke(messageFromEuroScopePluginJson.callsign)
            }
        }
    }

    private fun ArrivalJson.toArrival(): AtcClientArrivalData {
        return AtcClientArrivalData(
            callsign = this.callsign,
            icaoType = this.icaoType,
            assignedRunway = this.assignedRunway,
            assignedStar = this.assignedStar,
            assignedDirect = this.assignedDirect,
            scratchPad = this.scratchPad,
            remainingWaypoints = this.route.filter { !it.isPassed }.map {
                Waypoint(id = it.name, latLng = LatLng(it.latitude, it.longitude))
            },
            currentPosition = AircraftPosition(
                latLng = LatLng(this.latitude, this.longitude),
                altitudeFt = this.pressureAltitude,
                flightLevel = this.flightLevel,
                groundspeedKts = this.groundSpeed,
                trackDeg = this.track,
            ),
            arrivalAirportIcao = this.arrivalAirportIcao,
            flightPlanTas = this.flightPlanTas,
            trackingController = this.trackingController,
            recvTimestamp = NtpClock.now(),
        )
    }

    private fun DepartureJson.toDeparture(): AtcClientDepartureData {
        return AtcClientDepartureData(
            departureIcao = this.departureAirportIcao,
            callsign = this.callsign,
            icaoType = this.icaoType,
            assignedSid = this.sid,
            scratchPad = this.scratchPad,
            assignedRunway = this.runway,
            wakeCategory = this.wakeCategory,
            trackingController = this.trackingController,
            recvTimestamp = NtpClock.now()
        )
    }

    private fun RunwayStatusJson.toRunwayStatus(name: String) =
        AtcClientRunwaySelectionData(
            runway = name,
            allowArrivals = this.arrivals,
            allowDepartures = this.departures,
        )

    private fun facilityTypeToString(facilityType: Int?): String {
        return when (facilityType) {
            1 -> "FSS"
            2 -> "DEL"
            3 -> "GND"
            4 -> "TWR"
            5 -> "APP"
            6 -> "CTR"
            else -> "UNKNOWN"
        }
    }
}