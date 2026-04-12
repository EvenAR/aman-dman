package no.vaccsca.amandman.model.config.mapper

import no.vaccsca.amandman.model.aircraft.AircraftPerformance
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.config.*
import no.vaccsca.amandman.model.config.yaml.*
import no.vaccsca.amandman.model.navigation.LatLng
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration

fun AmanDmanSettingsYaml.toDomain(): AmanDmanSettings = AmanDmanSettings(
    connectionConfig = connectionConfig.toDomain(),
    arrivalLabelLayouts = arrivalLabelLayouts.mapValues { entry -> entry.value.map { it.toDomain() } },
    departureLabelLayouts = departureLabelLayouts?.mapValues { entry -> entry.value.map { it.toDomain() } } ?: emptyMap(),
    theme = theme?.toDomain() ?: Theme.FLATLAF_DARK,
    planningSettings = planningSettings.toDomain(),
)

fun PlanningSettingsYaml.toDomain() = PlanningSettings(
    useGroundspeedOnDirectRouting = useGroundspeedOnDirectRouting,
    feederFixMaxAbeamDistanceNm = feederFixMaxAbeamDistanceNm,
)

fun TimelineSettingsYaml.toDomain(): Map<String, AirportTimelines> = timelines.mapValues { entry -> entry.value.toDomain() }

fun Map<String, AirportTimelines>.toYaml(): TimelineSettingsYaml = TimelineSettingsYaml(
    timelines = entries.associate { (icao, airportTimelines) -> icao to airportTimelines.toYaml() }
)

fun AirportTimelinesYaml.toDomain(): AirportTimelines {
    val configuredDefaults = TimelineDefaults(
        defaultArrivalLabelLayoutId = defaults.defaultArrivalLabelLayoutId,
        defaultDepartureLabelLayoutId = defaults.defaultDepartureLabelLayoutId,
    )

    val runway = runwayBased.map { it.toDomain(configuredDefaults) }
    val feederFixTimelines = feederFixBased.map { it.toDomain(configuredDefaults) }

    return AirportTimelines(
        defaults = configuredDefaults,
        runwayBased = runway,
        feederFixBased = feederFixTimelines
    )
}

fun AirportTimelines.toYaml(): AirportTimelinesYaml = AirportTimelinesYaml(
    defaults = defaults.toYaml(),
    runwayBased = runwayBased.map { it.toYaml() },
    feederFixBased = feederFixBased.map { it.toYaml() },
)

fun TimelineDefaults.toYaml(): TimelineDefaultsYaml = TimelineDefaultsYaml(
    defaultArrivalLabelLayoutId = defaultArrivalLabelLayoutId,
    defaultDepartureLabelLayoutId = defaultDepartureLabelLayoutId,
)

fun RunwayTimelineYaml.toDomain(defaults: TimelineDefaults): RunwayTimeline {
    val effectiveArrivalLayout = arrivalLabelLayoutId ?: defaults.defaultArrivalLabelLayoutId
    val effectiveDepartureLayout = departureLabelLayoutId ?: defaults.defaultDepartureLabelLayoutId
        ?: throw IllegalArgumentException(
            "Runway timeline '$timelineTitle' is missing departureLabelLayoutId and no defaultDepartureLabelLayoutId is configured."
        )

    return RunwayTimeline(
        title = timelineTitle,
        left = left.map { it.uppercase() },
        right = right.map { it.uppercase() },
        arrivalLabelLayoutId = effectiveArrivalLayout,
        departureLabelLayoutId = effectiveDepartureLayout,
        timelineId = timelineId ?: generateTimelineId(),
    )
}

fun RunwayTimeline.toYaml(): RunwayTimelineYaml = RunwayTimelineYaml(
    timelineTitle = title,
    timelineId = timelineId,
    left = left.map { it.uppercase() },
    right = right.map { it.uppercase() },
    arrivalLabelLayoutId = arrivalLabelLayoutId,
    departureLabelLayoutId = departureLabelLayoutId,
)

