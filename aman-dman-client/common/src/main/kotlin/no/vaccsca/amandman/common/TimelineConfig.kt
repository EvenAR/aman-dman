package no.vaccsca.amandman.common

sealed interface TimelineConfig {
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
) : TimelineConfig {
    override val leftTargets: List<String> = leftRunways
    override val rightTargets: List<String> = rightRunways
}

data class MeteringPointTimelineConfig(
    override val title: String,
    override val airportIcao: String,
    val leftMeteringPoints: List<String>,
    val rightMeteringPoints: List<String>,
    override val arrLabelLayout: String,
) : TimelineConfig {
    override val leftTargets: List<String> = leftMeteringPoints
    override val rightTargets: List<String> = rightMeteringPoints
}
