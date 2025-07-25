package org.scottishtecharmy.soundscape.geoengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.vectorTileToGeoJson
import org.scottishtecharmy.soundscape.geoengine.utils.mergeAllPolygonsInFeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.network.ITileDAO
import org.scottishtecharmy.soundscape.network.ProtomapsTileClient
import retrofit2.awaitResponse
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
open class ProtomapsGridState(
    zoomLevel: Int = MAX_ZOOM_LEVEL,
    gridSize: Int = GRID_SIZE,
    passedInTreeContext: CloseableCoroutineDispatcher? = null
) : GridState(zoomLevel, gridSize, passedInTreeContext) {

    override fun start(applicationContext: Context) {
        tileClient = ProtomapsTileClient(applicationContext)
    }

    /**
     * updateTile is responsible for getting data from the protomaps server and translating it from
     * MVT format into a set of FeatureCollections.
     */
    override suspend fun updateTile(
        x: Int,
        y: Int,
        featureCollections: Array<FeatureCollection>,
        intersectionMap: HashMap<LngLatAlt, Intersection>
    ): Boolean {
        var ret = false
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val service =
                    tileClient.retrofitInstance?.create(ITileDAO::class.java)
                val tileReq =
                    async {
                        service?.getVectorTileWithCache(x, y, zoomLevel)
                    }
                val result = tileReq.await()?.awaitResponse()?.body()
                if (result != null) {
                    val requestTime = System.currentTimeMillis() - startTime
                    Log.e(TAG, "Tile size ${result.serializedSize}")
                    var tileFeatureCollection: FeatureCollection?
                    val mvtParseTime = measureTimeMillis {
                        tileFeatureCollection = vectorTileToGeoJson(
                            tileX = x,
                            tileY = y,
                            mvt = result,
                            intersectionMap = intersectionMap,
                            tileZoom = zoomLevel)
                    }
                    var collections: Array<FeatureCollection>?
                    val processTime = measureTimeMillis {
                        collections = processTileFeatureCollection(tileFeatureCollection!!)
                    }
                    val addTime = measureTimeMillis {
                        for ((index, collection) in collections!!.withIndex()) {
                            featureCollections[index].plusAssign(collection)
                        }
                    }

                    Log.e(TAG, "Request time $requestTime")
                    Log.e(TAG, "MVT parse time $mvtParseTime")
                    Log.e(TAG, "processTileFeatureCollection $processTime")
                    Log.e(TAG, "Add to FeatureCollection time $addTime")

                    ret = true
                } else {
                    Log.e(TAG, "No response for protomaps tile")
                }
            } catch (ce: CancellationException) {
                // We have to rethrow cancellation exceptions
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting protomaps tile $e")
            }
        }
        return ret
    }

    override fun fixupCollections(featureCollections: Array<FeatureCollection>) {
        // Merge any overlapping Polygons that are on the tile boundaries
        val mergedPoi = mergeAllPolygonsInFeatureCollection(featureCollections[TreeId.POIS.id])
        featureCollections[TreeId.POIS.id] = mergedPoi
    }
}