package org.scottishtecharmy.soundscape.geoengine.callouts

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.OsmRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.getDestinationCoordinate
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowedRouteTrackerTest {
    private val red = FollowedRoute(2866086, "Loch Ard Sculpture Trail")
    private val origin = LngLatAlt(-4.42, 56.18)
    private val ruler = CheapRuler(origin.latitude)

    private val onRoute = Way().apply {
        routes = listOf(OsmRoute(2866086, "foot", network = "lwn", name = "Loch Ard Sculpture Trail"))
    }
    private val offRoute = Way().apply {
        routes = listOf(OsmRoute(2866087, "foot", network = "lwn", name = "Ben Lomond Viewpoint"))
    }

    /** Walks north in 5m steps on [ways] in turn, returning what changed at each step. */
    private fun walk(tracker: FollowedRouteTracker, vararg ways: Way?, route: FollowedRoute? = red) =
        ways.mapIndexed { step, way ->
            tracker.update(route, getDestinationCoordinate(origin, 0.0, step * 5.0), way, ruler)
        }

    @Test
    fun leavingIsSaidOnceAfterThirtyMetresOffTheRoute() {
        val changes = walk(FollowedRouteTracker(), onRoute, onRoute, *Array(10) { offRoute })
        // Six 5m steps off the route make 30m
        assertEquals(listOf(FollowedRouteTracker.Change.LEFT), changes.filterNotNull())
        assertEquals(FollowedRouteTracker.Change.LEFT, changes[7])
    }

    @Test
    fun aWobbleOntoASidePathIsNotLeaving() {
        val changes = walk(FollowedRouteTracker(), onRoute, offRoute, offRoute, onRoute, offRoute, onRoute)
        assertEquals(emptyList(), changes.filterNotNull())
    }

    @Test
    fun comingBackIsSaid() {
        val changes = walk(FollowedRouteTracker(), onRoute, *Array(8) { offRoute }, onRoute, onRoute)
        assertEquals(
            listOf(FollowedRouteTracker.Change.LEFT, FollowedRouteTracker.Change.BACK),
            changes.filterNotNull()
        )
    }

    @Test
    fun nothingIsSaidBeforeReachingTheRoute() {
        // Setting off from the car park
        assertEquals(emptyList(), walk(FollowedRouteTracker(), *Array(10) { offRoute }).filterNotNull())
    }

    @Test
    fun aLostMatchCountsForNeither() {
        val changes = walk(FollowedRouteTracker(), onRoute, *Array(10) { null }, onRoute)
        assertEquals(emptyList(), changes.filterNotNull())
    }

    @Test
    fun choosingAnotherRouteStartsAgain() {
        val tracker = FollowedRouteTracker()
        walk(tracker, onRoute, *Array(8) { offRoute })
        val other = FollowedRoute(2866087, "Ben Lomond Viewpoint")
        assertEquals(emptyList(), walk(tracker, offRoute, offRoute, route = other).filterNotNull())
    }
}
