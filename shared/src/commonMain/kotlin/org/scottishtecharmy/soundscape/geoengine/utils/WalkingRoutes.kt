package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.OsmRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/** How far away a walking route can be and still be offered to follow. */
const val NEARBY_ROUTE_METRES = 400.0

/** A walking route near the user, [distance] metres to the nearest way on it. */
data class NearbyRoute(val route: FollowedRoute, val distance: Double)

/** A walking route the user could follow: not a stretch of a junction network, and with a name. */
fun OsmRoute.followable(): FollowedRoute? =
    if (isWalking && !isNodeNetworkStretch) displayName?.let { FollowedRoute(followKey, it) } else null

/** The walking routes [way] is on, each once - two sections of one route are the one route. */
fun followableRoutes(way: Way): List<FollowedRoute> =
    way.routes.mapNotNull { it.followable() }.distinctBy { it.key }

/**
 * The walking routes on the ways within [radius] of [location], nearest first - for the user to
 * pick one to follow (see GridState.followedRoute). Must be called within the grid's treeContext.
 */
fun walkingRoutesNear(
    gridState: GridState,
    location: LngLatAlt,
    radius: Double = NEARBY_ROUTE_METRES,
): List<NearbyRoute> {
    val nearest = HashMap<Long, NearbyRoute>()
    val ways = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
        .getNearestCollection(location, radius, Int.MAX_VALUE, gridState.ruler)
    for (feature in ways) {
        val way = feature as? Way ?: continue
        val routes = followableRoutes(way)
        if (routes.isEmpty()) continue
        val distance = getDistanceToFeature(location, way, gridState.ruler).distance
        for (route in routes) {
            val known = nearest[route.key]
            if ((known == null) || (distance < known.distance)) nearest[route.key] = NearbyRoute(route, distance)
        }
    }
    return nearest.values.sortedBy { it.distance }
}
