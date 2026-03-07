package no.vaccsca.amandman.common

sealed interface TimelineSideConfig {
    val targets: List<String>

    data class Runways(
        val runways: List<String>,
    ) : TimelineSideConfig {
        override val targets: List<String> = runways
    }

    data class MeteringPoints(
        val meteringPoints: List<String>,
    ) : TimelineSideConfig {
        override val targets: List<String> = meteringPoints
    }
}

data class TimelineConfig(
    val title: String,
    val left: TimelineSideConfig,
    val right: TimelineSideConfig,
    val airportIcao: String,
    val depLabelLayout: String?,
    val arrLabelLayout: String?,
) {
    // Backward-compatible convenience accessors for runway-based operations.
    val runwaysLeft: List<String>
        get() = (left as? TimelineSideConfig.Runways)?.runways ?: emptyList()

    val runwaysRight: List<String>
        get() = (right as? TimelineSideConfig.Runways)?.runways ?: emptyList()
}
