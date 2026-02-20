package no.vaccsca.amandman.model.timeline

import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.airport.Airport

data class TimelineGroup(
    val airport: Airport,
    val name: String,
    val userRole: UserRole
)