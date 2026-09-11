package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.IntersectionType
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import org.scottishtecharmy.soundscape.geoengine.utils.circularDifferenceDegrees
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import kotlin.math.abs
import kotlin.math.max

/**
 * Below this the app itself would describe the road as being "ahead" - getCombinedDirectionSegments
 * makes AHEAD a 60 degree sector - so there is no instruction to give.
 */
const val TURN_ANGLE_MIN_DEGREES = 30.0

/**
 * OSM splits a road into a separate Way at every junction along it, so staying on one road produces
 * a Way change at every corner shop. A bend in a road the user never left is only worth calling a
 * turn if it is sharp.
 */
const val SAME_ROAD_TURN_ANGLE_MIN_DEGREES = 60.0

/**
 * At or above this, the map matcher has flipped its idea of which way along the road we are going
 * rather than the user having turned round. A real U-turn is lost with it; that is the better
 * trade, since a phantom one appears in the middle of an otherwise straight road.
 *
 * 150 rather than 160 because replaying real journeys produced exactly-160-degree flips in the
 * middle of Hope Street and of an unnamed path - both between two Ways of the same road, which is
 * what a flip looks like. See MvtTileTest.testJourneys.
 */
const val U_TURN_MAX_DEGREES = 150.0

/** How many fixes the match must stay on the new Way before the turn onto it is believed. */
const val TURN_CONFIRM_FIXES = 3

/** ...or how far travelled along it. 15m is a blink at 70mph, hence the speed term. */
const val TURN_CONFIRM_MINIMUM_METRES = 15.0
const val TURN_CONFIRM_SECONDS = 3.0

/**
 * How many short Ways the detector will step across while still measuring one turn. A pedestrian
 * turning at a junction usually crosses a side road on the way, which is its own Way a few metres
 * long; without this each crossing would read as two turns of half the angle.
 */
const val TURN_MAX_BRIDGE_WAYS = 3

/**
 * Watches the map-matched Way and reports the junctions where the user did not carry straight on.
 *
 * This is deliberately driven by the road graph rather than by the GPS heading: Way.heading gives
 * the bearing of the road's own first segment leaving a junction, so the angle is the junction's
 * real geometry and does not wobble with the fix. What the detector has to work around instead is
 * the *matcher* changing its mind - flapping between a road and the pavement beside it, or between
 * two parallel roads - which is what the hold-down and bridging below are for.
 *
 * Callers must run this on GridState.treeContext: Way.getName reaches into the grid. The grid is
 * nullable only so that tests can drive a hand-built Way graph with no tiles behind it - getName
 * already treats a null grid as "no name confection available".
 */
class TurnDetector {

    /** The Way turns are currently being measured from - the last one we committed to. */
    private var referenceWay: Way? = null

    private var candidate: Candidate? = null
    private var lastLocationForRun: LngLatAlt? = null

    private class Candidate(
        val fromWay: Way,
        /** Where we left [fromWay]. The turn is reported here, which is on the path by construction. */
        val turnIntersection: Intersection,
        val incomingBearing: Double,
        val fromRoad: String,
        var toWay: Way,
        var outgoingBearing: Double,
        var toRoad: String,
        var tileEdge: Boolean,
        var joiner: Boolean,
        /** Counts the fix that created the candidate, so this is fixes spent on [toWay]. */
        var fixes: Int = 1,
        var metres: Double = 0.0,
        var bridges: Int = 0,
    )

    /** Forget everything. Used when the user stops, or a journey ends. */
    fun reset() {
        referenceWay = null
        candidate = null
        lastLocationForRun = null
    }

