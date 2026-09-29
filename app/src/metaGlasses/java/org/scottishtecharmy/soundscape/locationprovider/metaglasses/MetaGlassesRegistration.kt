package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.types.WearablesError
import kotlinx.coroutines.launch

/**
 * The one-off linking of this app to the user's Meta account, without which no
 * glasses session can start.
 *
 * Two directions, both handled here:
 *  - we ask, from [startIfAvailable]: the Meta AI app opens, the user approves,
 *    and it returns to us on the `soundscape` scheme already declared on
 *    MainActivity;
 *  - Meta AI asks, arriving as an intent that [handleIntent] recognises.
 *
 * The twin of this file in src/noMetaGlasses is all no-ops, so MainActivity can
 * call these unconditionally. See app/build.gradle.kts.
 */
object MetaGlassesRegistration {

    private var initialised = false

    /**
     * Log registration state and failures for the life of [activity]. Cheap,
     * and the only visibility there is into a flow that otherwise fails
     * silently - `META_AI_NOT_INSTALLED` being the likeliest reason.
     */
    fun observe(activity: ComponentActivity) {
        if (!ensureInitialised(activity)) return
        activity.lifecycleScope.launch {
            Wearables.registrationState.collect { Log.i(TAG, "Registration state: $it") }
        }
        activity.lifecycleScope.launch {
            Wearables.registrationErrorStream.collect { Log.w(TAG, "Registration error: $it") }
        }
    }

    /**
     * Open the Meta AI app to link this app, but only when that can actually
     * succeed: `AVAILABLE` means Meta AI is installed and has not linked us
     * yet, so nothing happens on a phone without it, or once registered.
     */
    fun startIfAvailable(activity: Activity) {
        if (!ensureInitialised(activity)) return
        val state = Wearables.registrationState.value
        if (state == RegistrationState.AVAILABLE) {
            Log.i(TAG, "Opening the Meta AI app to register for glasses head tracking")
            Wearables.startRegistration(activity)
        } else {
            Log.i(TAG, "Not starting registration, state is $state")
        }
    }

    /**
     * Handle a registration request the Meta AI app sent us. Returns true when
     * [intent] was one, in which case it is not a Soundscape navigation intent
     * and the caller should leave the current screen alone.
     */
    fun handleIntent(activity: Activity, intent: Intent): Boolean {
        if (!ensureInitialised(activity)) return false
        // Every intent the app receives is offered to a beta SDK here, so a
        // throw would take out onCreate/onNewIntent for intents that have
        // nothing to do with Meta glasses. Not worth the risk for a dev-mode
        // feature; a failure just means this wasn't a registration request.
        return runCatching {
            Wearables.handleIntent(intent) { request ->
                Log.i(TAG, "Meta AI registration request, flow ${request.flowId}")
                request.continueRegistration(activity).onFailure { error, _ ->
                    Log.w(TAG, "Could not continue registration: ${error.description}")
                }
                Unit
            }.getOrDefault(false)
        }.getOrElse {
            Log.w(TAG, "handleIntent threw", it)
            false
        }
    }

    /**
     * The SDK wants exactly one initialize() per process, before anything else
     * touches it - including [MwdatMotionClient], which calls through here
     * rather than keeping its own copy of the contract.
     */
    internal fun ensureInitialised(context: Context): Boolean {
        if (initialised) return true
        // Meta's own samples defer initialize() until BLUETOOTH_CONNECT is
        // granted; we call it from onCreate, before Soundscape has asked for
        // that, so treat a throw as "not initialised" rather than letting the
        // optional feature crash the app.
        val result = runCatching { Wearables.initialize(context.applicationContext) }
            .onFailure { Log.w(TAG, "Meta Wearables initialize threw", it) }
            .getOrNull()
            ?: return false
        // A second initialize() in the same process is fine, and expected: the
        // head tracking provider comes through here too.
        val error = result.errorOrNull()
        initialised = error == null || error == WearablesError.ALREADY_INITIALIZED
        if (!initialised) {
            Log.w(TAG, "Meta Wearables initialize failed: ${error?.description}")
        }
        return initialised
    }

    private const val TAG = "MetaGlassesRegistration"
}
