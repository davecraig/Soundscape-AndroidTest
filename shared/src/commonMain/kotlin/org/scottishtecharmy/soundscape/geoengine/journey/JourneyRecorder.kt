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
 * How long without a single fix at vehicle speed before a ride is reckoned to be over.
 *
 * Generous, because nothing else about a moment tells you whether you are on a bus: it dwells at
 * stops, crawls under walking pace in traffic - a Glasgow bus route spends a quarter of its fixes
 * below 2.5 m/s - and loses the sky in tunnels. Three minutes of none of that is a ride ended.
 *
 * Taking this long to notice costs nothing, because it is settled retrospectively: the waypoint
 * goes where the vehicle last certainly was, and anything recorded in between is re-marked as
 * walked rather than ridden. See [withRideEnded].
 */
const val VEHICLE_QUIET_MILLIS = 180_000L

/**
 * How long a window the speed that decides "in a vehicle" is measured over, and the ground that has
 * to be covered across it - [org.scottishtecharmy.soundscape.geoengine.UserGeometry]'s own vehicle
 * threshold, applied to a minute rather than an instant.
 *
 * A fix's own speed is no use for this. Recordings made on foot are full of it: a 1.4km walk to
 * Tesco reports 209 of its 710 fixes above 5 m/s and peaks at 13 - which would make almost every
 * walk a bus ride and throw its waypoints away. Where the user has actually got to over a minute
 * cannot lie in the same way, and a straight line between the ends of the window only ever
 * understates it, so this errs towards calling a ride a walk rather than the reverse.
 *
 * Known to miss one case, deliberately left: a bus in city-centre traffic. BusTripToMilngavie
 * averages 2.88 m/s over 76 minutes, and through central Glasgow it never covers enough ground in
 * a minute to reach the threshold, so the turns the bus took come through as though they were the
 * user's. Lowering the threshold far enough to catch it starts catching brisk walks - the walk from
 * Glasgow Central to Buchanan Street averages 1.75 m/s - and there is no clean air between the two.
 * Separating them needs a signal this doesn't have; see MvtTileTest.testJourneys to measure it.
 */
const val VEHICLE_SPEED_WINDOW_MILLIS = 60_000L

/**
 * The fastest a person is taken to be walking, in m/s - about 9 km/h.
 *
 * Used to pin down where a ride ended. A window a minute long is what makes "in a vehicle" reliable,
 * but it lags: it goes on reading fast for a while after the user has stepped off, which would put
 * the waypoint a minute's walk beyond the stop. The last step between two fixes that was quicker
 * than anyone walks is a much better guess at where the vehicle actually left them.
 */
const val WALKING_MAX_SPEED_MPS = 2.5

/**
 * How long after the last fix at speed a journey *being saved* is taken to have left the vehicle.
 *
 * Shorter than [VEHICLE_QUIET_MILLIS] because the question is different. While recording there is
 * no hurry and every reason to be sure. At the moment of saving there is no more evidence coming,
 * and the ordinary case for this whole feature is getting off a bus, walking home and asking to
 * save - so waiting the full three minutes would throw that walk away and leave no waypoint on the
 * stop either. A minute of no fix at speed is enough to settle it, and the worst a wrong call costs
 * is one waypoint on someone who saved while stuck in traffic.
 */