    /**
     * Feed one location update in. Returns a Turn when one has just been confirmed, otherwise null.
     */
    fun update(
        userGeometry: UserGeometry,
        gridState: GridState?,
        localizedStrings: LocalizedStrings?,
    ): JourneyEvent.Turn? {

        // Standing still, the match wanders between whatever is nearby. And on a train the road
        // matcher is latching onto whichever road happens to run alongside the line, so its "turns"
        // are about the road, not the journey.
        if (!userGeometry.inMotion() || userGeometry.probablyOnTrain()) {
            candidate = null
            lastLocationForRun = userGeometry.location
            return null
        }

        val current = userGeometry.mapMatchedWay
        if (current == null) {
            // A gap in the match is not evidence of anything. Hold the reference so that coming
            // back onto the same road isn't read as a turn onto it.
            lastLocationForRun = userGeometry.location
            return null
        }

        val movedMetres = lastLocationForRun?.let {
            userGeometry.ruler.distance(it, userGeometry.location)
        } ?: 0.0
        lastLocationForRun = userGeometry.location

        val reference = referenceWay
        if (reference == null) {
            referenceWay = current
            return null
        }

        if (current === reference) {
            // Either we never left, or a candidate flapped back to where it started. Either way
            // there was no turn.
            candidate = null
            return null
        }

        val pending = candidate
        if (pending != null && pending.toWay === current) {
            pending.fixes++
            pending.metres += movedMetres
            val confirmDistance = max(
                TURN_CONFIRM_MINIMUM_METRES,
                userGeometry.speed * TURN_CONFIRM_SECONDS
            )
            if (pending.fixes < TURN_CONFIRM_FIXES && pending.metres < confirmDistance) {
                return null
            }
            // Held long enough to be real.
            referenceWay = current
            candidate = null
            return commit(pending, userGeometry)
        }

        if (pending != null) {
            // The match has moved on again before this candidate was confirmed, so the Way it was
            // heading for was a short link rather than a destination - a crossing, or the stub
            // joining a pavement to its road. Step across it and keep measuring the same turn.
            if (pending.bridges < TURN_MAX_BRIDGE_WAYS && extend(pending, current, gridState, localizedStrings)) {
                return null
            }
            candidate = null
            referenceWay = current
            return null
        }

        candidate = begin(reference, current, gridState, localizedStrings)
        if (candidate == null) {
            // No shared intersection: the matcher jumped rather than the user turning, or the grid
            // was rebuilt under us and these are new Way objects for the same roads.
            referenceWay = current
        }
        return null
    }

    private fun begin(
        from: Way,
        to: Way,
        gridState: GridState?,
        localizedStrings: LocalizedStrings?,
    ): Candidate? {
        val shared = from.doesIntersect(to).first ?: return null

        // Name each road in the direction it is travelled: we came down `from` towards this
        // junction, and we are about to head off along `to`. This matches how Intersection
        // .updateName picks its direction.
        val fromRoad = from.roadNameForInstruction(
            from.intersections[WayEnd.END.id] === shared, gridState, localizedStrings
        )
        val toRoad = to.roadNameForInstruction(
            to.intersections[WayEnd.START.id] === shared, gridState, localizedStrings
        )

        return Candidate(
            fromWay = from,
            turnIntersection = shared,
            incomingBearing = (from.heading(shared) + 180.0) % 360.0,
            fromRoad = fromRoad,
            toWay = to,
            outgoingBearing = to.heading(shared),
            toRoad = toRoad,
            tileEdge = shared.intersectionType == IntersectionType.TILE_EDGE,
            joiner = from.wayType == WayType.JOINER || to.wayType == WayType.JOINER,
        )
    }

    /**
     * Re-aim an unconfirmed candidate at a further Way, treating the one it was heading for as a
     * link that was crossed rather than turned onto. The turn keeps its original location - where
     * the user left the road they were on - but takes its angle and its destination from where they
     * actually ended up.
     */
    private fun extend(
        pending: Candidate,
        next: Way,
        gridState: GridState?,
        localizedStrings: LocalizedStrings?,
    ): Boolean {
        val shared = pending.toWay.doesIntersect(next).first ?: return false
        pending.outgoingBearing = next.heading(shared)
        pending.toRoad = next.roadNameForInstruction(
            next.intersections[WayEnd.START.id] === shared, gridState, localizedStrings
        )
        pending.joiner = pending.joiner || next.wayType == WayType.JOINER
        pending.toWay = next
        pending.bridges++
        pending.fixes = 1
        pending.metres = 0.0
        return true
    }

