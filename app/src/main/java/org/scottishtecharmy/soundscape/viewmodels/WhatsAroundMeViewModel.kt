package org.scottishtecharmy.soundscape.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import javax.inject.Inject

@HiltViewModel
class WhatsAroundMeViewModel @Inject constructor( private val soundscapeServiceConnection : SoundscapeServiceConnection): ViewModel() {

    private var serviceConnection : SoundscapeServiceConnection? = null

    fun whatsAroundMe(){
        //Log.d(TAG, "myLocation() triggered")
        viewModelScope.launch {
            serviceConnection?.soundscapeService?.whatsAroundMe()
        }
    }

    init {
        serviceConnection = soundscapeServiceConnection

    }

    companion object {
        private const val TAG = "WhatsAroundMeViewModel"
    }
}