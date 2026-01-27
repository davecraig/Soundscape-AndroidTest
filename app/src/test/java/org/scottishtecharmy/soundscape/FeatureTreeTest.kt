package org.scottishtecharmy.soundscape

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.GRID_SIZE
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.time.measureTime

class FeatureTreeTest {

    @Test
    fun performanceTest() {
        runBlocking {
            val currentLocation = LngLatAlt(-3.1874692, 55.9487172)
            val gridState = getGridStateForLocation(currentLocation, MAX_ZOOM_LEVEL, GRID_SIZE)
            val bounds = gridState.totalBoundingBox

            val steps = 100
            val lonStep = (bounds.eastLongitude - bounds.westLongitude) / steps
            val latStep = (bounds.northLatitude - bounds.southLatitude) / steps
            val tree = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)

            val duration = measureTime {
                for (i in 0 until steps) {
                    for (j in 0 until steps) {
                        val lon = bounds.westLongitude + i * lonStep
                        val lat = bounds.southLatitude + j * latStep
                        val location = LngLatAlt(lon, lat)
                        tree.getNearbyCollection(location, 1000.0, gridState.ruler)
                    }
                }
            }
            println("Duration for ${steps*steps} points: $duration, tree size ${tree.tree!!.size()}")
        }
   }
}