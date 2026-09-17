package org.scottishtecharmy.soundscape.geoengine

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.JunctionArms
import org.scottishtecharmy.soundscape.geoengine.utils.WayGraph
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [DynamicBeacon] - the beacon that runs ahead of the user along the road rather than
 * sitting on a destination.
 */
class DynamicBeaconTest {

    private val graph = WayGraph()
    private val ruler = graph.ruler

    private fun beacon(
        mode: DynamicBeaconMode,
        arms: JunctionArms = JunctionArms.NamedRoads,
    ) = DynamicBeacon(mode = { mode }, junctionArms = { arms })

    /**
     * The user [metres] east of the origin, walking east, map-matched to [way].
     *
     * speed is above UserGeometry's in-motion threshold and the travel heading is due east, so
     * cursorOn gets a direction without needing the movement fallback.
     */
    private fun walkingEastAt(metres: Double, way: Way) = UserGeometry(
        location = graph.east(metres),
        speed = 1.4,
        travelHeading = 90.0,
        mapMatchedWay = way,
    )

    /** How far east of the origin the beacon is sitting. */
    private fun eastingOf(location: LngLatAlt?): Double {
        assertNotNull(location)
        return ruler.distance(graph.origin, location)
    }

    // ---------------------------------------------------------------- lead mode

