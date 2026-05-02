package org.scottishtecharmy.soundscape.screens.home

import android.content.SharedPreferences
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.scottishtecharmy.soundscape.AppCallbacks
import org.scottishtecharmy.soundscape.AppFlows
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import org.scottishtecharmy.soundscape.audio.AudioTour
import org.scottishtecharmy.soundscape.audio.TourButton
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.intents.IntentEventBus
import org.scottishtecharmy.soundscape.navigation.NavigationStateHolder
import org.scottishtecharmy.soundscape.navigation.SharedNavHost
import org.scottishtecharmy.soundscape.navigation.SharedRoutes
import org.scottishtecharmy.soundscape.preferences.PreferencesListener
import org.scottishtecharmy.soundscape.preferences.PreferencesProvider
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.screens.home.home.AdvancedMarkersAndRoutesSettingsScreenVM
import org.scottishtecharmy.soundscape.screens.home.settings.Settings
import org.scottishtecharmy.soundscape.screens.onboarding.language.LanguageScreen
import org.scottishtecharmy.soundscape.screens.onboarding.language.LanguageViewModel
import org.scottishtecharmy.soundscape.utils.AnalyticsProvider
import org.scottishtecharmy.soundscape.utils.getLanguageMismatch
import org.scottishtecharmy.soundscape.viewmodels.SettingsViewModel
import androidx.core.content.edit

class Navigator {
    var destination = MutableStateFlow(SharedRoutes.HOME)

    fun navigate(newDestination: String) {
        Log.d("NavigationRoot", "Navigate to $newDestination")
        this.destination.value = newDestination
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    preferences: SharedPreferences,
    viewModel: HomeViewModel = koinViewModel(),
    audioTour: AudioTour,
    rateSoundscape: () -> Unit,
    contactSupport: () -> Unit,
    permissionsRequired: Boolean,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val serviceConnection: SoundscapeServiceConnection = koinInject()
    val intentBus: IntentEventBus = koinInject()
    val routeDao: org.scottishtecharmy.soundscape.database.local.dao.RouteDao = koinInject()
    val prefsProvider: PreferencesProvider = koinInject()
    val offlineMaps: org.scottishtecharmy.soundscape.utils.AndroidOfflineMapsManager = koinInject()

    val audioTourRunningFlow = remember(audioTour) { MutableStateFlow(audioTour.isRunning()) }
    LaunchedEffect(audioTour) {
        audioTour.currentInstruction.collect {
            audioTourRunningFlow.value = audioTour.isRunning()
        }
    }

    val recordingEnabledFlow = remember(preferences) {
        val initial = preferences.getBoolean(MainActivity.RECORD_TRAVEL_KEY, MainActivity.RECORD_TRAVEL_DEFAULT)
        MutableStateFlow(initial).also { flow ->
            preferences.registerOnSharedPreferenceChangeListener { sp, key ->
                if (key == MainActivity.RECORD_TRAVEL_KEY) {
                    flow.value = sp.getBoolean(MainActivity.RECORD_TRAVEL_KEY, MainActivity.RECORD_TRAVEL_DEFAULT)
                }
            }
        }
    }

    val permissionsRequiredFlow = remember(permissionsRequired) { MutableStateFlow(permissionsRequired) }
    val voiceCommandListeningFlow: StateFlow<Boolean> = remember(viewModel) {
        viewModel.state.map { it.voiceCommandListening }
            .stateIn(
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate),
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }
    val locationFlow: StateFlow<org.scottishtecharmy.soundscape.locationprovider.SoundscapeLocation?> =
        remember(viewModel) {
            viewModel.state
                .map { state ->
                    state.location?.let {
                        org.scottishtecharmy.soundscape.locationprovider.SoundscapeLocation(
                            latitude = it.latitude,
                            longitude = it.longitude,
                        )
                    }
                }
                .stateIn(
                    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate),
                    started = SharingStarted.Eagerly,
                    initialValue = null,
                )
        }

    val navStateHolder = remember { NavigationStateHolder() }
    val callbackScope = androidx.compose.runtime.rememberCoroutineScope()

    val onMapLongClickListener: (LngLatAlt) -> Boolean = remember(viewModel, navStateHolder) {
        { lngLatAlt: LngLatAlt ->
            val ld = viewModel.getLocationDescription(lngLatAlt) ?: LocationDescription("", lngLatAlt)
            navStateHolder.navigateWithLocation(navController, SharedRoutes.LOCATION_DETAILS, ld)
            AnalyticsProvider.getInstance().logEvent("longPressOnMap", null)
            true
        }
    }

