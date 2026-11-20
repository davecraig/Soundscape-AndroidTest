package org.scottishtecharmy.soundscape.geoengine.callouts

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.followableRoutes
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * How far the user walks along a walking route before they're told they're on it - long enough
 * that it isn't just a crossing.
 */
const val ROUTE_OFFER_METRES = 150.0

/**
 * When to tell a user who isn't following a route that they're walking along one - "You're on the
 * West Highland Way" - so that they can choose to follow it from Places Nearby. Several routes
 * often share a path, which is why the user has to choose: the routes on the path are offered
 * together, each only once.
 *
 * Distance is counted for each route while the user is map matched to a way on it, and starts
 * again when they're on a way that isn't. A lost map match counts for nothing either way.
 */
class RouteOfferTracker {
    private val walked = HashMap<Long, Double>()
    private val offered = HashSet<Long>()
    private var lastLocation: LngLatAlt? = null

    /**
     * The routes to tell the user about now, or null. [following] is the route being followed:
     * while there is one, nothing is offered.
     */
    fun update(following: FollowedRoute?, location: LngLatAlt, matchedWay: Way?, ruler: Ruler): List<FollowedRoute>? {
        val moved = lastLocation?.let { ruler.distance(it, location) } ?: 0.0
        lastLocation = location
        if (following != null) return null
        val way = matchedWay ?: return null

        val routes = followableRoutes(way)
        val keys = routes.map { it.key }.toSet()
        walked.keys.retainAll(keys)
        for (key in keys) walked[key] = (walked[key] ?: 0.0) + moved

        if (routes.none { (walked[it.key] ?: 0.0) >= ROUTE_OFFER_METRES && it.key !in offered }) return null
        val toOffer = routes.filter { it.key !in offered }
        offered += toOffer.map { it.key }
        return toOffer
    }
}
