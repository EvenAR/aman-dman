package no.vaccsca.amandman.common.dto

data class CreateOrUpdateTimelineDto(
    val airportIcao: String,
    val title: String,
    val left: TimeLineSide,
    val right: TimeLineSide,
    val depLabelLayout: String,
    val arrLabelLayout: String,
) {
    data class TimeLineSide(
        val targetRunways: List<String>,
    )
}