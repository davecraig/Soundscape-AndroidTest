package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.AlongWayFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.AlongWayKind
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * Converts the result of [Ruler.distanceToLineString] into a distance in metres from the start of
 * that line.
 *
 * [PointAndDistanceAndHeading.positionAlongLine] is a *fractional vertex index* (segment index +
 * how far along that segment the point falls), not a distance, so turning it into metres means
 * walking the vertices up to that segment and adding the fraction of the final one. Shared by
 * StreetDescription (distance along a chain of Ways making up a street) and Way.distanceAlongWay
 * (position of a feature along a single Way).
 */
fun distanceAlongLineString(
    line: LineString,
    pdh: PointAndDistanceAndHeading,
    ruler: Ruler
): Double {
    if (pdh.index < 0 || pdh.positionAlongLine.isNaN()) return 0.0

    var distance = 0.0
    for (i in 0 until pdh.index) {
        distance += ruler.distance(line.coordinates[i], line.coordinates[i + 1])
    }
    distance += (pdh.positionAlongLine - pdh.index) * ruler.distance(
        line.coordinates[pdh.index],
        line.coordinates[pdh.index + 1]
    )
    return distance
}

/**
 * Where something is along the Way network: which Way, how far along it from that Way's START
 * intersection, and which way it's heading.
 *
 * This is the cursor the along-way queries below work from. Holding the user's own position in
 * this form is what lets "what's the next crossing?" and "did I pass anything since the last
 * update?" be answered by walking the Way graph, rather than by a radius search around the user
 * which can't tell the road ahead from the road alongside.
 *
 * @param forwards true when travelling from the Way's START intersection towards its END, false
 * for the reverse, and null when the direction isn't known - stationary, or no travel heading yet.
 * A null direction makes the queries below look both ways rather than guess.
 */
data class WayCursor(
    val way: Way,
    val distanceFromStart: Double,
    val forwards: Boolean?,
)

/**
 * How far a walk along the Way network is willing to follow the road.
 *
 * A single road is split into many Ways - at every junction, and at every tile boundary - so any
 * question about what lies ahead has to decide what counts as "ahead" once the road stops being
 * one Way.
 */
enum class WayContinuation {
    /**
     * Through intersections joining exactly two Ways, stopping at any real junction. The same
     * definition of "straight on" that Way.followWays uses and Street Preview follows, and the
     * honest answer for something the user is about to arrive at: past a junction there is no
     * single road ahead to be looking down.
     */
    STRAIGHT_ON,

    /**
     * Through junctions too, taking whichever Way continues the same road by name or ref. Needed
     * to see any distance up a road that is worth naming: an urban main road has a side street
     * every fifty metres, so STRAIGHT_ON stops almost immediately and a hundred-metre lookahead
     * would never reach anything.
     */
    SAME_ROAD,

    /**
     * Through anything that isn't a junction worth stopping at, deciding what counts with
     * [JunctionArms].
     *
     * What a beacon running ahead of the user wants. [STRAIGHT_ON] stops wherever an intersection
     * has more than one way out, which in this tile data means every pavement connector and every
     * driveway, so a beacon using it would spend the walk parked. This follows the road instead,
     * and stops only where the road genuinely forks.
     */
    REAL_JUNCTIONS,
}

/**
 * Which arms of an intersection make it a junction, for [WayContinuation.REAL_JUNCTIONS].
 *
 * A setting rather than a constant because the right answer is a judgement about how often a
 * beacon should stop, and that is something to be decided by walking around with it.
 */
enum class JunctionArms(val key: String) {
    /** Anything that isn't a pavement or crossing: service roads, tracks and paths included. */
    AnyWay("any"),

    /**
     * Only named roads. A path, track, service road or unnamed stub joining the road is walked
     * past rather than stopped at - it can still be called out, it just isn't a decision point,
     * and stopping the beacon at every farm track and supermarket access road makes the stopping
     * mean nothing.
     */
    NamedRoads("named");

    companion object {
        val default = NamedRoads
        val keys = entries.map { it.key }

        fun fromPreference(value: String?): JunctionArms =
            entries.firstOrNull { it.key == value } ?: default
    }
}

