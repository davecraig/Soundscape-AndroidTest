package org.scottishtecharmy.soundscape.locationprovider

import kotlin.math.PI
import kotlin.math.atan2

private const val RAD_TO_DEG = 180.0 / PI

/**
 * Yaw of an orientation quaternion in degrees - its rotation about the Z axis.
 *
 * Follows the right-hand rule, so the result increases **counter-clockwise**
 * seen from above. Compass headings increase clockwise, so every caller that
 * feeds a yaw to HeadphoneCalibrationManager negates this first; the sign is
 * load-bearing, because the calibrator can only correct a constant offset and
 * a flipped yaw makes the head turn the wrong way rather than fail to converge.
 *
 * Only meaningful for a sensor whose Z axis is roughly world-up. That holds for
 * head-worn devices reporting a gravity-fused attitude (Bose Frames, Meta
 * glasses); a loose IMU with an arbitrary mounting needs
 * [org.scottishtecharmy.soundscape.locationprovider.bleimu.GravityAlignedYawIntegrator]
 * instead.
 */
fun quaternionYawDegrees(x: Double, y: Double, z: Double, w: Double): Double =
    atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z)) * RAD_TO_DEG
