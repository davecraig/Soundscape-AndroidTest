package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.geoengine.headtracking.HeadphoneCalibrationManager
import org.scottishtecharmy.soundscape.locationprovider.DirectionProvider
import org.scottishtecharmy.soundscape.locationprovider.HeadHeading
import org.scottishtecharmy.soundscape.locationprovider.HeadTrackingProvider
import org.scottishtecharmy.soundscape.locationprovider.HeadTrackingStatus
import org.scottishtecharmy.soundscape.locationprovider.LocationProvider
import org.scottishtecharmy.soundscape.locationprovider.quaternionYawDegrees
import org.scottishtecharmy.soundscape.platform.currentTimeMillis
import kotlin.concurrent.Volatile

/**
 * HeadTrackingProvider backed by the IMU in Meta smart glasses, via the Meta
 * Wearables Device Access Toolkit.
 *
 * The same shape as [org.scottishtecharmy.soundscape.locationprovider.bose.BoseFramesHeadTrackingProvider]:
 *   1. the glasses' fused quaternion → yaw (degrees, sensor frame)
 *   2. [HeadphoneCalibrationManager] correlates that yaw with the phone's
 *      compass + walking course to estimate a constant offset
 *   3. the calibrated geographic heading is published on `headHeadingFlow`
 *
 * Audio is not part of this. The glasses are already an ordinary Bluetooth
 * output device, so spatial audio reaches them whether or not this provider is
 * running; the toolkit supplies only the head pose.
 *
 * Only built when Meta Wearables credentials are configured - see
 * app/build.gradle.kts and [MetaMotionClient].
 */
class MetaGlassesHeadTrackingProvider(
    private val directionProvider: DirectionProvider,
    private val locationProvider: LocationProvider,
    private val client: MetaMotionClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HeadTrackingProvider() {

    private val calibrationManager =
        HeadphoneCalibrationManager(deviceWindowSize = CALIBRATION_WINDOW_SAMPLES)

    private var scope: CoroutineScope? = null

    @Volatile
    private var lastDeviceHeadingDegrees: Double? = null

    @Volatile
    private var lastCourseDegrees: Double? = null

    @Volatile
    private var lastCourseTimestampMillis: Long = 0L

    override fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(dispatcher + Job())
        scope = newScope
        calibrationManager.start()
        mutableStatusFlow.value = HeadTrackingStatus.Disconnected

        newScope.launch { observeDeviceHeading() }
        newScope.launch { observeCourse() }
        newScope.launch { runSessionLoop() }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        calibrationManager.stop()
        lastDeviceHeadingDegrees = null
        lastCourseDegrees = null
        lastCourseTimestampMillis = 0L
        mutableHeadHeadingFlow.value = null
        mutableStatusFlow.value = HeadTrackingStatus.Inactive
    }

    private suspend fun observeDeviceHeading() {
        directionProvider.orientationFlow.collect { dir ->
            lastDeviceHeadingDegrees =
                if (dir != null && dir.headingAccuracyDegrees >= 0f) dir.headingDegrees.toDouble()
                else null
        }
    }

    private suspend fun observeCourse() {
        locationProvider.filteredLocationFlow.collect { loc ->
            val usable = loc != null &&
                loc.hasBearing &&
                loc.hasSpeed &&
                loc.speed >= COURSE_MIN_SPEED_MPS
            if (usable) {
                lastCourseDegrees = loc.bearing.toDouble()
                lastCourseTimestampMillis = currentTimeMillis()
            } else {
                lastCourseDegrees = null
            }
        }
    }

    private suspend fun runSessionLoop() {
        while (true) {
            try {
                client.runSession(
                    onConnected = {
                        mutableStatusFlow.value = HeadTrackingStatus.Connected
                        // Re-learn the offset for this session: the glasses
                        // orient yaw=0 wherever they happen to be at start-up,
                        // and they may have been put on differently since.
                        calibrationManager.stop()
                        calibrationManager.start()
                    },
                    onSample = { sample -> processSample(sample) },
                )
            } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // No registered glasses, session refused, device disconnected,
                // sensors unavailable - wait and try again.
                mutableHeadHeadingFlow.value = null
                mutableStatusFlow.value = HeadTrackingStatus.Disconnected
                delay(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private fun processSample(sample: MetaMotionSample) {
        // A Neural Band reports on the same stream; its wrist orientation is
        // not a head pose, and feeding it to the calibrator would poison the
        // offset with arm movement.
        if (!sample.fromGlasses) return

        val nowMillis = currentTimeMillis()
        // Right-hand rule (CCW positive) out of the quaternion, compass heading
        // is CW positive - negate, as the Bose and WitMotion providers do.
        val yawDegrees = -quaternionYawDegrees(sample.x, sample.y, sample.z, sample.w)

        calibrationManager.pushDeviceReference(yawDegrees, lastDeviceHeadingDegrees, nowMillis)

        val course = lastCourseDegrees
        val courseFresh = course != null &&
            (nowMillis - lastCourseTimestampMillis) <= COURSE_STALENESS_MILLIS
        calibrationManager.pushCourseReference(
            yawDegrees,
            if (courseFresh) course else null,
            nowMillis,
        )

        val heading = calibrationManager.headingFor(yawDegrees) ?: return
        mutableHeadHeadingFlow.value = HeadHeading(
            degrees = heading,
            accuracyDegrees = REPORTED_ACCURACY_DEGREES,
            timestampMillis = nowMillis,
        )
        if (mutableStatusFlow.value == HeadTrackingStatus.Connected) {
            mutableStatusFlow.value = HeadTrackingStatus.Calibrated
        }
    }

    companion object {
        // Same gating values as the other head trackers, for consistent UX.
        private const val COURSE_MIN_SPEED_MPS = 0.4f
        private const val COURSE_STALENESS_MILLIS = 3_000L

        private const val REPORTED_ACCURACY_DEGREES = 10.0
        private const val RECONNECT_DELAY_MILLIS = 2_000L

        /**
         * The device calibrator is sized in samples, so this tracks the rate
         * the client asks the glasses for: 300 samples at 30 Hz is ~10 s, the
         * same window the WitMotion provider gets at 100 Hz.
         */
        const val CALIBRATION_WINDOW_SAMPLES = 300
    }
}