/**
 * Index of the first entry strictly beyond [distance] in a list sorted by distanceFromStart.
 *
 * Binary search rather than a scan. The lists are short while only crossings are recorded, but
 * this is the primitive transit stops and highway junctions will use too, and a busy road carries
 * a lot more of those than it does bridges.
 */
internal fun List<AlongWayFeature>.firstIndexBeyond(distance: Double): Int {
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high).ushr(1)
        if (this[mid].distanceFromStart > distance) high = mid else low = mid + 1
    }
    return low
}

/**
 * Index of the first entry at or beyond [distance] - the inclusive counterpart of
 * [firstIndexBeyond], for a [distance] which is a Way boundary rather than the user's position.
 */
internal fun List<AlongWayFeature>.firstIndexAtOrBeyond(distance: Double): Int {
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high).ushr(1)
        if (this[mid].distanceFromStart >= distance) high = mid else low = mid + 1
    }
    return low
}

/**
 * The features on this Way beyond [distance], nearest first.
 *
 * Exclusive by default, so a feature exactly at the user's position counts as passed rather than
 * still ahead. [inclusive] is for the other caller: a walk entering this Way at one of its ends,
 * where [distance] is that boundary and a feature sitting exactly on it has not been reached yet.
 */
fun Way.alongWayFeaturesAfter(
    distance: Double,
    inclusive: Boolean = false
): List<AlongWayFeature> {
    val from = if (inclusive) {
        alongWayFeatures.firstIndexAtOrBeyond(distance)
    } else {
        alongWayFeatures.firstIndexBeyond(distance)
    }
    return alongWayFeatures.subList(from, alongWayFeatures.size)
}

/**
 * The features on this Way at or before [distance], nearest first - so in descending order of
 * distanceFromStart, which is the order they're met travelling END to START.
 */
fun Way.alongWayFeaturesBefore(distance: Double): List<AlongWayFeature> =
    alongWayFeatures.subList(0, alongWayFeatures.firstIndexBeyond(distance)).asReversed()

/** An [AlongWayFeature] found by a query, with how far along the network it is from the cursor. */
data class AlongWayFeatureAhead(
    val feature: AlongWayFeature,
    /** Metres from the querying cursor, following the Ways rather than as the crow flies. */
    val distance: Double,
    /** The Way the feature is recorded on, which needn't be the cursor's own Way. */
    val way: Way,
    /**
     * Whether the walk is travelling [way]'s own START-to-END direction where it met the feature.
     *
     * Not the same as the cursor's own direction once the walk has left the Way it started on: the
     * Ways a road is split into aren't all digitised the same way round, so a walk that is going
     * forwards along one can be going backwards along the next. Anything recorded relative to a
     * Way's own direction - [AlongWayFeature.side] above all - has to be read against this rather
     * than against the cursor.
     */
    val forwards: Boolean,
)

/**
 * Walks the Way network from [cursor] in the direction of travel, calling [action] for each
 * along-way feature met, nearest first, until [maxDistance] is exhausted or [action] returns false.
 *
 * [continuation] decides how far the walk is willing to follow the road - see [WayContinuation].
 *
 * When the cursor's direction is unknown, both directions are walked and the results interleaved
 * by distance, so a caller still gets "how far away is this, along the road" rather than a
 * crow-flies guess.
 */
fun forEachAlongWayFeatureAhead(
    cursor: WayCursor,
    maxDistance: Double,
    continuation: WayContinuation = WayContinuation.STRAIGHT_ON,
    action: (AlongWayFeatureAhead) -> Boolean
) {
    when (cursor.forwards) {
        true, false -> walkOneDirection(cursor, cursor.forwards, maxDistance, continuation, action)
        null -> {
            // Merge the two directions by distance so the nearest feature is still seen first.
            val both = mutableListOf<AlongWayFeatureAhead>()
            walkOneDirection(cursor, true, maxDistance, continuation) { both.add(it); true }
            walkOneDirection(cursor, false, maxDistance, continuation) { both.add(it); true }
            for (found in both.sortedBy { it.distance }) {
                if (!action(found)) return
            }
        }
    }
}

