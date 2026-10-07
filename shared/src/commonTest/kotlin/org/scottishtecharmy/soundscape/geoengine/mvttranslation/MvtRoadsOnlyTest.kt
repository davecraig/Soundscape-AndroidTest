package org.scottishtecharmy.soundscape.geoengine.mvttranslation

import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import vector_tile.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * With roadsOnly, a path is to be left out of the Way network altogether: not a Way, and not an
 * arm of the junction where it meets a road.
 */
class MvtRoadsOnlyTest {

    private class Translated(
        val collections: Array<FeatureCollection>,
        val intersections: HashMap<LngLatAlt, Intersection>
    ) {
        fun ways(id: TreeId) = collections[id.id].features.filterIsInstance<Way>()
        val junctionArms get() = intersections.values.flatMap { it.members }
    }

    /** A road running east, and a path of [subclass] heading south from the middle of it. */
    private fun translate(roadsOnly: Boolean, subclass: String = "footway"): Translated {
        val keys = listOf("class", "subclass", "name")
        val values = listOf(
            Tile.Value(string_value = "minor"),
            Tile.Value(string_value = "residential"),
            Tile.Value(string_value = "High Street"),
            Tile.Value(string_value = "path"),
            Tile.Value(string_value = subclass),
        )
        val road = Tile.Feature(
            id = 10L,
            tags = listOf(0, 0, 1, 1, 2, 2),
            type = Tile.GeomType.LINESTRING,
            // MoveTo 100,100 then LineTo 200,100 and 300,100, deltas zigzag encoded
            geometry = listOf(9, 200, 200, 18, 200, 0, 200, 0),
        )
        val footway = Tile.Feature(
            id = 11L,
            tags = listOf(0, 3, 1, 4),
            type = Tile.GeomType.LINESTRING,
            // MoveTo 200,100 then LineTo 200,300
            geometry = listOf(9, 400, 200, 10, 0, 400),
        )
        val tile = Tile(
            listOf(
                Tile.Layer(
                    version = 2,
                    name = "transportation",
                    features = listOf(road, footway),
                    keys = keys,
                    values = values,
                    extent = 4096
                )
            )
        )

        val intersectionMap: HashMap<LngLatAlt, Intersection> = hashMapOf()
        val streetNumberMap: HashMap<String, FeatureCollection> = hashMapOf()
        val collections = vectorTileToGeoJson(
            8000, 5000, tile, intersectionMap, streetNumberMap, true, 14,
            roadsOnly = roadsOnly
        )
        return Translated(collections, intersectionMap)
    }

    @Test
    fun pathsAreWaysAndJunctionArmsByDefault() {
        val translated = translate(roadsOnly = false)

        assertTrue(translated.ways(TreeId.ROADS_AND_PATHS).any { it.isPath() })
        assertFalse(translated.ways(TreeId.ROADS).any { it.isPath() })
        assertTrue(translated.junctionArms.any { it.isPath() })
    }

    @Test
    fun roadsOnlyLeavesPathsOutOfWaysAndJunctions() {
        val translated = translate(roadsOnly = true)

        assertFalse(translated.ways(TreeId.ROADS_AND_PATHS).any { it.isPath() })
        assertFalse(translated.junctionArms.any { it.isPath() })
        // Without the footway there's no junction part way along the road to split it at
        assertEquals(1, translated.ways(TreeId.ROADS_AND_PATHS).size)
        assertEquals(1, translated.ways(TreeId.ROADS).size)
    }

    @Test
    fun roadsOnlyLeavesOutPlatformsCorridorsAndSteps() {
        for (subclass in listOf("platform", "corridor", "steps")) {
            assertEquals(3, translate(roadsOnly = false, subclass).ways(TreeId.ROADS_AND_PATHS).size, subclass)
            assertEquals(1, translate(roadsOnly = true, subclass).ways(TreeId.ROADS_AND_PATHS).size, subclass)
        }
    }

    @Test
    fun roadsOnlyKeepsPedestrianStreets() {
        assertEquals(3, translate(roadsOnly = true, "pedestrian").ways(TreeId.ROADS_AND_PATHS).size)
    }
}
