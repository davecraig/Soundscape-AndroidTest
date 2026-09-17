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

    /**
     * Standing still is what the audio tutorial guides record against: a user who is moving gets
     * automatic callouts continuously, so the audio from a button press is never surrounded by the
     * silence needed to cut it out cleanly.
     */
    @Test
    fun speedZeroStandsStillAndKeepsPublishingFixes() = runTest {
        val provider = GpxDrivenProvider(StandardTestDispatcher(testScheduler))
        Assert.assertTrue(
            provider.start(lShapedTrack().byteInputStream(), speedMetresPerSecond = 0.0)
        )
        testScheduler.runCurrent()

        val seen = mutableListOf<SoundscapeLocation>()
        repeat(120) {
            provider.locationProvider.locationFlow.value?.let { seen.add(it) }
            testScheduler.advanceTimeBy(1000)
            testScheduler.runCurrent()
        }
        provider.stop()

        // Fixes have to keep arriving rather than stopping after one: StationaryDetector decides
        // from a 30s window of them and would never reach a verdict otherwise.
        Assert.assertTrue("Expected a continuing stream of fixes", seen.size >= 100)

        seen.forEach { fix ->
            Assert.assertEquals(startLatitude, fix.latitude, 1e-9)
            Assert.assertEquals(startLongitude, fix.longitude, 1e-9)
            Assert.assertEquals(0.0f, fix.speed, 1e-6f)
        }
        // Facing along the track - the first leg runs due east.
        Assert.assertEquals(90.0, seen.first().bearing.toDouble(), 1.0)

        // A bearing accuracy would make GeoEngine rate the course trustworthy, which is
        // StationaryDetector's fast escape out of the stationary state - so a standing replay
        // would be read as walking. See GeoEngine's stationaryDetector.update call.
        Assert.assertFalse("A replay must not claim bearing accuracy", seen.first().hasBearingAccuracy)
    }

    /** One point is enough to stand at, even though it is too few to walk along. */
    @Test
    fun aSinglePointTrackIsEnoughToStandAt() {
        val single = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><trkseg>
                <trkpt lat="$startLatitude" lon="$startLongitude"></trkpt>
              </trkseg></trk>
            </gpx>""".trimIndent()

        val provider = GpxDrivenProvider()
        Assert.assertTrue(provider.start(single.byteInputStream(), speedMetresPerSecond = 0.0))
        provider.stop()
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
