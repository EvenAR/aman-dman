package no.vaccsca.amandman.model.timeline

import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent


data class TimelineData(
    val timelineId: String,
    val left: List<TimelineEvent>,
    val right: List<TimelineEvent>,
)