package org.scottishtecharmy.soundscape.geoengine.headtracking

import org.scottishtecharmy.soundscape.geoengine.utils.normalizeDegrees

class HeadphoneCalibrationManager(
    private val calibrator: CompositeHeadphoneCalibrator = CompositeHeadphoneCalibrator(),
) {
    private var active = false

    val isCalibrated: Boolean
        get() = calibrator.estimatedOffsetDegrees != null

    fun start() {
        active = true
    }

    fun stop() {
        active = false
        calibrator.reset()
    }

    fun headingFor(yawDegrees: Double): Double? {
        if (!active) return null
        val offset = calibrator.estimatedOffsetDegrees ?: return null
        return (yawDegrees + offset).normalizeDegrees()
    }

    fun pushDeviceReference(
        yawDegrees: Double,
        deviceHeadingDegrees: Double?,
        timestampMillis: Long
    ) {
        if (!active) return
        calibrator.processDevice(yawDegrees, deviceHeadingDegrees, timestampMillis)
    }

    fun pushCourseReference(yawDegrees: Double, courseDegrees: Double?, timestampMillis: Long) {
        if (!active) return
        calibrator.processCourse(yawDegrees, courseDegrees, timestampMillis)
    }
}