/**
 * The most Ways one walk will cross before giving up, however much [maxDistance] is left. Guards
 * against a pathological chain - a run of zero-length JOINER ways at a tile boundary, say - the
 * same job Way.followWays' depth limit does.
 */
private const val MAX_WAYS_WALKED = 32

/**
 * One Way crossed by a walk, with everything a caller needs to place something on it without
 * repeating the direction arithmetic.
 *
 * [entry] and [exit] are both measured from [way]'s own START, whichever way round the walk
 * crossed it, because that is the frame [Ruler.along] and [AlongWayFeature.distanceFromStart] are
 * in. [forwards] is the only thing that says which of the two is the nearer end. The Ways a road
 * is split into aren't all digitised the same way round, so a walk going forwards along one can be
 * going backwards along the next - getting this wrong is silent and looks like a map data bug.
 */
data class WaySpan(
    val way: Way,
    val forwards: Boolean,
    val entry: Double,
    val exit: Double,
    /** Metres from the querying cursor to [entry], following the road. */
    val travelledAtEntry: Double,
    /**
     * The intersection the walk came in by, or null on the first span - the cursor starts in the
     * middle of a Way, not at one of its ends.
     *
     * Doubles as "is [entry] a Way boundary?": a feature sitting exactly on a boundary has not
     * been reached yet by a walk arriving from the previous piece, whereas one exactly at the
     * user's own position has been passed.
     */
    val entryIntersection: Intersection?,
    /** The intersection at the far end, or null where the mapped network simply stops. */
    val exitIntersection: Intersection?,
)

/** Why a walk ended. [distance] is metres from the querying cursor at the stopping point. */
sealed interface WalkStop {
    val distance: Double

    /** maxDistance ran out partway along [span]. */
    data class Exhausted(val span: WaySpan, override val distance: Double) : WalkStop

    /** A junction the [WayContinuation] rule won't walk through. */
    data class Junction(
        val intersection: Intersection,
        val arrivedOn: Way,
        val arrivedForwards: Boolean,
        override val distance: Double,
    ) : WalkStop

    /** Nothing continues: a cul-de-sac, or the far edge of the mapped network. */
    data class DeadEnd(val span: WaySpan, override val distance: Double) : WalkStop

    /** [MAX_WAYS_WALKED], or the walk looped back onto a Way it had already crossed. */
    data class Limit(val span: WaySpan, override val distance: Double) : WalkStop

    /** [onSpan] asked to stop. */
    data class Stopped(val span: WaySpan, override val distance: Double) : WalkStop
}

/**
 * Walks the Way network from [cursor], calling [onSpan] for each Way crossed, and reports why it
 * stopped.
 *
 * The traversal every along-way query is built on. Callers get the spans and do their own
 * measuring within them, so that the "which end did we come in by" arithmetic lives here once
 * rather than in each query.
 */
