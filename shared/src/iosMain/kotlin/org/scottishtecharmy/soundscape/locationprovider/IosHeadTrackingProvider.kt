package org.scottishtecharmy.soundscape.locationprovider

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.geoengine.headtracking.HeadphoneCalibrationManager
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMDeviceMotion
import platform.CoreMotion.CMHeadphoneMotionManager
import platform.CoreMotion.CMHeadphoneMotionManagerDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import kotlin.concurrent.Volatile
import kotlin.math.PI

private const val RAD_TO_DEG = 180.0 / PI

@OptIn(ExperimentalForeignApi::class)
class IosHeadTrackingProvider(
    private val directionProvider: DirectionProvider,
    private val locationProvider: LocationProvider,
) : HeadTrackingProvider() {

    private val motionManager: CMHeadphoneMotionManager? =
        if (isHeadphoneMotionSupported()) CMHeadphoneMotionManager() else null

    private val motionQueue = NSOperationQueue().apply {
        name = "HeadphoneMotionUpdatesQueue"
        maxConcurrentOperationCount = 1L
    }

    private val calibrationManager = HeadphoneCalibrationManager()
    private val delegate = HeadphoneDelegate(this)

    private var scope: CoroutineScope? = null

    init {
        if (motionManager == null) {
            mutableStatusFlow.value = HeadTrackingStatus.Unavailable
        }
    }

    override fun start() {
        val mm = motionManager ?: return
        if (mutableStatusFlow.value == HeadTrackingStatus.Unavailable) return

        if (!isAuthorizedOrUndetermined()) {
            mutableStatusFlow.value = HeadTrackingStatus.Unavailable
            return
        }

        if (!mm.isDeviceMotionAvailable()) {
            mutableStatusFlow.value = HeadTrackingStatus.Unavailable
            return
        }

        if (mm.isDeviceMotionActive()) return

        mm.delegate = delegate
        calibrationManager.start()
        mutableStatusFlow.value = HeadTrackingStatus.Disconnected

        mm.startDeviceMotionUpdatesToQueue(motionQueue) { motion: CMDeviceMotion?, _: NSError? ->
            if (motion != null) onDeviceMotion(motion)
        }

        val newScope = CoroutineScope(Dispatchers.Default + Job())
        scope = newScope
        newScope.launch {
            directionProvider.orientationFlow.collect { dir ->
                lastDeviceHeadingDegrees =
                    if (dir != null && dir.headingAccuracyDegrees >= 0f) dir.headingDegrees.toDouble()
                    else null
            }
        }
        newScope.launch {
            locationProvider.filteredLocationFlow.collect { loc ->
                lastCourseDegrees =
                    if (loc != null && loc.hasBearing) loc.bearing.toDouble() else null
            }
        }
    }

    override fun stop() {
        motionManager?.let {
            if (it.isDeviceMotionActive()) it.stopDeviceMotionUpdates()
            it.delegate = null
        }
        scope?.cancel()
        scope = null
        calibrationManager.stop()
        mutableHeadHeadingFlow.value = null
        mutableStatusFlow.value = HeadTrackingStatus.Inactive
    }

    @Volatile
    private var lastDeviceHeadingDegrees: Double? = null

    @Volatile
    private var lastCourseDegrees: Double? = null

    private fun onDeviceMotion(motion: CMDeviceMotion) {
        val attitude = motion.attitude ?: return
        val rawYawRadians = attitude.yaw
        // 180° flip so yaw increases clockwise from the AirPods origin (matches iOS reference).
        // This convention is load-bearing — flipping it requires inverting the offset sign.
        val yawDegrees = 180.0 - rawYawRadians * RAD_TO_DEG
        val timestampMillis = uptimeMillis()

        calibrationManager.pushDeviceReference(yawDegrees, lastDeviceHeadingDegrees, timestampMillis)
        lastCourseDegrees?.let { course ->
            calibrationManager.pushCourseReference(yawDegrees, course, timestampMillis)
        }

        val heading = calibrationManager.headingFor(yawDegrees)
        if (heading != null) {
            mutableHeadHeadingFlow.value = HeadHeading(
                degrees = heading,
                accuracyDegrees = 10.0,
                timestampMillis = timestampMillis,
            )
            if (mutableStatusFlow.value == HeadTrackingStatus.Connected) {
                mutableStatusFlow.value = HeadTrackingStatus.Calibrated
            }
        }
    }

    internal fun onHeadphonesConnected() {
        mutableHeadHeadingFlow.value = null
        if (mutableStatusFlow.value != HeadTrackingStatus.Inactive &&
            mutableStatusFlow.value != HeadTrackingStatus.Unavailable
        ) {
            mutableStatusFlow.value = HeadTrackingStatus.Connected
            calibrationManager.stop()
            calibrationManager.start()
        }
    }

    internal fun onHeadphonesDisconnected() {
        mutableHeadHeadingFlow.value = null
        if (mutableStatusFlow.value != HeadTrackingStatus.Inactive &&
            mutableStatusFlow.value != HeadTrackingStatus.Unavailable
        ) {
            mutableStatusFlow.value = HeadTrackingStatus.Disconnected
            calibrationManager.stop()
            calibrationManager.start()
        }
    }

    private fun isAuthorizedOrUndetermined(): Boolean {
        val status = CMHeadphoneMotionManager.authorizationStatus()
        return status != CMAuthorizationStatusDenied && status != CMAuthorizationStatusRestricted
    }

    private fun uptimeMillis(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()

    companion object {
        fun isHeadphoneMotionSupported(): Boolean {
            // CMHeadphoneMotionManager requires iOS 14.0+; AirPods Max landed in 14.2/14.3,
            // so the iOS reference gates on 14.4. Match that.
            val version = NSProcessInfo.processInfo.operatingSystemVersion
            return version.useContents {
                majorVersion > 14L || (majorVersion == 14L && minorVersion >= 4L)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class HeadphoneDelegate(
    private val provider: IosHeadTrackingProvider,
) : NSObject(), CMHeadphoneMotionManagerDelegateProtocol {

    override fun headphoneMotionManagerDidConnect(manager: CMHeadphoneMotionManager) {
        provider.onHeadphonesConnected()
    }

    override fun headphoneMotionManagerDidDisconnect(manager: CMHeadphoneMotionManager) {
        provider.onHeadphonesDisconnected()
    }
}