    @Test
    fun leadModeKeepsTheBeaconAheadAlongTheRoad() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, way)))
        assertEquals(35.0, eastingOf(dynamicBeacon.target), 0.5)

        assertTrue(dynamicBeacon.update(walkingEastAt(20.0, way)))
        assertEquals(45.0, eastingOf(dynamicBeacon.target), 0.5)

        assertTrue(dynamicBeacon.update(walkingEastAt(30.0, way)))
        assertEquals(55.0, eastingOf(dynamicBeacon.target), 0.5)
    }

    @Test
    fun leadModeIgnoresAStepTooSmallToMatter() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, way)))
        // A metre of GPS jitter is not a reason to move the beacon and re-do the spatialisation.
        assertFalse(dynamicBeacon.update(walkingEastAt(11.0, way)))
        assertEquals(35.0, eastingOf(dynamicBeacon.target), 0.5)
    }

    /**
     * The headline behaviour. A junction inside the lead distance holds the beacon still while the
     * user closes on it, and the beacon only moves on once the user is matched onto a road beyond
     * it - which is the map matcher answering "which way did they go", not this class.
     */
    @Test
    fun leadModeParksAtAJunctionAndResumesOnTheChosenRoad() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 200.0)
        val side = graph.sideWay("Church Road", 50.0, 120.0)
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            side to WayEnd.START,
        )
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        // Well back: the junction is beyond the lead distance, so the beacon is out on the road.
        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, approach)))
        assertEquals(35.0, eastingOf(dynamicBeacon.target), 0.5)

        // Now the junction is inside the lead distance, and the beacon sits on it...
        assertTrue(dynamicBeacon.update(walkingEastAt(30.0, approach)))
        assertEquals(50.0, eastingOf(dynamicBeacon.target), 0.5)

        // ...and stays there, update after update, as the user walks the last 15 metres.
        for (metres in listOf(35.0, 40.0, 45.0, 48.0)) {
            assertFalse(
                dynamicBeacon.update(walkingEastAt(metres, approach)),
                "beacon should not move while the user closes on the junction"
            )
            assertEquals(50.0, eastingOf(dynamicBeacon.target), 0.5)
        }

        // Through the junction and matched onto High Street again: the beacon runs on ahead.
        assertTrue(dynamicBeacon.update(walkingEastAt(55.0, onward)))
        assertEquals(80.0, eastingOf(dynamicBeacon.target), 0.5)
    }

    @Test
    fun leadModeWalksPastAServiceRoadWhenOnlyNamedRoadsCount() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 200.0)
        val service = graph.sideWay(null, 50.0, 40.0, featureValue = "service")
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            service to WayEnd.START,
        )

        val strict = beacon(DynamicBeaconMode.Lead, JunctionArms.NamedRoads)
        assertTrue(strict.update(walkingEastAt(40.0, approach)))
        assertEquals(65.0, eastingOf(strict.target), 0.5)

        val lenient = beacon(DynamicBeaconMode.Lead, JunctionArms.AnyWay)
        assertTrue(lenient.update(walkingEastAt(40.0, approach)))
        assertEquals(50.0, eastingOf(lenient.target), 0.5)
    }

    // ---------------------------------------------------------------- junction mode

    @Test
    fun junctionModeSitsOnTheNextJunctionAndHopsPastIt() {
        val approach = graph.straightWay("High Street", 0.0, 100.0)
        val middle = graph.straightWay("High Street", 100.0, 300.0)
        val onward = graph.straightWay("High Street", 300.0, 500.0)
        val firstSide = graph.sideWay("Church Road", 100.0, 120.0)
        val secondSide = graph.sideWay("Mill Lane", 300.0, 120.0)
        graph.joinAll(
            graph.east(100.0),
            approach to WayEnd.END,
            middle to WayEnd.START,
            firstSide to WayEnd.START,
        )
        graph.joinAll(
            graph.east(300.0),
            middle to WayEnd.END,
            onward to WayEnd.START,
            secondSide to WayEnd.START,
        )
        val dynamicBeacon = beacon(DynamicBeaconMode.Junction)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, approach)))
        assertEquals(100.0, eastingOf(dynamicBeacon.target), 0.5)

        // Unmoved all the way up to it - the point of the mode is that it marks the junction.
        assertFalse(dynamicBeacon.update(walkingEastAt(50.0, approach)))
        assertFalse(dynamicBeacon.update(walkingEastAt(90.0, approach)))
        assertEquals(100.0, eastingOf(dynamicBeacon.target), 0.5)

        // Through it and onto the next road: one hop, to the junction after.
        assertTrue(dynamicBeacon.update(walkingEastAt(110.0, middle)))
        assertEquals(300.0, eastingOf(dynamicBeacon.target), 0.5)
    }

    // ---------------------------------------------------------------- holding

    @Test
    fun holdsWhenThereIsNoMapMatch() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, way)))
        val held = dynamicBeacon.target

        // Off the network, or the grid is still loading. Silence and a jump to the raw fix are
        // both worse than a beacon that is a moment out of date.
        assertFalse(
            dynamicBeacon.update(
                UserGeometry(
                    location = graph.east(20.0),
                    speed = 1.4,
                    travelHeading = 90.0,
                    mapMatchedWay = null,
                )
            )
        )
        assertEquals(held, dynamicBeacon.target)
    }

    @Test
    fun holdsWhenTheDirectionIsUnknown() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, way)))
        val held = dynamicBeacon.target

        // Standing still, so no travel heading, and no movement to derive a fallback from.
        assertFalse(
            dynamicBeacon.update(
                UserGeometry(
                    location = graph.east(10.0),
                    speed = 0.0,
                    mapMatchedWay = way,
                )
            )
        )
        assertEquals(held, dynamicBeacon.target)
    }

    @Test
    fun aDestinationBeaconWins() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, way)))
        assertNotNull(dynamicBeacon.target)

        // The user has set a destination: the service only has one beacon handle, and the
        // destination is the one they asked for.
        assertTrue(
            dynamicBeacon.update(
                UserGeometry(
                    location = graph.east(20.0),
                    speed = 1.4,
                    travelHeading = 90.0,
                    mapMatchedWay = way,
                    currentBeacon = graph.east(500.0),
                )
            )
        )
        assertNull(dynamicBeacon.target)

        // Once it is stopped, the dynamic beacon starts again from wherever the user now is.
        assertTrue(dynamicBeacon.update(walkingEastAt(30.0, way)))
        assertEquals(55.0, eastingOf(dynamicBeacon.target), 0.5)
    }

    @Test
    fun offMeansOff() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        var mode = DynamicBeaconMode.Lead
        val dynamicBeacon = DynamicBeacon(mode = { mode })

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, way)))
        assertNotNull(dynamicBeacon.target)

        mode = DynamicBeaconMode.Off
        assertTrue(dynamicBeacon.update(walkingEastAt(20.0, way)))
        assertNull(dynamicBeacon.target)
        // ...and stays off, without reporting a change every update.
        assertFalse(dynamicBeacon.update(walkingEastAt(30.0, way)))
    }

    // ---------------------------------------------------------------- hysteresis

    /**
     * A reversed travel heading at walking pace is as likely to be noise as a real about-turn, so
     * it has to be said twice before the beacon swings round behind the user.
     */
    @Test
    fun aReversalIsOnlyTakenOnceConfirmed() {
        val way = graph.straightWay("High Street", 0.0, 400.0)
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(100.0, way)))
        assertEquals(125.0, eastingOf(dynamicBeacon.target), 0.5)

        val walkingWest = UserGeometry(
            location = graph.east(100.0),
            speed = 1.4,
            travelHeading = 270.0,
            mapMatchedWay = way,
        )

        assertFalse(dynamicBeacon.update(walkingWest), "one update is not enough to turn round")
        assertEquals(125.0, eastingOf(dynamicBeacon.target), 0.5)

        assertTrue(dynamicBeacon.update(walkingWest), "a second agreeing update takes it")
        assertEquals(75.0, eastingOf(dynamicBeacon.target), 0.5)
    }

    /**
     * The counterpart: parking at a junction and hopping past it are big moves, but they aren't
     * ambiguous, so they must not be delayed. Covered by the two mode tests above - this pins the
     * distinction so that a future "smooth out the jumps" change has to face it.
     */
    @Test
    fun aLargeMoveInTheSameDirectionIsTakenAtOnce() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 400.0)
        val side = graph.sideWay("Church Road", 50.0, 120.0)
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            side to WayEnd.START,
        )
        val dynamicBeacon = beacon(DynamicBeaconMode.Lead)

        assertTrue(dynamicBeacon.update(walkingEastAt(10.0, approach)))
        assertEquals(35.0, eastingOf(dynamicBeacon.target), 0.5)

        // 35m out on the road to parked on the junction is a 15m move, in one update.
        assertTrue(dynamicBeacon.update(walkingEastAt(30.0, approach)))
        assertEquals(50.0, eastingOf(dynamicBeacon.target), 0.5)
    }
}