internal fun walkWays(
    cursor: WayCursor,
    forwards: Boolean,
    maxDistance: Double,
    continuation: WayContinuation,
    junctionArms: JunctionArms = JunctionArms.default,
    onSpan: (WaySpan) -> Boolean,
): WalkStop {
    // The identity of the road being followed, for WayContinuation.SAME_ROAD. Taken once from the
    // Way the walk starts on: a road keeps its name and ref across the Ways it is split into, and
    // that is what makes them the same road.
    val roadName = cursor.way.name
    val roadRef = cursor.way.ref
    // A walk that starts on a pavement may stay on pavements; one that starts on a road may not
    // turn down one.
    val startedOnSidewalk = cursor.way.isSidewalkOrCrossing()
    var way = cursor.way
    var stepForwards = forwards
    // Where on the current Way the walk enters it: at the cursor to begin with, then at whichever
    // end we came in by.
    var entry = cursor.distanceFromStart
    var travelled = 0.0
    var entryIntersection: Intersection? = null
    val visited = mutableSetOf<Way>()

    while (true) {
        // Read from the Way itself rather than found after the fact, so that a span knows both of
        // its ends before it is handed out.
        val exit = if (stepForwards) {
            way.intersections[WayEnd.END.id]
        } else {
            way.intersections[WayEnd.START.id]
        }
        val span = WaySpan(
            way = way,
            forwards = stepForwards,
            entry = entry,
            exit = if (stepForwards) way.length else 0.0,
            travelledAtEntry = travelled,
            entryIntersection = entryIntersection,
            exitIntersection = exit,
        )

        if (!visited.add(way)) return WalkStop.Limit(span, travelled)
        if (visited.size > MAX_WAYS_WALKED) return WalkStop.Limit(span, travelled)

        if (!onSpan(span)) return WalkStop.Stopped(span, travelled)

        travelled += if (stepForwards) way.length - entry else entry
        if (travelled > maxDistance) return WalkStop.Exhausted(span, maxDistance)

        // Walked here rather than by calling Way.followWays because that seeds from the
        // intersection *behind* the first Way, which a Way at the end of the mapped network
        // doesn't have.
        if (exit == null) return WalkStop.DeadEnd(span, travelled)

        val candidates = exit.members.filter { it !== way }
        val next = when (continuation) {
            // A pass-through node: one road in, one road out, nothing to choose between.
            WayContinuation.STRAIGHT_ON -> candidates.singleOrNull()
                ?: return if (candidates.isEmpty()) {
                    WalkStop.DeadEnd(span, travelled)
                } else {
                    WalkStop.Junction(exit, way, stepForwards, travelled)
                }

            // A real junction, and we're following the road rather than stopping at it. Exactly
            // one continuation has to identify itself as the same road, otherwise there's no
            // single answer and guessing would be worse than stopping - which is what a staggered
            // junction of two same-named arms looks like from here.
            WayContinuation.SAME_ROAD -> candidates.singleOrNull() ?: run {
                val sameRoad = candidates.filter { sameRoad(it, roadName, roadRef) }
                sameRoad.singleOrNull()
                // A JOINER carries no name to match on - it's the synthetic zero-length link
                // across a tile boundary (see GridState.joinTileEdgeIntersections) - so it's the
                // fallback when nothing else here continues the road, not a rival to something
                // that does.
                    ?: candidates.filter { it.wayType == WayType.JOINER }
                        .takeIf { sameRoad.isEmpty() }?.singleOrNull()
                    ?: return if (candidates.isEmpty()) {
                        WalkStop.DeadEnd(span, travelled)
                    } else {
                        WalkStop.Junction(exit, way, stepForwards, travelled)
                    }
            }

            WayContinuation.REAL_JUNCTIONS -> {
                val onward = if (startedOnSidewalk) {
                    candidates
                } else {
                    candidates.filter { !it.isSidewalkOrCrossing() }
                }
                if (onward.isEmpty()) return WalkStop.DeadEnd(span, travelled)
                // Two separate questions: does this count as a junction, and if not, which arm
                // carries the road on?
                val arms = onward.filter { countsAsJunctionArm(it, junctionArms) }
                when {
                    // Two or more roads that count is a fork, and a fork is where a beacon waits.
                    arms.size >= 2 -> return WalkStop.Junction(exit, way, stepForwards, travelled)
                    // The case JunctionArms.NamedRoads exists for: a named road meeting an unnamed
                    // service road leaves one counting arm, so the walk sails past it.
                    arms.size == 1 -> arms.first()
                    onward.size == 1 -> onward.first()
                    // Several ways onward and not one of them counts. Carrying straight on is the
                    // only useful answer, and stopping here is what makes JunctionArms.NamedRoads
                    // do nothing at all on a pedestrian route: walk a pavement and every node is
                    // two or three unnamed footways, so without this the walk stops at all of them
                    // exactly as JunctionArms.AnyWay does.
                    else -> straightestContinuation(way, exit, onward)
                        ?: return WalkStop.Junction(exit, way, stepForwards, travelled)
                }
            }
        }

        stepForwards = (next.intersections[WayEnd.START.id] === exit)
        entry = if (stepForwards) 0.0 else next.length
        entryIntersection = exit
        way = next
    }
}

/** Whether a Way continues the road the walk started on, by name or by route number. */
private fun sameRoad(candidate: Way, name: String?, ref: String?): Boolean {
    if (candidate.wayType == WayType.JOINER) return false
    if ((name != null) && (candidate.name == name)) return true
    if ((ref != null) && (candidate.ref == ref)) return true
    return false
}

