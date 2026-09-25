package org.scottishtecharmy.soundscape.viewmodels

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import org.scottishtecharmy.soundscape.screens.onboarding.audiobeacons.getBeaconResourceId
import org.scottishtecharmy.soundscape.audio.VoiceDescriptor
import org.scottishtecharmy.soundscape.utils.StorageUtils
import org.scottishtecharmy.soundscape.utils.getCurrentLocale
import org.scottishtecharmy.soundscape.utils.getOfflineMapStorage

class SettingsViewModel(
    private val soundscapeServiceConnection: SoundscapeServiceConnection,
    val appContext: Context
) : ViewModel() {
    data class SettingsUiState(
        // Data for the ViewMode that affects the UI
        var beaconDescriptions: List<org.jetbrains.compose.resources.StringResource> = emptyList(),
        var beaconValues: List<String> = emptyList(),
        var engineTypes: List<String> = emptyList(),
        var voiceDescriptors: List<VoiceDescriptor> = emptyList(),
        var storages: List<StorageUtils.StorageSpace> = emptyList(),
        var currentStoragePath: String = "",
        var selectedStorageIndex: Int = -1,
    )

    private val _state: MutableStateFlow<SettingsUiState> = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private var serviceBoundJob: Job? = null

    init {
        viewModelScope.launch {
            // Connect to the service and use its audio engine to get configuration and to
            // demonstrate settings changes.
            val storages = getOfflineMapStorage(appContext)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
            val path = sharedPreferences.getString(
                MainActivity.SELECTED_STORAGE_KEY,
                MainActivity.SELECTED_STORAGE_DEFAULT
            )
            var currentPath = ""
            var currentIndex = 0
            if ((path != null) && (path.isNotEmpty())) {
                for ((index, storage) in storages.withIndex()) {
                    if (storage.path == path) {
                        currentIndex = index
                        currentPath = path
                        break
                    }
                }
            } else {
                if (storages.isNotEmpty()) {
                    currentPath = storages[0].path
                    currentIndex = 0
                }
            }
            _state.value = SettingsUiState(
                storages = storages,
                currentStoragePath = currentPath,
                selectedStorageIndex = currentIndex
            )
            soundscapeServiceConnection.serviceBoundState.collect {
                Log.d(TAG, "serviceBoundState $it")
                val audioEngine = soundscapeServiceConnection.soundscapeService?.audioEngine
                if (it && audioEngine != null) {
                    serviceBoundJob = viewModelScope.launch {
                        audioEngine.ttsRunningStateChange.collectLatest { initialized ->
                            if (initialized) {
                                // Only once the TextToSpeech engine is initialized can we populate the
                                // members of these lists.
                                // getAvailableSpeechEngines/getAvailableSpeechVoices end up making a
                                // synchronous Binder call into the system TTS service
                                // (ITextToSpeechService.getVoices), which can block for a long time on
                                // some OEM builds. Run them off the main thread to avoid ANRs.
                                val (audioEngineTypes, audioEngineVoiceTypes) = withContext(Dispatchers.IO) {
                                    Pair(
                                        audioEngine.getAvailableSpeechEngines(),
                                        audioEngine.getAvailableSpeechVoices()
                                    )
                                }
                                // Ordering and grouping are buildVoiceCatalogue's job, shared
                                // with iOS, so all this does is drop the voices that aren't
                                // usable: ones needing the network, and ones the engine lists
                                // but hasn't downloaded. Quality is left at the default because
                                // Android's Voice.getQuality() doesn't distinguish tiers of the
                                // same speaker, and provider is left null because a voice list
                                // here already belongs to the engine picked one setting up.
                                val voiceDescriptors = audioEngineVoiceTypes
                                    .filter {
                                        !it.isNetworkConnectionRequired &&
                                            !it.features.contains("notInstalled")
                                    }
                                    .map { voice ->
                                        VoiceDescriptor(
                                            identifier = voice.name,
                                            // Android voices have no display name of their own,
                                            // only the identifier, so that is what is shown.
                                            displayName = voice.name,
                                            languageTag = voice.locale.toLanguageTag(),
                                        )
                                    }

                                val audioEngineBeaconTypes = audioEngine.getListOfBeaconTypes()
                                val beaconTypes =
                                    mutableListOf<org.jetbrains.compose.resources.StringResource>()
                                val beaconValues = mutableListOf<String>()
                                for (type in audioEngineBeaconTypes) {
                                    beaconTypes.add(getBeaconResourceId(type))
                                    beaconValues.add(type)
                                }
                                _state.value = _state.value.copy(
                                    beaconDescriptions = beaconTypes,
                                    beaconValues = beaconValues,
                                    voiceDescriptors = voiceDescriptors,
                                    engineTypes = audioEngineTypes.map { engine -> "${engine.label}:::${engine.name}" },
                                )
                            } else {
                                Log.d(TAG, "Engine has gone uninitialized")
                            }
                        }
                    }
                } else {
                    serviceBoundJob?.cancel()
                }
            }
        }
    }

    fun selectStorage(path: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        sharedPreferences.edit(commit = true) { putString(MainActivity.SELECTED_STORAGE_KEY, path) }

        var currentIndex = -1
        for ((index, storage) in _state.value.storages.withIndex()) {
            if (storage.path == path) {
                currentIndex = index
                break
            }
        }
        _state.value = _state.value.copy(
            currentStoragePath = path,
            selectedStorageIndex = currentIndex
        )
    }

    fun startBeaconPreview(beaconType: String) {
        soundscapeServiceConnection.startBeaconPreview(beaconType)
    }

    fun updateBeaconPreviewType(beaconType: String) {
        soundscapeServiceConnection.updateBeaconPreviewType(beaconType)
    }

    fun stopBeaconPreview(commit: Boolean, chosenBeaconType: String?) {
        soundscapeServiceConnection.stopBeaconPreview(commit, chosenBeaconType)
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}