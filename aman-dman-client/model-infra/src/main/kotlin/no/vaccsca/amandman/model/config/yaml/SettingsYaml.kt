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
            @Valid List<@Valid TimelineYaml>
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

data class TimelineYaml(
    @field:NotBlank
    val timelineTitle: String,

    @field:Valid
    val left: SideYaml? = null,

    @field:Valid
    @field:NotNull
    val right: SideYaml,

    @field:NotBlank
    val arrivalLabelLayoutId: String
)

data class SideYaml(
    val runways: List<
            @Pattern(regexp = "^[0-9]{2}[A-Z]?$") String
            >? = null,

    val meteringPoints: List<
            @Pattern(regexp = "^[A-Z0-9]{2,10}$") String
            >? = null,
) {
    init {
        val hasRunways = !runways.isNullOrEmpty()
        val hasMeteringPoints = !meteringPoints.isNullOrEmpty()
        require(hasRunways.xor(hasMeteringPoints)) { "SideYaml must define exactly one of runways or meteringPoints" }
    }
}

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
    val maxLen: Int? = null
)

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
    ESTIMATED_LANDING_TIME("estimatedLandingTime"),
    GROUND_SPEED("groundSpeed"),
    GROUND_SPEED_10("groundSpeed10"),
    ALTITUDE("altitude"),
    TTL_TTG("timeToLoseOrGain");

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
