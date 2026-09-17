package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [pointAhead] and [nextJunctionAhead] - "where is the point 25 metres up the road?" and
 * "where does the road next fork?", the two queries a beacon that runs ahead of the user is built
 * on.
 */
class PointAheadTest {

    private val graph = WayGraph()
    private val ruler = graph.ruler

    /** How far east of the origin a result landed, which is how the fixtures are laid out. */
    private fun eastingOf(point: PointAhead): Double =
        ruler.distance(graph.origin, point.location)

    // ---------------------------------------------------------------- a single Way

    @Test
    fun pointAheadOnOneWayForwards() {
        val way = graph.straightWay("High Street", 0.0, 100.0)
        val cursor = WayCursor(way, 10.0, forwards = true)

        val ahead = pointAhead(cursor, 25.0, ruler)

        assertTrue(ahead is PointAhead.OnWay)
        assertEquals(35.0, eastingOf(ahead), 0.1)
        assertEquals(35.0, ahead.distanceFromStart, 0.1)
        assertEquals(25.0, ahead.distance, 0.1)
        assertTrue(ahead.forwards)
    }

    @Test
    fun pointAheadOnOneWayBackwards() {
        val way = graph.straightWay("High Street", 0.0, 100.0)
        val cursor = WayCursor(way, 60.0, forwards = false)

        val ahead = pointAhead(cursor, 25.0, ruler)

        assertTrue(ahead is PointAhead.OnWay)
        // Travelling towards START, so 25m "ahead" is 25m *west*.
        assertEquals(35.0, eastingOf(ahead), 0.1)
        assertEquals(35.0, ahead.distanceFromStart, 0.1)
    }

    @Test
    fun noDirectionGivesNoAnswer() {
        val way = graph.straightWay("High Street", 0.0, 100.0)

        // Stationary, with no travel heading and no fallback: the walk would have to guess which
        // way the user is facing, and a beacon is better off holding where it was.
        assertNull(pointAhead(WayCursor(way, 50.0, forwards = null), 25.0, ruler))
    }

    // ---------------------------------------------------------------- crossing between Ways

    @Test
    fun pointAheadCrossesOntoTheNextWay() {
        val first = graph.straightWay("High Street", 0.0, 50.0)
        val second = graph.straightWay("High Street", 50.0, 150.0)
        graph.join(first, second)

        val ahead = pointAhead(WayCursor(first, 40.0, forwards = true), 25.0, ruler)

        assertTrue(ahead is PointAhead.OnWay)
        assertEquals(65.0, eastingOf(ahead), 0.1)
        assertEquals(second, ahead.way)
        assertEquals(15.0, ahead.distanceFromStart, 0.1)
        assertTrue(ahead.forwards)
    }

    /**
     * The direction flip. Two pieces of one road digitised towards each other meet END to END, so
     * a walk going forwards along the first goes *backwards* along the second - and the point's
     * distanceFromStart has to come back in the second Way's own frame, never negated.
     */
    @Test
    fun pointAheadFlipsDirectionOnAReversedWay() {
        val first = graph.straightWay("High Street", 0.0, 50.0)
        val second = graph.reversedWay("High Street", 50.0, 150.0)
        graph.joinEndToEnd(first, second)

        val ahead = pointAhead(WayCursor(first, 40.0, forwards = true), 25.0, ruler)

        assertTrue(ahead is PointAhead.OnWay)
        assertEquals(65.0, eastingOf(ahead), 0.1)
        assertEquals(second, ahead.way)
        // second runs east-to-west, so its START is at 150m east: the point 65m east is 85m along.
        assertEquals(85.0, ahead.distanceFromStart, 0.1)
        // The cursor was going forwards; this Way is being crossed backwards.
        assertTrue(!ahead.forwards)
    }

