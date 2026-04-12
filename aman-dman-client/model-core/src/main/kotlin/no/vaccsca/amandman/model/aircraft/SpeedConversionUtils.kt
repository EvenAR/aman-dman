package no.vaccsca.amandman.model.aircraft

import no.vaccsca.amandman.model.weather.WindVector
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SpeedConversionUtils {
    private const val P0_INHG = 29.92126          // Sea-level pressure [inHg]
    private const val CS0 = 661.4786               // Speed of sound at SL [kt]
    private const val LAPSE = 6.8755856e-6         // T'/T0
    private const val EXP_P = 5.2558797

    /**
     * Convert IAS (≈CAS) to TAS using compressible flow equations
     * and true ambient temperature (OAT).
     *
     * @param iasKnots Indicated airspeed [kt]
     * @param pressureAltitudeFt Pressure altitude [ft]
     * @param oatC Outside / static air temperature [°C] (NOAA)
     */
    fun iasToTAS(
        iasKnots: Int,
        pressureAltitudeFt: Int,
        oatC: Int
    ): Int {
        // --- Pressure at altitude (inHg)
        val pressureRatio = 1.0 - LAPSE * pressureAltitudeFt
        val pressure = P0_INHG * pressureRatio.pow(EXP_P)

        // --- Dynamic pressure from IAS
        val dp = P0_INHG * ((1.0 + 0.2 * (iasKnots / CS0).pow(2.0)).pow(3.5) - 1.0)

        // --- Mach number (subsonic pitot equation)
        val mach = sqrt(5.0 * ((dp / pressure + 1.0).pow(2.0 / 7.0) - 1.0))

        // --- Speed of sound at altitude (from OAT)
        val cs = 38.967854 * sqrt(oatC + 273.15)

        // --- True airspeed
        return (mach * cs).roundToInt()
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
     * Convert TAS to IAS (≈CAS) using compressible flow equations
     * and true ambient temperature (OAT).
     *
     * @param tasKnots True airspeed [kt]
     * @param pressureAltitudeFt Pressure altitude [ft]
     * @param oatC Outside / static air temperature [°C] (NOAA)
     */
    fun tasToIAS(
        tasKnots: Int,
        pressureAltitudeFt: Int,
        oatC: Int
    ): Int {

        // --- Speed of sound at altitude
        val cs = 38.967854 * sqrt(oatC + 273.15)

        // --- Mach number
        val mach = tasKnots / cs

        require(mach <= 1.0) {
            "Supersonic TAS→IAS not supported (M=$mach)"
        }

        // --- Pressure ratio term
        val x = (1.0 - LAPSE * pressureAltitudeFt).pow(EXP_P)

        // --- IAS from Mach (subsonic pitot equation)
        return (CS0 * sqrt(
            5.0 * (
                    (1.0 + x * ((1.0 + mach * mach / 5.0).pow(3.5) - 1.0))
                        .pow(2.0 / 7.0) - 1.0
                    )
        )).roundToInt()
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