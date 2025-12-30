package no.vaccsca.amandman.backend.domain.service

import no.vaccsca.amandman.common.domain.valueobjects.CdmData
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientDepartureData
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.DepartureEvent

object DepartureEventService {
    fun createRunwayDepartureEvent(departure: AtcClientDepartureData, cdmDepartures: List<CdmData>?): DepartureEvent? {
        val cdmDataForDeparture = cdmDepartures?.find { it.callsign == departure.callsign }
        val expectedTime = cdmDataForDeparture?.ctot ?: cdmDataForDeparture?.ttot

        if (expectedTime == null) {
            return null
        }

        if (departure.assignedRunway == null) {
            println("No assigned runway found for departure ${departure.callsign}")
            return null
        }

        return DepartureEvent(
            airportIcao = departure.departureIcao,
            callsign = departure.callsign,
            icaoType = departure.icaoType,
            scheduledTime = expectedTime,
            estimatedTime = expectedTime,
            runway = departure.assignedRunway!!,
            wakeCategory = departure.wakeCategory,
            trackingController = departure.trackingController,
            sid = departure.assignedSid,
            lastTimestamp = departure.recvTimestamp
        )
    }
}