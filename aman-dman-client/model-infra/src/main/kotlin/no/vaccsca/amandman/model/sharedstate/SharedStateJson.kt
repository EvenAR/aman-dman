package no.vaccsca.amandman.model.sharedstate

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.DepartureEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayDelayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent

/**
 * Wrapper for shared state data with a timestamp of the last update.
 * Used for sharing state between master and slave instances of the application.
 */
data class SharedStateJson<T>(
    val lastUpdate: Instant,
    val data: T
)

data class MasterRoleResponse(
    val isMaster: Boolean,
    val currentMaster: String,
    val sessionId: String,
)

/**
 * Wrapper for polymorphic TimelineEvent serialization/deserialization.
 * The "type" field is used to determine the concrete subclass of Timeline
 */
data class SharedStateTimelineEventJson(
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

data class CompatibilityCheckJson(
    val apiVersion: String,
    val latestClientVersion: String,
    val minClientVersion: String,
    val status: VersionStatus,
)

data class NonSequencedEventsJson(
    val events: List<NonSequencedEvent>
)

enum class VersionStatus {
    OK,
    UPDATE_REQUIRED,
    UPDATE_RECOMMENDED,
}
