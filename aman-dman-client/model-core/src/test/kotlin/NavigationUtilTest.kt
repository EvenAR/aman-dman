import no.vaccsca.amandman.model.navigation.NavigationUtils.isBehind
import no.vaccsca.amandman.model.navigation.LatLng
import kotlin.test.Test

class NavigationUtilTest {


    @Test
    fun `Test that `() {

        val aircraftPosition = LatLng(0.0, 0.0)

        val waypointNorth = LatLng(1.0, 0.0)

        val behindWhenFlyingEast = waypointNorth.isBehind(aircraftPosition, trackDeg = 90)
        val behindWhenFlyingWest = waypointNorth.isBehind(aircraftPosition, trackDeg = 270)
        val behindWhenFlyingNorth = waypointNorth.isBehind(aircraftPosition, trackDeg = 0)
        val behindWhenFlyingSouth = waypointNorth.isBehind(aircraftPosition, trackDeg = 180)

        assert(behindWhenFlyingEast)
        assert(behindWhenFlyingWest)
        assert(!behindWhenFlyingNorth)  // False, since waypoint is in front of aircraft when flying north
        assert(behindWhenFlyingSouth)
    }

}