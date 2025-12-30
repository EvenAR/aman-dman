package no.vaccsca.amandman.backend.domain.util

import no.vaccsca.amandman.common.domain.valueobjects.weather.WindVector
import kotlin.div
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.times

object SpeedConversionUtils {
    /**
     * Convert Indicated Airspeed (IAS) to True Airspeed (TAS)
     */
    fun iasToTAS(ias: Int, altitudeFt: Int, tempCelsius: Int): Int {
        val tempKelvin = tempCelsius + 273.15
        val altitudeMeters = altitudeFt * 0.3048

        // Constants
        val P0 = 101325.0 // Sea level pressure in Pascals
        val T0 = 288.15   // Sea level temperature in Kelvin
        val L = 0.0065    // Temperature lapse rate (K/m)
        val R = 287.05    // Specific gas constant for dry air (J/kg·K)
        val g = 9.80665   // Gravity (m/s²)

        // ISA Pressure at altitude
        val pressure = P0 * (1 - (L * altitudeMeters) / T0).pow(g / (R * L))

        // Air density at altitude
        val rho = pressure / (R * tempKelvin)
        val rho0 = P0 / (R * T0)

        // TAS estimation
        return (ias * sqrt(rho0 / rho)).roundToInt()
    }

    /**
     * Convert Mach number to Indicated Airspeed (IAS)
     *
     * TODO: Improve accuracy
     *
     * @param mach Mach number
     * @param altitudeFt Altitude in feet
     * @param satCelsius Static air temperature in Celsius (this is what the weather forecast provides)
     */
    fun machToIAS(mach: Float, altitudeFt: Int, satCelsius: Int): Int {
        val satKelvin = satCelsius + 273.15
        val speedOfSound = sqrt(1.4 * 287.05 * satKelvin)  // Use SAT directly
        val tas = mach * speedOfSound
        val tasKts = tas * 1.94384
        val cas = tasToCAS(tasKts, altitudeFt, satCelsius)
        return cas.roundToInt()
    }

    /**
     * Convert True Airspeed (TAS) to Calibrated Airspeed (CAS)
     */
    fun tasToCAS(tasKts: Double, altitudeFt: Int, satCelsius: Int): Double {
        // Constants
        val gamma = 1.4
        val R = 287.05            // J/(kg·K)
        val P0 = 101325.0         // sea-level standard pressure, Pa
        val T0 = 288.15           // sea-level standard temperature, K
        val rho0 = P0 / (R * T0)  // sea-level density, kg/m³

        // Convert inputs
        val tas = tasKts / 1.94384           // knots → m/s
        val altitudeM = altitudeFt * 0.3048  // ft → m
        val T = satCelsius + 273.15          // SAT in Kelvin

        // Pressure at altitude (ISA)
        val L = 0.0065  // lapse rate K/m
        val P = P0 * (1 - (L * altitudeM) / T0).pow(5.2561)

        // Speed of sound at altitude
        val a = sqrt(gamma * R * T)

        // Mach number from TAS (not EAS)
        val M = tas / a

        // Impact pressure q_c using ambient pressure P
        val qc = P * ((1 + (gamma - 1) / 2 * M * M).pow(gamma / (gamma - 1)) - 1)

        // Calibrated Airspeed from impact pressure (using P0)
        val cas = sqrt((2 * gamma * P0) / ((gamma - 1) * rho0) * ((qc / P0 + 1).pow((gamma - 1) / gamma) - 1))

        return cas * 1.94384 // m/s → knots
    }

    /**
     * Convert True Airspeed (TAS) to Indicated Airspeed (IAS)
     */
    fun tasToIAS(tas: Int, altitudeFt: Int, tempCelsius: Int): Int {
        val tempKelvin = tempCelsius + 273.15
        val altitudeMeters = altitudeFt * 0.3048

        // Constants
        val P0 = 101325.0
        val T0 = 288.15
        val L = 0.0065
        val R = 287.05
        val g = 9.80665

        // ISA pressure at altitude
        val pressure = P0 * (1 - (L * altitudeMeters) / T0).pow(g / (R * L))

        // Air density
        val rho = pressure / (R * tempKelvin)
        val rho0 = P0 / (R * T0)

        // IAS = TAS * sqrt(ρ / ρ0)
        return (tas * sqrt(rho / rho0)).roundToInt()
    }

    /**
     * Convert Ground Speed (GS) to True Airspeed (TAS) based on wind direction and flown track
     */
    fun gsToTAS(gs: Int, windVector: WindVector, track: Int): Int {
        val windAngleRad = Math.toRadians((windVector.directionDeg - track).toDouble())
        val windSpeed = windVector.speedKts.toDouble()
        val gsDouble = gs.toDouble()

        // Quadratic equation: TAS² - 2*TAS*wind*cos(angle) + wind² - GS² = 0
        val a = 1.0
        val b = -2 * windSpeed * cos(windAngleRad)
        val c = windSpeed.pow(2) - gsDouble.pow(2)

        val discriminant = b * b - 4 * a * c

        if (discriminant < 0) {
            // No real solution, return GS as fallback
            return gs
        }

        val tas = (-b + sqrt(discriminant)) / (2 * a)
        return tas.roundToInt()
    }

    /**
     * Convert True Airspeed (TAS) to Ground Speed (GS) based on wind direction and flown track
     */
    fun tasToGS(tas: Int, windVector: WindVector, track: Int): Int {
        val windAngleRad = Math.toRadians((windVector.directionDeg - track).toDouble())
        val windSpeed = windVector.speedKts.toDouble()

        val gs = sqrt(
            tas.toDouble().pow(2) + windSpeed.pow(2) -
                    2 * tas * windSpeed * cos(windAngleRad)
        )

        return gs.roundToInt()
    }
}