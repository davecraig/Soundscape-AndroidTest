package org.scottishtecharmy.soundscape.screens.home.placesnearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.scottishtecharmy.soundscape.components.EnabledFunction
import org.scottishtecharmy.soundscape.components.LocationItem
import org.scottishtecharmy.soundscape.components.LocationItemDecoration
import org.scottishtecharmy.soundscape.screens.home.locationDetails.generateLocationDetailsRoute
import org.scottishtecharmy.soundscape.screens.markers_routes.navigation.ScreensForMarkersAndRoutes

@Composable
fun PlacesNearbyList(
    uiState: PlacesNearbyUiState,
    navController: NavController,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(uiState.locations) { locationDescription ->
            LocationItem(
                item = locationDescription,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary),
                decoration = LocationItemDecoration(
                    location = true,
                    details = EnabledFunction(
                    true,
                        {
                            // This effectively replaces the current screen with the new one
                            navController.navigate(generateLocationDetailsRoute(locationDescription))
                        }
                    )
                ),
                userLocation = uiState.userLocation
            )
        }
    }
}
