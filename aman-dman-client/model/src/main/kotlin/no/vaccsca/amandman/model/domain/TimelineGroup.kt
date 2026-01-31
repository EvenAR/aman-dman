package no.vaccsca.amandman.model.domain

import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.UserRole
import no.vaccsca.amandman.model.domain.valueobjects.Airport

data class TimelineGroup(
    val airport: Airport,
    val name: String,
    val userRole: UserRole
)