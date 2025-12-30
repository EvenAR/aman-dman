package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.UserRole
import no.vaccsca.amandman.common.domain.valueobjects.Airport

data class TimelineGroup(
    val airport: Airport,
    val name: String,
    val timelines: MutableList<TimelineConfig>,
    val userRole: UserRole,
    val presenter: AirportPresenterInterface
)