    private fun commit(pending: Candidate, userGeometry: UserGeometry): JourneyEvent.Turn? {
        // Both of these are grid-stitching artefacts rather than roads: a TILE_EDGE intersection is
        // where one tile's geometry was joined to the next, and a JOINER is the zero-length Way that
        // does the joining. Their angles are meaningless.
        if (pending.tileEdge || pending.joiner) return null

        val signed = circularDifferenceDegrees(pending.outgoingBearing, pending.incomingBearing)
        val magnitude = abs(signed)

        if (magnitude < TURN_ANGLE_MIN_DEGREES) return null
        if (magnitude >= U_TURN_MAX_DEGREES) return null

        // Stepping between a road and its own pavement is not a turn, however sharp the stub
        // joining them happens to be.
        if (isPavementOfTheOther(pending.fromWay, pending.toWay)) return null

        // Compare the raw OSM names, not the display names: displayName is translated for speech,
        // and two Ways of one road are only reliably the same road by their original name.
        val sameRoad = pending.fromWay.name != null && pending.fromWay.name == pending.toWay.name
        if (sameRoad && magnitude < SAME_ROAD_TURN_ANGLE_MIN_DEGREES) return null

        // Two un-named Ways that describe themselves identically are one thing seen twice, not two
        // roads: the pavement either side of a road, most often, which the matcher swaps between.
        // A real corner on a real road is caught by the check above instead, so a sharp bend on a
        // named street still counts. Found on a bus route that produced a 90 degree "turn" from
        // "Pavement next to Milngavie Road" onto itself - see MvtTileTest.testJourneys.
        if (!sameRoad && pending.fromRoad.isNotBlank() && pending.fromRoad == pending.toRoad) {
            return null
        }

        return JourneyEvent.Turn(
            location = pending.turnIntersection.location.clone(),
            timestampMillis = userGeometry.timestampMilliseconds,
            fromRoad = pending.fromRoad,
            toRoad = pending.toRoad,
            signedAngleDegrees = signed,
        )
    }

    /**
     * The road's name for use in a turn instruction, or "" when it hasn't got one worth saying.
     *
     * Stricter than a plain getName because an instruction is imperative: being told to "turn right
     * onto Path to dead end" is worse than being told to turn right and left to look. Replaying
     * real journeys produced exactly that, along with "Service to OSM Feature parking parking".
     * JourneyToRoute.turnInstruction drops the road from the wording when this is empty.
     */
    private fun Way.roadNameForInstruction(
        direction: Boolean,
        gridState: GridState?,
        localizedStrings: LocalizedStrings?,
    ): String = getName(
        direction,
        gridState,
        localizedStrings,
        nonGenericOnly = true,
        noGenericDeadEnds = true,
    )

    /**
     * Whether these two Ways are a road and its own pavement, or the two pavements of one road.
     * Stepping between them is the matcher changing its mind about which side of the street the
     * user is on, not the user turning a corner.
     *
     * The `pavement` property is what says so: addSidewalk (see WayNaming) writes the road's name
     * into it on each pavement, and also *renames the pavement itself* to "Pavement next to X" - so
     * the two sides of one road end up sharing a name and can't be told apart by name alone. That
     * is what put a 90 degree turn from "Pavement next to Milngavie Road" onto itself into a bus
     * route through Glasgow; see MvtTileTest.testJourneys.
     */
    private fun isPavementOfTheOther(a: Way, b: Way): Boolean {
        val aPavementOf = a.properties?.get("pavement")
        val bPavementOf = b.properties?.get("pavement")

        // Two pavements of the same road - opposite kerbs, or the same kerb split at a crossing.
        if (aPavementOf != null && aPavementOf == bPavementOf) return true

        return (aPavementOf != null && aPavementOf == b.name) ||
            (bPavementOf != null && bPavementOf == a.name)
    }
}