const val VEHICLE_SETTLED_MILLIS = 60_000L

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

    /** The last minute of positions, which is what says whether the user is being carried along. */
    private val recentPositions = ArrayDeque<Pair<Long, LngLatAlt>>()

    /** The fix before this one, for the step-by-step pace that says where a ride ended. */
    private var previousFix: Pair<Long, LngLatAlt>? = null

    // The ride in progress, if there is one: when it was last certainly moving, where that was, and
    // the last stop announced along it. See JourneyEvent.Alighting.
    private var lastVehicleMillis: Long? = null
    private var lastVehicleLocation: LngLatAlt? = null
    private var lastTransitStopName: String? = null
    private var travellingByVehicle = false


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

        updateVehicleState(userGeometry)

        turnDetector.update(userGeometry, gridState, localizedStrings)?.let { turn ->
            val stamped = turn.copy(inVehicle = travellingByVehicle)
            if (!collapseIntoPreviousTurn(stamped)) {
                add(stamped)
            }
        }

        maybeAnchor(userGeometry)
    }

    /**
     * Follows whether the user is being carried along, and marks where they stopped being.
     *
     * The end of a ride is the only thing worth a waypoint on it, so it is recorded at the last
     * place the user was certainly moving at speed rather than a minute later where the sticky
     * window happens to expire - that is the stop, and it is where the beacon belongs.
     */
    /**
     * How fast the user has been going over the last [VEHICLE_SPEED_WINDOW_MILLIS], or null until
     * there is enough of the window to say.
     */
    private fun windowedSpeed(userGeometry: UserGeometry): Double? {
        val now = userGeometry.timestampMilliseconds
        recentPositions.addLast(now to userGeometry.location.clone())
        while (recentPositions.size > 1 &&
            now - recentPositions.first().first > VEHICLE_SPEED_WINDOW_MILLIS
        ) {
            recentPositions.removeFirst()
        }

        val (then, there) = recentPositions.first()
        val elapsed = now - then
        if (elapsed < VEHICLE_SPEED_WINDOW_MILLIS / 2) return null
        return there.createCheapRuler().distance(there, userGeometry.location) / (elapsed / 1000.0)
    }

    /** How fast the user covered the ground between the previous fix and this one. */
    private fun stepSpeed(userGeometry: UserGeometry): Double? {
        val (then, there) = previousFix ?: return null
        val elapsed = userGeometry.timestampMilliseconds - then
        if (elapsed <= 0L) return null
        return there.createCheapRuler().distance(there, userGeometry.location) / (elapsed / 1000.0)
    }

    private fun updateVehicleState(userGeometry: UserGeometry) {
        val carried = (windowedSpeed(userGeometry) ?: 0.0) >
            UserGeometry.VEHICLE_SPEED_THRESHOLD_MPS

        val step = stepSpeed(userGeometry)
        previousFix = userGeometry.timestampMilliseconds to userGeometry.location.clone()

        if (carried) {
            travellingByVehicle = true
            // Only while actually covering ground faster than walking, so the waypoint ends up on
            // the stop rather than a minute's walk past it - see WALKING_MAX_SPEED_MPS.
            if (lastVehicleLocation == null || (step != null && step > WALKING_MAX_SPEED_MPS)) {
                lastVehicleMillis = userGeometry.timestampMilliseconds
                lastVehicleLocation = userGeometry.location.clone()
            }
            return
        }

        val since = lastVehicleMillis ?: return
        if (userGeometry.timestampMilliseconds - since < VEHICLE_QUIET_MILLIS) {
            travellingByVehicle = true
            return
        }

        val where = lastVehicleLocation
        travellingByVehicle = false
        lastVehicleMillis = null
        lastVehicleLocation = null
        if (where == null) {
            lastTransitStopName = null
            return
        }

        val ended = withRideEnded(events.toList(), since, where, lastTransitStopName)
        lastTransitStopName = null
        events.clear()
        events.addAll(ended)
    }

    /**
     * [events] with a ride that ended at [atMillis] written into it.
     *
     * Two things happen at once, and both are backdated. The waypoint goes in where the vehicle was
     * last certainly moving - that is the stop, not wherever the user had got to by the time it
     * became clear they had got off - and everything recorded after that point is re-marked as
     * walked, because it was: the delay is in the noticing, not in the walking.
     */
    private fun withRideEnded(
        events: List<JourneyEvent>,
        atMillis: Long,
        where: LngLatAlt,
        stopName: String?,
    ): List<JourneyEvent> {
        val result = mutableListOf<JourneyEvent>()
        var placed = false
        for (event in events) {
            if (!placed && event.timestampMillis > atMillis) {
                result.add(JourneyEvent.Alighting(where, atMillis, stopName))
                placed = true
            }
            result.add(
                if (!placed) event else when (event) {
                    is JourneyEvent.Turn -> event.copy(inVehicle = false)
                    is JourneyEvent.Landmark -> event.copy(inVehicle = false)
                    else -> event
                }
            )
        }
        if (!placed) result.add(JourneyEvent.Alighting(where, atMillis, stopName))
        return result
    }

    /**
     * Called by AutoCallout when it announces a named place. Only the builders that name a *place*
     * report here - a road-sense description or a crossing is not somewhere a beacon can be put.
     *
     * [location] is the user's own position rather than the feature's; see [JourneyEvent.Landmark].
     */
    fun onLandmark(
        name: String,
        location: LngLatAlt,
        timestampMillis: Long,
        isTransitStop: Boolean = false,
    ) {
        if (!enabled) return
        if (name.isBlank()) return

        // The last stop named during a ride is the one it ended at, which is what to call the
        // waypoint left where the user got off.
        if (isTransitStop && travellingByVehicle) lastTransitStopName = name

        add(JourneyEvent.Landmark(location.clone(), timestampMillis, name, travellingByVehicle))
    }

    /**
     * Everything recorded, oldest first. Take this on the tree context too.
     *
     * Empty once switched off, without waiting for the buffer itself to be emptied: turning the
     * switch off means the app no longer holds your journey, and that has to be true the moment it
     * is turned off rather than whenever the next fix happens to arrive. See [enabled].
     */
    fun snapshot(): List<JourneyEvent> {
        if (!enabled) return emptyList()

        // A ride still open at the moment of asking is the ordinary case - get off the bus, walk
        // home, ask to save - so it is ended here too. Without this the walk since getting off is
        // still marked as ridden and thrown away, and there is no waypoint on the stop either.
        val since = lastVehicleMillis
        val where = lastVehicleLocation
        val latest = events.lastOrNull()?.timestampMillis
        if (since == null || where == null || latest == null) return events.toList()
        if (latest - since < VEHICLE_SETTLED_MILLIS) return events.toList()
        return withRideEnded(events.toList(), since, where, lastTransitStopName)
    }

    fun clear() {
        events.clear()
        lastAnchor = null
        lastVehicleMillis = null
        lastVehicleLocation = null
        lastTransitStopName = null
        travellingByVehicle = false
        recentPositions.clear()
        previousFix = null
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
