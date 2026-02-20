package no.vaccsca.amandman.model.config.yaml

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class AircraftPerformanceConfigYaml(
    @field:NotNull
    @field:Valid
    @field:JsonPropertyDescription("Map of aircraft type codes to performance parameters")
    val aircraft: Map<@NotEmpty String, @Valid AircraftPerformanceYaml>
)

data class AircraftPerformanceYaml(
    @field:Min(0)
    @field:JsonPropertyDescription("Takeoff V2 speed in knots")
    val takeOffV2: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Takeoff distance in meters")
    val takeOffDistance: Int? = null,

    @field:JsonPropertyDescription("Takeoff Weight Category (A/B/C/D)")
    val takeOffWTC: Char,

    @field:JsonPropertyDescription("RECAT code for takeoff weight")
    val takeOffRECAT: String,

    @field:Min(0)
    @field:JsonPropertyDescription("Maximum Takeoff Weight in kg")
    val takeOffMTOW: Int,

    @field:Min(0)
    @field:JsonPropertyDescription("Initial climb indicated airspeed in knots")
    val initialClimbIAS: Int? = null,

    @field:JsonPropertyDescription("Initial climb rate of climb in ft/min")
    val initialClimbROC: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Climb speed at 1500 ft in IAS knots")
    val climb150IAS: Int? = null,

    @field:JsonPropertyDescription("Climb rate at 1500 ft in ft/min")
    val climb150ROC: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Climb speed at 2400 ft in IAS knots")
    val climb240IAS: Int? = null,

    @field:JsonPropertyDescription("Climb rate at 2400 ft in ft/min")
    val climb240ROC: Int? = null,

    @field:JsonPropertyDescription("Climb Mach number")
    val machClimbMACH: Float? = null,

    @field:JsonPropertyDescription("Climb rate of climb at Mach")
    val machClimbROC: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Cruise True Airspeed in knots")
    val cruiseTAS: Int,

    @field:JsonPropertyDescription("Cruise Mach number")
    val cruiseMACH: Float? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Cruise ceiling in feet")
    val cruiseCeiling: Int,

    @field:Min(0)
    @field:JsonPropertyDescription("Cruise range in nautical miles")
    val cruiseRange: Int? = null,

    @field:JsonPropertyDescription("Initial descent Mach number")
    val initialDescentMACH: Float? = null,

    @field:JsonPropertyDescription("Initial descent rate of descent in ft/min")
    val initialDescentROD: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Descent indicated airspeed in knots")
    val descentIAS: Int,

    @field:JsonPropertyDescription("Descent rate of descent in ft/min")
    val descentROD: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Approach indicated airspeed in knots")
    val approachIAS: Int,

    @field:JsonPropertyDescription("Approach rate of descent in ft/min")
    val approachROD: Int? = null,

    @field:JsonPropertyDescription("Approach Mach number")
    val approachMCS: Int? = null,

    @field:Min(0)
    @field:JsonPropertyDescription("Landing VAT speed in knots")
    val landingVat: Int,

    @field:Min(0)
    @field:JsonPropertyDescription("Landing distance in meters")
    val landingDistance: Int? = null,

    @field:JsonPropertyDescription("Landing APC code")
    val landingAPC: String
)
