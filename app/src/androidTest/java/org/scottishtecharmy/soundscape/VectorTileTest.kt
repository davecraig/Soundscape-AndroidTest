package org.scottishtecharmy.soundscape

import android.util.Log
import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import org.scottishtecharmy.soundscape.geojsonparser.moshi.GeoJsonObjectMoshiAdapter
import org.scottishtecharmy.soundscape.utils.getLatLonTileWithOffset
import org.scottishtecharmy.soundscape.utils.processTileString
import vector_tile.VectorTile
import vector_tile.VectorTile.Tile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.HashMap


@RunWith(AndroidJUnit4::class)
class VectorTileTest {

    @Test
    fun simpleVectorTile() {

        // This test does the simplest vector tile test:
        //
        //  1. It gets a vector tile from the protomaps server
        //  2. Parses it with the code auto-generated from the vector_tile.proto specification
        //  3. Prints it out
        val remoteTile = URL("https://api.protomaps.com/tiles/v3/15/15992/10212.mvt?key=9f3c764359583830")
        val tile: Tile = Tile.parseFrom(remoteTile.openStream())
        println(tile.layersList)
    }


    private fun convertGeometry(tileX : Int, tileY : Int, tileZoom : Int, geometry: ArrayList<Pair<Int, Int>>) : ArrayList<LngLatAlt> {
        val results = arrayListOf<LngLatAlt>()
        for(point in geometry) {
            results.add(getLatLonTileWithOffset(tileX,
                                                tileY,
                -                               tileZoom,
                -                        point.first.toDouble()/4096.0,
                -                        point.second.toDouble()/4096.0))
        }
        return results
    }

    private fun parseGeometry(geometry: MutableList<Int>) : ArrayList<Pair<Int, Int>> {

        //  Converting the geometry coordinates requires some effort. See
        //      https://github.com/mapbox/vector-tile-spec/blob/master/2.1/README.md#43-geometry-encoding
        //
        var x = 0
        var y = 0
        val results = arrayListOf<Pair<Int, Int>>()
        var id : Int
        var count = 0
        var deltaX = 0
        var deltaY : Int
        var firstOfPair = true
        for(commandOrParameterInteger in geometry) {
            if(count == 0) {
                id = commandOrParameterInteger.and(0x7)
                count = commandOrParameterInteger.shr(3)
                when(id) {
                    1 -> {
                        // MoveTo
                        deltaX = 0
                        firstOfPair = true
                    }
                    2 -> {
                        // LineTo
                        deltaX = 0
                    }

                    7 -> {
                        // Close the polygon
                        if(count != 1) {
                            Log.e(TAG, "Unexpected count $count")
                        }

                        // Polygons can have nested, wound polygons. However, we are only interested
                        // in the outer polygon. That means that we can bail early and return as
                        // soon as it is closed. Simply add the first point in the result as that
                        // was the start of the polygon.
                        results.add(results[0])
                        return results
                    }
                    else ->
                        Log.e(TAG, "Unexpected id $id")
                }
            }
            else {
                val value = ((commandOrParameterInteger.ushr(1)).xor(-(commandOrParameterInteger.and(1))))

                if(firstOfPair) {
                    deltaX = value
                    firstOfPair = false
                } else {
                    deltaY = value
                    firstOfPair = true

                    x += deltaX
                    y += deltaY

                    // The vector tile has pixel coordinates relative to the tile origin. Convert
                    // these to global coordinates
                    results.add(Pair(x, y))
                    --count
                }
            }
        }

        return results
    }

    @Test
    fun pixelToLocation() {
        val tileX = 15992
        val tileY = 10212
        val tileZoom = 15

        val tileOrigin2 = getLatLonTileWithOffset(tileX, tileY, tileZoom, 0.0, 0.0)
        Log.e(TAG, "tileOrigin2 " + tileOrigin2.latitude + "," + tileOrigin2.longitude)
    }

