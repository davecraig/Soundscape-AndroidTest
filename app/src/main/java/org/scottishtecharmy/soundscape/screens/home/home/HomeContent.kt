package org.scottishtecharmy.soundscape.screens.home.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.components.NavigationButton
import org.scottishtecharmy.soundscape.database.local.model.Location
import org.scottishtecharmy.soundscape.database.local.model.MarkerData
import org.scottishtecharmy.soundscape.database.local.model.RouteData
import org.scottishtecharmy.soundscape.geoengine.StreetPreviewEnabled
import org.scottishtecharmy.soundscape.geoengine.StreetPreviewState
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.HomeRoutes
import org.scottishtecharmy.soundscape.screens.home.RouteFunctions
import org.scottishtecharmy.soundscape.screens.home.StreetPreviewFunctions
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.screens.home.locationDetails.generateLocationDetailsRoute
import org.scottishtecharmy.soundscape.services.RoutePlayerState

@Composable
fun HomeContent(
    location: LngLatAlt?,
    beaconLocation: LngLatAlt?,
    routePlayerState: RoutePlayerState,
    heading: Float,
    onNavigate: (String) -> Unit,
    onMapLongClick: (LatLng) -> Boolean,
    getCurrentLocationDescription: () -> LocationDescription,
    searchBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    streetPreviewState: StreetPreviewState,
    streetPreviewFunctions: StreetPreviewFunctions,
    routeFunctions: RouteFunctions
) {
    val markersHint = stringResource(R.string.search_button_markers_accessibility_hint)
    val placesNearbyHint = stringResource(R.string.search_button_nearby_accessibility_hint)
    val currentLocationHint = stringResource(R.string.search_button_current_location_accessibility_hint)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if(streetPreviewState.enabled != StreetPreviewEnabled.OFF) {
            StreetPreview(streetPreviewState, heading, streetPreviewFunctions)
        } else {
            searchBar()

            Column(
                verticalArrangement = Arrangement.spacedBy((1).dp),
            ) {
                // Places Nearby
                NavigationButton(
                    onClick = {
                        onNavigate(HomeRoutes.PlacesNearby.route)
                    },
                    text = stringResource(R.string.search_nearby_screen_title),
                    horizontalPadding = 8.dp,
                    modifier = Modifier.semantics {
                        heading()
                        onClick(label = placesNearbyHint, action = { false })
                    },
                )
                // Markers and routes
                NavigationButton(
                    onClick = {
                        onNavigate(HomeRoutes.MarkersAndRoutes.route)
                    },
                    text = stringResource(R.string.search_view_markers),
                    horizontalPadding = 8.dp,
                    modifier = Modifier
                        .semantics { onClick(label = markersHint, action = { false }) }
                )
                // Current location
                NavigationButton(
                    onClick = {
                        if (location != null) {
                            val ld = getCurrentLocationDescription()
                            onNavigate(generateLocationDetailsRoute(ld))
                        }
                    },
                    text = stringResource(R.string.search_use_current_location),
                    horizontalPadding = 8.dp,
                    modifier = Modifier
                        .semantics { onClick(label = currentLocationHint, action = { false }) }
                )
            }
        }
        if (location != null) {
            if(routePlayerState.routeData != null) {
                Card(modifier = Modifier.padding(8.dp).weight(1F)) {
                    Column {
                        Row(modifier = Modifier.height(40.dp)) {
                            Column {
                                Text(
                                    text = "${routePlayerState.routeData.name} - ${routePlayerState.currentWaypoint + 1}/${routePlayerState.routeData.waypoints.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Row(modifier = Modifier.weight(1F)) {
                            MapContainerLibre(
                                beaconLocation = beaconLocation,
                                routeData = routePlayerState.routeData,
                                mapCenter = location,
                                allowScrolling = false,
                                mapViewRotation = 0.0F,
                                userLocation = location,
                                userSymbolRotation = heading,
                                onMapLongClick = onMapLongClick,
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().height(40.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                            Button(onClick = { routeFunctions.skipPrevious() })
                            {
                                Icon(
                                    modifier = Modifier,
                                    imageVector = Icons.Filled.SkipPrevious,
                                    tint = if(routePlayerState.currentWaypoint == 0) Color.Unspecified else Color.White,
                                    contentDescription = "",
                                )
                            }
                            Button(onClick = { routeFunctions.skipNext() })
                            {
                                Icon(
                                    modifier = Modifier,
                                    imageVector = Icons.Filled.SkipNext,
                                    tint = if(routePlayerState.currentWaypoint < routePlayerState.routeData.waypoints.size - 1) Color.White else Color.Unspecified,
                                    contentDescription = "",
                                )
                            }
                            Button(onClick = {
                                    onNavigate("${HomeRoutes.RouteDetails.route}/${routePlayerState.routeData.objectId.toHexString()}")
                                }
                            )
                            {
                                Icon(
                                    modifier = Modifier,
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "",
                                )
                            }
                            Button(onClick = { routeFunctions.mute() })
                            {
                                Icon(
                                    modifier = Modifier,
                                    imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                                    contentDescription = "",
                                )
                            }
                        }
                    }
                }
            } else  {
                MapContainerLibre(
                    beaconLocation = beaconLocation,
                    routeData = null,
                    mapCenter = location,
                    allowScrolling = false,
                    mapViewRotation = 0.0F,
                    userLocation = location,
                    userSymbolRotation = heading,
                    onMapLongClick = onMapLongClick,
                )
            }
        }
    }
}

@Preview
@Composable
fun StreetPreviewHomeContent() {
    HomeContent(
        location = null,
        beaconLocation = null,
        routePlayerState = RoutePlayerState(),
        heading = 0.0f,
        onNavigate = {},
        onMapLongClick = { false },
        searchBar = {},
        streetPreviewState = StreetPreviewState(StreetPreviewEnabled.ON),
        streetPreviewFunctions = StreetPreviewFunctions(null),
        routeFunctions = RouteFunctions(null),
        getCurrentLocationDescription = { LocationDescription("Current location", LngLatAlt()) }
    )
}

@Preview
@Composable
fun PreviewHomeContent() {
    val routePlayerState = RoutePlayerState(
        routeData = RouteData(
            name = "Route 1",
            description = "Description 1"
        ),
        currentWaypoint = 0
    )

    routePlayerState.routeData!!.waypoints.add(MarkerData("Marker 1", Location(40.7128, -74.0060), "Description 1"))
    routePlayerState.routeData.waypoints.add(MarkerData("Marker 2", Location(40.7128, -74.0060), "Description 2"))

    HomeContent(
        location = LngLatAlt(),
        beaconLocation = LngLatAlt(),
        routePlayerState = routePlayerState,
        heading = 0.0f,
        onNavigate = {},
        onMapLongClick = { false },
        searchBar = {},
        streetPreviewState = StreetPreviewState(StreetPreviewEnabled.OFF),
        streetPreviewFunctions = StreetPreviewFunctions(null),
        routeFunctions = RouteFunctions(null),
        getCurrentLocationDescription = { LocationDescription("Current location", LngLatAlt()) }
    )
}
