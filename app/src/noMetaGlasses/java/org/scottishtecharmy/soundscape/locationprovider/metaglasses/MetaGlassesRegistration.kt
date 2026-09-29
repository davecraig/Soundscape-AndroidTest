package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity

/**
 * No-op stand-in for the Meta account linking flow, for builds without Meta
 * Wearables credentials - the Device Access Toolkit isn't on the classpath, so
 * there is nothing to register with. MainActivity calls these unconditionally
 * and they cost nothing here. See app/build.gradle.kts and the real
 * implementation in src/metaGlasses.
 */
@Suppress("UNUSED_PARAMETER")
object MetaGlassesRegistration {

    fun observe(activity: ComponentActivity) = Unit

    fun startIfAvailable(activity: Activity) = Unit

    fun handleIntent(activity: Activity, intent: Intent): Boolean = false
}