fun FeederFixTimelineYaml.toDomain(defaults: TimelineDefaults): FeederFixTimeline {
    val effectiveArrivalLayout = arrivalLabelLayoutId ?: defaults.defaultArrivalLabelLayoutId
    return FeederFixTimeline(
        title = timelineTitle,
        left = left.map { it.uppercase() },
        right = right.map { it.uppercase() },
        arrivalLabelLayoutId = effectiveArrivalLayout,
        timelineId = timelineId ?: generateTimelineId(),
    )
}

fun FeederFixTimeline.toYaml(): FeederFixTimelineYaml = FeederFixTimelineYaml(
    timelineTitle = title,
    timelineId = timelineId,
    left = left.map { it.uppercase() },
    right = right.map { it.uppercase() },
    arrivalLabelLayoutId = arrivalLabelLayoutId,
)

fun ConnectionConfigYaml.toDomain() = ConnectionConfig(
    atcClient = atcClient.toDomain(),
    api = masterSlaveApi.toDomain()
)

fun AtcClientConnectionParamsYaml.toDomain() = AtcClientConnectionParameters(host, port)
fun MasterSlaveApiConnectionParamsYaml.toDomain() = SharedStateConnectionParameters(host)
fun LabelItemYaml.toDomain() = LabelItem(
    source = src.toDomain(),
    width = w,
    alignment = align?.toDomain(),
    defaultValue = def,
    maxLength = maxLen,
    timeFormat = timeFormat
)

fun LabelItemAlignmentEnumYaml.toDomain() = when(this) {
    LabelItemAlignmentEnumYaml.LEFT -> LabelItemAlignment.LEFT
    LabelItemAlignmentEnumYaml.CENTER -> LabelItemAlignment.CENTER
    LabelItemAlignmentEnumYaml.RIGHT -> LabelItemAlignment.RIGHT
}

fun LabelItemSourceEnumYaml.toDomain() = when(this) {
    LabelItemSourceEnumYaml.CALL_SIGN -> LabelItemSource.CALL_SIGN
    LabelItemSourceEnumYaml.ASSIGNED_RUNWAY -> LabelItemSource.ASSIGNED_RUNWAY
    LabelItemSourceEnumYaml.ASSIGNED_STAR -> LabelItemSource.ASSIGNED_STAR
    LabelItemSourceEnumYaml.AIRCRAFT_TYPE -> LabelItemSource.AIRCRAFT_TYPE
    LabelItemSourceEnumYaml.WAKE_CATEGORY -> LabelItemSource.WAKE_CATEGORY
    LabelItemSourceEnumYaml.TIME_BEHIND_PRECEDING -> LabelItemSource.TIME_BEHIND_PRECEDING
    LabelItemSourceEnumYaml.TIME_BEHIND_PRECEDING_ROUNDED -> LabelItemSource.TIME_BEHIND_PRECEDING_ROUNDED
    LabelItemSourceEnumYaml.REMAINING_DISTANCE -> LabelItemSource.REMAINING_DISTANCE
    LabelItemSourceEnumYaml.DISTANCE_BEHIND_PRECEDING -> LabelItemSource.DISTANCE_BEHIND_PRECEDING
    LabelItemSourceEnumYaml.DIRECT_ROUTING -> LabelItemSource.DIRECT_ROUTING
    LabelItemSourceEnumYaml.SCRATCH_PAD -> LabelItemSource.SCRATCH_PAD
    LabelItemSourceEnumYaml.ESTIMATED_LANDING_TIME -> LabelItemSource.ESTIMATED_LANDING_TIME
    LabelItemSourceEnumYaml.ESTIMATED_ARRIVAL_TIME -> LabelItemSource.ESTIMATED_ARRIVAL_TIME
    LabelItemSourceEnumYaml.SCHEDULED_ARRIVAL_TIME -> LabelItemSource.SCHEDULED_ARRIVAL_TIME
    LabelItemSourceEnumYaml.GROUND_SPEED -> LabelItemSource.GROUND_SPEED
    LabelItemSourceEnumYaml.GROUND_SPEED_10 -> LabelItemSource.GROUND_SPEED_10
    LabelItemSourceEnumYaml.ALTITUDE -> LabelItemSource.ALTITUDE
    LabelItemSourceEnumYaml.TTL_TTG -> LabelItemSource.TTL_TTG
}

