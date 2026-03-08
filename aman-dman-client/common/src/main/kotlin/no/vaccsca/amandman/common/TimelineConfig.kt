package no.vaccsca.amandman.common

sealed interface TimelineConfig {
    val timelineId: String?
    val title: String
    val airportIcao: String
    val arrLabelLayout: String
    val leftTargets: List<String>
    val rightTargets: List<String>
}

data class RunwayTimelineConfig(
    override val title: String,
    override val airportIcao: String,
    val leftRunways: List<String>,
    val rightRunways: List<String>,
    val depLabelLayout: String,
    override val arrLabelLayout: String,
    override val timelineId: String? = null,
) : TimelineConfig {
    override val leftTargets: List<String> = leftRunways
    override val rightTargets: List<String> = rightRunways
}

data class FeederFixTimelineConfig(
    override val title: String,
    override val airportIcao: String,
    val leftFixes: List<String>,
    val rightFixes: List<String>,
    override val arrLabelLayout: String,
    override val timelineId: String? = null,
) : TimelineConfig {
    override val leftTargets: List<String> = leftFixes
    override val rightTargets: List<String> = rightFixes
}
