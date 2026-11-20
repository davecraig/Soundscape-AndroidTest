package org.scottishtecharmy.soundscape.geoengine.callouts

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * How far along ways off a followed route the user walks before they have left it: enough for map
 * matching's wobbles onto a side path at a junction. Following the West Highland Way through
 * mugdock-whw.gpx there were none in 5km.
 */
const val FOLLOWED_ROUTE_LEAVE_METRES = 30.0

/**
 * Whether a user following a route (GridState.followedRoute) has left it or come back to it, from
 * the Way they are map matched to on each update - for AutoCallout's "Leaving ..." and "Back on
 * ...".
 *
 * Nothing is said until they have been on the route, so that setting off for it from a car park
 * isn't leaving it, and they have left it once they've walked [FOLLOWED_ROUTE_LEAVE_METRES] along
 * ways that aren't on it. A lost map match counts for neither: in a forest that's often just the
 * GPS. Choosing another route starts again.
 */
class FollowedRouteTracker {
    enum class Change { LEFT, BACK }

    private var routeKey: Long? = null
    private var beenOnRoute = false
    private var offRouteMetres = 0.0
    private var left = false
    private var lastLocation: LngLatAlt? = null

    fun update(route: FollowedRoute?, location: LngLatAlt, matchedWay: Way?, ruler: Ruler): Change? {
        if (route?.key != routeKey) {
            routeKey = route?.key
            beenOnRoute = false
            offRouteMetres = 0.0
            left = false
            lastLocation = null
        }
        if (route == null) return null

        val moved = lastLocation?.let { ruler.distance(it, location) } ?: 0.0
        lastLocation = location
        val way = matchedWay ?: return null

        if (way.routes.any { it.followKey == route.key }) {
            beenOnRoute = true
            offRouteMetres = 0.0
            if (!left) return null
            left = false
            return Change.BACK
        }
        if (!beenOnRoute || left) return null
        offRouteMetres += moved
        if (offRouteMetres < FOLLOWED_ROUTE_LEAVE_METRES) return null
        left = true
        return Change.LEFT
    }
}