    @Test
    fun vectorTileToGeoJson() {

        // Do we really want to go via GeoJSON, or instead go direct to our parsed format?
        var tileX = 15992/2
        var tileY = 10212/2
        val tileZoom = 14
        var remoteTile = URL("http://192.168.86.39:8080/data/openmaptiles/$tileZoom/$tileX/$tileY.pbf")

        val tile: Tile = Tile.parseFrom(remoteTile.openStream())

        val collection = FeatureCollection()
        val highwayPoints : HashMap< Int, ArrayList<String>> = hashMapOf()
        for(layer in tile.layersList) {
            Log.d(TAG, "Process layer: " + layer.name)
            for (feature in layer.featuresList) {
                // Convert coordinates to GeoJSON
                if((layer.name != "transportation") && (layer.name != "transportation_name"))
                    continue

                // And map the tags
                val geoFeature = Feature()
                geoFeature.id = feature.id.toString()
                var intGeometry = arrayListOf<Pair<Int, Int>>()
                when(feature.type) {
                    VectorTile.Tile.GeomType.POLYGON -> {
                        intGeometry = parseGeometry(feature.geometryList)
                        geoFeature.geometry =
                            Polygon(convertGeometry(tileX, tileY, tileZoom, intGeometry))
                    }

                    VectorTile.Tile.GeomType.POINT -> {
                        intGeometry = parseGeometry(feature.geometryList)
                        val coordinates = convertGeometry(tileX, tileY, tileZoom, intGeometry)
                        geoFeature.geometry = Point(coordinates[0].longitude, coordinates[0].latitude)
                    }

                    VectorTile.Tile.GeomType.LINESTRING -> {
                        intGeometry = parseGeometry(feature.geometryList)
                        geoFeature.geometry = LineString(convertGeometry(tileX, tileY, tileZoom, intGeometry))
                    }
                    Tile.GeomType.UNKNOWN,null -> Log.e(TAG, "Unknown GeomType")
                }

                var firstInPair = true
                var key = ""
                var value : Any? = null
                for(tag in feature.tagsList) {
                    if(firstInPair)
                        key = layer.getKeys(tag)
                    else {
                        val raw = layer.getValues(tag)
                        if(raw.hasStringValue())
                            value = raw.stringValue
                        else if(raw.hasBoolValue())
                            value = raw.boolValue
                        else if(raw.hasIntValue())
                            value = raw.intValue
                        else if(raw.hasSintValue())
                            value = raw.sintValue
                        else if(raw.hasFloatValue())
                            value = raw.doubleValue
                        else if(raw.hasDoubleValue())
                            value = raw.floatValue
                        else if(raw.hasUintValue())
                            value = raw.uintValue
                    }

                    if(!firstInPair) {
                        if(geoFeature.properties == null) {
                            geoFeature.properties = HashMap<String, Any?>()
                        }
                        geoFeature.properties?.put(key, value)
                        firstInPair = true
                    }
                    else
                        firstInPair = false
                }
                collection.addFeature(geoFeature)
                if((layer.name == "transportation") || (layer.name == "transportation_name")) {
                    Log.e(TAG, "Process " + geoFeature.properties?.get("name").toString() + " " + geoFeature.id)
                    // Add each point in the road to a hashMap so that we can see where roads
                    // intersect. The x,y coordinates are only 12 bits each, so we can create a
                    // single Int key that specifies the position within the tile.
                    for (point in intGeometry) {
                        if((point.first < 0) || (point.first > 4095) ||
                           (point.second < 0) || (point.second > 4095)) {
                            continue
                        }
//                        Log.e(TAG, "Point: " + point.first + "," + point.second)
                        val coordinateKey = point.first.toInt().shl(12) + point.second
                        if (highwayPoints[coordinateKey] == null) {
                            highwayPoints[coordinateKey] =
                                arrayListOf(geoFeature.id!!)
                        }
                        else {
                            if(!highwayPoints[coordinateKey]?.contains(geoFeature.id!!)!!) {
                                highwayPoints[coordinateKey]?.add(geoFeature.id!!)
                                //
                                // Unfortunately, this intersection spotting is unreliable with the
                                // maptiler tiles :-( There's an explanation of why here:
                                // https://gis.stackexchange.com/questions/319422/mapbox-vector-tiles-appear-to-lack-accurate-intersection-nodes
                                //
                                // There's no Roselea Drive/Strathblane Drive intersection because the
                                // Strathblane section was drawn first and then Roselea Drive joined in
                                // half way between two nodes. That node doesn't affect how you'd draw
                                // Strathblane Road and so it isn't included in its list of nodes.
                                //
                                // It's possible that we can generate the tiles so that they don't
                                // exclude intersection nodes by disabling simplification at the max
                                // zoom level, see:
                                // https://github.com/Scottish-Tech-Army/Soundscape-Android/actions/workflows/nightly.yaml
                                //
                                // This would make our tiles a little larger, but that's what you'd expect!
                                //
                                // One remaining question is whether it would then be  possible to have
                                // a single road be made up of two separate lines which would mean that
                                // we end up finding an intersection where there isn't one in the real
                                // world? Also, does that render properly on the UI map?
                                // Most roads segments wouldn't split other than at a junction, but the
                                // code has to deal with that correctly too.
                                val roads = highwayPoints[coordinateKey]
                                if (roads != null) {
                                    var intersectionNames = ""
                                    var firstRoad = true
                                    for (road in roads) {
                                        if (!firstRoad)
                                            intersectionNames += ","
                                        intersectionNames += road
                                        firstRoad = false
                                    }
                                    Log.e(TAG, "Intersection: $intersectionNames")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Find any road and path intersections

        val adapter = GeoJsonObjectMoshiAdapter()
//        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
//
//        Log.e(TAG, "Output directory: $dir")
//        var fileOutputStream = FileOutputStream(File(dir, "vector-tile.json"))
//        fileOutputStream.write(adapter.toJson(collection).toString().toByteArray())
//        fileOutputStream.close()

        // What does the soundscape-backend tile look like for comparison?
        tileX *= 2
        tileY *= 2
        remoteTile = URL("https://soundscape.scottishtecharmy.org/tiles/16/$tileX/$tileY.json")
        // Read all the text returned by the server
        val reader = BufferedReader(InputStreamReader(remoteTile.openStream()))
        var tileContents = ""
        var line : String?
        while(true) {
            line = reader.readLine()
            if(line == null)
                break
            tileContents += line
        }
        reader.close()

        val tileData = processTileString("MadeUpQuadKey", tileContents)
//        fileOutputStream = FileOutputStream(File(dir, "soundscape-tile.json"))
//        fileOutputStream.write(tileData.toString().toByteArray())
//        fileOutputStream.close()
    }

    companion object {
        const val TAG = "VectorTileTest"
    }
}