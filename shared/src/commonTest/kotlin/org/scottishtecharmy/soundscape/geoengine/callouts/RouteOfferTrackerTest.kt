package org.scottishtecharmy.soundscape.geoengine.callouts

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.OsmRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.getDestinationCoordinate
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteOfferTrackerTest {
    private val origin = LngLatAlt(-4.42, 56.18)
    private val ruler = CheapRuler(origin.latitude)

    private val red = OsmRoute(2866086, "foot", network = "lwn", name = "Loch Ard Sculpture Trail")
    private val blue = OsmRoute(2866087, "foot", network = "lwn", name = "Ben Lomond Viewpoint")
    private val cycle = OsmRoute(11017, "bicycle", network = "ncn", ref = "7")

    private fun way(vararg routes: OsmRoute) = Way().apply { this.routes = routes.toList() }

    /** Walks north in 10m steps on [ways] in turn, returning the names offered at each step. */
    private fun walk(tracker: RouteOfferTracker, vararg ways: Way?, following: FollowedRoute? = null) =
        ways.mapIndexed { step, way ->
            tracker.update(following, getDestinationCoordinate(origin, 0.0, step * 10.0), way, ruler)
                ?.map { it.name }
        }

    @Test
    fun aRouteIsOfferedOnceAfterOneHundredAndFiftyMetres() {
        val shared = way(red, blue)
        val offers = walk(RouteOfferTracker(), *Array(30) { shared }).filterNotNull()
        // Both routes on the path, together, and only once
        assertEquals(listOf(listOf("Loch Ard Sculpture Trail", "Ben Lomond Viewpoint")), offers)
    }

    @Test
    fun crossingARouteIsntWalkingAlongIt() {
        val plain = way()
        val offers = walk(RouteOfferTracker(), plain, way(red), way(red), plain, *Array(20) { plain })
        assertEquals(emptyList(), offers.filterNotNull())
    }

    @Test
    fun aRouteJoiningLaterIsOfferedOnItsOwn() {
        val tracker = RouteOfferTracker()
        walk(tracker, *Array(20) { way(red) })
        val offers = walk(tracker, *Array(20) { way(red, blue) }).filterNotNull()
        assertEquals(listOf(listOf("Ben Lomond Viewpoint")), offers)
    }

    @Test
    fun nothingIsOfferedWhileFollowingARoute() {
        val following = FollowedRoute(2866086, "Loch Ard Sculpture Trail")
        assertEquals(emptyList(), walk(RouteOfferTracker(), *Array(30) { way(red, blue) }, following = following).filterNotNull())
    }

    @Test
    fun cycleRoutesArentOffered() {
        assertEquals(emptyList(), walk(RouteOfferTracker(), *Array(30) { way(cycle) }).filterNotNull())
    }
}