/**
 * Which of [candidates] carries on in most nearly the same direction the walk arrived in, or null
 * where two of them are equally straight and picking one would be a guess.
 *
 * Only used for a node where nothing counts as a junction arm - a run of unnamed footway stubs
 * along a pavement, typically - so the question is "which of these is the path I am on continuing
 * as", and the answer really is geometric. A genuine fork of two equally plausible arms is a place
 * to stop even when neither is named, which is what the null is for.
 */
private fun straightestContinuation(
    way: Way,
    exit: Intersection,
    candidates: List<Way>,
): Way? {
    // Way.heading points away from the intersection, so the direction the walk arrived travelling
    // is the reverse of the heading of the Way it came in on.
    val arrival = (way.heading(exit) + 180.0) % 360.0
    val ranked = candidates
        .map { it to calculateHeadingOffset(it.heading(exit), arrival) }
        .sortedBy { it.second }
    val best = ranked.firstOrNull() ?: return null
    if (best.second > STRAIGHT_ENOUGH_DEGREES) return null
    val runnerUp = ranked.getOrNull(1) ?: return best.first
    if ((runnerUp.second - best.second) < AMBIGUOUS_DEGREES) return null
    return best.first
}

/** Beyond this bend, nothing here is "straight on" and the walk has run out of road to follow. */
private const val STRAIGHT_ENOUGH_DEGREES = 60.0

/** Two arms this close in angle are a fork, not a road and a turning off it. */
private const val AMBIGUOUS_DEGREES = 30.0

/** Whether [way] is one of the arms that makes its intersection a junction - see [JunctionArms]. */
private fun countsAsJunctionArm(way: Way, arms: JunctionArms): Boolean {
    // Never, in either mode: a JOINER is the synthetic zero-length link across a tile boundary,
    // not a road joining anything.
    if (way.wayType == WayType.JOINER) return false
    return when (arms) {
        JunctionArms.AnyWay -> true
        JunctionArms.NamedRoads ->
            !way.isPath() &&
                (way.featureValue != "service") &&
                (way.featureValue != "track") &&
                ((way.name != null) || (way.ref != null))
    }
}

private fun walkOneDirection(
    cursor: WayCursor,
    forwards: Boolean,
    maxDistance: Double,
    continuation: WayContinuation,
    action: (AlongWayFeatureAhead) -> Boolean
) {
    walkWays(cursor, forwards, maxDistance, continuation) { span ->
        val features = if (span.forwards) {
            // The walk starts at the user, where a feature exactly at the cursor has been passed.
            // Every Way after this one is entered at one of its ends instead, and a feature
            // sitting exactly on that boundary is still ahead - a railway=stop on the node where a
            // line is split, say, which would otherwise be invisible to the walk arriving from the
            // previous piece.
            span.way.alongWayFeaturesAfter(
                span.entry,
                inclusive = span.entryIntersection != null
            )
        } else {
            // Already inclusive of its own boundary: alongWayFeaturesBefore takes everything at
            // or before the entry, which on a backwards entry is the Way's END.
            span.way.alongWayFeaturesBefore(span.entry)
        }
        for (feature in features) {
            val distance = span.travelledAtEntry + if (span.forwards) {
                feature.distanceFromStart - span.entry
            } else {
                span.entry - feature.distanceFromStart
            }
            // Features come out nearest-first and travelled only grows, so nothing later in this
            // walk can be nearer than one that has already overshot.
            if (distance > maxDistance) return@walkWays false
            if (!action(AlongWayFeatureAhead(feature, distance, span.way, span.forwards))) {
                return@walkWays false
            }
        }
        true
    }
}

/**
 * The next along-way feature of [kind] ahead of [cursor] within [maxDistance], or null.
 *
 * This is the "when is the next crossing?" query - and, once stops and junctions are recorded, the
 * "where is the next bus stop?" one.
 */
fun nextAlongWayFeature(
    cursor: WayCursor,
    maxDistance: Double,
    kind: AlongWayKind? = null,
    continuation: WayContinuation = WayContinuation.STRAIGHT_ON
): AlongWayFeatureAhead? {
    var found: AlongWayFeatureAhead? = null
    forEachAlongWayFeatureAhead(cursor, maxDistance, continuation) {
        if ((kind == null) || (it.feature.kind == kind)) {
            found = it
            false
        } else {
            true
        }
    }
    return found
}

