package no.vaccsca.amandman.common.domain.valueobjects

import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent


data class TimelineData(
    val timelineId: String,
    val left: List<TimelineEvent>,
    val right: List<TimelineEvent>,
)