package org.scottishtecharmy.soundscape.screens.home.placesnearby

import org.scottishtecharmy.soundscape.geoengine.types.FeatureList
import org.scottishtecharmy.soundscape.geoengine.types.emptyFeatureList
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription

data class PlacesNearbyUiState(
    var userLocation: LngLatAlt? = null,
    var level: Int = 0,
    var nearbyPlaces: FeatureList = emptyFeatureList(),
    var nearbyIntersections: FeatureList = emptyFeatureList(),
    var filter: String = "",
    var title: String = "",
    var markerDescription: LocationDescription? = null
)
