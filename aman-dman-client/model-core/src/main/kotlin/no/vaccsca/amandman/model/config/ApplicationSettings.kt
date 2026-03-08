package no.vaccsca.amandman.model.config

data class AmanDmanSettings(
    val timelines: Map<String, AirportTimelines>,
    val connectionConfig: ConnectionConfig,
    val arrivalLabelLayouts: Map<String, List<LabelItem>>,
    val departureLabelLayouts: Map<String, List<LabelItem>>,
    val theme: Theme,
)

data class AirportTimelines(
    val defaults: TimelineDefaults,
    val runwayBased: List<RunwayTimeline> = emptyList(),
    val meteringPointBased: List<MeteringPointTimeline> = emptyList(),
)

data class TimelineDefaults(
    val defaultArrivalLabelLayoutId: String,
    val defaultDepartureLabelLayoutId: String?,
)

data class RunwayTimeline(
    val title: String,
    val left: List<String> = emptyList(),
    val right: List<String>,
    val arrivalLabelLayoutId: String,
    val departureLabelLayoutId: String,
)

data class MeteringPointTimeline(
    val title: String,
    val left: List<String> = emptyList(),
    val right: List<String>,
    val arrivalLabelLayoutId: String,
)

data class ConnectionConfig(
    val atcClient: AtcClientConnectionParameters,
    val api: SharedStateConnectionParameters
)

data class AtcClientConnectionParameters(
    val host: String,
    val port: Int
)

data class SharedStateConnectionParameters(
    val host: String
)

data class LabelItem(
    val source: LabelItemSource,
    val width: Int,
    val alignment: LabelItemAlignment? = null,
    val defaultValue: String? = null,
    val maxLength: Int? = null,
    val timeFormat: String? = null
) {
    init {
        require(timeFormat == null || timeFormat.isNotBlank()) { "Label item timeFormat must not be blank" }
        require(timeFormat == null || source.supportsTimeFormat()) {
            "Label item timeFormat is only supported for time-based label sources"
        }
    }
}

enum class LabelItemAlignment {
    LEFT, CENTER, RIGHT
}

enum class LabelItemSource {
    CALL_SIGN,
    ASSIGNED_RUNWAY,
    ASSIGNED_STAR,
    AIRCRAFT_TYPE,
    WAKE_CATEGORY,
    TIME_BEHIND_PRECEDING,
    TIME_BEHIND_PRECEDING_ROUNDED,
    REMAINING_DISTANCE,
    DISTANCE_BEHIND_PRECEDING,
    DIRECT_ROUTING,
    SCRATCH_PAD,
    @Deprecated("Use ESTIMATED_ARRIVAL_TIME instead")
    ESTIMATED_LANDING_TIME,
    ESTIMATED_ARRIVAL_TIME,
    SCHEDULED_ARRIVAL_TIME,
    GROUND_SPEED,
    GROUND_SPEED_10,
    ALTITUDE,
    TTL_TTG;

    fun supportsTimeFormat(): Boolean = when (this) {
        ESTIMATED_LANDING_TIME, ESTIMATED_ARRIVAL_TIME, SCHEDULED_ARRIVAL_TIME -> true
        else -> false
    }
}

enum class Theme {
    FLATLAF_DARK,
    JTATTOO,
}
