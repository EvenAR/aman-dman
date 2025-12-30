package no.vaccsca.amandman.backend.data.dto.sharedState

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayDelayEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent

/**
 * Wrapper for shared state data with a timestamp of the last update.
 * Used for sharing state between master and slave instances of the application.
 */
data class SharedStateJson<T>(
    val lastUpdate: Instant,
    val data: T
)

/**
 * Wrapper for polymorphic TimelineEvent serialization/deserialization.
 * The "type" field is used to determine the concrete subclass of Timeline
 */
data class SharedStateEventJson(
    val type: String,
    @param:JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "type"
    )
    @param:JsonSubTypes(
        JsonSubTypes.Type(value = RunwayArrivalEvent::class, name = "runwayArrival"),
        JsonSubTypes.Type(value = DepartureEvent::class, name = "runwayDeparture"),
        JsonSubTypes.Type(value = RunwayDelayEvent::class, name = "runwayDelay")
    )
    val event: TimelineEvent
)
