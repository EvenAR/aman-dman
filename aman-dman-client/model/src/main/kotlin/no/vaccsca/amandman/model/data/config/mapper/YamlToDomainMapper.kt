package no.vaccsca.amandman.model.data.config.mapper

import no.vaccsca.amandman.model.data.config.yaml.AircraftPerformanceYaml
import no.vaccsca.amandman.model.data.config.yaml.AirportJson
import no.vaccsca.amandman.model.data.config.yaml.AmanDmanSettingsYaml
import no.vaccsca.amandman.model.data.config.yaml.MasterSlaveApiConnectionParamsYaml
import no.vaccsca.amandman.model.data.config.yaml.AtcClientConnectionParamsYaml
import no.vaccsca.amandman.model.data.config.yaml.ConnectionConfigYaml
import no.vaccsca.amandman.model.data.config.yaml.LabelItemAlignmentEnumYaml
import no.vaccsca.amandman.model.data.config.yaml.LabelItemSourceEnumYaml
import no.vaccsca.amandman.model.data.config.yaml.LabelItemYaml
import no.vaccsca.amandman.model.data.config.yaml.SideYaml
import no.vaccsca.amandman.model.data.config.yaml.StarYamlEntry
import no.vaccsca.amandman.model.data.config.yaml.StarYamlFile
import no.vaccsca.amandman.model.data.config.yaml.TimelineYaml
import no.vaccsca.amandman.model.domain.valueobjects.AircraftPerformance
import no.vaccsca.amandman.model.domain.valueobjects.Airport
import no.vaccsca.amandman.model.domain.valueobjects.AmanDmanSettings
import no.vaccsca.amandman.model.domain.valueobjects.SharedStateConnectionParameters
import no.vaccsca.amandman.model.domain.valueobjects.AtcClientConnectionParameters
import no.vaccsca.amandman.model.domain.valueobjects.ConnectionConfig
import no.vaccsca.amandman.model.domain.valueobjects.LabelItem
import no.vaccsca.amandman.model.domain.valueobjects.LabelItemAlignment
import no.vaccsca.amandman.model.domain.valueobjects.LabelItemSource
import no.vaccsca.amandman.model.domain.valueobjects.LatLng
import no.vaccsca.amandman.model.domain.valueobjects.Side
import no.vaccsca.amandman.model.domain.valueobjects.Timeline
import no.vaccsca.amandman.model.domain.valueobjects.RunwayThreshold
import no.vaccsca.amandman.model.domain.valueobjects.Star
import no.vaccsca.amandman.model.domain.valueobjects.StarFix
import no.vaccsca.amandman.model.domain.valueobjects.Theme

fun AmanDmanSettingsYaml.toDomain(): AmanDmanSettings = AmanDmanSettings(
    timelines = timelines.mapValues { entry -> entry.value.map { it.toDomain() } },
    connectionConfig = connectionConfig.toDomain(),
    arrivalLabelLayouts = arrivalLabelLayouts.mapValues { entry -> entry.value.map { it.toDomain() } },
    departureLabelLayouts = departureLabelLayouts?.mapValues { entry -> entry.value.map { it.toDomain() } } ?: emptyMap(),
    theme = theme?.toDomain() ?: Theme.FLATLAF_DARK,
)

fun TimelineYaml.toDomain() = Timeline(
    title = timelineTitle,
    left = left?.toDomain(),
    right = right.toDomain(),
    arrivalLabelLayoutId = arrivalLabelLayoutId,
    departureLabelLayoutId = null // TODO
)

fun SideYaml.toDomain() = Side(runways)

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
    maxLength = maxLen
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
    LabelItemSourceEnumYaml.GROUND_SPEED -> LabelItemSource.GROUND_SPEED
    LabelItemSourceEnumYaml.GROUND_SPEED_10 -> LabelItemSource.GROUND_SPEED_10
    LabelItemSourceEnumYaml.ALTITUDE -> LabelItemSource.ALTITUDE
    LabelItemSourceEnumYaml.TTL_TTG -> LabelItemSource.TTL_TTG
}

fun AirportJson.toDomain(icao: String, stars: StarYamlFile) =
    Airport(
        icao = icao,
        location = LatLng(location.latitude, location.longitude),
        independentRunwaySystems = independentRunwaySystems?.map { it.toSet() } ?: listOf(runwayThresholds.keys),
        runways = runwayThresholds.mapValues { (id, value) ->
            RunwayThreshold(
                id = id,
                latLng = LatLng(
                    value.location.latitude,
                    value.location.longitude
                ),
                elevation = value.elevation,
                trueHeading = value.trueHeading,
                stars = stars.stars.filter { it.runway == id }.map { starYaml ->
                    starYaml.toDomain()
                }
            )
        },
    )

fun StarYamlEntry.toDomain() = Star(
    id = name,
    fixes = waypoints.map {
        StarFix(
            id = it.id,
            typicalAltitude = it.typicalAltitude,
            typicalSpeedIas = it.typicalSpeed
        )
    },
)

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
