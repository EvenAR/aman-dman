package no.vaccsca.amandman.backend

import io.ktor.server.application.*
import io.ktor.server.netty.*
import no.vaccsca.amandman.backend.data.repository.CdmClient
import no.vaccsca.amandman.backend.data.repository.WeatherDataRepository
import no.vaccsca.amandman.backend.domain.PlannerManager

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {

    val plannerManager = PlannerManager(
        weatherDataRepository = WeatherDataRepository(),
        cdmClient = CdmClient()
    )

    configureSerialization()
    configureSockets()
    configureRouting(plannerManager)
}
