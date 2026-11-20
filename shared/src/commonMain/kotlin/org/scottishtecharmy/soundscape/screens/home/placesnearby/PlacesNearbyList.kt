package org.scottishtecharmy.soundscape.screens.home.placesnearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.scottishtecharmy.soundscape.geoengine.formatDistanceAndDirection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.utils.NearbyRoute
import org.scottishtecharmy.soundscape.resources.places_nearby_no_walking_routes
import org.scottishtecharmy.soundscape.resources.route_follow_action
import org.scottishtecharmy.soundscape.resources.route_following_state
import org.scottishtecharmy.soundscape.resources.route_stop_following_action
import org.scottishtecharmy.soundscape.ui.theme.smallPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.scottishtecharmy.soundscape.components.EnabledFunction
import org.scottishtecharmy.soundscape.components.FolderItem
import org.scottishtecharmy.soundscape.components.LocationItem
import org.scottishtecharmy.soundscape.components.LocationItemDecoration
import org.scottishtecharmy.soundscape.components.LocationListActions
import org.scottishtecharmy.soundscape.components.rememberLocationItemActions
import org.scottishtecharmy.soundscape.i18n.ComposeLocalizedStrings
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.location_detail_action_beacon_hint
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.screens.talkbackHint
import org.scottishtecharmy.soundscape.ui.theme.spacing
import org.scottishtecharmy.soundscape.utils.process

@Composable
fun PlacesNearbyList(
    uiState: PlacesNearbyUiState,
    onSelectItem: (LocationDescription) -> Unit,
    onClickFolder: (String, String) -> Unit,
    onStartBeacon: (LocationDescription) -> Unit,
    modifier: Modifier,
    itemActions: LocationListActions = LocationListActions(),
    onToggleFollowRoute: (FollowedRoute) -> Unit = {},
) {
    val extraActions = rememberLocationItemActions(itemActions)
    val localizedStrings = remember { ComposeLocalizedStrings() }
    val locations = remember(uiState) {
        filterLocations(uiState, localizedStrings)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.tiny),
    ) {
        if (uiState.level == 0) {
            itemsIndexed(placesNearbyFolders) { index, folderItem ->
                if (index == 0) {
                    HorizontalDivider(
                        thickness = spacing.tiny,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                val name = stringResource(folderItem.nameResource)
                FolderItem(
                    name = name,
                    icon = folderItem.icon,
                    onClick = {
                        onClickFolder(folderItem.filter, name)
                    },
                    modifier = Modifier
                        .testTag("placesNearby-$index")
                        .talkbackHint(stringResource(folderItem.talkbackDescriptionResource))
                )
            }
        } else if (uiState.filter == WALKING_ROUTES_FILTER) {
            if (uiState.nearbyRoutes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.places_nearby_no_walking_routes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.smallPadding(),
                    )
                }
            }
            itemsIndexed(uiState.nearbyRoutes) { index, nearby ->
                if (index == 0) {
                    HorizontalDivider(
                        thickness = spacing.tiny,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                WalkingRouteItem(
                    nearby = nearby,
                    following = nearby.route == uiState.followedRoute,
                    onClick = { onToggleFollowRoute(nearby.route) },
                    modifier = Modifier.testTag("placesNearbyRoute-$index"),
                )
            }
        } else {
            itemsIndexed(locations) { index, locationDescription ->
                if (index == 0) {
                    HorizontalDivider(
                        thickness = spacing.tiny,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                locationDescription.process(localizedStrings, uiState.countryCode)
                LocationItem(
                    item = locationDescription,
                    decoration = LocationItemDecoration(
                        location = true,
                        details = EnabledFunction(
                            true,
                            {
                                onSelectItem(locationDescription)
                            }
                        ),
                        startPlayback = EnabledFunction(
                            enabled = true,
                            functionLocation = onStartBeacon,
                            hint = stringResource(Res.string.location_detail_action_beacon_hint)
                        ),
                        extraActions = extraActions,
                    ),
                    userLocation = uiState.userLocation,
                    modifier = Modifier.testTag("placesNearby-$index")
                )
            }
        }
    }
}

/**
 * One walking route in the Walking routes folder: its name and how far away it is, and whether it
 * is the one being followed. Choosing it follows it, or stops following it.
 */
@Composable
private fun WalkingRouteItem(
    nearby: NearbyRoute,
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val distance = remember(nearby.distance) {
        formatDistanceAndDirection(nearby.distance, null, ComposeLocalizedStrings())
    }
    val actionLabel = stringResource(
        if (following) Res.string.route_stop_following_action else Res.string.route_follow_action
    )
    val followingText = stringResource(Res.string.route_following_state)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .smallPadding()
            .clickable(role = Role.Button, onClickLabel = actionLabel) { onClick() }
            .semantics { if (following) stateDescription = followingText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Hiking,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(spacing.icon)
        )
        Column(modifier = Modifier.padding(start = spacing.small).weight(1F)) {
            Text(
                text = nearby.route.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (following) "$followingText, $distance" else distance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        thickness = spacing.tiny,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
