package org.scottishtecharmy.soundscape.geoengine.utils.geocoders

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.utils.toLocationDescription
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The AndroidGeocoder class abstracts away the use of a built in Android Geocoder for geocoding
 * and reverse geocoding.
 */
class AndroidGeocoder(val applicationContext: Context) : SoundscapeGeocoder() {
    private var geocoder: Geocoder = Geocoder(applicationContext)

    // Not all Android platforms have Geocoder capability
    val enabled = Geocoder.isPresent()

    /**
     * The main weakness of the AndroidGeocoder is that it doesn't include the names of Points of
     * Interest in the search results. It will include the address, but it won't have the associated
     * business name for that address. All we can do is pass through the "locationName" that was
     * searched for, typos and all.
     */
    override suspend fun getAddressFromLocationName(locationName: String, nearbyLocation: LngLatAlt) : LocationDescription? {
        if(!enabled)
            return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return suspendCoroutine { continuation ->
                val geocodeListener =
                    Geocoder.GeocodeListener { addresses ->
                        Log.d(
                            TAG,
                            "getAddressFromLocationName results count " + addresses.size.toString()
                        )
                        if (addresses.isNotEmpty()) {
                            continuation.resume(
                                addresses[0]
                            )
                        } else {
                            continuation.resume(null)
                        }
                    }
                geocoder.getFromLocationName(
                    locationName,
                    5,
                    nearbyLocation.latitude - 0.1,
                    nearbyLocation.longitude - 0.1,
                    nearbyLocation.latitude + 0.1,
                    nearbyLocation.longitude + 0.1,
                    geocodeListener
                )
            }?.toLocationDescription(locationName)
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(locationName, 5)
            if(addresses != null) {
                return addresses[0].toLocationDescription(locationName)
            }
        }
        return null
    }

    override suspend fun getAddressFromLngLat(userGeometry: UserGeometry) : LocationDescription? {
        if(!enabled)
            return null

        val location = userGeometry.mapMatchedLocation?.point ?: userGeometry.location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return suspendCoroutine { continuation ->
                val geocodeListener =
                    Geocoder.GeocodeListener { addresses ->
                        Log.d(
                            TAG,
                            "getAddressFromLocationName results count " + addresses.size.toString()
                        )
                        continuation.resume(addresses[0])
                    }
                geocoder.getFromLocation(location.latitude, location.longitude, 5, geocodeListener)
            }?.toLocationDescription(null)
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 5)
            if(addresses != null) {
                return addresses[0].toLocationDescription(null)
            }
        }
        return null
    }

    companion object {
        const val TAG = "AndroidGeocoder"
    }
}