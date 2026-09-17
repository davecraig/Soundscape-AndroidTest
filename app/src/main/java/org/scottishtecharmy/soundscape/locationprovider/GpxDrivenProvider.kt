package org.scottishtecharmy.soundscape.locationprovider

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.geoengine.utils.bearingFromTwoPoints
import org.scottishtecharmy.soundscape.geoengine.utils.gpx.parseGpx
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.GeodesicRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.io.InputStream

/**
 * Replays a recorded GPX track as if the user were walking it, so that the whole app - geoengine,
 * callouts, beacons - can be driven over a known route without leaving the desk.
 *
 * It synthesizes nothing but position and heading: the flows it feeds are the same ones a real
 * location provider fills, so everything downstream (including the audio engine geometry, which
 * GeoEngine derives from these flows) behaves exactly as it would on a real walk.
 *
 * The track is walked at the constant speed given to [start] rather than at the pace it was
 * recorded at. That's deliberate: a replay used to produce a tutorial recording needs to be
 * reproducible and needs to be able to cover dull stretches quickly, neither of which a recorded
 * pace gives you. Recorded timestamps are ignored entirely.
 *
 * Debug builds only - see SoundscapeIntents' REPLAY_GPX handling.
 */
class GpxDrivenProvider(
    /** Overridden by tests so the replay can be stepped through virtual time. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Only valid once [start] has returned true; it is rebuilt there so that it can be seeded with
     * the first point of the track rather than publishing a (0, 0) fix before the replay begins.
     */
    var locationProvider = StaticLocationProvider(LngLatAlt())
        private set

    val directionProvider = DirectionProvider()

    private var scope: CoroutineScope? = null

    /**
     * Parses [input] and begins replaying it. Returns false, having done nothing, unless the
     * stream is a GPX holding a track that can actually be replayed.
     *
     * A [speedMetresPerSecond] of zero means **stand still** at the first track point, facing along
     * the track, rather than walking it. That is what the audio tutorial guides record against: a
     * user who is moving gets automatic callouts the whole time, so the audio from pressing a
     * button is never surrounded by silence and cannot be cut out cleanly. Standing still also
     * makes the geoengine's StationaryDetector agree, after its thirty second window has filled.
     *
     * Only one point is needed to stand at one; walking needs two with some distance between them.
     */
    fun start(
        input: InputStream,
        speedMetresPerSecond: Double = DEFAULT_SPEED_MPS,
        loop: Boolean = false,
    ): Boolean {
        val points = parseTrackPoints(input)
        val standingStill = speedMetresPerSecond <= 0.0

        if (points.isEmpty()) {
            Log.e(TAG, "GPX has no track points")
            return false
        }
        if (!standingStill) {
            if (points.size < 2) {
                Log.e(TAG, "GPX has ${points.size} track point(s), need at least 2 to walk")
                return false
            }
            // A track recorded while stationary has plenty of points but no length, and there
            // would be nothing to walk along - the replay would never advance off the first point.
            val ruler = GeodesicRuler()
            val trackLength = points.zipWithNext().sumOf { (a, b) -> ruler.distance(a, b) }
            if (trackLength <= 0.0) {
                Log.e(TAG, "GPX track has no length, nothing to walk")
                return false
            }
            Log.d(
                TAG,
                "Replaying ${points.size} track points over ${trackLength.toInt()}m " +
                        "at $speedMetresPerSecond m/s (loop=$loop)"
            )
        } else {
            Log.d(TAG, "Standing still at ${points.first()}")
        }

        locationProvider = StaticLocationProvider(points.first())

        val newScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = newScope
        newScope.launch {
            if (standingStill) standStill(points) else replay(points, speedMetresPerSecond, loop)
            Log.d(TAG, "Replay finished")
        }
        return true
    }

    /**
     * Keeps publishing the first track point. The fixes have to keep coming rather than stopping
     * after one: the geoengine's StationaryDetector decides from a window of fixes, and with no
     * fixes at all it would never reach a verdict.
     */
    private suspend fun standStill(points: List<LngLatAlt>) {
        val heading =
            if (points.size >= 2) bearingFromTwoPoints(points[0], points[1]) else 0.0
        while (currentScopeIsActive()) {
            emit(points.first(), heading, speed = 0.0)
            delay(TICK_MILLIS)
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    /**
     * Walks the polyline at a constant speed, emitting a fix every [TICK_MILLIS] at whatever point
     * along it has been reached. Heading is the bearing of the segment currently being walked, so
     * it stays correct between track points rather than only at them.
     */
    private suspend fun replay(points: List<LngLatAlt>, speed: Double, loop: Boolean) {
        // start() has already established that the track has some length, so the advance below
        // always makes progress.
        val ruler = GeodesicRuler()
        val metresPerTick = speed * (TICK_MILLIS / 1000.0)

        // Position along the track: the segment points[index] -> points[index + 1], and how far
        // into that segment we are.
        var index = 0
        var metresIntoSegment = 0.0

        while (currentScopeIsActive()) {
            val from = points[index]
            val to = points[index + 1]
            val segmentLength = ruler.distance(from, to)
            val fraction =
                if (segmentLength > 0.0) (metresIntoSegment / segmentLength).coerceIn(0.0, 1.0)
                else 0.0

            emit(
                point = LngLatAlt(
                    from.longitude + ((to.longitude - from.longitude) * fraction),
                    from.latitude + ((to.latitude - from.latitude) * fraction),
                ),
                heading = bearingFromTwoPoints(from, to),
                speed = speed,
            )

            delay(TICK_MILLIS)

            // Advance by one tick's worth of travel, crossing as many track points as that takes -
            // a recording made while stationary can have several within a single step.
            var remaining = metresPerTick
            while (remaining > 0.0) {
                val segment = ruler.distance(points[index], points[index + 1])
                val leftInSegment = segment - metresIntoSegment
                if (remaining < leftInSegment) {
                    metresIntoSegment += remaining
                    remaining = 0.0
                } else {
                    remaining -= leftInSegment
                    metresIntoSegment = 0.0
                    index++
                    if (index >= points.size - 1) {
                        if (!loop) return
                        index = 0
                    }
                }
            }
        }
    }

    private fun emit(point: LngLatAlt, heading: Double, speed: Double) {
        directionProvider.mutableOrientationFlow.value = DeviceDirection(
            attitude = FloatArray(4),
            headingDegrees = heading.toFloat(),
            headingAccuracyDegrees = 0.0f,
            elapsedRealtimeNanos = System.nanoTime(),
        )
        locationProvider.updateLocation(
            SoundscapeLocation(
                latitude = point.latitude,
                longitude = point.longitude,
                bearing = heading.toFloat(),
                hasBearing = true,
                speed = speed.toFloat(),
                hasSpeed = true,
                // No accuracy at all, rather than a perfect 0.0m one: isAccuracyUsable() treats a
                // fix with no accuracy as a synthesized one and lets it through, which is what a
                // replay wants.
                hasAccuracy = false,
                timestampMilliseconds = System.currentTimeMillis(),
            )
        )
    }

    private fun currentScopeIsActive() = scope?.isActive == true

    /** Flattens every segment of every track in the file into one list of points to walk. */
    private fun parseTrackPoints(input: InputStream): List<LngLatAlt> {
        return try {
            parseGpx(input.bufferedReader().readText())
                .tracks
                .flatMap { it.trackSegments }
                .flatMap { it.trackPoints }
                .map { LngLatAlt(it.longitude, it.latitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Exception whilst parsing GPX file: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "GpxDrivenProvider"

        /** Brisk walking pace, and the default if the intent doesn't ask for another. */
        const val DEFAULT_SPEED_MPS = 1.4

        /** One fix a second, which is what a phone's GPS typically manages. */
        private const val TICK_MILLIS = 1000L
    }
}
