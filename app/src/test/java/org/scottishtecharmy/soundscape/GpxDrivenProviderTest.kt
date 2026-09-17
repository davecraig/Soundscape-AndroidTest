package org.scottishtecharmy.soundscape

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.scottishtecharmy.soundscape.locationprovider.GpxDrivenProvider
import org.scottishtecharmy.soundscape.locationprovider.SoundscapeLocation

/**
 * The replay walks an L-shaped track: 20m due east, then 20m due north, from a point in Glasgow.
 * At 1 m/s with a fix a second that's ~20 fixes along each leg, which is enough to see what the
 * provider reports *between* track points as well as at them.
 */
class GpxDrivenProviderTest {

    private val startLatitude = 55.8642
    private val startLongitude = -4.2518

    // 20m east and 20m north at this latitude, near enough for the bearings being asserted.
    private val eastOffset = 20.0 / (111320.0 * Math.cos(Math.toRadians(startLatitude)))
    private val northOffset = 20.0 / 111132.0

    private fun lShapedTrack(): String {
        val points = listOf(
            startLatitude to startLongitude,
            startLatitude to startLongitude + eastOffset,
            startLatitude + northOffset to startLongitude + eastOffset,
        )
        val trkpts = points.joinToString("\n") { (lat, lon) ->
            """<trkpt lat="$lat" lon="$lon"></trkpt>"""
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><trkseg>
              $trkpts
              </trkseg></trk>
            </gpx>""".trimIndent()
    }

    /**
     * Runs a replay to completion (or [maxTicks], whichever comes first) and hands the fixes it
     * published to [assertions].
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun withReplay(
        maxTicks: Int = 200,
        assertions: (List<SoundscapeLocation>) -> Unit,
    ) = runTest {
        val provider = GpxDrivenProvider(StandardTestDispatcher(testScheduler))
        val fixes = mutableListOf<SoundscapeLocation>()

        Assert.assertTrue(
            "Replay should start",
            provider.start(lShapedTrack().byteInputStream(), speedMetresPerSecond = 1.0)
        )

        // Let the replay publish its first fix before sampling, otherwise the first thing seen is
        // the fix a second into the walk.
        testScheduler.runCurrent()

        // Sample the flow rather than collecting it: the provider publishes into a StateFlow, and
        // stepping the scheduler a tick at a time is what a real subscriber would see.
        repeat(maxTicks) {
            provider.locationProvider.locationFlow.value?.let { fix ->
                if (fixes.lastOrNull() != fix) fixes.add(fix)
            }
            testScheduler.advanceTimeBy(1000)
            testScheduler.runCurrent()
        }
        provider.stop()
        assertions(fixes)
    }

    @Test
    fun replayFollowsTheTrackAndStopsAtTheEnd() = withReplay { fixes ->
        Assert.assertTrue("Expected a run of fixes, got ${fixes.size}", fixes.size > 30)
        // 40m at 1m/s is ~40 fixes; well short of the 200 ticks we gave it, so it stopped on its own.
        Assert.assertTrue("Replay should have ended, got ${fixes.size} fixes", fixes.size < 60)

        Assert.assertEquals(startLatitude, fixes.first().latitude, 1e-6)
        Assert.assertEquals(startLongitude, fixes.first().longitude, 1e-6)

        Assert.assertEquals(startLatitude + northOffset, fixes.last().latitude, 1e-5)
        Assert.assertEquals(startLongitude + eastOffset, fixes.last().longitude, 1e-5)
    }

    /**
     * The bearing reported partway along a leg used to be 0 regardless of which way the track went,
     * because it was only computed when a new track point was reached. That put the listener facing
     * north for all but one fix in twenty, which quietly wrecks the spatialisation of every beacon
     * and callout in a replay.
     */
    @Test
    fun bearingIsTheDirectionOfTravelBetweenTrackPointsNotOnlyAtThem() = withReplay { fixes ->
        // First leg is due east, second due north. Allow a few fixes either side of the corner.
        val eastLeg = fixes.take(15)
        val northLeg = fixes.takeLast(15)

        Assert.assertTrue("Expected fixes on both legs", eastLeg.size == 15 && northLeg.size == 15)

        eastLeg.forEachIndexed { index, fix ->
            Assert.assertEquals("Fix $index should head east", 90.0, fix.bearing.toDouble(), 1.0)
            Assert.assertTrue("Fix $index should report a bearing", fix.hasBearing)
        }
        northLeg.forEachIndexed { index, fix ->
            Assert.assertEquals("Fix $index should head north", 0.0, fix.bearing.toDouble(), 1.0)
        }
    }

    @Test
    fun aTrackWithTooFewPointsIsRejected() {
        val single = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><trkseg><trkpt lat="55.0" lon="-4.0"></trkpt></trkseg></trk>
            </gpx>""".trimIndent()

        Assert.assertFalse(GpxDrivenProvider().start(single.byteInputStream()))
        Assert.assertFalse(GpxDrivenProvider().start("not a gpx file".byteInputStream()))
    }

    /** A recording made without moving has points but no length, and nothing to walk along. */
    @Test
    fun aTrackWithNoLengthIsRejected() {
        val stationary = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><trkseg>
                <trkpt lat="$startLatitude" lon="$startLongitude"></trkpt>
                <trkpt lat="$startLatitude" lon="$startLongitude"></trkpt>
                <trkpt lat="$startLatitude" lon="$startLongitude"></trkpt>
              </trkseg></trk>
            </gpx>""".trimIndent()

        Assert.assertFalse(GpxDrivenProvider().start(stationary.byteInputStream(), loop = true))
    }
}
