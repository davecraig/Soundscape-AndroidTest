package org.scottishtecharmy.soundscape.geoengine.utils.geocoders

import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription

open class SoundscapeGeocoder {
    open suspend fun getAddressFromLocationName(locationName: String, nearbyLocation: LngLatAlt) : LocationDescription? { return null }
    open suspend fun getAddressFromLngLat(userGeometry: UserGeometry) : LocationDescription? { return null }
}
