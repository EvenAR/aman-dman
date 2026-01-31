package no.vaccsca.amandman.model.domain.service.planning

import no.vaccsca.amandman.model.domain.valueobjects.CdmData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientDepartureData
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.DepartureEvent
import org.slf4j.LoggerFactory

object DepartureEventService {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createRunwayDepartureEvent(departure: AtcClientDepartureData, cdmDepartures: List<CdmData>?): DepartureEvent? {
        val cdmDataForDeparture = cdmDepartures?.find { it.callsign == departure.callsign }
        val expectedTime = cdmDataForDeparture?.ctot ?: cdmDataForDeparture?.ttot

        if (expectedTime == null) {
            return null
        }

        if (departure.assignedRunway == null) {
            logger.warn("No assigned runway found for departure ${departure.callsign}. Cannot create DepartureEvent.")
            return null
        }

        return DepartureEvent(
            airportIcao = departure.departureIcao,
            callsign = departure.callsign,
            icaoType = departure.icaoType,
            scheduledTime = expectedTime,
            estimatedTime = expectedTime,
            runway = departure.assignedRunway,
            wakeCategory = departure.wakeCategory,
            trackingController = departure.trackingController,
            sid = departure.assignedSid,
            lastTimestamp = departure.recvTimestamp
        )
    }
}