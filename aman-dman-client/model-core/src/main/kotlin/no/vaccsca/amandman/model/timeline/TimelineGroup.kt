package no.vaccsca.amandman.model.timeline

import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.user.UserRole

data class TimelineGroup(
    val airport: Airport,
    val name: String,
    val userRole: UserRole
)