package org.scottishtecharmy.soundscape

import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * Survey, not a regression test: do pedestrian areas (highway=pedestrian area=yes) and platform
 * polygons survive from the tiles into the GridState trees, and what do they carry? This is
 * groundwork for open-area directions, where the user is in a precinct or on a platform and
 * there's no line to follow.
 */
class OpenAreaSurveyTest {

    private data class Site(val name: String, val location: LngLatAlt)

    private val sites = listOf(
        Site("Glasgow Buchanan St", LngLatAlt(-4.2535, 55.8615)),
        Site("Glasgow Central", LngLatAlt(-4.2577, 55.8590)),
        Site("Milngavie precinct", LngLatAlt(-4.3128, 55.9412)),
        Site("London Paddington", LngLatAlt(-0.1757, 51.5167)),
        Site("Bristol Broadmead", LngLatAlt(-2.5880, 51.4584)),
        Site("Bristol Temple Meads", LngLatAlt(-2.5813, 51.4490)),
        Site("Paris Les Halles", LngLatAlt(2.3470, 48.8620)),
        Site("Paris Gare de Lyon", LngLatAlt(2.3735, 48.8443)),
    )

    private val openValues = setOf("pedestrian", "platform", "footway", "steps", "path")

    @Test
    fun surveyOpenAreaPolygons() {
        for (site in sites) {
            val grid = getGridStateForLocation(site.location, MAX_ZOOM_LEVEL, 2)
            println("==== ${site.name}")
            for (tree in TreeId.entries) {
                if (tree == TreeId.MAX_COLLECTION_ID) continue
                val features = try {
                    grid.getFeatureTree(tree).getAllCollection()
                } catch (e: Exception) {
                    continue
                }
                val areas = features.features.filter {
                    val f = it as MvtFeature
                    (f.geometry.type == "Polygon" || f.geometry.type == "MultiPolygon") &&
                        (f.featureValue in openValues)
                }
                if (areas.isEmpty()) continue
                val byValue = areas.groupBy { (it as MvtFeature).featureValue }
                for ((value, list) in byValue) {
                    val named = list.count { (it as MvtFeature).name != null }
                    println("  ${tree.name}: $value polygons=${list.size} named=$named " +
                        "featureType=${list.map { (it as MvtFeature).featureType }.toSet()}")
                    list.take(3).forEach {
                        val f = it as MvtFeature
                        val ring = when (f.geometry.type) {
                            "Polygon" -> (f.geometry as org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon)
                                .coordinates.first().size
                            else -> -1
                        }
                        println("    osm=${f.osmId} name=${f.name} ring=$ring props=${f.properties}")
                    }
                }
            }
        }
    }
}
