package org.scottishtecharmy.soundscape.geoengine

import org.scottishtecharmy.soundscape.geoengine.filters.TrackedCallout
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

interface GeoEngineListener {
    fun isAudioEngineBusy(): Boolean
    fun speakCallout(callout: TrackedCallout?, addModeEarcon: Boolean): Long
    fun updateAudioEngineGeometry(userGeometry: UserGeometry)
    fun tileGridUpdated()
    fun updateStreetPreviewBestChoice(bestChoice: StreetPreviewChoice)
    fun announceStreetPreviewBestChoice(bestChoice: StreetPreviewChoice)
    fun getStreetPreviewChoices(): List<StreetPreviewChoice>
    fun getStreetPreviewBestChoice(): StreetPreviewChoice?
    val menuActive: Boolean

    /**
     * Moves the dynamic beacon to [location], creating it if it isn't playing yet.
     *
     * Deliberately not routed through the destination-beacon path: that one tells the geo engine
     * where the beacon is, which switches on the "destination, 50 metres" callout and suppresses
     * everything else. A beacon that is 25m ahead of you at all times has nothing to say about
     * distance, and the intersection callouts are exactly what you want alongside it.
     *
     * Defaulted so that the test fakes implementing this interface don't all have to care.
     */
    fun moveDynamicBeacon(location: LngLatAlt) {}

    /** Stops the dynamic beacon, for the mode being switched off. */
    fun stopDynamicBeacon() {}
}
