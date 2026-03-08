package no.vaccsca.amandman.model.timeline

sealed interface CreateOrUpdateTimelineDto {
    val airportIcao: String
    val title: String
    val left: List<String>
    val right: List<String>
    val arrLabelLayout: String

    data class Runway(
        override val airportIcao: String,
        override val title: String,
        override val left: List<String>,
        override val right: List<String>,
        val depLabelLayout: String,
        override val arrLabelLayout: String,
    ) : CreateOrUpdateTimelineDto

    data class MeteringPoint(
        override val airportIcao: String,
        override val title: String,
        override val left: List<String>,
        override val right: List<String>,
        override val arrLabelLayout: String,
    ) : CreateOrUpdateTimelineDto
}