    @Test
    fun pointAheadWalksThroughATileEdgeJoiner() {
        val first = graph.straightWay("High Street", 0.0, 50.0)
        val joiner = graph.straightWay(null, 50.0, 50.0).apply { wayType = WayType.JOINER }
        val second = graph.straightWay("High Street", 50.0, 150.0)
        graph.join(first, joiner)
        graph.join(joiner, second)

        val ahead = pointAhead(WayCursor(first, 40.0, forwards = true), 25.0, ruler)

        assertTrue(ahead is PointAhead.OnWay)
        assertEquals(65.0, eastingOf(ahead), 0.1)
        assertEquals(second, ahead.way)
    }

    // ---------------------------------------------------------------- stopping at junctions

    @Test
    fun parksAtANamedJunction() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 150.0)
        val side = graph.sideWay("Church Road", 50.0, 80.0)
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            side to WayEnd.START,
        )

        val ahead = pointAhead(WayCursor(approach, 40.0, forwards = true), 25.0, ruler)

        assertTrue(ahead is PointAhead.AtJunction)
        assertEquals(50.0, eastingOf(ahead), 0.1)
        // Stopped 10m short of the 25m asked for, and that distance is what got walked.
        assertEquals(10.0, ahead.distance, 0.1)
        assertTrue(!ahead.deadEnd)
    }

    @Test
    fun parksAtADeadEnd() {
        val way = graph.straightWay("Cul De Sac", 0.0, 50.0)

        val ahead = pointAhead(WayCursor(way, 40.0, forwards = true), 25.0, ruler)

        assertTrue(ahead is PointAhead.AtJunction)
        assertEquals(50.0, eastingOf(ahead), 0.1)
        assertEquals(10.0, ahead.distance, 0.1)
        assertTrue(ahead.deadEnd)
    }

    @Test
    fun nextJunctionAheadIsNullWhenTheRoadCarriesOn() {
        val first = graph.straightWay("High Street", 0.0, 50.0)
        val second = graph.straightWay("High Street", 50.0, 400.0)
        graph.join(first, second)

        assertNull(nextJunctionAhead(WayCursor(first, 10.0, forwards = true), 250.0, ruler))
    }

    @Test
    fun nextJunctionAheadFindsAJunctionWellBeyondTheLeadDistance() {
        val approach = graph.straightWay("High Street", 0.0, 120.0)
        val onward = graph.straightWay("High Street", 120.0, 300.0)
        val side = graph.sideWay("Church Road", 120.0, 80.0)
        graph.joinAll(
            graph.east(120.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            side to WayEnd.START,
        )

        val junction = nextJunctionAhead(WayCursor(approach, 20.0, forwards = true), 250.0, ruler)

        assertNotNull(junction)
        assertEquals(120.0, eastingOf(junction), 0.1)
        assertEquals(100.0, junction.distance, 0.1)
    }

    // ---------------------------------------------------------------- JunctionArms

    /**
     * The setting's whole point: a service road joining a named road is somewhere to walk past,
     * not somewhere for a beacon to wait.
     */
    @Test
    fun namedRoadsOnlyWalksPastAServiceRoad() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 150.0)
        val service = graph.sideWay(null, 50.0, 40.0, featureValue = "service")
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            service to WayEnd.START,
        )
        val cursor = WayCursor(approach, 40.0, forwards = true)

        val strict = pointAhead(cursor, 25.0, ruler, junctionArms = JunctionArms.NamedRoads)
        assertTrue(strict is PointAhead.OnWay)
        assertEquals(65.0, eastingOf(strict), 0.1)

        // The permissive setting treats the same node as a fork and stops there.
        val lenient = pointAhead(cursor, 25.0, ruler, junctionArms = JunctionArms.AnyWay)
        assertTrue(lenient is PointAhead.AtJunction)
        assertEquals(50.0, eastingOf(lenient), 0.1)
    }

    @Test
    fun namedRoadsOnlyWalksPastAFootpathAndATrack() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 150.0)
        val path = graph.sideWay("Kelvin Walkway", 50.0, 40.0, featureValue = "path")
        val track = graph.sideWay(null, 50.0, 40.0, featureValue = "track")
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            path to WayEnd.START,
            track to WayEnd.START,
        )

        val ahead = pointAhead(
            WayCursor(approach, 40.0, forwards = true),
            25.0,
            ruler,
            junctionArms = JunctionArms.NamedRoads,
        )

        // Named, but a footpath is still not a decision point for a beacon following a road.
        assertTrue(ahead is PointAhead.OnWay)
        assertEquals(65.0, eastingOf(ahead), 0.1)
    }

    @Test
    fun namedRoadsOnlyWalksPastAnUnnamedStub() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 150.0)
        val stub = graph.sideWay(null, 50.0, 20.0)
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            stub to WayEnd.START,
        )

        val ahead = pointAhead(
            WayCursor(approach, 40.0, forwards = true),
            25.0,
            ruler,
            junctionArms = JunctionArms.NamedRoads,
        )

        assertTrue(ahead is PointAhead.OnWay)
        assertEquals(65.0, eastingOf(ahead), 0.1)
    }

    @Test
    fun namedRoadsOnlyStillStopsAtARealCrossroads() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val onward = graph.straightWay("High Street", 50.0, 150.0)
        val north = graph.sideWay("Church Road", 50.0, 80.0)
        val south = graph.sideWay("Church Road", 50.0, -80.0)
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            onward to WayEnd.START,
            north to WayEnd.START,
            south to WayEnd.START,
        )

        val ahead = pointAhead(
            WayCursor(approach, 40.0, forwards = true),
            25.0,
            ruler,
            junctionArms = JunctionArms.NamedRoads,
        )

        assertTrue(ahead is PointAhead.AtJunction)
        assertEquals(50.0, eastingOf(ahead), 0.1)
    }

    /**
     * A named road ending at a T of another named road is a junction under both settings - there
     * are two counting arms and no "straight on".
     */
    @Test
    fun stopsAtATJunction() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val north = graph.sideWay("Church Road", 50.0, 80.0)
        val south = graph.sideWay("Church Road", 50.0, -80.0)
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            north to WayEnd.START,
            south to WayEnd.START,
        )

        for (arms in JunctionArms.entries) {
            val ahead = pointAhead(
                WayCursor(approach, 40.0, forwards = true),
                25.0,
                ruler,
                junctionArms = arms,
            )
            assertTrue(ahead is PointAhead.AtJunction, "$arms should stop at a T junction")
            assertEquals(50.0, eastingOf(ahead), 0.1)
        }
    }

    @Test
    fun aWalkAlongARoadDoesNotTurnOntoAPavement() {
        val approach = graph.straightWay("High Street", 0.0, 50.0)
        val pavement = graph.sideWay(null, 50.0, 60.0, featureValue = "footway").apply {
            properties = hashMapOf("footway" to "sidewalk")
        }
        graph.joinAll(
            graph.east(50.0),
            approach to WayEnd.END,
            pavement to WayEnd.START,
        )

        val ahead = pointAhead(WayCursor(approach, 40.0, forwards = true), 25.0, ruler)

        // The pavement is the only candidate, and it isn't one - so the road has ended.
        assertTrue(ahead is PointAhead.AtJunction)
        assertTrue(ahead.deadEnd)
        assertEquals(50.0, eastingOf(ahead), 0.1)
    }

    // ---------------------------------------------------------------- pathological graphs

    @Test
    fun aLoopTerminates() {
        val a = graph.straightWay("Ring Road", 0.0, 30.0)
        val b = graph.straightWay("Ring Road", 30.0, 60.0)
        val c = graph.straightWay("Ring Road", 60.0, 90.0)
        graph.join(a, b)
        graph.join(b, c)
        // c's END loops straight back to a's START.
        graph.joinAll(graph.east(0.0), c to WayEnd.END, a to WayEnd.START)

        // Far more than the loop is long: without the visited set this would never return.
        val ahead = pointAhead(WayCursor(a, 10.0, forwards = true), 10_000.0, ruler)

        // A loop is not an honest answer to "where is the point 10km ahead?", so there is none.
        assertNull(ahead)
    }
}
