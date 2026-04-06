package no.vaccsca.amandman.model.airport

data class ArrivalFixExpectation(
    val fixName: String,
    val role: ArrivalFixRole? = null,
    val typicalAltitude: Int? = null,
    val typicalSpeedIas: Int? = null,
)

enum class ArrivalFixRole {
    IF,
    IAF,
}
