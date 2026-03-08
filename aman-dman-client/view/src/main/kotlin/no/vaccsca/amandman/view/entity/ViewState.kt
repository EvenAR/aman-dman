package no.vaccsca.amandman.view.entity

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.timeline.MeteringPointState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class MainViewState(
    val currentClock: SharedValue<Instant> = SharedValue(NtpClock.now()),
    val airportViewStates: SharedValue<List<AirportViewState>> = SharedValue(emptyList()),
    val currentTab: SharedValue<String?> = SharedValue(null)
)

data class AircraftSelection(
    val callsign: String,
    val timestamp: Instant
)

data class AirportViewState(
    val airportIcao: String,
    val userRole: UserRole,
    val maxHistory: Duration = 20.minutes,
    val maxFuture: Duration = 2.hours,
    val events: SharedValue<List<TimelineEvent>> = SharedValue(emptyList()),
    val nonSequencedList: SharedValue<List<NonSequencedEvent>> = SharedValue(emptyList()),
    val openTimelines: SharedValue<List<TimelineConfig>> = SharedValue(emptyList()),
    val runwayModes: SharedValue<List<Pair<String, Boolean>>> = SharedValue(emptyList()),
    val weatherProfile: SharedValue<VerticalWeatherProfile?> = SharedValue(null),
    val integrationStatuses: SharedValue<Map<IntegrationKind, IntegrationDisplayStatus>> = SharedValue(emptyMap()),
    val minimumSpacingNm: SharedValue<Double> = SharedValue(3.0),
    val meteringPointState: SharedValue<MeteringPointState> = SharedValue(MeteringPointState()),
    val showDepartures: SharedValue<Boolean> = SharedValue(false),
    val aircraftSelection: SharedValue<AircraftSelection?> = SharedValue(null),
    val draggedLabelState: SharedValue<DraggedLabelState?> = SharedValue(null),
    val selectedTimeRange: SharedValue<TimeRange> = SharedValue(
        initialValue = TimeRange(
            NtpClock.now() - 10.minutes,
            NtpClock.now() + 60.minutes,
        )
    ),
    val availableTimeRange: SharedValue<TimeRange> = SharedValue(
        initialValue = TimeRange(
            NtpClock.now() - maxHistory,
            NtpClock.now() + maxFuture,
        )
    ),
)

data class DraggedLabelState(
    val timelineEvent: TimelineEvent,
    val proposedTime: Instant,
    val isAvailable: Boolean
)
