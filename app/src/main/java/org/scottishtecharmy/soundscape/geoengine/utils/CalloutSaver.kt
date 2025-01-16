package org.scottishtecharmy.soundscape.geoengine.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiLineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import org.scottishtecharmy.soundscape.geojsonparser.moshi.GeoJsonObjectMoshiAdapter
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.write

class CalloutSaver() {

//    private var travelFile: File? = null
//
//    fun open(directory: File, filename: String) {
//        travelFile = File(directory, filename)
//        travelFile?.writeText("")
//
//    }
//
//    fun close(directory: File, filename: String) {

    val callouts = FeatureCollection()

    fun addCallout(location: LngLatAlt,
                   calloutText: String,
                   heading: Float = Float.NaN,
                   fovLeft: LngLatAlt? = null,
                   fovRight: LngLatAlt? = null) {

        val callout = Feature()
        if((fovLeft == null) || (fovRight == null)) {
            // Save the callout as a Point
            callout.geometry = Point(location.longitude, location.latitude)
        } else {
            // Save the callout as a field of view triangle
            val coordinates = arrayListOf<LngLatAlt>()
            coordinates.add(location)
            coordinates.add(fovLeft)
            coordinates.add(fovRight)
            callout.geometry = Polygon(coordinates)
        }
        callout.properties = hashMapOf()
        callout.properties!!["text"] = calloutText
        callout.properties!!["heading"] = heading.toInt().toString()
        callouts.addFeature(callout)
    }

    fun getCalloutHistory() : FeatureCollection {
        return callouts
    }

//    val jsonString =
//        "\"location\": {\n" +
//                "\"latitude\": \"${location.latitude}\",\n" +
//                "\"longitude\": \"${location.longitude}\",\n" +
//                "\"altitude\": \"${location.altitude}\",\n" +
//                "\"accuracy\": \"${location.accuracy}\",\n" +
//                "\"speed\": \"${location.speed}\",\n" +
//                "\"bearing\": \"${location.bearing}\",\n" +
//                "\"bearingAccuracyDegrees\": \"${location.bearingAccuracyDegrees}\",\n" +
//                "\"time\": \"${location.time}\"\n" +
//                "\"heading\": \"${heading}\"\n" +
//                "},\n"
//    FileOutputStream(travelFile, true).use { outputStream ->
//        outputStream.write(jsonString.toByteArray())
//    }

}