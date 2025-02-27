package org.scottishtecharmy.soundscape.screens.markers_routes.screens.addandeditroutescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.scottishtecharmy.soundscape.components.EnabledFunction
import org.scottishtecharmy.soundscape.components.LocationItem
import org.scottishtecharmy.soundscape.components.LocationItemDecoration
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.ui.theme.extraSmallPadding
import org.scottishtecharmy.soundscape.ui.theme.spacing

@Composable
fun AddWaypointsList(
    uiState: AddAndEditRouteUiState,
    userLocation: LngLatAlt?,
) {
    // Create our list of locations, with those already in the route first
    val locations = remember(uiState) {
        mutableStateListOf<LocationDescription>()
            .apply {
                addAll(uiState.routeMembers)
                addAll(uiState.markers.filter { marker ->
                    uiState.routeMembers.none { routeMember ->
                        routeMember.markerObjectId == marker.markerObjectId
                    }
                }
            )
        }
    }

    // Set the switches for those in the route to true
    val routeMember = remember(uiState) {
        mutableStateMapOf<LocationDescription, Boolean>()
            .apply {
                uiState.markers.associateWith { false }.also { putAll(it) }
                uiState.routeMembers.associateWith { true }.also { putAll(it) }
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.tiny),
    ) {
        items(locations) { locationDescription ->
            LocationItem(
                item = locationDescription,
                modifier = Modifier
                    .extraSmallPadding()
                    .background(MaterialTheme.colorScheme.primary)
                    .fillMaxWidth(),
                decoration = LocationItemDecoration(
                    location = false,
                    editRoute = EnabledFunction(
                        enabled = true,
                        functionBoolean = {
                            routeMember[locationDescription] = it
                            val updatedList = uiState.routeMembers.toMutableList()
                            if(it)
                                updatedList.add(locationDescription)
                            else
                                updatedList.remove(locationDescription)
                            uiState.routeMembers = updatedList
                        },
                        value = routeMember[locationDescription] ?: false
                    )
                ),
                userLocation = userLocation
            )
        }
    }
}