    val flows = remember(audioTour, audioTourRunningFlow, recordingEnabledFlow, permissionsRequiredFlow, voiceCommandListeningFlow, intentBus, offlineMaps, locationFlow) {
        AppFlows(
            locationFlow = locationFlow,
            homeState = viewModel.state,
            audioTourRunning = audioTourRunningFlow.asStateFlow(),
            audioTourInstruction = audioTour.currentInstruction,
            recordingEnabled = recordingEnabledFlow.asStateFlow(),
            permissionsRequired = permissionsRequiredFlow.asStateFlow(),
            voiceCommandListening = voiceCommandListeningFlow,
            pendingIntent = intentBus.pendingIntent,
            onPendingIntentHandled = { intentBus.handled() },
            offlineMapsNearbyExtracts = offlineMaps.availableExtracts,
            offlineMapsDownloadedFc = offlineMaps.downloadedExtractsFc,
            offlineMapsDownloadState = offlineMaps.downloadState,
        )
    }

    val callbacks = remember(
        viewModel, audioTour, activity, rateSoundscape, contactSupport,
        serviceConnection, routeDao, prefsProvider, callbackScope, offlineMaps,
    ) {
        AppCallbacks(
            onStartBeacon = { lat, lng, _ ->
                serviceConnection.soundscapeService?.createBeacon(LngLatAlt(lng, lat), headingOnly = false)
            },
            onStopBeacon = { serviceConnection.soundscapeService?.destroyBeacon() },
            onStartRoute = { routeId -> serviceConnection.routeStart(routeId) },
            onStartRouteInReverse = { routeId ->
                serviceConnection.soundscapeService?.routeStartReverse(routeId)
            },
            onStartRouteByName = { name -> activity.startRouteByName(name) },
            onMyLocation = { viewModel.myLocation(); audioTour.onButtonPressed(TourButton.MY_LOCATION) },
            onWhatsAroundMe = { viewModel.whatsAroundMe(); audioTour.onButtonPressed(TourButton.AROUND_ME) },
            onAheadOfMe = { viewModel.aheadOfMe(); audioTour.onButtonPressed(TourButton.AHEAD_OF_ME) },
            onNearbyMarkers = { viewModel.nearbyMarkers(); audioTour.onButtonPressed(TourButton.NEARBY_MARKERS) },
            onRouteSkipPrevious = { viewModel.routeSkipPrevious() },
            onRouteSkipNext = { viewModel.routeSkipNext() },
            onRouteMute = { viewModel.routeMute() },
            onRouteStop = { viewModel.routeStop() },
            onSearch = { viewModel.onTriggerSearch(it) },
            onStreetPreviewGo = { viewModel.streetPreviewGo() },
            onStreetPreviewExit = { viewModel.streetPreviewExit() },
            onSleep = { activity.setServiceState(newServiceState = false, sleeping = true) },
            onWakeUp = { activity.setServiceState(newServiceState = true, sleeping = false) },
            onShareRecording = { activity.shareRecording() },
            onRateApp = rateSoundscape,
            onContactSupport = contactSupport,
            onToggleAudioTour = { audioTour.toggleState() },
            onAudioTourInstructionAcknowledged = { audioTour.onInstructionAcknowledged() },
            onMapLongClick = onMapLongClickListener,
            onGoToAppSettings = { org.scottishtecharmy.soundscape.utils.goToAppSettings(context) },
            onGetCurrentLocationDescription = {
                val location = viewModel.state.value.location
                if (location != null) {
                    viewModel.getLocationDescription(location) ?: LocationDescription("", location)
                } else {
                    LocationDescription("", LngLatAlt())
                }
            },
            onSetApplicationLocale = { tag ->
                if (tag != null) {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(tag),
                    )
                }
            },
            onGetLanguageMismatch = { getLanguageMismatch(context) },
            getOpenSourceLicensesJson = {
                context.assets.open("open_source_licenses.json")
                    .bufferedReader()
                    .use { it.readText() }
            },
            // Markers / Routes / Places-nearby / AddRoute factories: the shared
            // NavGraph instantiates these via `viewModel { factory() }` so they
            // are scoped to the destination's NavBackStackEntry. Captured deps
            // come from Koin in the enclosing composable.
            createPlacesNearbyViewModel = {
                org.scottishtecharmy.soundscape.screens.home.placesnearby.PlacesNearbyViewModel(
                    serviceConnection, audioTour,
                )
            },
            createMarkersViewModel = {
                org.scottishtecharmy.soundscape.screens.markers_routes.screens.markersscreen.MarkersViewModel(
                    routeDao, prefsProvider, serviceConnection,
                )
            },
            createRoutesViewModel = {
                org.scottishtecharmy.soundscape.screens.markers_routes.screens.routesscreen.RoutesViewModel(
                    routeDao, prefsProvider, serviceConnection,
                )
            },
            createAddAndEditRouteViewModel = {
                org.scottishtecharmy.soundscape.screens.markers_routes.screens.addandeditroutescreen.AddAndEditRouteViewModel(
                    routeDao, serviceConnection,
                )
            },
            // Marker / route persistence callbacks used by EDIT_MARKER and
            // ROUTE_DETAILS in the shared graph.
            onSaveMarker = { desc ->
                callbackScope.launch {
                    val name = desc.name.ifEmpty { desc.description ?: "Unknown" }
                    val marker = org.scottishtecharmy.soundscape.database.local.model.MarkerEntity(
                        markerId = desc.databaseId,
                        name = name,
                        fullAddress = desc.description ?: "",
                        longitude = desc.location.longitude,
                        latitude = desc.location.latitude,
                    )
                    if (desc.databaseId != 0L) routeDao.updateMarker(marker)
                    else routeDao.insertMarker(marker)
                    audioTour.onMarkerCreateDone()
                }
            },
            onDeleteMarker = { markerId ->
                callbackScope.launch { runCatching { routeDao.removeMarker(markerId) } }
            },
            onLoadRoute = { routeId ->
                kotlinx.coroutines.runBlocking {
                    val routeWithMarkers = runCatching { routeDao.getRouteWithMarkers(routeId) }.getOrNull()
                    routeWithMarkers?.markers?.map { marker ->
                        LocationDescription(
                            name = marker.name,
                            description = marker.fullAddress,
                            location = LngLatAlt(marker.longitude, marker.latitude),
                            databaseId = marker.markerId,
                        )
                    } ?: emptyList()
                }
            },
            onShareLocation = { desc, shareMessage ->
                org.scottishtecharmy.soundscape.utils.shareLocation(context, shareMessage, desc)
            },
            onShareRoute = { routeId ->
                callbackScope.launch {
                    val route = runCatching { routeDao.getRouteWithMarkers(routeId) }.getOrNull()
                        ?: return@launch
                    org.scottishtecharmy.soundscape.utils.shareRoute(context, route)
                }
            },
            // Offline-maps surface: routes through AndroidOfflineMapsManager so the
            // shared OFFLINE_MAPS composable behaves identically to iOS.
            onOfflineMapsRefresh = { offlineMaps.refresh() },
            onOfflineMapsGetExtracts = { location -> offlineMaps.getExtractsContaining(location) },
            onOfflineMapsDownload = { name, feature -> offlineMaps.startDownload(name, feature) },
            onOfflineMapsDelete = { feature -> offlineMaps.deleteExtractByFeature(feature) },
            onOfflineMapsCancelDownload = { offlineMaps.cancelDownload() },
        )
    }

    SharedNavHost(
            navController = navController,
            navStateHolder = navStateHolder,
            flows = flows,
            callbacks = callbacks,
            startDestination = SharedRoutes.HOME,
            audioTour = audioTour,
            preferencesProvider = remember(preferences) { AndroidSharedPreferencesAdapter(preferences) },
            settingsContent = { navCtrl ->
                val settingsViewModel: SettingsViewModel = koinViewModel()
                val uiState by settingsViewModel.state.collectAsStateWithLifecycle()
                val languageViewModel: LanguageViewModel = koinViewModel()
                val languageUiState by languageViewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    settingsViewModel.restartAppEvent.collect { activity.recreate() }
                }

                Settings(
                    navController = navCtrl,
                    uiState = uiState,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .semantics { testTagsAsResourceId = true },
                    supportedLanguages = languageUiState.supportedLanguages,
                    onLanguageSelected = { selectedLanguage ->
                        languageViewModel.updateLanguage(selectedLanguage)
                        settingsViewModel.updateLanguage(activity)
                    },
                    selectedLanguageIndex = languageUiState.selectedLanguageIndex,
                    storages = uiState.storages,
                    onStorageSelected = { path -> settingsViewModel.selectStorage(path) },
                    selectedStorageIndex = uiState.selectedStorageIndex,
                    resetSettings = { settingsViewModel.resetToDefaults() },
                )
            },
            platformNavBuilder = {
                // Android-only destinations that have no shared equivalent — both
                // are app-settings UIs that don't make sense on iOS today.
                composable(SharedRoutes.LANGUAGE) {
                    LanguageScreen(
                        onNavigate = { navController.navigateUp() },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .semantics { testTagsAsResourceId = true },
                    )
                }
                composable(SharedRoutes.ADVANCED_MARKERS_AND_ROUTES_SETTINGS) {
                    AdvancedMarkersAndRoutesSettingsScreenVM(
                        navController = navController,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .semantics { testTagsAsResourceId = true },
                    )
                }
            },
        )
}

private class AndroidSharedPreferencesAdapter(
    private val prefs: SharedPreferences,
) : PreferencesProvider {
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }
    override fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }
    override fun addListener(listener: PreferencesListener) {}
    override fun removeListener(listener: PreferencesListener) {}
}
