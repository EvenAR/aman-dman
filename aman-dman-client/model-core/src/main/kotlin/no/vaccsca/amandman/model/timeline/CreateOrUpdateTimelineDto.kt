package no.vaccsca.amandman.model.timeline

data class CreateOrUpdateTimelineDto(
    val airportIcao: String,
    val title: String,
    val left: TimeLineSide,
    val right: TimeLineSide,
    val depLabelLayout: String,
    val arrLabelLayout: String,
) {
    sealed interface TimeLineSide {
        val targets: List<String>

        data class Runways(
            val targetRunways: List<String>,
        ) : TimeLineSide {
            override val targets: List<String> = targetRunways
        }

        data class MeteringPoints(
            val targetMeteringPoints: List<String>,
        ) : TimeLineSide {
            override val targets: List<String> = targetMeteringPoints
        }
    }
}
