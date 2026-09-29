package org.scottishtecharmy.soundscape.locationprovider

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class QuaternionYawTest {

    /** Quaternion for a rotation of [degrees] about the Z axis. */
    private fun aboutZ(degrees: Double): DoubleArray {
        val half = degrees * PI / 180.0 / 2.0
        return doubleArrayOf(0.0, 0.0, sin(half), cos(half))
    }

    private fun yawOf(q: DoubleArray) = quaternionYawDegrees(q[0], q[1], q[2], q[3])

    @Test
    fun identityQuaternionHasZeroYaw() {
        assertEquals(0.0, yawOf(doubleArrayOf(0.0, 0.0, 0.0, 1.0)), 1e-9)
    }

    @Test
    fun yawIsPositiveCounterClockwise() {
        // Right-hand rule: +90° about Z reads as +90, the opposite sense to a
        // compass heading. Providers negate this before calibrating.
        assertEquals(90.0, yawOf(aboutZ(90.0)), 1e-9)
        assertEquals(-45.0, yawOf(aboutZ(-45.0)), 1e-9)
    }

    @Test
    fun yawWrapsAtHalfTurn() {
        assertEquals(179.0, yawOf(aboutZ(179.0)), 1e-9)
        assertEquals(-179.0, yawOf(aboutZ(181.0)), 1e-9)
    }

    @Test
    fun pitchAndRollDoNotChangeYaw() {
        // 30° yaw then 20° pitch about the (already rotated) X axis: the yaw
        // component is unchanged, which is what makes this usable for a
        // head-worn sensor that is never perfectly level.
        val yawHalf = 15.0 * PI / 180.0
        val pitchHalf = 10.0 * PI / 180.0
        // Hamilton product of yaw-about-Z and pitch-about-X quaternions.
        val w = cos(yawHalf) * cos(pitchHalf)
        val x = cos(yawHalf) * sin(pitchHalf)
        val y = sin(yawHalf) * sin(pitchHalf)
        val z = sin(yawHalf) * cos(pitchHalf)
        assertEquals(30.0, quaternionYawDegrees(x, y, z, w), 1e-9)
    }
}
