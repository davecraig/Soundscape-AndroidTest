package org.scottishtecharmy.soundscape.geoengine.journey

import kotlin.concurrent.Volatile
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.utils.circularDifferenceDegrees
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.createCheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import kotlin.math.abs
import kotlin.math.sign

/** How far, or how long, between the path anchors that segmentation and the route's ends are read from. */
const val JOURNEY_ANCHOR_METRES = 100.0
const val JOURNEY_ANCHOR_MILLIS = 60_000L

/** Nothing useful is remembered from more than half a day ago, and the buffer is not a diary. */
const val JOURNEY_MAX_EVENTS = 4000
const val JOURNEY_MAX_AGE_MILLIS = 12 * 60 * 60 * 1000L

/**
 * Two turns this close together, bending the same way, are folded into one. On a mini-roundabout
 * they are literally one gyratory; at two junctions a few metres apart they are two instructions
 * nobody can act on separately. Either way, leaving them apart produces waypoints closer together
 * than RoutePlayer's 12m arrival radius, which it would skip straight through.
 */
const val ROUNDABOUT_COLLAPSE_METRES = 40.0
const val ROUNDABOUT_COLLAPSE_MILLIS = 15_000L

/**
 * How far, and for how long, a pair of turns that leaves a road and comes straight back onto it is
 * still treated as never having left.
 *
 * Wider than the gyratory window above because this shape is unambiguous: whatever happened in
 * between, someone who is back on the road they started on has carried straight along it, and
 * saying otherwise is worse than saying nothing. Walking up West Nile Street in Glasgow produced
 * six of these - the matcher hopping onto each side lane and back - which read as six turns on a
 * street the user never left. See MvtTileTest.testJourneys.
 */
const val RETURNED_TO_ROAD_METRES = 150.0
const val RETURNED_TO_ROAD_MILLIS = 120_000L

/**
 * Keeps a running, in-memory account of the journey the user is on, so that afterwards they can say
 * "save the last journey" and get a Route out of it.
 *
 * This is a sibling of [org.scottishtecharmy.soundscape.geoengine.LocationRecorder] / GpxRecorder,
 * not a replacement for it. They look similar and are not: GpxRecorder takes every raw fix, ahead of
 * the accuracy gate, behind the RECORD_TRAVEL developer switch, because a recording made in a tunnel
 * is only useful for diagnosis if the bad fixes are in it. This one takes derived events, after the
 * accuracy gate, and is on by default - bad fixes are the main source of phantom turns, so they are
 * exactly what it does not want.
 *
 * Nothing here is written to disk. The account dies with the service, which is the honest cost of
 * recording all the time without persisting anything.
 *
 * Confined to GridState.treeContext - see [onLocation].
 */
class JourneyRecorder {

