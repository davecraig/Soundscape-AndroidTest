package org.scottishtecharmy.soundscape.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import javax.inject.Inject

@HiltViewModel
class TestViewModel @Inject constructor( private val soundscapeServiceConnection : SoundscapeServiceConnection): ViewModel() {

    private var serviceConnection : SoundscapeServiceConnection? = null

    private fun startMonitoringLocation() {
        viewModelScope.launch {
            // Observe orientation updates from the service
            serviceConnection?.soundscapeService?.orientationFlow?.collectLatest { value ->
                if (value != null) {
                    val heading = value.headingDegrees
                    Log.e(TAG, "Location $heading")
                }
            }
        }
    }

    init {
        serviceConnection = soundscapeServiceConnection
        viewModelScope.launch {
            soundscapeServiceConnection.serviceBoundState.collect {
                Log.d(TAG, "TestViewModel serviceBoundState $it")
                if(it) {
                    startMonitoringLocation()
                }
            }
        }
    }

    companion object {
        private const val TAG = "TestViewModel"
    }
}
