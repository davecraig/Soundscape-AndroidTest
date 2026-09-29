package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import android.content.Context
import org.scottishtecharmy.soundscape.locationprovider.DirectionProvider
import org.scottishtecharmy.soundscape.locationprovider.HeadTrackingProvider
import org.scottishtecharmy.soundscape.locationprovider.LocationProvider

/**
 * No Meta glasses head tracking in this build: without metaWearablesAppId and
 * metaWearablesClientToken in local.properties the Device Access Toolkit can't
 * attest the app, so neither the SDK nor its client wrapper is compiled in, and
 * this stub stands in for the factory in src/metaGlasses. See
 * app/build.gradle.kts.
 */
@Suppress("UNUSED_PARAMETER")
fun createMetaGlassesHeadTrackingProvider(
    context: Context,
    directionProvider: DirectionProvider,
    locationProvider: LocationProvider,
): HeadTrackingProvider? = null