    /**
     * Turned off by the user in Settings. Nothing is recorded and anything held is dropped.
     *
     * Assigned from wherever the preference change arrives - the main thread, on Android - so it
     * can't touch the buffer itself: everything else here runs on the tree context. Clearing is
     * deferred to the next location update instead, which is the next moment anything reads it.
     */
    @Volatile
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) clearRequested = true
        }

    /** Set when [enabled] goes false, acted on by the recording thread. See [enabled]. */
    @Volatile
    private var clearRequested = false

    private val events = ArrayDeque<JourneyEvent>()
    private val turnDetector = TurnDetector()

    private var lastAnchor: JourneyEvent.Anchor? = null

    /**
     * Called for every usable location update, on GridState.treeContext - Way.getName reaches into
     * the grid, and the names have to be resolved now because the Ways they come from will not
     * exist by the time the user saves.
     */
    fun onLocation(
        userGeometry: UserGeometry,
        gridState: GridState?,
        localizedStrings: LocalizedStrings?,
    ) {
        if (clearRequested) {
            clearRequested = false
            clear()
        }
        if (!enabled) return

        turnDetector.update(userGeometry, gridState, localizedStrings)?.let { turn ->
            if (!collapseIntoPreviousTurn(turn)) {
                add(turn)
            }
        }

        maybeAnchor(userGeometry)
    }

    /**
     * Called by AutoCallout when it announces a named place. Only the builders that name a *place*
     * report here - a road-sense description or a crossing is not somewhere a beacon can be put.
     *
     * [location] is the user's own position rather than the feature's; see [JourneyEvent.Landmark].
     */
    fun onLandmark(name: String, location: LngLatAlt, timestampMillis: Long) {
        if (!enabled) return
        if (name.isBlank()) return
        add(JourneyEvent.Landmark(location.clone(), timestampMillis, name))
    }

    /**
     * Everything recorded, oldest first. Take this on the tree context too.
     *
     * Empty once switched off, without waiting for the buffer itself to be emptied: turning the
     * switch off means the app no longer holds your journey, and that has to be true the moment it
     * is turned off rather than whenever the next fix happens to arrive. See [enabled].
     */
    fun snapshot(): List<JourneyEvent> = if (enabled) events.toList() else emptyList()

    fun clear() {
        events.clear()
        lastAnchor = null
        turnDetector.reset()
    }

    private fun maybeAnchor(userGeometry: UserGeometry) {
        val previous = lastAnchor
        val due = previous == null ||
            userGeometry.timestampMilliseconds - previous.timestampMillis >= JOURNEY_ANCHOR_MILLIS ||
            userGeometry.ruler.distance(previous.location, userGeometry.location) >= JOURNEY_ANCHOR_METRES
        if (!due) return

        val anchor = JourneyEvent.Anchor(
            location = userGeometry.location.clone(),
            timestampMillis = userGeometry.timestampMilliseconds,
            speedMps = userGeometry.speed,
            inVehicle = userGeometry.inVehicle(),
        )
        lastAnchor = anchor
        add(anchor)
    }

    /**
     * Fold a turn into the one before it when the two are really one manoeuvre. The combined turn
     * keeps the *entry* location - that is where the user has to know which way to go - but takes
     * its exit road from the turn being folded in.
     */
    private fun collapseIntoPreviousTurn(turn: JourneyEvent.Turn): Boolean {
        val index = events.indexOfLast { it is JourneyEvent.Turn }
        if (index == -1) return false
        val previous = events[index] as JourneyEvent.Turn

        val elapsed = turn.timestampMillis - previous.timestampMillis
        val apart = previous.location.createCheapRuler()
            .distance(previous.location, turn.location)

        // Left a road and came back onto it: a detour the route has no reason to mention, and far
        // more often the matcher dipping onto a side lane and back.
        val returnedToTheRoadItLeft =
            previous.fromRoad.isNotBlank() && turn.toRoad == previous.fromRoad

        val foldable = if (returnedToTheRoadItLeft) {
            elapsed <= RETURNED_TO_ROAD_MILLIS && apart <= RETURNED_TO_ROAD_METRES
        } else {
            // One gyratory: close together, and bending the same way throughout.
            turn.signedAngleDegrees.sign == previous.signedAngleDegrees.sign &&
                elapsed <= ROUNDABOUT_COLLAPSE_MILLIS &&
                apart <= ROUNDABOUT_COLLAPSE_METRES
        }
        if (!foldable) return false

        // The net change of heading, not the sum of two numbers: heading is circular, so three
        // quarter-turns the same way around a gyratory leave you going a quarter-turn the other
        // way, and that is what the instruction has to say.
        val combined = circularDifferenceDegrees(
            previous.signedAngleDegrees + turn.signedAngleDegrees,
            0.0,
        )
        val folded = previous.copy(toRoad = turn.toRoad, signedAngleDegrees = combined)

        // Folding can produce something that is not a manoeuvre at all: two turns that between them
        // put the user back on the road they started on, or that cancel each other out. That is the
        // matcher wobbling at a junction rather than a gyratory, so the whole thing goes - an
        // instruction invented to paper over it is worse than no waypoint. Found on a bus route
        // through Glasgow, where it produced "turn from Hope Street onto Hope Street" - see
        // MvtTileTest.testJourneys.
        val backOnTheSameRoad = folded.fromRoad.isNotBlank() && folded.fromRoad == folded.toRoad
        val cancelledOut = abs(combined) < TURN_ANGLE_MIN_DEGREES
        // A fold reaching the flip threshold means the matcher turned the user round, not the road.
        // commit() rejects those for a single turn; a fold has to as well, or the check is simply
        // bypassed by arriving in two pieces - which is how a 172 degree "sharp left" got into a
        // walk across Glasgow.
        val flipped = abs(combined) >= U_TURN_MAX_DEGREES
        if (backOnTheSameRoad || cancelledOut || flipped) {
            events.removeAt(index)
            return true
        }

        events[index] = folded
        return true
    }

    private fun add(event: JourneyEvent) {
        events.addLast(event)
        while (events.size > JOURNEY_MAX_EVENTS) {
            events.removeFirst()
        }
        val oldestAllowed = event.timestampMillis - JOURNEY_MAX_AGE_MILLIS
        while (events.isNotEmpty() && events.first().timestampMillis < oldestAllowed) {
            events.removeFirst()
        }
    }
}
