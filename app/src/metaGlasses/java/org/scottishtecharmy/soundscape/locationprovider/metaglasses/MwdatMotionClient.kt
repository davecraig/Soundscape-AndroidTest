package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.removeMotion
import com.meta.wearable.dat.motion.types.MotionConfiguration
import com.meta.wearable.dat.motion.types.MotionSamplingRate
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.motion.types.MotionState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * [MetaMotionClient] backed by the Meta Wearables Device Access Toolkit.
 *
 * Only compiled when metaWearablesAppId / metaWearablesClientToken are set in
 * local.properties - see app/build.gradle.kts.
 *
 * Getting samples out of this needs three things beyond the credentials, none
 * of which the app can arrange for the user:
 *  - Developer Mode enabled in the Meta AI app on the same phone;
 *  - the app registered against the user's Meta account, which
 *    [MetaGlassesRegistration] drives from MainActivity - until it completes,
 *    [runSession] parks on [awaitRegistration] and logs why;
 *  - Motion, a beta capability, served only to Meta's development and beta
 *    release channels, so the account must be an invited tester.
 */
class MwdatMotionClient(private val context: Context) : MetaMotionClient {

    override suspend fun runSession(
        onConnected: () -> Unit,
        onSample: suspend (MetaMotionSample) -> Unit,
    ) {
        check(MetaGlassesRegistration.ensureInitialised(context)) {
            "Meta Wearables initialize failed"
        }
        awaitRegistration()

        // AutoDeviceSelector picks the best connected device, but createSession
        // fails outright with NO_ELIGIBLE_DEVICE when there is none. Wait for
        // one to show up rather than spinning the provider's retry loop.
        Wearables.devices.first { it.isNotEmpty() }

        val session = Wearables.createSession(AutoDeviceSelector()).getOrThrow()
        try {
            session.start()
            withTimeout(START_TIMEOUT_MILLIS) {
                session.state.first { it == DeviceSessionState.STARTED }
            }

            val motion = session.addMotion(MotionConfiguration(SAMPLING_RATE)).getOrThrow()
            try {
                motion.start()
                withTimeout(START_TIMEOUT_MILLIS) {
                    motion.state.first { it == MotionState.STARTED }
                }
                onConnected()

                coroutineScope {
                    val errorLogger = launch {
                        motion.errors.collect { Log.w(TAG, "Motion error: $it") }
                    }
                    try {
                        motion.samples.collect { sample ->
                            // Every reading is independently nullable; without
                            // an orientation there is no head pose to report.
                            val orientation = sample.orientation ?: return@collect
                            onSample(
                                MetaMotionSample(
                                    x = orientation.x.toDouble(),
                                    y = orientation.y.toDouble(),
                                    z = orientation.z.toDouble(),
                                    w = orientation.w.toDouble(),
                                    fromGlasses = sample.source == MotionSource.GLASSES,
                                )
                            )
                        }
                    } finally {
                        errorLogger.cancel()
                    }
                }
            } finally {
                motion.stop()
                session.removeMotion()
            }
        } finally {
            session.stop()
        }
    }

    private suspend fun awaitRegistration() {
        if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
            Log.i(
                TAG,
                "Waiting for Meta Wearables registration " +
                    "(state ${Wearables.registrationState.value}) - the app has to be linked to " +
                    "the user's Meta account from an Activity before any session can start",
            )
        }
        Wearables.registrationState.first { it == RegistrationState.REGISTERED }
    }

    companion object {
        private const val TAG = "MwdatMotionClient"

        /**
         * 30 Hz is the lowest rate that still feels immediate when the audio
         * pans with a head turn. Higher rates cost glasses battery, which is
         * the scarce resource on a long walk - if streaming proves too
         * expensive, drop to HZ_15 and halve
         * [MetaGlassesHeadTrackingProvider.CALIBRATION_WINDOW_SAMPLES] with it.
         */
        private val SAMPLING_RATE = MotionSamplingRate.HZ_30

        private const val START_TIMEOUT_MILLIS = 15_000L
    }
}
