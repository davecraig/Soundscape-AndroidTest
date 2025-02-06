package org.scottishtecharmy.soundscape.screens.markers_routes.screens.markersscreen

import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription

data class MarkersUiEditState(
    val markers: List<LocationDescription> = emptyList(),
    val route: List<LocationDescription> = emptyList(),
)