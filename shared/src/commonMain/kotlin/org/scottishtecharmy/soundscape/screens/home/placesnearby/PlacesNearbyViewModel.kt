package org.scottishtecharmy.soundscape.screens.home.placesnearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.audio.AudioTour
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.geoengine.utils.walkingRoutesNear
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.logging.AppLog
import org.scottishtecharmy.soundscape.services.ServiceConnection
import org.scottishtecharmy.soundscape.utils.addressCountryCode

/** How far the user has to move before the country their address is written in is looked up again. */
private const val COUNTRY_LOOKUP_DISTANCE = 5000.0

private val logger = AppLog.tag("PlacesNearbyViewModel")

/**
 * Shared ViewModel backing the PlacesNearby screen on both Android and iOS.
 *
 * Owns nearby-places UI state and folder navigation. Listens for service binding
 * and starts/stops monitoring location + grid flows accordingly.
 */
open class PlacesNearbyViewModel(
    private val connection: ServiceConnection,
    audioTour: AudioTour? = null,
) : ViewModel() {

    val internalUiState = MutableStateFlow(PlacesNearbyUiState())
    val uiState: StateFlow<PlacesNearbyUiState> = internalUiState

    private var monitorJob: Job? = null
    private var followedRouteJob: Job? = null

    init {
        audioTour?.onNavigatedToPlacesNearby()

        viewModelScope.launch {
            connection.serviceBoundState.collect { bound ->
                if (bound) startMonitoring() else stopMonitoring()
            }
        }
    }

    fun onClickBack() {
        internalUiState.value = internalUiState.value.copy(level = 0, filter = "", title = "")
    }

    fun onClickFolder(filter: String, title: String) {
        internalUiState.value =
            internalUiState.value.copy(level = 1, filter = filter, title = title)
    }

    fun startBeacon(location: LngLatAlt, name: String) {
        connection.service?.startBeacon(location, name)
    }

    /**
     * Follows [route] from the Walking routes folder, or stops following it if it is the one
     * already being followed.
     */
    fun toggleFollowRoute(route: FollowedRoute) {
        val service = connection.service ?: return
        service.followRoute(if (service.followedRouteFlow.value == route) null else route)
    }

    /**
     * Manually cancel the viewModelScope. Use only when this holder is embedded
     * inside another ViewModel (e.g. AddAndEditRouteViewModel.logic) and so
     * the framework would never call onCleared() on it directly.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    private data class LocationAndGridState(val location: LngLatAlt?, val gridState: GridState?)

    private var countryLookupLocation: LngLatAlt? = null
    private var countryCode: String? = null

    /**
     * The country [location] is in, looked up from the country boundaries the first time and then
     * only again once the user has moved far enough for the answer to plausibly have changed. A
     * location update arrives every second or so, and a country is not something you walk out of.
     */
    private fun countryCodeFor(location: LngLatAlt): String? {
        val lastLookup = countryLookupLocation
        if ((lastLookup == null) ||
            (CheapRuler(location.latitude).distance(lastLookup, location) > COUNTRY_LOOKUP_DISTANCE)
        ) {
            countryLookupLocation = location
            countryCode = addressCountryCode(location)
        }
        return countryCode
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMonitoring() {
        monitorJob?.cancel()
        followedRouteJob?.cancel()
        val service = connection.service ?: return
        followedRouteJob = viewModelScope.launch {
            service.followedRouteFlow.collect { route ->
                internalUiState.update { it.copy(followedRoute = route) }
            }
        }
        monitorJob = viewModelScope.launch {
            combine(service.gridStateFlow, service.locationFlow) { gridState, location ->
                LocationAndGridState(
                    location = location?.let { LngLatAlt(it.longitude, it.latitude) },
                    gridState = gridState,
                )
            }.collect { locationAndGrid ->
                if (locationAndGrid.location != null) {
                    val country = countryCodeFor(locationAndGrid.location)
                    internalUiState.update {
                        it.copy(userLocation = locationAndGrid.location, countryCode = country)
                    }
                }
                if (locationAndGrid.gridState != null) {
                    val userLocation = locationAndGrid.location
                    val routes = withContext(locationAndGrid.gridState.treeContext) {
                        if (userLocation == null) emptyList()
                        else runCatching {
                            walkingRoutesNear(locationAndGrid.gridState, userLocation)
                        }.getOrElse {
                            logger.e(it) { "Walking routes lookup failed" }
                            emptyList()
                        }
                    }
                    internalUiState.update { it.copy(nearbyRoutes = routes) }
                    val (pois, intersections) = withContext(locationAndGrid.gridState.treeContext) {
                        val p = runCatching {
                            locationAndGrid.gridState.getFeatureCollection(TreeId.POIS)
                        }.getOrElse {
                            logger.e(it) { "POIS lookup failed" }
                            FeatureCollection()
                        }
                        val i = runCatching {
                            locationAndGrid.gridState.getFeatureCollection(TreeId.INTERSECTIONS)
                        }.getOrElse {
                            logger.e(it) { "INTERSECTIONS lookup failed" }
                            FeatureCollection()
                        }
                        p to i
                    }
                    internalUiState.value = internalUiState.value.copy(
                        nearbyPlaces = pois,
                        nearbyIntersections = intersections,
                    )
                } else {
                    internalUiState.value = internalUiState.value.copy(
                        nearbyPlaces = FeatureCollection(),
                        nearbyIntersections = FeatureCollection(),
                    )
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        followedRouteJob?.cancel()
        followedRouteJob = null
    }
}
