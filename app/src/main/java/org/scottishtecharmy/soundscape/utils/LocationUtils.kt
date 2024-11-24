package org.scottishtecharmy.soundscape.utils

import android.location.Location
import org.scottishtecharmy.soundscape.components.SearchItem
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import java.util.Locale

fun ArrayList<Feature>.mapSearchFeaturesToSearchItem(
    currentLocationLatitude: Double,
    currentLocationLongitude: Double
): List<SearchItem> =
    mapNotNull { feature ->
        feature.properties?.let { properties ->
            SearchItem(
                text = listOfNotNull(
                    properties["name"],
                    properties["housenumber"],
                    properties["street"],
                    properties["postcode"],
                    properties["city"],
                    properties["country"]
                ).joinToString(" "),
                label = formatDistance(
                    calculateDistance(
                        lat1 = currentLocationLatitude,
                        lon1 = currentLocationLongitude,
                        lat2 = (feature.geometry as Point).coordinates.latitude,
                        lon2 = (feature.geometry as Point).coordinates.longitude
                    )
                )
            )
        }
    }

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}

fun formatDistance(distanceInMeters: Float): String {
    val distanceInKm = distanceInMeters / 1000
    return String.format(Locale.getDefault(), "%.1f km", distanceInKm)
}
