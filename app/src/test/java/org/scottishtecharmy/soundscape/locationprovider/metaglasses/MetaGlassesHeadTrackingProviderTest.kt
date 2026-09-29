package org.scottishtecharmy.soundscape.locationprovider.metaglasses

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.scottishtecharmy.soundscape.locationprovider.Accuracy
import org.scottishtecharmy.soundscape.locationprovider.DeviceDirection
import org.scottishtecharmy.soundscape.locationprovider.DirectionProvider
import org.scottishtecharmy.soundscape.locationprovider.HeadTrackingStatus
import org.scottishtecharmy.soundscape.locationprovider.LocationProvider
import org.scottishtecharmy.soundscape.locationprovider.metaglasses.MetaGlassesHeadTrackingProvider.Companion.CALIBRATION_WINDOW_SAMPLES
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unlike the Bose and WitMotion providers - whose BLE clients are final classes
 * with no seam - this one takes a [MetaMotionClient], so the whole data path
 * (sample → yaw → calibration → published heading) can be driven with
 * synthetic samples and no glasses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetaGlassesHeadTrackingProviderTest {

    private class FakeDirectionProvider : DirectionProvider()

    private class FakeLocationProvider : LocationProvider() {
        override fun start(accuracy: Accuracy) {}
        override fun destroy() {}
    }

    private class FakeMetaMotionClient : MetaMotionClient {
        private var sink: (suspend (MetaMotionSample) -> Unit)? = null
        var sessions = 0
            private set

        override suspend fun runSession(
            onConnected: () -> Unit,
            onSample: suspend (MetaMotionSample) -> Unit,
        ) {
            sessions++
            sink = onSample
            onConnected()
            awaitCancellation()
        }

        suspend fun emit(sample: MetaMotionSample) {
            val target = checkNotNull(sink) { "runSession has not been called" }
            target(sample)
        }
    }

    /** Sample whose orientation is a rotation of [yawDegrees] CCW about Z. */
    private fun sampleAt(yawDegrees: Double, fromGlasses: Boolean = true): MetaMotionSample {
        val half = yawDegrees * PI / 180.0 / 2.0
        return MetaMotionSample(
            x = 0.0,
            y = 0.0,
            z = sin(half),
            w = cos(half),
            fromGlasses = fromGlasses,
        )
    }

    private class Fixture {
        val direction = FakeDirectionProvider()
        val location = FakeLocationProvider()
        val client = FakeMetaMotionClient()
        val provider = MetaGlassesHeadTrackingProvider(
            directionProvider = direction,
            locationProvider = location,
            client = client,
            dispatcher = UnconfinedTestDispatcher(),
        )

        /** Point the phone's compass at [degrees] before the provider starts. */
        fun compassAt(degrees: Float) {
            direction.mutableOrientationFlow.value = DeviceDirection(
                attitude = floatArrayOf(0f, 0f, 0f, 1f),
                headingDegrees = degrees,
                headingAccuracyDegrees = 5f,
                elapsedRealtimeNanos = 0L,
            )
        }
    }

    @Test
    fun initialStateIsInactiveWithNoHeading() {
        val f = Fixture()
        assertEquals(HeadTrackingStatus.Inactive, f.provider.statusFlow.value)
        assertNull(f.provider.headHeadingFlow.value)
    }

    @Test
    fun stopBeforeStartIsSafeAndLeavesStateInactive() {
        val f = Fixture()
        f.provider.stop()
        assertEquals(HeadTrackingStatus.Inactive, f.provider.statusFlow.value)
        assertNull(f.provider.headHeadingFlow.value)
    }

    @Test
    fun startConnectsButPublishesNothingUntilCalibrated() = runTest {
        val f = Fixture()
        f.compassAt(90f)
        f.provider.start()

        assertEquals(1, f.client.sessions)
        assertEquals(HeadTrackingStatus.Connected, f.provider.statusFlow.value)

        repeat(CALIBRATION_WINDOW_SAMPLES) { f.client.emit(sampleAt(0.0)) }
        assertNull(f.provider.headHeadingFlow.value)

        f.provider.stop()
    }

    @Test
    fun headingMatchesCompassOnceCalibratedFacingTheSameWay() = runTest {
        val f = Fixture()
        f.compassAt(90f)
        f.provider.start()

        // The device calibrator emits an offset on the sample after its window
        // fills; with a steady compass and a steady head the offset is exactly
        // the compass heading.
        repeat(CALIBRATION_WINDOW_SAMPLES + 1) { f.client.emit(sampleAt(0.0)) }

        val heading = f.provider.headHeadingFlow.value
        assertNotNull(heading)
        assertEquals(90.0, heading!!.degrees, 1e-6)
        assertEquals(HeadTrackingStatus.Calibrated, f.provider.statusFlow.value)

        f.provider.stop()
    }

    @Test
    fun turningTheHeadLeftDecreasesTheHeading() = runTest {
        val f = Fixture()
        f.compassAt(90f)
        f.provider.start()
        repeat(CALIBRATION_WINDOW_SAMPLES + 1) { f.client.emit(sampleAt(0.0)) }

        // +30° about Z is a counter-clockwise (leftward) head turn, which has
        // to read as a *smaller* compass heading. Getting this sign backwards
        // is the one calibration can't rescue, so it is pinned here.
        f.client.emit(sampleAt(30.0))
        assertEquals(60.0, f.provider.headHeadingFlow.value!!.degrees, 1e-6)

        f.client.emit(sampleAt(-30.0))
        assertEquals(120.0, f.provider.headHeadingFlow.value!!.degrees, 1e-6)

        f.provider.stop()
    }

    @Test
    fun samplesFromOtherWearablesAreIgnored() = runTest {
        val f = Fixture()
        f.compassAt(90f)
        f.provider.start()

        // A Neural Band's wrist orientation arrives on the same stream; it must
        // not reach the calibrator, however many of them turn up.
        repeat((CALIBRATION_WINDOW_SAMPLES + 1) * 2) {
            f.client.emit(sampleAt(45.0, fromGlasses = false))
        }

        assertNull(f.provider.headHeadingFlow.value)
        assertEquals(HeadTrackingStatus.Connected, f.provider.statusFlow.value)

        f.provider.stop()
    }

    @Test
    fun stopClearsHeadingAndStatus() = runTest {
        val f = Fixture()
        f.compassAt(90f)
        f.provider.start()
        repeat(CALIBRATION_WINDOW_SAMPLES + 1) { f.client.emit(sampleAt(0.0)) }
        assertNotNull(f.provider.headHeadingFlow.value)

        f.provider.stop()

        assertEquals(HeadTrackingStatus.Inactive, f.provider.statusFlow.value)
        assertNull(f.provider.headHeadingFlow.value)
    }
}
