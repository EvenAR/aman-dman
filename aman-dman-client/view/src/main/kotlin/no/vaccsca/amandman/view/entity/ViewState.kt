package no.vaccsca.amandman.view.entity

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.UserRole
import no.vaccsca.amandman.model.domain.valueobjects.NonSequencedEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
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
    val openTimelines: SharedValue<Set<TimelineConfig>> = SharedValue(emptySet()),
    val runwayModes: SharedValue<List<Pair<String, Boolean>>> = SharedValue(emptyList()),
    val weatherProfile: SharedValue<VerticalWeatherProfile?> = SharedValue(null),
    val minimumSpacingNm: SharedValue<Double> = SharedValue(3.0),
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