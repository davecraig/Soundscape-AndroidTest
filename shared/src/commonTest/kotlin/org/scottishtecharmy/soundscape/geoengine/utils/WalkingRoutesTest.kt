package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.OsmRoute
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals

class WalkingRoutesTest {
    private val origin = LngLatAlt(-4.32, 55.97)
    private val ruler = CheapRuler(origin.latitude)

    private val whwPart1 = OsmRoute(19772647, "hiking", network = "nwn", name = "West Highland Way (Milngavie to Drymen part 1)", rootId = 16287, rootName = "West Highland Way")
    private val whwPart2 = OsmRoute(19772648, "hiking", network = "nwn", name = "West Highland Way (Milngavie to Drymen part 2)", rootId = 16287, rootName = "West Highland Way")
    private val johnMuir = OsmRoute(49215, "hiking", network = "nwn", name = "John Muir Way")
    private val cycle = OsmRoute(11017, "bicycle", network = "ncn", ref = "7")
    private val nodeNetwork = OsmRoute(1, "hiking", network = "rwn", networkType = "node_network", ref = "V72-V74")

    private fun way(metresNorth: Double, vararg routes: OsmRoute) = Way().apply {
        val start = getDestinationCoordinate(origin, 0.0, metresNorth)
        geometry = LineString(start, getDestinationCoordinate(start, 90.0, 50.0))
        this.routes = routes.toList()
    }

    @Test
    fun routesNearbyAreListedOnceEachNearestFirst() {
        val grid = GridState().apply {
            validateContext = false
            ruler = this@WalkingRoutesTest.ruler
            featureTrees[TreeId.ROADS_AND_PATHS.id] = FeatureTree(FeatureCollection().apply {
                addFeature(way(300.0, johnMuir))
                addFeature(way(100.0, whwPart2, cycle))
                addFeature(way(50.0, whwPart1, nodeNetwork))
                addFeature(way(20.0))
                addFeature(way(NEARBY_ROUTE_METRES + 100.0, OsmRoute(9, "foot", name = "Too Far Trail")))
            })
        }
        val routes = walkingRoutesNear(grid, origin)
        assertEquals(
            listOf(FollowedRoute(16287, "West Highland Way"), FollowedRoute(49215, "John Muir Way")),
            routes.map { it.route }
        )
        assertEquals(50.0, routes.first().distance, 1.0)
    }
}
