import no.vaccsca.amandman.common.domain.valueobjects.AircraftPerformance
import no.vaccsca.amandman.common.domain.valueobjects.LatLng
import no.vaccsca.amandman.common.domain.valueobjects.Runway
import no.vaccsca.amandman.common.domain.valueobjects.Star
import no.vaccsca.amandman.common.domain.valueobjects.StarFix

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

val star19LEseba4M =
    Star(
        id="ESEBA4M",
        fixes= listOf(
            StarFix(id="ESEBA", typicalAltitude=null, typicalSpeedIas=250),
            StarFix(id="GM422", typicalAltitude=10000, typicalSpeedIas=220),
            StarFix(id="TITLA", typicalAltitude=5000, typicalSpeedIas=200),
            StarFix(id="OSPAD", typicalAltitude=4000, typicalSpeedIas=180),
            StarFix(id="XIVTA", typicalAltitude=3500, typicalSpeedIas=170),
            StarFix(id="ENGM", typicalAltitude=700, typicalSpeedIas=null)
        )
    )


val star19LAdopi3M =
    Star(
        id="ADOPI3M",
        fixes=listOf(
            StarFix(id="ADOPI", typicalAltitude=null, typicalSpeedIas=250),
            StarFix(id="GM428", typicalAltitude=10000, typicalSpeedIas=220),
            StarFix(id="BAVAD", typicalAltitude=5000, typicalSpeedIas=200),
            StarFix(id="OSPAD", typicalAltitude=4000, typicalSpeedIas=180),
            StarFix(id="XIVTA", typicalAltitude=3500, typicalSpeedIas=170),
            StarFix(id="ENGM", typicalAltitude=700, typicalSpeedIas=null)
        )
    )


val star19LInrex4M = Star(
    id="INREX4M",
    fixes= listOf(
        StarFix(id="INREX", typicalAltitude=null, typicalSpeedIas=250),
        StarFix(id="GM418", typicalAltitude=11000, typicalSpeedIas=220),
        StarFix(id="TITLA", typicalAltitude=5000, typicalSpeedIas=200),
        StarFix(id="OSPAD", typicalAltitude=4000, typicalSpeedIas=180),
        StarFix(id="XIVTA", typicalAltitude=3500, typicalSpeedIas=170),
        StarFix(id="ENGM", typicalAltitude=700, typicalSpeedIas=null)
    )
)

val rwy19L = Runway(
    "19L",
    location = LatLng(60.20116653568569,11.12244616482607),
    elevation = 681f,
    trueHeading = 194f,
    stars=listOf(star19LInrex4M, star19LEseba4M, star19LAdopi3M)
)
