package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A hand-built park of paths, walked into from the south at junction J:
 *
 *                 castle (on the path, 150m out)
 *                    |
 *   Mugdock Road -- (W) ---- J ---- (E) ---- fork --- 250m --- Caulders (a shop)
 *                            |                \
 *                         approach             100m --- Craigend Road
 *
 * The west arm reaches the castle and Mugdock Road; the east arm forks, and the nearer landmark
 * past the fork is the shop - but a named road beats a shop.
 */
class SignpostsTest {

    private val origin = LngLatAlt(-4.32, 55.97)
    private val ruler = CheapRuler(origin.latitude)

    private fun at(bearing: Double, metres: Double, from: LngLatAlt = origin) =
        getDestinationCoordinate(from, bearing, metres)

    private fun node(location: LngLatAlt) = Intersection().apply {
        this.location = location
        geometry = Point(location)
    }

    private fun way(from: Intersection, to: Intersection?, toLocation: LngLatAlt? = null, name: String? = null) =
        Way().apply {
            this.name = name
            featureType = "highway"
            featureValue = if (name == null) "path" else "residential"
            featureClass = if (name == null) "path" else "minor"
            featureSubClass = if (name == null) "path" else null
            val end = to?.location ?: toLocation!!
            geometry = LineString(from.location, end)
            length = ruler.distance(from.location, end)
            intersections[WayEnd.START.id] = from
            to?.let { intersections[WayEnd.END.id] = it }
            from.members.add(this)
            to?.members?.add(this)
        }

    private fun poi(name: String, location: LngLatAlt, category: SuperCategoryId) = MvtFeature().apply {
        this.name = name
        geometry = Point(location)
        superCategory = category
        featureType = "tourism"
        featureValue = "attraction"
    }

    private class Park(val grid: GridState, val junction: Intersection, val west: Way, val east: Way, val approach: Way)

    private fun park(withRoadPastTheFork: Boolean = true, withRoadAtJunction: Boolean = false): Park {
        val j = node(origin)
        val south = node(at(180.0, 50.0))
        val westEnd = node(at(270.0, 300.0))
        val fork = node(at(90.0, 100.0))
        val shop = node(at(90.0, 350.0))
        val approach = way(south, j)
        val west = way(j, westEnd)
        way(westEnd, null, at(270.0, 400.0), name = "Mugdock Road")
        val east = way(j, fork)
        way(fork, shop)
        if (withRoadPastTheFork) {
            val roadEnd = node(at(135.0, 100.0, fork.location))
            way(fork, roadEnd)
            way(roadEnd, null, at(135.0, 200.0, fork.location), name = "Craigend Road")
        }
        if (withRoadAtJunction) way(j, null, at(0.0, 100.0), name = "Mugdock Road")

        val grid = GridState().apply { validateContext = false; ruler = this@SignpostsTest.ruler }
        grid.featureTrees[TreeId.POIS.id] = FeatureTree(FeatureCollection().apply {
            addFeature(poi("Mugdock Castle", at(270.0, 150.0), SuperCategoryId.LANDMARK))
            addFeature(poi("Caulders", at(90.0, 355.0), SuperCategoryId.PLACE))
        })
        grid.featureTrees[TreeId.NAMED_WATER_POLYGONS.id] = FeatureTree(FeatureCollection())
        return Park(grid, j, west, east, approach)
    }

    private fun signpost(way: Way, junction: Intersection, grid: GridState) =
        signpostDestination(way, way.intersections[WayEnd.START.id] === junction, grid, null)

    @Test
    fun eachArmIsSignpostedToTheNearestThingItLeadsTo() {
        val park = park()
        assertEquals("Mugdock Castle", signpost(park.west, park.junction, park.grid))
        assertEquals("Craigend Road", signpost(park.east, park.junction, park.grid))
    }

    @Test
    fun aShopIsOnlySignpostedWhenThereIsNothingElse() {
        val park = park(withRoadPastTheFork = false)
        assertEquals("Caulders", signpost(park.east, park.junction, park.grid))
    }

    @Test
    fun noTwoArmsShareALandmark() {
        // The approach can only reach the castle and the road through the junction, by way of the
        // other arms, so nothing is signposted down it
        val park = park()
        assertNull(signpost(park.approach, park.junction, park.grid))
    }

    @Test
    fun aRoadAtTheJunctionIsntWhereAnArmLeads() {
        // Mugdock Road meets the junction, so the west arm, which rejoins it, is the way to the castle
        // and nothing beyond it is "Path to Mugdock Road"
        val park = park(withRoadAtJunction = true)
        assertEquals("Mugdock Castle", signpost(park.west, park.junction, park.grid))
        val bare = park(withRoadAtJunction = true).also {
            it.grid.featureTrees[TreeId.POIS.id] = FeatureTree(FeatureCollection())
        }
        assertNull(signpost(bare.west, bare.junction, bare.grid))
    }

    @Test
    fun aLandmarkAtTheJunctionIsWhereTheWalkerIs() {
        val park = park()
        park.grid.featureTrees[TreeId.POIS.id] = FeatureTree(FeatureCollection().apply {
            addFeature(poi("Mugdock Castle", at(270.0, 10.0), SuperCategoryId.LANDMARK))
        })
        assertEquals("Mugdock Road", signpost(park.west, park.junction, park.grid))
    }

    @Test
    fun theSignpostIsRemembered() {
        val park = park()
        assertEquals("Mugdock Castle", signpost(park.west, park.junction, park.grid))
        park.grid.featureTrees[TreeId.POIS.id] = FeatureTree(FeatureCollection())
        assertEquals("Mugdock Castle", signpost(park.west, park.junction, park.grid))
    }

    @Test
    fun beyondRangeThereIsNoSignpost() {
        val j = node(origin)
        val far = node(at(0.0, SIGNPOST_RANGE_METRES + 100.0))
        val arm = way(j, far)
        way(far, null, at(0.0, SIGNPOST_RANGE_METRES + 200.0), name = "Faraway Road")
        val grid = GridState().apply {
            validateContext = false
            ruler = this@SignpostsTest.ruler
            featureTrees[TreeId.POIS.id] = FeatureTree(FeatureCollection())
            featureTrees[TreeId.NAMED_WATER_POLYGONS.id] = FeatureTree(FeatureCollection())
        }
        assertNull(signpost(arm, j, grid))
    }
}
