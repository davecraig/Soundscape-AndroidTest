package org.scottishtecharmy.soundscape.audio

import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

const val EARCON_MODE_ENTER = "file:///android_asset/Sounds/mode_enter.wav"
const val EARCON_MODE_EXIT = "file:///android_asset/Sounds/mode_exit.wav"

interface AudioEngine {
    fun createBeacon(location: LngLatAlt, headingOnly: Boolean): Long
    fun destroyBeacon(beaconHandle: Long)

    /**
     * Moves a beacon to a new coordinate, leaving its audio playing.
     *
     * Creating a beacon bakes the coordinate into the native positioning mode, so moving a beacon
     * used to mean destroy-and-recreate: the buffers are re-decoded and playback restarts from the
     * top, which is audible. A beacon that runs ahead of the user along the road moves every
     * second, so it needs this instead.
     */
    fun updateBeaconLocation(beaconHandle: Long, location: LngLatAlt)
    fun toggleBeaconMute(): Boolean
    fun createTextToSpeech(
        text: String,
        type: AudioType,
        latitude: Double = Double.NaN,
        longitude: Double = Double.NaN,
        heading: Double = Double.NaN
    ): Long

    fun createEarcon(
        asset: String,
        type: AudioType,
        latitude: Double = Double.NaN,
        longitude: Double = Double.NaN,
        heading: Double = Double.NaN
    ): Long

    fun clearTextToSpeechQueue()
    fun getQueueDepth(): Long
    fun isHandleActive(handle: Long): Boolean
    fun updateGeometry(
        listenerLatitude: Double,
        listenerLongitude: Double,
        listenerHeading: Double?,
        focusGained: Boolean,
        duckingAllowed: Boolean,
        proximityNear: Double
    )

    fun setBeaconType(beaconType: String)
    fun getListOfBeaconTypes(): Array<String>
    fun setSpeechLanguage(language: String): Boolean
    fun onAllBeaconsCleared()
    fun setHrtfEnabled(enabled: Boolean)
}
