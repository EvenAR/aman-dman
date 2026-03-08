package no.vaccsca.amandman.model.config.yaml

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import no.vaccsca.amandman.model.config.Theme

data class AmanDmanSettingsYaml(
    @field:NotEmpty
    val timelines: Map<
            @Pattern(regexp = "^[A-Z]{4}$") String,
            @Valid AirportTimelinesYaml
            >,

    @field:NotEmpty
    val arrivalLabelLayouts: Map<
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String,
            @Valid List<@Valid LabelItemYaml>
            >,

    @field:Valid
    @field:NotNull
    val connectionConfig: ConnectionConfigYaml,

    val departureLabelLayouts: Map<
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String,
            @Valid List<@Valid LabelItemYaml>
            >? = null,

    val theme: ThemeYaml?,
)

data class AirportTimelinesYaml(
    @field:Valid
    @field:NotNull
    val defaults: TimelineDefaultsYaml,

    @field:NotNull
    @field:Valid
    val runwayBased: List<@Valid RunwayTimelineYaml> = emptyList(),

    @field:NotNull
    @field:Valid
    val meteringPointBased: List<@Valid MeteringPointTimelineYaml> = emptyList(),
)

data class TimelineDefaultsYaml(
    @field:NotBlank
    val defaultArrivalLabelLayoutId: String,

    val defaultDepartureLabelLayoutId: String? = null,
)

data class RunwayTimelineYaml(
    @field:NotBlank
    val timelineTitle: String,

    val left: List<
            @Pattern(regexp = "^[0-9]{2}[A-Z]?$") String
            > = emptyList(),

    @field:NotEmpty
    val right: List<
            @Pattern(regexp = "^[0-9]{2}[A-Z]?$") String
            >,

    val arrivalLabelLayoutId: String? = null,

    val departureLabelLayoutId: String? = null,
)

data class MeteringPointTimelineYaml(
    @field:NotBlank
    val timelineTitle: String,

    val left: List<
            @Pattern(regexp = "^[A-Z0-9]{2,10}$") String
            > = emptyList(),

    @field:NotEmpty
    val right: List<
            @Pattern(regexp = "^[A-Z0-9]{2,10}$") String
            >,

    val arrivalLabelLayoutId: String? = null,
)

data class ConnectionConfigYaml(
    @field:Valid
    @field:NotNull
    val atcClient: AtcClientConnectionParamsYaml,

    @field:Valid
    @field:NotNull
    val masterSlaveApi: MasterSlaveApiConnectionParamsYaml
)

data class AtcClientConnectionParamsYaml(
    @field:NotBlank
    val host: String,

    @field:Min(1)
    @field:Max(65535)
    val port: Int
)

data class MasterSlaveApiConnectionParamsYaml(
    @field:NotBlank
    val host: String
)

data class LabelItemYaml(
    @field:NotNull
    val src: LabelItemSourceEnumYaml,

    @field:Min(1)
    val w: Int,

    val via: Boolean? = null,

    val align: LabelItemAlignmentEnumYaml? = LabelItemAlignmentEnumYaml.LEFT,

    val def: String? = null,

    @field:Min(1)
    val maxLen: Int? = null,

    val timeFormat: String? = null
) {
    init {
        require(timeFormat == null || timeFormat.isNotBlank()) { "Label item timeFormat must not be blank" }
        require(timeFormat == null || src.supportsTimeFormat()) {
            "Label item timeFormat is only supported for time-based label sources"
        }
    }
}

enum class LabelItemAlignmentEnumYaml(@JsonValue val value: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromValue(value: String) =
            entries.find { it.value.equals(value, ignoreCase = true) }
    }

    override fun toString(): String {
        return value
    }
}

enum class LabelItemSourceEnumYaml(@JsonValue val value: String) {
    CALL_SIGN("callSign"),
    ASSIGNED_RUNWAY("assignedRunway"),
    ASSIGNED_STAR("assignedStar"),
    AIRCRAFT_TYPE("aircraftType"),
    WAKE_CATEGORY("wakeCategory"),
    TIME_BEHIND_PRECEDING("timeBehindPreceding"),
    TIME_BEHIND_PRECEDING_ROUNDED("minutesBehindPrecedingRounded"),
    REMAINING_DISTANCE("remainingDistance"),
    DISTANCE_BEHIND_PRECEDING("distanceBehindPreceding"),
    DIRECT_ROUTING("directRouting"),
    SCRATCH_PAD("scratchPad"),
    @Deprecated("Use estimatedArrivalTime instead")
    ESTIMATED_LANDING_TIME("estimatedLandingTime"),
    ESTIMATED_ARRIVAL_TIME("estimatedArrivalTime"),
    SCHEDULED_ARRIVAL_TIME("scheduledArrivalTime"),
    GROUND_SPEED("groundSpeed"),
    GROUND_SPEED_10("groundSpeed10"),
    ALTITUDE("altitude"),
    TTL_TTG("timeToLoseOrGain");

    fun supportsTimeFormat(): Boolean = when (this) {
        ESTIMATED_LANDING_TIME, ESTIMATED_ARRIVAL_TIME, SCHEDULED_ARRIVAL_TIME -> true
        else -> false
    }

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromValue(value: String) =
            entries.find { it.value.equals(value, ignoreCase = true) }
    }

    override fun toString(): String {
        return value
    }
}

enum class ThemeYaml(@JsonValue val value: String) {
    JTATTOO("JTattoo"),
    FLATLAF_DARK("FlatLaf");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromValue(value: String) =
            entries.find { it.value.equals(value, ignoreCase = true) }
    }

    override fun toString(): String {
        return value
    }

    fun toDomain(): Theme = when(this) {
        JTATTOO -> Theme.JTATTOO
        FLATLAF_DARK -> Theme.FLATLAF_DARK
    }
}
