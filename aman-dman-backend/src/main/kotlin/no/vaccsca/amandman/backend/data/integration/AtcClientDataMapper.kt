package no.vaccsca.amandman.backend.data.integration

import no.vaccsca.amandman.backend.data.dto.atcClient.ArrivalJson
import no.vaccsca.amandman.backend.data.dto.atcClient.DepartureJson
import no.vaccsca.amandman.backend.data.dto.atcClient.RunwayStatusJson
import no.vaccsca.amandman.common.domain.valueobjects.AircraftPosition
import no.vaccsca.amandman.common.domain.valueobjects.LatLng
import no.vaccsca.amandman.common.domain.valueobjects.Waypoint
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientArrivalData
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientDepartureData
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientRunwaySelectionData
import no.vaccsca.amandman.common.NtpClock

object AtcClientDataMapper {

    fun ArrivalJson.toArrival(): AtcClientArrivalData {
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
            recvTimestamp = NtpClock.now()
        )
    }

    fun DepartureJson.toDeparture(): AtcClientDepartureData {
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

    fun RunwayStatusJson.toRunwayStatus(name: String) =
        AtcClientRunwaySelectionData(
            runway = name,
            allowArrivals = this.arrivals,
            allowDepartures = this.departures,
        )

    fun facilityTypeToString(facilityType: Int?): String {
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

