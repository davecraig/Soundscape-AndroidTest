package org.scottishtecharmy.soundscape.screens.home.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.Marker
import org.maplibre.android.geometry.LatLng
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.components.MainSearchBar
import org.scottishtecharmy.soundscape.screens.home.DrawerContent

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Preview
@Composable
fun HomePreview() {
    Home(
        latitude = null,
        longitude = null,
        beaconLocation = null,
        heading = 0.0f,
        onNavigate = {},
        onMapLongClick = { false },
        onMarkerClick = { true },
        getMyLocation = {},
        getWhatsAheadOfMe = {},
        getWhatsAroundMe = {},
        shareLocation = {},
        rateSoundscape = {},
        streetPreviewEnabled = false,
        tileGridGeoJson = ""
    )
}

@Composable
fun Home(
    latitude: Double?,
    longitude: Double?,
    beaconLocation: LatLng?,
    heading: Float,
    onNavigate: (String) -> Unit,
    onMapLongClick: (LatLng) -> Boolean,
    onMarkerClick: (Marker) -> Boolean,
    getMyLocation: () -> Unit,
    getWhatsAroundMe: () -> Unit,
    getWhatsAheadOfMe: () -> Unit,
    shareLocation: () -> Unit,
    rateSoundscape: () -> Unit,
    streetPreviewEnabled : Boolean,
    modifier: Modifier = Modifier,
    tileGridGeoJson: String
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onNavigate = onNavigate,
                drawerState = drawerState,
                shareLocation = shareLocation,
                rateSoundscape = rateSoundscape
            )
                        },
        gesturesEnabled = false,
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                HomeTopAppBar(
                    drawerState,
                    coroutineScope,
                    streetPreviewEnabled
                )
            },
            bottomBar = {
                HomeBottomAppBar(
                    getMyLocation = getMyLocation,
                    getWhatsAroundMe = getWhatsAroundMe,
                    getWhatsAheadOfMe = getWhatsAheadOfMe,
                )
            },
            floatingActionButton = {},
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            HomeContent(
                latitude = latitude,
                longitude = longitude,
                beaconLocation = beaconLocation,
                heading = heading,
                modifier = Modifier.padding(innerPadding),
                onNavigate = onNavigate,
                searchBar = {
                    MainSearchBar(
                        searchText = "",
                        isSearching = false,
                        itemList = emptyList(),
                        onSearchTextChange = { },
                        onToggleSearch = { },
                        onItemClick = { },
                    )
                },
                onMapLongClick = onMapLongClick,
                onMarkerClick = onMarkerClick,
                tileGridGeoJson = tileGridGeoJson
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    drawerState: DrawerState,
    coroutineScope: CoroutineScope,
    streetPreviewEnabled : Boolean
) {
    val context = LocalContext.current
    TopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = Color.White,
            ),
        title = { Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() }
        ) },
        navigationIcon = {
            IconButton(
                onClick = {
                    coroutineScope.launch { drawerState.open() }
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.ui_menu),
                    tint = Color.White,
                )
            }
        },
        actions = {
            var serviceRunning by remember { mutableStateOf(true) }
            IconToggleButton(
                checked = streetPreviewEnabled,
                enabled = true,
                onCheckedChange = { state ->
                    if(!state) {
                        (context as MainActivity).soundscapeServiceConnection.setStreetPreviewMode(
                            false
                        )
                    }
                },
            ) {
                if (streetPreviewEnabled) {
                    Icon(
                        Icons.Rounded.Preview,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = stringResource(R.string.street_preview_enabled)
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.preview_off),
                        tint = MaterialTheme.colorScheme.secondary,
                        contentDescription = stringResource(R.string.street_preview_disabled)
                    )
                }
            }
            IconToggleButton(
                checked = serviceRunning,
                enabled = true,
                onCheckedChange = { state ->
                    serviceRunning = state
                    (context as MainActivity).toggleServiceState(state)
                },
            ) {
                if (serviceRunning) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = "Service running")
                } else {
                    Icon(Icons.Rounded.LocationOff, contentDescription = "Service stopped")
                }
            }
        },
    )
}