/**
 * A place on the road network reached by walking ahead from a cursor - somewhere a beacon can sit.
 */
sealed interface PointAhead {
    val location: LngLatAlt

    /** Metres from the querying cursor, following the road rather than as the crow flies. */
    val distance: Double

    /** The full distance was available, and the point is out in the middle of a Way. */
    data class OnWay(
        override val location: LngLatAlt,
        override val distance: Double,
        val way: Way,
        /** From [way]'s own START, whichever way round the walk crossed it - see [WaySpan]. */
        val distanceFromStart: Double,
        val forwards: Boolean,
    ) : PointAhead

    /** The walk stopped short of the distance asked for; the point is where it stopped. */
    data class AtJunction(
        override val location: LngLatAlt,
        override val distance: Double,
        /** Null at an un-noded end of the mapped network. */
        val intersection: Intersection?,
        val arrivedOn: Way,
        val arrivedForwards: Boolean,
        val deadEnd: Boolean,
    ) : PointAhead
}

/**
 * The point [distance] metres ahead of [cursor] along the road, or wherever the walk had to stop
 * short of it.
 *
 * Null when the cursor has no direction, or when the walk hit its Way limit - in both cases there
 * is no honest answer, and a caller placing a beacon is better off holding what it had than
 * guessing.
 */
fun pointAhead(
    cursor: WayCursor,
    distance: Double,
    ruler: Ruler,
    continuation: WayContinuation = WayContinuation.REAL_JUNCTIONS,
    junctionArms: JunctionArms = JunctionArms.default,
): PointAhead? {
    val forwards = cursor.forwards ?: return null

    var found: PointAhead.OnWay? = null
    val stop = walkWays(cursor, forwards, distance, continuation, junctionArms) { span ->
        val remaining = distance - span.travelledAtEntry
        val available = if (span.forwards) span.way.length - span.entry else span.entry
        if (remaining > available) return@walkWays true

        // Note that this is never negated. Where the walk entered this Way through the Way's own
        // END - because the two pieces were digitised towards each other - span.forwards is false
        // and span.entry is way.length, so the point is at entry - remaining, already in the Way's
        // own frame. Ruler.along wants exactly that, so callers never have to know.
        val distanceFromStart = if (span.forwards) {
            span.entry + remaining
        } else {
            span.entry - remaining
        }
        (span.way.geometry as? LineString)?.let { line ->
            found = PointAhead.OnWay(
                location = ruler.along(line, distanceFromStart),
                distance = distance,
                way = span.way,
                distanceFromStart = distanceFromStart,
                forwards = span.forwards,
            )
        }
        false
    }

    found?.let { return it }

    return when (stop) {
        is WalkStop.Junction -> PointAhead.AtJunction(
            location = stop.intersection.location,
            distance = stop.distance,
            intersection = stop.intersection,
            arrivedOn = stop.arrivedOn,
            arrivedForwards = stop.arrivedForwards,
            deadEnd = false,
        )

        is WalkStop.DeadEnd -> (stop.span.way.geometry as? LineString)?.let { line ->
            PointAhead.AtJunction(
                location = ruler.along(line, stop.span.exit),
                distance = stop.distance,
                intersection = stop.span.exitIntersection,
                arrivedOn = stop.span.way,
                arrivedForwards = stop.span.forwards,
                deadEnd = true,
            )
        }

        // Limit means a loop or a pathological chain of Ways, Exhausted and Stopped mean the span
        // callback should already have produced a point. Degenerate geometry lands here too.
        else -> null
    }
}

/**
 * The next junction ahead of [cursor] within [maxDistance], or null when the road just carries on
 * that far without forking.
 */
fun nextJunctionAhead(
    cursor: WayCursor,
    maxDistance: Double,
    ruler: Ruler,
    continuation: WayContinuation = WayContinuation.REAL_JUNCTIONS,
    junctionArms: JunctionArms = JunctionArms.default,
): PointAhead.AtJunction? =
    pointAhead(cursor, maxDistance, ruler, continuation, junctionArms) as? PointAhead.AtJunction
