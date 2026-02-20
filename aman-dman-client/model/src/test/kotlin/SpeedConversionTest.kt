import no.vaccsca.amandman.model.weather.WindVector
import no.vaccsca.amandman.model.aircraft.SpeedConversionUtils
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedConversionTest {


    @Test
    fun`Convert IAS to TAS`() {

        assertEquals(
            107,
            SpeedConversionUtils.iasToTAS(iasKnots = 100, pressureAltitudeFt = 3000, oatC = 20)
        )

        assertEquals(
            314,
            SpeedConversionUtils.iasToTAS(iasKnots = 250, pressureAltitudeFt = 15000, oatC = -10)
        )

        assertEquals(
            481,
            SpeedConversionUtils.iasToTAS(iasKnots = 275, pressureAltitudeFt = 37000, oatC = -58)
        )

        assertEquals(
            452,
            SpeedConversionUtils.iasToTAS(iasKnots = 244, pressureAltitudeFt = 39000, oatC = -57)
        )

        assertEquals(
            374,
            SpeedConversionUtils.iasToTAS(iasKnots = 279, pressureAltitudeFt = 20450, oatC = -28)
        )
    }

    @Test
    fun`Convert TAS to IAS`() {
        assertEquals(
            100,
            SpeedConversionUtils.tasToIAS(tasKnots = 107, pressureAltitudeFt = 3000, oatC = 20)
        )

        assertEquals(
            253,
            SpeedConversionUtils.tasToIAS(tasKnots = 318, pressureAltitudeFt = 15000, oatC = -10)
        )

        assertEquals(
            296,
            SpeedConversionUtils.tasToIAS(tasKnots = 364, pressureAltitudeFt = 15625, oatC = -24)
        )

        assertEquals(
            257,
            SpeedConversionUtils.tasToIAS(tasKnots = 444, pressureAltitudeFt = 36000, oatC = -58)
        )
    }

    @Test
    fun `Convert MACH to IAS`() {
        val ias = SpeedConversionUtils.machToIAS(mach = 0.783f, altitudeFt = 37000, satCelsius = -57).toDouble()
        assertEquals(254.0, ias, 3.0)

        val ias2 = SpeedConversionUtils.machToIAS(mach = 0.416f, altitudeFt = 7000, satCelsius = -6).toDouble()
        assertEquals(240.0, ias2, 3.0)

        val ias3 = SpeedConversionUtils.machToIAS(mach = 0.78f, altitudeFt = 39000, satCelsius = -58).toDouble()
        assertEquals(241.0, ias3, 3.0)
    }

    @Test
    fun `Convert TAS to CAS at sea level with standard temp`() {
        val cas = SpeedConversionUtils.tasToCAS(tasKts = 450.0, altitudeFt = 35000, satCelsius = -56)
        assertEquals(265.0, cas, 1.0)
    }

    @Test
    fun `Convert GS to TAS based on wind`() {
        // No wind
        assertEquals(
            100,
            SpeedConversionUtils.gsToTAS(gs = 100, windVector = WindVector(0, 0), track = 0)
        )

        // Headwind
        assertEquals(
            110,
            SpeedConversionUtils.gsToTAS(gs = 100, windVector = WindVector(0, 10), track = 0)
        )

        // Tailwind
        assertEquals(
            90,
            SpeedConversionUtils.gsToTAS(gs = 100, windVector = WindVector(180, 10), track = 0)
        )

        // Crosswind
        assertEquals(
            100.0,
            SpeedConversionUtils.gsToTAS(gs = 100, windVector = WindVector(90, 10), track = 0).toDouble(),
            1.0
        )
    }

    @Test
    fun `Convert TAS to GS`() {
        // No wind
        assertEquals(
            100,
            SpeedConversionUtils.tasToGS(tas = 100, windVector = WindVector(0, 0), track = 0)
        )

        // Headwind
        assertEquals(
            90,
            SpeedConversionUtils.tasToGS(tas = 100, windVector = WindVector(0, 10), track = 0)
        )

        // Tailwind
        assertEquals(
            110,
            SpeedConversionUtils.tasToGS(tas = 100, windVector = WindVector(180, 10), track = 0)
        )

        // Crosswind
        assertEquals(
            100.0,
            SpeedConversionUtils.tasToGS(tas = 100, windVector = WindVector(90, 10), track = 0).toDouble(),
            1.0
        )
    }

}