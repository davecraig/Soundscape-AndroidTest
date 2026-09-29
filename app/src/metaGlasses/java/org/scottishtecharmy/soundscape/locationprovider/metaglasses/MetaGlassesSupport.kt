package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import android.content.Context
import org.scottishtecharmy.soundscape.locationprovider.DirectionProvider
import org.scottishtecharmy.soundscape.locationprovider.HeadTrackingProvider
import org.scottishtecharmy.soundscape.locationprovider.LocationProvider

/**
 * Meta glasses head tracking, built in because this build has Meta Wearables
 * credentials. The twin of this file in src/noMetaGlasses returns null.
 */
fun createMetaGlassesHeadTrackingProvider(
    context: Context,
    directionProvider: DirectionProvider,
    locationProvider: LocationProvider,
): HeadTrackingProvider? = MetaGlassesHeadTrackingProvider(
    directionProvider = directionProvider,
    locationProvider = locationProvider,
    client = MwdatMotionClient(context.applicationContext),
)
