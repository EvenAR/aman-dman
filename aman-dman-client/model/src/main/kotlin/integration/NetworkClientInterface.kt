package integration

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent

interface NetworkClientInterface {

    fun monitorDescentTrajectoryForAircraft(callSign: String): String
    fun checkIfScheduledTimeIsAvailable(airportIcao: String, timelineEvent: TimelineEvent, newInstant: Instant)
    fun setMinimumSpacingForAirport(airportIcao: String, minimumSpacingDistanceNm: Double)
    fun setSubscribeForDeparturesOption(airportIcao: String, selected: Boolean)


}