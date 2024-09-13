// The code for Marker manipulation in maplibre has been moved into an annotations plugin.
// However, this doesn't appear to be supported in Kotlin yet. There's talk of un-deprecating those
// functions in the next release if support isn't added. In the meantime we use the deprecated
// functions and suppress the warnings here.
@file:Suppress("DEPRECATION")

package org.scottishtecharmy.soundscape.viewmodels

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.geometry.LatLng
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(@ApplicationContext context: Context, private val soundscapeServiceConnection : SoundscapeServiceConnection): ViewModel() {

    private var serviceConnection : SoundscapeServiceConnection? = null
    private var iconFactory : IconFactory
    var latitude : Double = 0.0
    var longitude : Double = 0.0
    var heading : Float = 0.0F
    private var initialLocation : Location? = null
    private var mapCentered : Boolean = false

    private val _homeMapStateFlow = MutableStateFlow(LatLng(0.0, 0.0))
    var homeMapStateFlow: StateFlow<LatLng> = _homeMapStateFlow

    private fun startMonitoringLocation() {
        Log.d(TAG, "ViewModel startMonitoringLocation")
        viewModelScope.launch {
            // Observe location updates from the service
            serviceConnection?.getLocationFlow()?.collectLatest { value ->
                if (value != null) {
                    Log.d(TAG, "Location $value")
                }
            }
        }

        viewModelScope.launch {
            // Observe orientation updates from the service
            serviceConnection?.getOrientationFlow()?.collectLatest { value ->
                if (value != null) {
                    heading = value.headingDegrees
                }
            }
        }
        viewModelScope.launch {
            // Observe beacon location update from the service so we can show it on the map
            serviceConnection?.getBeaconFlow()?.collectLatest { value ->
                if (value != null) {
                    // Use MarkerOptions and addMarker() to add a new marker in map
                    _homeMapStateFlow.value = LatLng(value.latitude, value.longitude)
                }
                else {
                    _homeMapStateFlow.value = LatLng(0.0, 0.0)
                }
            }
        }
    }

    fun onMapLongClick(location: LatLng ) : Boolean {
        soundscapeServiceConnection.soundscapeService?.createBeacon(
            location.latitude,
            location.longitude
        )
        return false
    }

    init {
        Log.e(TAG, "HomeViewModel init")

        serviceConnection = soundscapeServiceConnection
        iconFactory = IconFactory.getInstance(context)
        viewModelScope.launch {
            soundscapeServiceConnection.serviceBoundState.collect {
                Log.d(TAG, "serviceBoundState $it")
                if(it) {
                    // The service has started, so start monitoring the location and heading
                    startMonitoringLocation()
                }
                else {
                    // Reset map view variables so that the map re-centers when the service comes back
                    initialLocation = null
                    mapCentered = false

                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        Log.e(TAG, "onCleared called")
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
