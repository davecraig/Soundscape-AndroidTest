package org.scottishtecharmy.soundscape.locationprovider

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.CLActivityTypeOtherNavigation
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/** How old CLLocationManager's cached fix may be before it is ignored on start. */
private const val MAX_CACHED_FIX_AGE_SECONDS = 60.0

class IosLocationProvider : LocationProvider() {

    private val locationManager = CLLocationManager()
    private val delegate = LocationDelegate(this)

    init {
        locationManager.allowsBackgroundLocationUpdates = true

        // Show the blue status-bar indicator while we hold location in the background.
        // Under When In Use authorization iOS shows it regardless of this flag, so this
        // is really for anyone who has granted Always — either from an older build that
        // asked for it, or by choosing it in Settings — who would otherwise get no
        // visual sign at all that Soundscape is still tracking. Matches what the
        // original iOS app sets in CoreLocationManager.startCoreLocationUpdates.
        locationManager.showsBackgroundLocationIndicator = true

        start()
    }

    /**
     * Asks for When In Use rather than Always.
     *
     * Always is what triggers iOS's recurring "Soundscape has been using your location
     * in the background" alert, and we don't need it: the `location` background mode
     * plus [CLLocationManager.allowsBackgroundLocationUpdates] already keeps updates
     * coming once they have been started, with the status bar indicator showing for as
     * long as they do. The original iOS app asks for exactly this and no more.
     *
     * Both call sites are foreground UI — the onboarding permissions screen and
     * MainViewController's returning-user path — which is what When In Use needs.
     * Anyone who granted Always to an earlier build keeps it; iOS never downgrades an
     * authorization because a later version asked for less.
     */
    fun requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    override fun start(accuracy: Accuracy) {
        locationManager.delegate = delegate

        locationManager.desiredAccuracy = when (accuracy) {
            Accuracy.High -> kCLLocationAccuracyBest
            Accuracy.Balanced -> kCLLocationAccuracyNearestTenMeters
        }

        locationManager.distanceFilter = accuracy.minimumDistanceM.toDouble()

        locationManager.pausesLocationUpdatesAutomatically = when (accuracy) {
            Accuracy.Balanced -> true
            Accuracy.High -> false
        }

        locationManager.activityType = when (accuracy) {
            Accuracy.Balanced -> CLActivityTypeOther
            Accuracy.High -> CLActivityTypeOtherNavigation
        }

        locationManager.startUpdatingLocation()
        seedFromCachedFix()
    }

    /**
     * Publishes CLLocationManager's cached fix straight away, if it is recent enough.
     *
     * Until the first delegate callback arrives, locationFlow stays null, and on a cold
     * start that can take longer than an assistant command is willing to wait — Siri
     * answering "Soundscape doesn't have your location yet" while a perfectly good
     * recent fix sat unread in the location manager.
     *
     * Bounded by age deliberately: for callouts a stale position is worse than none,
     * because describing surroundings the user has already walked away from misleads
     * rather than merely disappoints.
     */
    private fun seedFromCachedFix() {
        val cached = locationManager.location ?: return
        val ageSeconds = NSDate().timeIntervalSince1970 - cached.timestamp.timeIntervalSince1970
        if (ageSeconds in 0.0..MAX_CACHED_FIX_AGE_SECONDS) {
            onLocationUpdate(cached)
        }
    }

    fun pause() {
        locationManager.stopUpdatingLocation()
        locationManager.delegate = null
    }

    /**
     * Pauses updates and forgets the fix we were holding.
     *
     * For the intent-only processes that give their updates back when the command is
     * done (see IosSoundscapeService.onIntentFinished): the position is only going to
     * get more wrong while nothing is watching, and the next assistant command — which
     * may arrive minutes or hours later — has to wait for a fresh fix rather than
     * describe where the user used to be. Clearing the flows is what makes it wait:
     * SoundscapeActionExecutor.callout() only spends its ready timeout when
     * locationFlow is null, and would otherwise take the stale value at face value.
     *
     * Same reasoning as [MAX_CACHED_FIX_AGE_SECONDS] — for callouts a stale position is
     * worse than none. Kept as a separate entry point rather than folded into [pause],
     * whose other callers (sleep mode, street preview, [destroy]) hand the provider
     * straight back to something that reads the flows, and have no such problem.
     */
    fun pauseAndForgetFix() {
        pause()
        mutableLocationFlow.value = null
        mutableFilteredLocationFlow.value = null
    }

    override fun destroy() {
        pause()
    }

    @OptIn(ExperimentalForeignApi::class)
    internal fun onLocationUpdate(location: CLLocation) {
        val coordinate = location.coordinate.useContents {
            SoundscapeLocation(
                latitude = latitude,
                longitude = longitude,
                accuracy = location.horizontalAccuracy.toFloat(),
                bearing = location.course.toFloat(),
                speed = location.speed.toFloat(),
                hasAccuracy = location.horizontalAccuracy >= 0,
                hasBearing = location.course >= 0,
                hasSpeed = location.speed >= 0,
                timestampMilliseconds = (location.timestamp.timeIntervalSince1970 * 1000).toLong(),
            )
        }
        mutableLocationFlow.value = coordinate
        mutableFilteredLocationFlow.value = coordinate
    }
}

private class LocationDelegate(
    private val provider: IosLocationProvider
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        provider.onLocationUpdate(location)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        // Location errors are logged but not fatal
        println("Location error: ${didFailWithError.localizedDescription}")
    }
}
