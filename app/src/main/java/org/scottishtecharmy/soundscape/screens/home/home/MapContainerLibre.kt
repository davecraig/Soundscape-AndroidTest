package org.scottishtecharmy.soundscape.screens.home.home

import androidx.compose.runtime.Composable


const val USER_POSITION_MARKER_NAME = "USER_POSITION_MARKER_NAME"
const val LOCATION_MARKER_NAME = "LOCATION-%d"

/**
 * A map disable component that uses maplibre.
 *
 * @mapCenter The `LatLng` to center around
 * @mapHeading
 * @userLocation The `LatLng` to draw the user location symbol at
 * @userHeading
 * @beaconLocation An optional `LatLng` to show a beacon marker
 * @onMapLongClick Action to take if a long click is made on the map
 * @onMarkerClick Action to take if a beacon marker is clicked on
 */
@Composable
fun MapContainerLibre() {
}