fun AirportDataJson.toDomain(icao: String): Airport {
    val airportAreas = areas.toAirportAreas(airportIcao = icao)
    val runwayProfilesByRunway = arrivalProfiles.toRunwayProfilesByRunway(
        airportIcao = icao,
        availableRunways = runwayThresholds.keys.map { it.uppercase() }.toSet(),
        availableAreaIds = airportAreas.keys,
    )

    return Airport(
        icao = icao,
        location = LatLng(location.latitude, location.longitude),
        areas = airportAreas,
        independentRunwaySystems = independentRunwaySystems?.map { it.toSet() } ?: listOf(runwayThresholds.keys),
        sequencingHorizon = sequencingHorizon?.toKotlinDuration() ?: 30.minutes,
        lockedHorizon = lockedHorizon?.toKotlinDuration() ?: 10.minutes,
        weatherFetchRadiusNm = (weatherFetchRadiusNm ?: 200.0).also {
            require(it > 0.0) { "Airport $icao has invalid weatherFetchRadiusNm=$it. Value must be > 0." }
        },
        runways = runwayThresholds.mapValues { (id, value) ->
            RunwayThreshold(
                id = id,
                latLng = LatLng(
                    value.location.latitude,
                    value.location.longitude
                ),
                elevation = value.elevation,
                trueHeading = value.trueHeading,
                arrivalProfiles = runwayProfilesByRunway[id.uppercase()] ?: emptyList(),
            )
        },
        feederFixes = feederFixes.map { it.uppercase() },
        feederFixTimelineArrivalLabelLayoutId = feederFixTimelineArrivalLabelLayoutId,
        feederFixTransitTimesMinutes = feederFixTransitTimesMinutes
            ?.mapKeys { (fix, _) -> fix.uppercase() }
            ?.mapValues { (_, byRunway) -> byRunway.mapKeys { (runway, _) -> runway.uppercase() } }
            ?: emptyMap(),
    )
}

fun AircraftPerformanceYaml.toDomain() = AircraftPerformance(
        takeOffV2 = this.takeOffV2,
        takeOffDistance = this.takeOffDistance,
        takeOffWTC = this.takeOffWTC,
        takeOffRECAT = this.takeOffRECAT,
        takeOffMTOW = this.takeOffMTOW,
        initialClimbIAS = this.initialClimbIAS,
        initialClimbROC = this.initialClimbROC,
        climb150IAS = this.climb150IAS,
        climb150ROC = this.climb150ROC,
        climb240IAS = this.climb240IAS,
        climb240ROC = this.climb240ROC,
        machClimbMACH = this.machClimbMACH,
        machClimbROC = this.machClimbROC,
        cruiseTAS = this.cruiseTAS,
        cruiseMACH = this.cruiseMACH,
        cruiseCeiling = this.cruiseCeiling,
        cruiseRange = this.cruiseRange,
        initialDescentMACH = this.initialDescentMACH,
        initialDescentROD = this.initialDescentROD,
        descentIAS = this.descentIAS,
        descentROD = this.descentROD,
        approachIAS = this.approachIAS,
        approachROD = this.approachROD,
        approachMCS = this.approachMCS,
        landingVat = this.landingVat,
        landingDistance = this.landingDistance,
        landingAPC = this.landingAPC
    )

private fun generateTimelineId(): String = UUID.randomUUID().toString()
