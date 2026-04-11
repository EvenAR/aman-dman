package no.vaccsca.amandman.model

import no.vaccsca.amandman.model.aircraft.AircraftPerformance
import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.airport.RunwayArrivalProfile
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.navigation.LatLng

val b738performance = AircraftPerformance(
    takeOffV2 = 145,
    takeOffDistance = 2300,
    takeOffWTC = 'M',
    takeOffRECAT = "Upper Medium",
    takeOffMTOW = 70530,
    initialClimbIAS = 165,
    initialClimbROC = 3000,
    climb150IAS = 290,
    climb150ROC = 2000,
    climb240IAS = 290,
    climb240ROC = 2000,
    machClimbMACH = 0.78f,
    machClimbROC = 1500,
    cruiseTAS = 460,
    cruiseMACH = 0.79f,
    cruiseCeiling = 41000, // in feet
    cruiseRange = 2000, // in nautical miles
    initialDescentMACH = 0.78f,
    initialDescentROD = 800, // in feet per minute
    descentIAS = 280,
    descentROD = 3500, // in feet per minute
    approachIAS = 250,
    approachROD = 1500, // in feet per minute
    approachMCS = 210, // minimum clean speed
    landingVat = 147, // Vapp
    landingDistance = 1600, // in meters
    landingAPC = "D" // Approach Category D
)

val rwy19LArrivalFixExpectations = listOf(
    arrivalFixExpectation("ADOPI", typicalSpeedIas = 250),
    arrivalFixExpectation("BAVAD", typicalAltitude = 5000, typicalSpeedIas = 200),
    arrivalFixExpectation("ENGM", typicalAltitude = 700),
    arrivalFixExpectation("ESEBA", typicalSpeedIas = 250),
    arrivalFixExpectation("GM418", typicalAltitude = 11000, typicalSpeedIas = 220),
    arrivalFixExpectation("GM422", typicalAltitude = 10000, typicalSpeedIas = 220),
    arrivalFixExpectation("GM428", typicalAltitude = 10000, typicalSpeedIas = 220),
    arrivalFixExpectation("INREX", typicalSpeedIas = 250),
    arrivalFixExpectation("OSPAD", typicalAltitude = 4000, typicalSpeedIas = 180),
    arrivalFixExpectation("TITLA", typicalAltitude = 5000, typicalSpeedIas = 200),
    arrivalFixExpectation("XIVTA", typicalAltitude = 3500, typicalSpeedIas = 170),
)

val rwy19L = RunwayThreshold(
    "19L",
    latLng = LatLng(60.20116653568569,11.12244616482607),
    elevation = 681f,
    trueHeading = 194f,
    arrivalProfiles = listOf(
        RunwayArrivalProfile(
            arrivalNamePattern = "*",
            fixExpectations = rwy19LArrivalFixExpectations,
        )
    ),
)

private fun arrivalFixExpectation(
    fixName: String,
    typicalAltitude: Int? = null,
    typicalSpeedIas: Int? = null,
) = ArrivalFixExpectation(
    fixName = fixName,
    role = null,
    typicalAltitude = typicalAltitude,
    typicalSpeedIas = typicalSpeedIas,
)
