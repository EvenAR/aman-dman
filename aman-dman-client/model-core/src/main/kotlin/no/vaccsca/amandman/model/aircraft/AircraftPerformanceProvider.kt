package no.vaccsca.amandman.model.aircraft

interface AircraftPerformanceProvider {
    fun get(icao: String): AircraftPerformance
}
