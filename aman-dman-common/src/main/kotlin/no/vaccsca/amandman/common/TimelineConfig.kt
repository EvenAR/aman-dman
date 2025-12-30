package no.vaccsca.amandman.common

data class TimelineConfig(
    val title: String,
    val runwaysLeft: List<String>,
    val runwaysRight: List<String>,
    val airportIcao: String,
    val depLabelLayout: String?,
    val arrLabelLayout: String?,
)
