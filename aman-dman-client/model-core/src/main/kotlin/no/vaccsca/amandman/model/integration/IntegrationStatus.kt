package no.vaccsca.amandman.model.integration

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock

enum class IntegrationKind {
    ATC,
    CDM,
    SERVER,
    MET,
}

enum class IntegrationStatusState {
    LOADING,
    OK,
    ERROR,
}

data class IntegrationStatus(
    val state: IntegrationStatusState,
    val updatedAt: Instant = NtpClock.now(),
    val shouldFlash: Boolean = false,
    val relevant: Boolean = true,
    val detail: String? = null,
)

data class IntegrationDisplayStatus(
    val label: String,
    val status: IntegrationStatus,
)

data class AirportIntegrationStatuses(
    val byKind: Map<IntegrationKind, IntegrationStatus>
) {
    fun get(kind: IntegrationKind): IntegrationStatus =
        byKind[kind] ?: IntegrationStatus(IntegrationStatusState.ERROR, detail = "No status")

    companion object {
        fun errorAll(detail: String = "Unavailable"): AirportIntegrationStatuses {
            val status = IntegrationStatus(IntegrationStatusState.ERROR, detail = detail)
            return AirportIntegrationStatuses(
                byKind = mapOf(
                    IntegrationKind.ATC to status,
                    IntegrationKind.CDM to status,
                    IntegrationKind.SERVER to status,
                    IntegrationKind.MET to status,
                )
            )
        }
    }
}
