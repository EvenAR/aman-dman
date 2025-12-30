package no.vaccsca.amandman.backend.domain

import no.vaccsca.amandman.backend.data.repository.CdmClient
import no.vaccsca.amandman.backend.data.repository.SettingsRepository
import no.vaccsca.amandman.backend.data.repository.WeatherDataRepository
import no.vaccsca.amandman.backend.domain.service.PlannerService

class PlannerManager(
    val weatherDataRepository: WeatherDataRepository,
    val cdmClient: CdmClient
) {
    private val services: MutableList<PlannerService> = mutableListOf()

    fun registerService(service: PlannerService) {
        services.add(service)
    }

    fun unregisterService(airportIcao: String) {
        val serviceToRemove = services.find { it.getAirportIcao() == airportIcao }
        println("Unregistering service for $serviceToRemove")
        services.remove(serviceToRemove)
    }

    fun getServiceForAirport(airportIcao: String): PlannerService? {
        return services.find { it.getAirportIcao() == airportIcao }
    }

    fun createServiceForAirport(airportIcao: String): PlannerService {
        val airport = SettingsRepository.getAirportData().find { it.icao == airportIcao }

        val newService = PlannerService(
            airport = airport!!,
            weatherDataRepository = weatherDataRepository,
            cdmClient = cdmClient
        )

        services.add(newService)

        return newService
    }

    fun getAllServices(): List<PlannerService> {
        return services.toList()
    }
}