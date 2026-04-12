package no.vaccsca.amandman.model.timeline

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent

data class TimelineData(
    val timelineId: String,
    val left: List<TimelineDisplayEvent>,
    val right: List<TimelineDisplayEvent>,
)

data class TimelineDisplayEvent(
    val event: TimelineEvent,
    val displayScheduledTime: Instant = event.scheduledTime,
    val displayEstimatedTime: Instant? = (event as? RunwayEvent)?.estimatedTime,
    val anchorId: String? = null,
    val isAbeamPosition: Boolean = false,
)
