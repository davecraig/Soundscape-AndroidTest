package org.scottishtecharmy.soundscape.screens.onboarding.hearing

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.scottishtecharmy.soundscape.audio.AudioType
import org.scottishtecharmy.soundscape.audio.NativeAudioEngine
import javax.inject.Inject

@HiltViewModel
class HearingViewModel @Inject constructor(private val audioEngine : NativeAudioEngine): ViewModel() {

    override fun onCleared() {
        super.onCleared()
        audioEngine.destroy()
    }

    fun playSpeech(speechText: String) {
        // Set our listener position, and play the speech
        // TODO: If updateGeometry isn't called, then the audioEngine doesn't move on to   the next
        //  queued text to speech. That resulted in the Listen button only working one time.
        //  Calling updateGeometry (which in the service is called every 30ms) sorts this out.
        //  We should consider another way of doing this.
        audioEngine.clearTextToSpeechQueue()
        audioEngine.updateGeometry(
            listenerLatitude = 0.0,
            listenerLongitude = 0.0,
            listenerHeading = 0.0,
            focusGained = true,
            duckingAllowed = false
        )
        audioEngine.createTextToSpeech(speechText, AudioType.LOCALIZED)
    }

    fun silenceSpeech() {
        audioEngine.clearTextToSpeechQueue()
    }
}