package org.scottishtecharmy.soundscape.viewmodels.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.utils.blankOrEmpty
import org.scottishtecharmy.soundscape.utils.toLocationDescriptions
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class)
class HomeViewModel
    @Inject
    constructor(
        private val soundscapeServiceConnection: SoundscapeServiceConnection,
    ) : ViewModel() {
    private val _state: MutableStateFlow<HomeState> = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private val _searchText: MutableStateFlow<String> = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private var job = Job()
    private var spJob = Job()

    init {
        handleMonitoring()
        fetchSearchResult()
    }

    private fun handleMonitoring() {
        viewModelScope.launch {
            soundscapeServiceConnection.serviceBoundState.collect { serviceBoundState ->
                Log.d(TAG, "serviceBoundState $serviceBoundState")
                if (serviceBoundState) {
                    // The service has started, so start monitoring the location and heading
                    startMonitoringLocation()
                    // And start monitoring the street preview state
                    startMonitoringStreetPreviewState()
                } else {
                    // The service has gone away so remove the current location marker
                    _state.update { it.copy(location = null) }
                    stopMonitoringStreetPreviewState()
                    stopMonitoringLocation()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoringStreetPreviewState()
        stopMonitoringLocation()
    }

    /**
     * startMonitoringLocation launches monitoring of the location and orientation providers. These
     * can change e.g. when switching to and from StreetPreview mode, so they are launched in a job.
     * That job is cancelled when the StreetPreview mode changes and the monitoring restarted.
     */
    private fun startMonitoringLocation() {
        Log.d(TAG, "ViewModel startMonitoringLocation")
        job = Job()
        viewModelScope.launch(job) {
            // Observe location updates from the service
            soundscapeServiceConnection.getLocationFlow()?.collectLatest { value ->
                if (value != null) {
                    Log.d(TAG, "Location $value")
                    _state.update { it.copy(location = LngLatAlt(value.longitude, value.latitude)) }
                }
            }
        }
        viewModelScope.launch(job) {
            // Observe orientation updates from the service
            soundscapeServiceConnection.getOrientationFlow()?.collectLatest { value ->
                if (value != null) {
                    _state.update { it.copy(heading = value.headingDegrees) }
                }
            }
        }
        viewModelScope.launch(job) {
            // Observe beacon location update from the service so we can show it on the map
            soundscapeServiceConnection.getBeaconFlow()?.collectLatest { value ->
                Log.d(TAG, "beacon collected $value")
                _state.update {
                    it.copy(
                        beaconState = value
                    )
                }
            }
        }
        viewModelScope.launch(job) {
            // Observe current route from the service so we can show it on the map
            soundscapeServiceConnection.getCurrentRouteFlow()?.collectLatest { value ->
                _state.update { it.copy(currentRouteData = value) }
            }
        }
    }

    private fun stopMonitoringLocation() {
        Log.d(TAG, "stopMonitoringLocation")
        job.cancel()
    }

    /**
     * startMonitoringStreetPreviewState launches a job to monitor the state of street preview mode.
     * When the mode from the service changes then the local flow for the UI is updated and the
     * location and orientation monitoring is turned off and on again so as to use the new providers.
     */
    private fun startMonitoringStreetPreviewState() {
        Log.d(TAG, "startMonitoringStreetPreviewState")
        spJob = Job()
        viewModelScope.launch(spJob) {
            // Observe street preview mode from the service so we can update state
            soundscapeServiceConnection.getStreetPreviewModeFlow()?.collect { value ->
                Log.d(TAG, "Street Preview Mode: $value")
                val enabled = state.value.streetPreviewState.enabled
                _state.update { it.copy(streetPreviewState = value) }

                if(enabled != value.enabled) {
                    // Restart location monitoring for new provider
                    stopMonitoringLocation()
                    startMonitoringLocation()
                }
            }
        }
    }

    private fun stopMonitoringStreetPreviewState() {
        Log.d(TAG, "stopMonitoringStreetPreviewState")
        spJob.cancel()
    }

//
// We moved from the deprecated MapLibre Marker API to using the Symbol API instead. We don't yet
// have a listener for a click on symbols.
//
//    fun onMarkerClick(marker: Marker): Boolean {
//        Log.d(TAG, "marker click")
//
//        if ((marker.position.latitude == _state.value.beaconLocation?.latitude) &&
//            (marker.position.longitude == _state.value.beaconLocation?.longitude)){
//            soundscapeServiceConnection.soundscapeService?.destroyBeacon()
//            _state.update { it.copy(beaconLocation = null) }
//
//            return true
//        }
//        return false
//    }

    fun myLocation() {
        // Log.d(TAG, "myLocation() triggered")
        viewModelScope.launch {
            soundscapeServiceConnection.soundscapeService?.myLocation()
        }
    }

    fun aheadOfMe() {
        // Log.d(TAG, "myLocation() triggered")
        viewModelScope.launch {
            soundscapeServiceConnection.soundscapeService?.aheadOfMe()
        }
    }

    fun whatsAroundMe() {
        // Log.d(TAG, "myLocation() triggered")
        viewModelScope.launch {
            soundscapeServiceConnection.soundscapeService?.whatsAroundMe()
        }
    }

    fun nearbyMarkers() {
        // Log.d(TAG, "nearbyMarkers() triggered")
        viewModelScope.launch {
            soundscapeServiceConnection.soundscapeService?.nearbyMarkers()
        }
    }

    fun streetPreviewGo() {
        // Log.d(TAG, "myLocation() triggered")
        viewModelScope.launch {
            soundscapeServiceConnection.streetPreviewGo()
        }
    }

    fun streetPreviewExit() {
        // Log.d(TAG, "myLocation() triggered")
        viewModelScope.launch {
            soundscapeServiceConnection.setStreetPreviewMode(false)
        }
    }

    fun routeSkipPrevious() {
        viewModelScope.launch {
            soundscapeServiceConnection.routeSkipPrevious()
        }
    }
    fun routeSkipNext() {
        viewModelScope.launch {
            soundscapeServiceConnection.routeSkipNext()
        }
    }
    fun routeMute() {
        viewModelScope.launch {
            soundscapeServiceConnection.routeMute()
        }
    }
    fun routeStop() {
        viewModelScope.launch {
            soundscapeServiceConnection.routeStop()
        }
    }

    fun getLocationDescription(location: LngLatAlt) : LocationDescription? {
        return soundscapeServiceConnection.soundscapeService?.getLocationDescription(location)
    }

    fun onSearchTextChange(text: String) {
        _searchText.value = text
    }

    private fun fetchSearchResult() {
        viewModelScope.launch {
            _searchText
                .debounce(500)
                .distinctUntilChanged()
                .collectLatest { searchText ->
                    if (searchText.blankOrEmpty()) {
                        _state.update { it.copy(searchItems = emptyList()) }
                    } else {
                        val result =
                            soundscapeServiceConnection.soundscapeService?.searchResult(searchText)

                        _state.update {
                            it.copy(
                                searchItems = result?.toLocationDescriptions(),
                            )
                        }
                    }
                }
        }
    }

    fun onToggleSearch() {
        _state.update { it.copy(isSearching = !it.isSearching) }

        if (!state.value.isSearching) {
            onSearchTextChange("")
        }
    }

    fun setRoutesAndMarkersTab(pickRoutes: Boolean) {
        _state.update { it.copy(routesTabSelected = pickRoutes) }
    }

companion object {
        private const val TAG = "HomeViewModel"
    }
}
