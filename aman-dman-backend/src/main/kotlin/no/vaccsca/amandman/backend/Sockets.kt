package no.vaccsca.amandman.backend

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
