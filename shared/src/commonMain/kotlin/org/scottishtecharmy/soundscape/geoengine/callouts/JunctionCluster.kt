package org.scottishtecharmy.soundscape.geoengine.callouts

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import org.scottishtecharmy.soundscape.geoengine.utils.getCompassLabel
import org.scottishtecharmy.soundscape.geoengine.utils.isJunctionArm
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import kotlin.math.roundToInt

/**
 * PROTOTYPE - not wired into the callouts yet.
 *
 * A junction as a pedestrian experiences it, rather than as the tile data draws it: the node the
 * user is approaching plus any other nodes a few metres from it, joined by short links. Where paths
 * fan out across a precinct the data has a node every few metres, and announcing each one
 * separately describes the mapping rather than the place.
 *
 * The exits are the Ways leaving that group of nodes, split into left, ahead and right relative to
 * the way the user arrives. Each side is then described by where it leads - "keep left for
 * Hillhead Street" - found by searching the network a short way out through that side's exits.
 */

/** Ways at most this long join two nodes into one junction. */
private const val clusterLinkMetres = 12.0

/**
 * And no node further than this from the node the user reaches first joins it, however it is
 * linked. Without a cap, a precinct full of short links merges into one 60-90m blob.
 */
private const val clusterRadiusMetres = 20.0

/**
 * A dual carriageway spreads one junction over several nodes: where the road splits, one node per
 * carriageway for the side street, and a stub across the central reservation joining them. The
 * carriageways of the street being walked link nodes this far apart, within [carriagewayRadiusMetres].
 */
private const val carriagewayLinkMetres = 40.0
private const val carriagewayRadiusMetres = 35.0
private const val reservationStubMetres = 15.0
/** How far apart a dual carriageway's two carriageways can be, centre line to centre line. */
private const val dualCarriagewayApartMetres = 30.0

/**
 * A roundabout is one junction however big its ring is, so its ring Ways join the cluster whatever
 * their length - up to this radius, which is larger than any town roundabout.
 */
private const val roundaboutRadiusMetres = 250.0

/** See [isClusterLink]: links named after the ring are folded in up to this long, and this far. */
private const val ringNamedLinkMetres = 50.0
private const val ringNamedLinkRadiusMetres = 80.0

/** How far out an exit is followed to decide which way it really goes. */
private const val exitBearingMetres = 15.0

/** How far through each side's exits the destination search goes. */
private const val destinationSearchMetres = 150.0

/** A POI this close to a node the search reaches counts as reached. */
private const val poiReachMetres = 15.0

/** See the destination ranking in [buildJunctionCluster]. */
private const val poiHandicapMetres = 30.0

/**
 * A beacon is usually well beyond [destinationSearchMetres], so it gets its own longer search. Still
 * bounded: past this the shortest route says little about which side to take.
 */
private const val beaconSearchMetres = 1500.0

/** A beacon is placed on a building or POI, which can be a fair way from the nearest node. */
private const val beaconSnapMetres = 150.0

/**
 * A destination is only put on one side if getting there that way is shorter than via any other
 * side by at least this much, or by [destinationMarginFraction] of the distance if that is more.
 * Otherwise "keep left for X" would be said where either way does.
 */
private const val destinationMarginMetres = 20.0
private const val destinationMarginFraction = 0.25

/**
 * The same for a beacon, which is usually much further away: a quarter of a 400m route would
 * leave almost nothing with a clear side.
 */
private const val beaconMarginFraction = 0.1

enum class JunctionSide { LEFT, AHEAD, RIGHT }

data class ClusterExit(
    val way: Way,
    /** The cluster node the exit leaves from. */
    val from: Intersection,
    /** -180..180, relative to the direction of travel into the junction. Negative is left. */
    val relativeBearing: Double,
    val side: JunctionSide,
    /** On a roundabout, which exit this is counting in the direction of traffic; otherwise null. */
    val exitNumber: Int? = null,
    /**
     * What the current callout would call this exit ("Path to Main Street via steps"), and whether
     * that says where it goes. An exit with a real name, or a confected one that's worth keeping,
     * is announced as it is now; only the rest are described by the destination search.
     */
    val label: String = "",
    val labelIsUseful: Boolean = false,
    /**
     * Where this exit heads, when another exit has the same label - "Main Street" leaving a
     * roundabout twice says nothing about which one to take. A settlement where the two lead to
     * different ones, otherwise the first road on this exit's way that the other doesn't reach.
     */
    val towards: String? = null,
)

data class JunctionDestination(
    val name: String,
    val isRoad: Boolean,
    /** Shortest network distance from the junction via each side that reaches it. */
    val distanceBySide: Map<JunctionSide, Double>,
)

data class JunctionCluster(
    val entry: Intersection,
    val approach: Way,
    val nodes: Set<Intersection>,
    val diameter: Double,
    val exits: List<ClusterExit>,
    /** The side each destination is clearly reached by - see [destinationMarginMetres]. */
    val destinationsBySide: Map<JunctionSide, List<JunctionDestination>>,
    /** For a beacon: which side the shortest route to it leaves by, if that is clear. */
    val beaconSide: JunctionSide?,
    val isRoundabout: Boolean,
    /** For a beacon at a roundabout: the exit the shortest route to it leaves by. */
    val beaconExit: ClusterExit? = null,
    /** Diagnostic: the route length to the beacon via each exit. */
    val beaconDistanceByExit: Map<ClusterExit, Double> = emptyMap(),
    /** Diagnostic: for exits that needed telling apart, where each one heads and what it crosses. */
    val settlementByExit: Map<String, String?> = emptyMap(),
    /** A roundabout's own name, from its ring - "Burnbrae Roundabout". */
    val roundaboutName: String? = null,
    /**
     * Which way traffic turns on joining the ring: LEFT where it goes round clockwise (the UK),
     * RIGHT where it goes anticlockwise (France). Exits in [exits] order are met going this way;
     * walking round the other way meets them in reverse.
     */
    val trafficTurns: JunctionSide? = null,
)

private fun normalise(bearing: Double): Double {
    var b = bearing % 360.0
    if (b > 180.0) b -= 360.0
    if (b <= -180.0) b += 360.0
    return b
}

/** The point [metres] along [way] from its [from] end - or its far end if it is shorter. */
private fun pointAlong(way: Way, from: Intersection, metres: Double, gridState: GridState): LngLatAlt {
    val line = way.geometry as LineString
    val d = minOf(metres, way.length)
    return if (way.intersections[WayEnd.START.id] === from)
        gridState.ruler.along(line, d)
    else
        gridState.ruler.along(line, maxOf(0.0, way.length - d))
}

/**
 * True for a piece of a roundabout's ring - including `junction=circular`, which OpenStreetMap
 * uses for a ring where circulating traffic doesn't have priority (signalled sections, say). For
 * describing the exits that's the same thing, and one ring can mix the two: Whirlies Roundabout
 * in East Kilbride has seven pieces of one and three of the other.
 */
fun Way.isRoundabout() = properties?.get("junction").let { (it == "roundabout") || (it == "circular") }

/**
 * True if this Way is one-way and can only be travelled *towards* [node] - an entry carriageway
 * into a roundabout, say, which is not a way out of it. `oneway` is 1 along the Way's digitised
 * direction (START to END) and -1 against it, as MapMatchFilter reads it.
 */
fun Way.isOnewayInto(node: Intersection): Boolean {
    val oneway = when (val value = properties?.get("oneway")) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }
    return when (oneway) {
        1 -> intersections[WayEnd.END.id] === node
        -1 -> intersections[WayEnd.START.id] === node
        else -> false
    }
}

/**
 * Not the roads a roundabout's exits are counted by: paths, footways, cycleways and tracks, and
 * bus-only ways - a busway across a roundabout (Saint-Quentin's TCSP, Tehran's BRT) is no way out
 * for anyone the description is for.
 */
private val nonRoadClasses = setOf("path", "track", "busway")

private fun Way.isClusterLink(
    from: Intersection,
    approach: Way,
    ringName: String?,
    dual: DualCarriageways,
): Boolean {
    if (this === approach) return false
    if (isRoundabout()) return getOtherIntersection(from) != null
    // Links within a big roundabout junction are sometimes drawn untagged but carry its name -
    // "Whirlies Roundabout" - and are as much a part of it as the ring.
    // Only short pieces, though: a ring is sometimes named after the street through it, and that
    // street isn't part of the roundabout.
    if ((ringName != null) && (name == ringName) && (length <= ringNamedLinkMetres))
        return getOtherIntersection(from) != null
    if (ringName == null) {
        if (isCarriagewayOf(approach, dual) && (length <= carriagewayLinkMetres))
            return getOtherIntersection(from) != null
        if (isReservationStub(from, approach, dual)) return true
    }
    if (length > clusterLinkMetres) return false
    // A different named street leaving is a real exit, however short this piece of it - only
    // unnamed connectors and the street being walked fold nodes together.
    if ((name != null) && (name != approach.name)) return false
    // A pavement running beside the approach road isn't a link into the junction ahead - it is
    // the same street.
    if (isSidewalkOrCrossing()) return false
    return getOtherIntersection(from) != null
}

/**
 * Whether a one-way Way is one carriageway of a dual carriageway: another one-way Way with the
 * same name runs alongside it the other way, not joined to it. A one-way street in a town centre
 * grid has no such twin.
 */
private class DualCarriageways(private val gridState: GridState) {
    private val known = HashMap<Way, Boolean>()

    fun isDual(way: Way): Boolean = known.getOrPut(way) {
        val name = way.name ?: return@getOrPut false
        if (way.properties?.get("oneway") == null) return@getOrPut false
        val start = way.intersections[WayEnd.START.id] ?: return@getOrPut false
        val end = way.intersections[WayEnd.END.id] ?: return@getOrPut false
        val ruler = gridState.ruler
        val heading = ruler.bearing(start.location, end.location)
        val middle = ruler.along(way.geometry as LineString, way.length / 2.0)
        gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
            .getNearbyCollection(middle, dualCarriagewayApartMetres, ruler)
            .map { it as Way }
            .any { other ->
                val os = other.intersections[WayEnd.START.id]
                val oe = other.intersections[WayEnd.END.id]
                (other !== way) && (other.name == name) && (other.properties?.get("oneway") != null) &&
                    (os != null) && (oe != null) &&
                    way.intersections.none { it != null && (it === os || it === oe) } &&
                    (kotlin.math.abs(normalise(ruler.bearing(os.location, oe.location) - heading)) > 135.0)
            }
    }
}

/** One carriageway of the dual-carriageway street [approach] is on - not at a roundabout. */
private fun Way.isCarriagewayOf(approach: Way, dual: DualCarriageways): Boolean =
    (approach.name != null) && (name == approach.name) && !isSidewalkOrCrossing() && !isRoundabout() &&
        intersections.filterNotNull().none { node -> node.members.any { it.isRoundabout() } } &&
        dual.isDual(this)

/**
 * A short piece of road across a dual carriageway's central reservation - between a node on one
 * carriageway of [approach]'s street and a node on the other. Whatever it's called, it's inside
 * the junction, not a way out of it.
 */
private fun Way.isReservationStub(from: Intersection, approach: Way, dual: DualCarriageways): Boolean {
    if ((length > reservationStubMetres) || isSidewalkOrCrossing() || (featureClass in nonRoadClasses)) return false
    val other = getOtherIntersection(from) ?: return false
    val fromCarriageways = from.members.filter { it !== this && it.isCarriagewayOf(approach, dual) }
    val otherCarriageways = other.members.filter { it !== this && it.isCarriagewayOf(approach, dual) }
    return fromCarriageways.isNotEmpty() && otherCarriageways.isNotEmpty() &&
        fromCarriageways.none { it in otherCarriageways }
}

/**
 * Builds the cluster around [description]'s intersection and works out what each side leads to.
 *
 * @param beacon Where the user is heading, if anywhere. Its side is found from the same search.
 */
fun buildJunctionCluster(
    description: IntersectionDescription,
    gridState: GridState,
    strings: LocalizedStrings?,
    beacon: LngLatAlt? = null,
): JunctionCluster? {
    val entry = description.intersection ?: return null
    val approach = description.nearestRoad ?: return null
    if (!approach.containsIntersection(entry)) return null
    val ruler = gridState.ruler

    // Direction of travel into the junction: heading() is the approach leaving the junction, i.e.
    // back towards the user.
    val travelHeading = (approach.heading(entry) + 180.0) % 360.0

    // The name of the roundabout the user is arriving at, if that's what this is.
    val ringName = entry.members.firstOrNull { it.isRoundabout() && (it.name != null) }?.name

    // Gather the cluster
    val dual = DualCarriageways(gridState)
    val nodes = LinkedHashSet<Intersection>()
    val queue = ArrayDeque(listOf(entry))
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (!nodes.add(node)) continue
        for (way in node.members) {
            if (!way.isClusterLink(node, approach, ringName, dual)) continue
            val other = way.getOtherIntersection(node) ?: continue
            if (other in nodes) continue
            val partOfRing = way.isRoundabout() || ((ringName != null) && (way.name == ringName))
            val radius = when {
                way.isRoundabout() -> roundaboutRadiusMetres
                partOfRing -> ringNamedLinkRadiusMetres
                way.isCarriagewayOf(approach, dual) || way.isReservationStub(node, approach, dual) -> carriagewayRadiusMetres
                else -> clusterRadiusMetres
            }
            // A tile-edge joiner has no length: its far node is the same place as this one, already
            // inside the cluster, so it's never too far away.
            if ((way.wayType != WayType.JOINER) &&
                (ruler.distance(entry.location, other.location) > radius)) continue
            // Don't fold in anything behind the user - a short link back alongside the approach
            // is somewhere they have already been. A roundabout's ring goes all the way round, so
            // that's not a test for it.
            if (!partOfRing && (way.wayType != WayType.JOINER)) {
                val rel = normalise(ruler.bearing(entry.location, other.location) - travelHeading)
                if (kotlin.math.abs(rel) > 120.0) continue
            }
            queue.add(other)
        }
    }
    var diameter = 0.0
    for (a in nodes) for (b in nodes) diameter = maxOf(diameter, ruler.distance(a.location, b.location))

    // At a roundabout the exits are placed relative to its centre: measured from the entry node,
    // an exit on the far side of the ring can point almost anywhere, and two entry carriageways
    // of the same road would disagree about which side an exit is on.
    val isRoundabout = nodes.any { node -> node.members.any { it.isRoundabout() } }
    val origin = if (isRoundabout) {
        val ring = nodes.filter { node -> node.members.any { it.isRoundabout() } }
        LngLatAlt(ring.sumOf { it.location.longitude } / ring.size, ring.sumOf { it.location.latitude } / ring.size)
    } else entry.location
    // And "ahead" is the way the approach points at the centre, not along its last few metres.
    val referenceHeading = if (isRoundabout) ruler.bearing(entry.location, origin) else travelHeading

    // The exits: anything leaving the group that isn't the approach, a link inside it, or a
    // pavement/connector that the current callout would also leave out.
    val exits = mutableListOf<ClusterExit>()
    for (node in nodes) {
        for (way in node.members) {
            if (way === approach) continue
            if (way.isRoundabout()) continue
            if (isRoundabout) {
                // Only the ways a vehicle can leave the ring by are its numbered exits: not an
                // entry carriageway, and not a footpath that happens to touch the ring.
                if (way.isOnewayInto(node)) continue
                if (way.featureClass in nonRoadClasses) continue
            }
            val other = way.getOtherIntersection(node)
            if ((other != null) && (other in nodes)) continue
            if (!way.isJunctionArm(node, approach, gridState, strings)) continue
            val farPoint = pointAlong(way, node, exitBearingMetres, gridState)
            val rel = normalise(ruler.bearing(origin, farPoint) - referenceHeading)
            val side = when {
                // Heads back the way they came - except at a roundabout, where every other road
                // is a real exit whichever way it points.
                !isRoundabout && (rel < -150.0 || rel > 150.0) -> continue
                isRoundabout && (rel < -150.0 || rel > 150.0) -> if (rel < 0) JunctionSide.LEFT else JunctionSide.RIGHT
                rel < -30.0 -> JunctionSide.LEFT
                rel > 30.0 -> JunctionSide.RIGHT
                else -> JunctionSide.AHEAD
            }
            exits.add(ClusterExit(way, node, rel, side))
        }
    }
    if (exits.isEmpty()) return null

    // On a roundabout, number the exits in the direction of traffic: the ring's Ways are drawn in
    // that direction, so follow START to END from the entry node.
    var trafficTurns: JunctionSide? = null
    if (isRoundabout) {
        val order = HashMap<Intersection, Int>()
        var node: Intersection? = nodes.firstOrNull { n -> n.members.any { it.isRoundabout() } }
            ?.let { if (entry.members.any { it.isRoundabout() }) entry else it }
        var step = 0
        while ((node != null) && (node !in order)) {
            order[node] = step++
            val current: Intersection = node
            node = current.members.firstOrNull {
                it.isRoundabout() && (it.intersections[WayEnd.START.id] === current)
            }?.intersections?.get(WayEnd.END.id)
                // Where a tile edge cuts the ring, the two tiles' copies of the node are stitched
                // together by zero-length joiners rather than by a roundabout Way. Step across them
                // - it's all the same place - or the count stops at the tile edge.
                ?: ringContinuationAcrossJoiners(current, order.keys)
        }
        // Which way round the ring is drawn, and so which way traffic turns on joining it: the
        // sign of the area the ring nodes enclose, taken in traffic order (shoelace formula; with
        // x east and y north, positive is anticlockwise).
        val ringInOrder = order.entries.sortedBy { it.value }.map { it.key.location }
        var twiceArea = 0.0
        for (i in ringInOrder.indices) {
            val a = ringInOrder[i]
            val b = ringInOrder[(i + 1) % ringInOrder.size]
            twiceArea += (a.longitude * b.latitude) - (b.longitude * a.latitude)
        }
        trafficTurns = when {
            ringInOrder.size < 3 -> null
            twiceArea < 0.0 -> JunctionSide.LEFT
            else -> JunctionSide.RIGHT
        }

        // Nodes reached by a short link rather than on the ring itself (a splitter island, say)
        // take the position of the nearest ring node.
        fun position(n: Intersection) = order[n]
            ?: order.minByOrNull { ruler.distance(it.key.location, n.location) }?.value ?: 0
        val ordered = exits.sortedWith(compareBy({ position(it.from) }, { it.relativeBearing }))
        // Roads meeting a roundabout are very often drawn as a separate one-way Way in and out,
        // each joining the ring at its own node. To the user that is one exit, so neighbours in
        // ring order with the same name (or both unnamed and close together) count once.
        val merged = mutableListOf<ClusterExit>()
        for (exit in ordered) {
            val previous = merged.lastOrNull()
            // Same name isn't enough on its own - a road can cross a roundabout and leave on both
            // sides - so the two must leave in roughly the same direction from the centre too.
            val sameRoad = (previous != null) &&
                (exit.way.name == previous.way.name) &&
                (kotlin.math.abs(normalise(exit.relativeBearing - previous.relativeBearing)) < 60.0)
            if (!sameRoad) merged.add(exit)
        }
        // The approach road's own outbound carriageway is the way back, not an exit. It leaves
        // the ring right beside where the user joins it, seen from the centre, whereas the same
        // road carrying on across the roundabout leaves from the far side.
        // The way back is the exit with the user's own road name that heads off in the same
        // direction the approach came from, seen from the centre: a divided road's two
        // carriageways stay within a few degrees of each other however far apart they run, while
        // the same road continuing across - or bending at the roundabout and leaving both ways -
        // is 80 degrees or more round. Measured as far out as both roads can be followed (they
        // often change name or end within 100m), up to [wayBackProbeMetres]: right by the ring
        // the two carriageways' mouths can be a fair angle apart.
        val forward = merged.filterNot { exit ->
            if (exit.way.name != approach.name) return@filterNot false
            if (kotlin.math.abs(exit.relativeBearing) > 150.0) return@filterNot true
            val (_, approachReach) = pointAlongRoad(approach, entry, wayBackProbeMetres, nodes, ruler)
            val (_, exitReach) = pointAlongRoad(exit.way, exit.from, wayBackProbeMetres, nodes, ruler)
            val probe = minOf(approachReach, exitReach)
            val a = pointAlongRoad(approach, entry, probe, nodes, ruler).first
            val b = pointAlongRoad(exit.way, exit.from, probe, nodes, ruler).first
            kotlin.math.abs(normalise(ruler.bearing(origin, b) - ruler.bearing(origin, a))) < wayBackMaxAngle
        }
        exits.clear()
        forward.forEachIndexed { i, exit -> exits.add(exit.copy(exitNumber = i + 1)) }
    } else {
        // At a dual carriageway, the user's own street's other carriageway heading back is the far
        // side of the road they came along, not a way out. And a street's two carriageways leaving
        // on the same side are one way to go, whichever of them traffic uses.
        val kept = mutableListOf<ClusterExit>()
        for (exit in exits.sortedBy { kotlin.math.abs(it.relativeBearing) }) {
            if (exit.way.isCarriagewayOf(approach, dual) && (kotlin.math.abs(exit.relativeBearing) > 120.0)) continue
            val twin = kept.any { k ->
                (k.side == exit.side) && (k.way.name != null) && (k.way.name == exit.way.name) &&
                    dual.isDual(k.way) && dual.isDual(exit.way)
            }
            if (!twin) kept.add(exit)
        }
        exits.retainAll { it in kept }
    }

    // Labels, as the current callout would say them. A confected "Path to X" is only worth keeping
    // if X isn't the road the user is already on and no exit on another side is called the same -
    // "Path to Lennox Park goes left, Path to Lennox Park continues ahead" helps nobody.
    val labelled = exits.map { exit ->
        val forwards = exit.way.intersections[WayEnd.START.id] === exit.from
        val label = exit.way.getName(forwards, gridState, strings)
        val props = exit.way.properties
        val destination = props
            ?.get(if (forwards) "destination:forward" else "destination:backward")?.toString()
        // Steps, a bridge or a tunnel on the way, or a dead end, are things the user needs to
        // know about the path itself, whatever it leads to - so a label carrying them is kept.
        val qualified = (props?.get(if (forwards) "passes:forward" else "passes:backward") != null) ||
            (props?.get(if (forwards) "dead-end:forward" else "dead-end:backward") != null)
        Triple(exit.copy(label = label), destination, qualified)
    }
    exits.clear()
    for ((exit, destination, qualified) in labelled) {
        val useful = when {
            exit.way.name != null -> true
            qualified -> true
            destination == null -> exit.label.contains(" next to ") // pavement/waterside confection
            destination == approach.name -> false
            else -> labelled.none { (other, _, _) -> other.side != exit.side && other.label == exit.label }
        }
        exits.add(exit.copy(labelIsUseful = useful))
    }

    // The ring's own name, if it has one: the most common name among its Ways.
    val roundaboutName = if (isRoundabout) {
        nodes.flatMap { it.members }.filter { it.isRoundabout() }.toSet()
            .mapNotNull { it.name }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            // A ring is often named after the road through it ("Park Lane"), and announcing that
            // as the roundabout's name is just confusing next to an exit with the same name.
            ?.takeIf { ring -> (approach.name != ring) && exits.none { it.way.name == ring } }
    } else null

    val settlementDiagnostics = HashMap<String, String?>()
    // Tell apart exits that would otherwise be announced identically.
    // At a roundabout every exit is spoken, unnamed ones included ("Service"), so those need telling
    // apart too.
    val duplicates = exits.filter { it.labelIsUseful || isRoundabout }.groupBy { it.label }
        .filterValues { it.size > 1 }
    for ((_, group) in duplicates) {
        val routes = group.associateWith { followRoad(it, nodes, roundaboutName, gridState) }
        // A settlement, if each one heads to a different place.
        val settlements = routes.mapValues { (_, route) ->
            gridState.settlementNameProvider?.invoke(route.end)
        }
        for ((exit, settlement) in settlements) {
            val route = routes.getValue(exit)
            settlementDiagnostics["${exit.label} #${exit.exitNumber}"] =
                "$settlement, %.0fm, crossing ${route.anyCrossings}".format(route.travelled)
        }
        val qualifiers: Map<ClusterExit, String?> =
            if (settlements.values.all { it != null } && (settlements.values.toSet().size == group.size)) {
                settlements
            } else {
                // Otherwise the first road crossed that the others never reach: a main road if
                // there is one, or else any named road, such as a business park's service roads.
                routes.mapValues { (exit, route) ->
                    val others = routes.filterKeys { it !== exit }.values
                    route.crossings.firstOrNull { c -> others.none { c in it.crossings } }
                        ?: route.anyCrossings.firstOrNull { c -> others.none { c in it.anyCrossings } }
                }.let { byRoad -> if (byRoad.values.count { it != null } >= group.size - 1) byRoad else null }
                    // Car park and business park roads often meet no named road at all, but end
                    // at somewhere that has a name.
                    ?: routes.mapValues { (_, route) ->
                        (gridState.getFeatureTree(TreeId.POIS)
                            .getNearestFeature(route.end, ruler, routeEndPoiMetres) as MvtFeature?)?.name
                    }.takeIf { places -> places.values.all { it != null } && (places.values.toSet().size == group.size) }
                    // Failing everything else, which way each one heads.
                    ?: routes.mapValues { (_, route) ->
                        compassWord(ruler.bearing(origin, route.end), strings)
                    }.takeIf { it.values.toSet().size == group.size }
                    // A road that loops round can end where it started, so then the way it leaves.
                    ?: routes.mapValues { (exit, _) ->
                        val out = pointAlongRoad(exit.way, exit.from, exitBearingMetres * 2, nodes, ruler).first
                        compassWord(ruler.bearing(origin, out), strings)
                    }.takeIf { it.values.toSet().size == group.size }
                    ?: emptyMap()
            }
        for ((exit, towards) in qualifiers) {
            if (towards == null) continue
            val index = exits.indexOfFirst { it === exit }
            exits[index] = exit.copy(towards = towards)
        }
    }

    // One bounded search per side, each starting from that side's exits and never re-entering the
    // cluster, so a destination's distance per side says how far it is if you go that way.
    val poiTree = gridState.getFeatureTree(TreeId.POIS)
    val beaconNode = beacon?.let {
        gridState.getFeatureTree(TreeId.INTERSECTIONS).getNearestFeature(it, ruler, beaconSnapMetres) as Intersection?
    }
    val bestBySide = HashMap<String, HashMap<JunctionSide, Double>>()
    val isRoad = HashMap<String, Boolean>()
    val beaconDistanceBySide = HashMap<JunctionSide, Double>()
    // Per exit too, for roundabouts, where the exit number matters more than the side.
    var beaconExit: ClusterExit? = null
    var beaconExitDistance = Double.MAX_VALUE
    for (side in JunctionSide.entries) {
        val sideExits = exits.filter { it.side == side }
        if (sideExits.isEmpty()) continue
        val dist = HashMap<Intersection, Double>()
        val open = ArrayList<Pair<Intersection, Double>>()
        fun record(name: String?, road: Boolean, d: Double) {
            if (name.isNullOrBlank()) return
            val bySide = bestBySide.getOrPut(name) { HashMap() }
            if (d < (bySide[side] ?: Double.MAX_VALUE)) bySide[side] = d
            // A road wins over a POI of the same name.
            isRoad[name] = (isRoad[name] == true) || road
        }
        for (exit in sideExits) {
            val start = ruler.distance(entry.location, exit.from.location)
            record(exit.way.name, true, start)
            val far = exit.way.getOtherIntersection(exit.from) ?: continue
            if (far in nodes) continue
            val d = start + exit.way.length
            if (d < (dist[far] ?: Double.MAX_VALUE)) {
                dist[far] = d
                open.add(far to d)
            }
        }
        val settled = HashSet<Intersection>()
        while (open.isNotEmpty()) {
            val best = open.minBy { it.second }
            open.remove(best)
            val (node, d) = best
            if (!settled.add(node)) continue
            if (d > destinationSearchMetres) continue
            if (node === beaconNode) beaconDistanceBySide[side] = d
            for (poi in poiTree.getNearbyCollection(node.location, poiReachMetres, ruler)) {
                // Bus stop names describe the road, often oddly ("Main Street (after)").
                if ((poi as MvtFeature).featureValue in setOf("bus_stop", "platform", "stop_position"))
                    continue
                record(poi.name, false, d)
            }
            for (way in node.members) {
                if (way.isSidewalkOrCrossing()) continue
                record(way.name, true, d)
                val next = way.getOtherIntersection(node) ?: continue
                if (next in nodes) continue
                val nd = d + way.length
                if (nd < (dist[next] ?: Double.MAX_VALUE)) {
                    dist[next] = nd
                    open.add(next to nd)
                }
            }
        }
    }

    fun clearSide(
        bySide: Map<JunctionSide, Double>,
        fraction: Double = destinationMarginFraction,
    ): JunctionSide? {
        val ranked = bySide.entries.sortedBy { it.value }
        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1) ?: return first.key
        val margin = maxOf(destinationMarginMetres, first.value * fraction)
        return if (second.value - first.value >= margin) first.key else null
    }

    val destinationsBySide = HashMap<JunctionSide, MutableList<JunctionDestination>>()
    for ((name, bySide) in bestBySide) {
        val side = clearSide(bySide) ?: continue
        destinationsBySide.getOrPut(side) { mutableListOf() }
            .add(JunctionDestination(name, isRoad[name] == true, bySide))
    }
    // Nearest first, a road preferred over a POI unless the POI is clearly nearer: a path that ends
    // at a shop 15m away is better described by the shop than by a street 80m beyond it. The road
    // the user is on is only worth naming ahead.
    for ((side, list) in destinationsBySide) {
        list.removeAll { (side != JunctionSide.AHEAD) && (it.name == approach.name) }
        list.sortBy { (it.distanceBySide[side] ?: 0.0) + if (it.isRoad) 0.0 else poiHandicapMetres }
    }

    // The beacon: which exit the shortest route to it leaves by, searched further than the
    // destinations are. Per exit rather than per side, as a roundabout needs the exit itself.
    var beaconSideFromExits: JunctionSide? = null
    val byExit = HashMap<ClusterExit, Double>()
    if (beaconNode != null) {
        val bySide = HashMap<JunctionSide, Double>()
        byExit.clear()
        for (exit in exits) {
            val far = exit.way.getOtherIntersection(exit.from) ?: continue
            if (far in nodes) continue
            val d = shortestBoundedDistance(far, beaconNode, nodes, beaconSearchMetres) ?: continue
            val total = d + exit.way.length + ruler.distance(entry.location, exit.from.location)
            if (total < (bySide[exit.side] ?: Double.MAX_VALUE)) bySide[exit.side] = total
            byExit[exit] = total
        }
        // Only name an exit for the beacon if it is clearly the shortest, as for the sides.
        if (isRoundabout) {
            val ranked = byExit.entries.sortedBy { it.value }
            val first = ranked.firstOrNull()
            val second = ranked.getOrNull(1)
            if ((first != null) && ((second == null) ||
                    (second.value - first.value >= maxOf(destinationMarginMetres, first.value * beaconMarginFraction)))
            ) {
                beaconExit = first.key
                beaconExitDistance = first.value
            }
        }
        beaconSideFromExits = clearSide(bySide, beaconMarginFraction)

        // If going back the way the user came is the shorter route, there's no side to keep to.
        val toJunction = description.centreLineDistance ?: 0.0
        val back = approach.getOtherIntersection(entry)?.let { far ->
            shortestBoundedDistance(far, beaconNode, nodes, beaconSearchMetres)
                ?.plus(maxOf(0.0, approach.length - toJunction))
        }
        val forward = (bySide.values.minOrNull() ?: Double.MAX_VALUE) + toJunction
        if ((back != null) && (back < forward)) {
            beaconSideFromExits = null
            beaconExit = null
        }
    }

    return JunctionCluster(
        entry, approach, nodes, diameter, exits, destinationsBySide,
        beaconSideFromExits,
        isRoundabout,
        beaconExit,
        byExit,
        settlementDiagnostics,
        roundaboutName,
        trafficTurns,
    )
}

/** See the way-back test in [buildJunctionCluster]. */
private const val wayBackProbeMetres = 150.0
private const val wayBackMaxAngle = 50.0

/**
 * The point [distance] along [way] going away from [from], carrying on along Ways of the same name
 * - the straightest on, either one-way direction, never back into [cluster] - and how far it got,
 * which is less than [distance] where the road changes name or ends first.
 */
private fun pointAlongRoad(
    way: Way,
    from: Intersection,
    distance: Double,
    cluster: Set<Intersection>,
    ruler: Ruler,
): Pair<LngLatAlt, Double> {
    var current = way
    var node = from
    var travelled = 0.0
    val seen = HashSet<Way>()
    while (seen.add(current)) {
        val line = current.geometry as LineString
        if (travelled + current.length >= distance) {
            val along = distance - travelled
            val point = if (current.intersections[WayEnd.START.id] === node) ruler.along(line, along)
                else ruler.along(line, maxOf(0.0, current.length - along))
            return point to distance
        }
        travelled += current.length
        val next = current.getOtherIntersection(node) ?: return node.location to travelled
        val arriving = (current.heading(next) + 180.0) % 360.0
        val previous = current
        current = next.members.filter {
            (it !== previous) && (it !in seen) && (way.name != null) && (it.name == way.name) &&
                !it.isRoundabout() && (it.getOtherIntersection(next) !in cluster)
        }.minByOrNull { kotlin.math.abs(normalise(it.heading(next) - arriving)) }
            ?: return next.location to travelled
        node = next
    }
    return node.location to travelled
}

/**
 * The node on the far side of a tile edge where a roundabout's ring carries on from [node].
 *
 * Not simply the first joiner: where another road crosses the tile edge at the same spot the
 * joiners meet at a hub, and one of them leads to that road's copy - a dead end as far as the ring
 * is concerned. So search everything reachable through joiners for the node a ring Way starts at.
 */
private fun ringContinuationAcrossJoiners(node: Intersection, visited: Set<Intersection>): Intersection? {
    val seen = HashSet<Intersection>()
    val queue = ArrayDeque(listOf(node))
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!seen.add(current)) continue
        if ((current !== node) && (current !in visited) &&
            current.members.any { it.isRoundabout() && (it.intersections[WayEnd.START.id] === current) }
        ) return current
        for (way in current.members) {
            if (way.wayType != WayType.JOINER) continue
            way.getOtherIntersection(current)?.let { queue.add(it) }
        }
    }
    return null
}

/** How far along an exit's own road [followRoad] looks for where it is heading. */
private const val followRoadMetres = 600.0
/** How near the end of a followed road a named place must be to say the road goes towards it. */
private const val routeEndPoiMetres = 40.0

/** "the north west" - which way an exit heads, for when nothing else tells two exits apart. */
private fun compassWord(bearing: Double, strings: LocalizedStrings?): String {
    val key = getCompassLabel(bearing.roundToInt())
    val word = strings?.getOrNull(key) ?: key.name.removePrefix("DirectionsCardinal")
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    return "the ${word.lowercase()}"
}

private val crossingRoadClasses =
    setOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor", "busway")

private class RoadRoute(
    val end: LngLatAlt,
    val crossings: List<String>,
    val nodes: Set<Intersection> = emptySet(),
    val travelled: Double = 0.0,
    /** Every named road met, service roads included - for when [crossings] can't tell exits apart. */
    val anyCrossings: List<String> = emptyList(),
)

/**
 * Follows [exit]'s road by name away from the junction, up to [followRoadMetres], noting the named
 * roads it crosses in the order it meets them. Stops where the road's name runs out. Without a
 * name to follow, it carries on along unnamed road of the same class.
 */
private fun followRoad(
    exit: ClusterExit,
    cluster: Set<Intersection>,
    roundaboutName: String?,
    gridState: GridState,
): RoadRoute = followRoad(exit.way, exit.from, cluster, roundaboutName, followRoadMetres, true, gridState.ruler)

/**
 * As above, from [start] leaving [from]. [respectOneway] off follows a road against its one-way
 * direction - for tracing back along the road the user arrived on.
 */
private fun followRoad(
    start: Way,
    from: Intersection,
    cluster: Set<Intersection>,
    roundaboutName: String?,
    limit: Double,
    respectOneway: Boolean,
    ruler: Ruler,
): RoadRoute {
    val name = start.name
    val crossings = mutableListOf<String>()
    val anyCrossings = mutableListOf<String>()
    val visited = LinkedHashSet<Intersection>()
    var way = start
    var node = from
    var travelled = 0.0
    val seen = HashSet<Way>()
    while (seen.add(way)) {
        val next = way.getOtherIntersection(node) ?: break
        travelled += way.length
        visited.add(next)
        if ((travelled > limit) || (next in cluster)) {
            node = next
            break
        }
        for (member in next.members) {
            // Roads only: a path or cycle route alongside ("Bears Way") says little about where
            // the road is going.
            if ((member.featureClass in nonRoadClasses) || member.isSidewalkOrCrossing()) continue
            val other = member.name ?: continue
            if ((other == name) || (other == roundaboutName)) continue
            if (other !in anyCrossings) anyCrossings.add(other)
            if ((member.featureClass in crossingRoadClasses) && (other !in crossings)) crossings.add(other)
        }
        // Carry on along the same road, the straightest way on. Never back into the junction:
        // where an exit carriageway meets its entry twin, that twin has the same name and leads
        // straight back to the roundabout.
        val arrivingHeading = (way.heading(next) + 180.0) % 360.0
        val current = way
        way = next.members.filter {
            (it !== current) && (it.name == name) && !it.isSidewalkOrCrossing() &&
                ((name != null) || (it.featureClass == start.featureClass)) &&
                (it !in seen) && !(respectOneway && it.isOnewayInto(next)) &&
                (it.getOtherIntersection(next) !in cluster)
        }.minByOrNull { kotlin.math.abs(normalise(it.heading(next) - arrivingHeading)) } ?: run {
            node = next
            break
        }
        node = next
    }
    return RoadRoute(node.location, crossings, visited, travelled, anyCrossings)
}

private fun shortestBoundedDistance(
    from: Intersection,
    to: Intersection,
    blocked: Set<Intersection>,
    limit: Double,
): Double? {
    if (from === to) return 0.0
    val dist = HashMap<Intersection, Double>()
    val open = ArrayList<Pair<Intersection, Double>>()
    dist[from] = 0.0
    open.add(from to 0.0)
    val settled = HashSet<Intersection>()
    while (open.isNotEmpty()) {
        val best = open.minBy { it.second }
        open.remove(best)
        val (node, d) = best
        if (!settled.add(node)) continue
        if (node === to) return d
        if (d > limit) return null
        for (way in node.members) {
            if (way.isSidewalkOrCrossing()) continue
            val next = way.getOtherIntersection(node) ?: continue
            if (next in blocked) continue
            val nd = d + way.length
            if (nd < (dist[next] ?: Double.MAX_VALUE)) {
                dist[next] = nd
                open.add(next to nd)
            }
        }
    }
    return null
}

private fun ordinal(n: Int) = when (n) {
    1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; else -> "${n}th"
}

/**
 * The prototype wording: one line per side, naming where that side leads if one destination
 * clearly belongs to it, otherwise how many paths go that way.
 */
fun describeJunctionCluster(cluster: JunctionCluster, beaconName: String? = null): List<String> {
    val lines = mutableListOf<String>()
    if ((beaconName != null) && (cluster.beaconExit?.exitNumber != null)) {
        val exit = cluster.beaconExit
        val keep = when (exit.side) {
            JunctionSide.LEFT -> "Keep left"
            JunctionSide.AHEAD -> "Carry on"
            JunctionSide.RIGHT -> "Keep right"
        }
        lines.add("$keep, ${ordinal(exit.exitNumber!!)} exit, for $beaconName")
        return lines
    }
    if (cluster.isRoundabout) return describeRoundabout(cluster)
    return describeSides(cluster, beaconName)
}

private fun ClusterExit.spokenName(): String? = label.takeIf { it.isNotEmpty() }
    ?.let { l -> towards?.let { "$l towards $it" } ?: l }

/**
 * A roundabout in brief: how many exits it has, and the first one met going each way round from
 * where the user joins it - which is what someone on foot walking round the outside meets first,
 * whichever way they choose. Going with the traffic that's exit 1; going against it, the last.
 */
fun describeRoundabout(cluster: JunctionCluster): List<String> {
    val name = cluster.roundaboutName ?: "Roundabout"
    val exits = cluster.exits
    val count = if (exits.size == 1) "1 exit" else "${exits.size} exits"
    if (exits.size <= 1) {
        return listOf("$name, $count" + (exits.firstOrNull()?.spokenName()?.let { ", $it" } ?: ""))
    }
    val turns = cluster.trafficTurns
    // Without a known direction round, fall back on which side each end of the list is on.
    val leftFirst = when (turns) {
        JunctionSide.LEFT -> exits.first()
        JunctionSide.RIGHT -> exits.last()
        else -> exits.minBy { it.relativeBearing }
    }
    val rightFirst = when (turns) {
        JunctionSide.LEFT -> exits.last()
        JunctionSide.RIGHT -> exits.first()
        else -> exits.maxBy { it.relativeBearing }
    }
    return listOf(
        "$name, $count",
        "First on the left, ${leftFirst.spokenName() ?: "an unnamed road"}",
        "First on the right, ${rightFirst.spokenName() ?: "an unnamed road"}",
    )
}

/** Every exit in turn, numbered - for a user who asks to hear them all. */
fun describeRoundaboutInFull(cluster: JunctionCluster): List<String> {
    val lines = mutableListOf<String>()
    run {
        // Every exit separately: at a roundabout the user needs to count them, not just pick a side.
        lines.add("${cluster.roundaboutName ?: "Roundabout"}, ${cluster.exits.size} exits")
        for (exit in cluster.exits) {
            // Every exit is spoken by name at a roundabout, generic or not ("Service"), as the
            // count only works if each one is mentioned.
            val destination = exit.label.takeIf { it.isNotEmpty() }
                ?.let { label -> exit.towards?.let { "$label towards $it" } ?: label }
            val where = when (exit.side) {
                JunctionSide.LEFT -> "on the left"
                JunctionSide.AHEAD -> "ahead"
                JunctionSide.RIGHT -> "on the right"
            }
            lines.add("${ordinal(exit.exitNumber ?: 0).replaceFirstChar { it.uppercase() }} exit $where" +
                (destination?.let { " for $it" } ?: ""))
        }
    }
    return lines
}

private fun describeSides(cluster: JunctionCluster, beaconName: String?): List<String> {
    val lines = mutableListOf<String>()
    if ((beaconName != null) && (cluster.beaconSide != null)) {
        lines.add(
            when (cluster.beaconSide) {
                JunctionSide.LEFT -> "Keep left for $beaconName"
                JunctionSide.AHEAD -> "Carry on for $beaconName"
                JunctionSide.RIGHT -> "Keep right for $beaconName"
            }
        )
        return lines
    }
    for (side in listOf(JunctionSide.LEFT, JunctionSide.AHEAD, JunctionSide.RIGHT)) {
        // An exit whose name already says where it goes is announced as it is now - "Douglas
        // Street goes left". Only the rest of the side is described by where it leads.
        val sideExits = cluster.exits.filter { it.side == side }
        for (label in sideExits.filter { it.labelIsUseful }
            .map { exit -> exit.towards?.let { "${exit.label} towards $it" } ?: exit.label }.distinct()) {
            lines.add(
                when (side) {
                    JunctionSide.LEFT -> "$label goes left"
                    JunctionSide.AHEAD -> "$label continues ahead"
                    JunctionSide.RIGHT -> "$label goes right"
                }
            )
        }
        val count = sideExits.count { !it.labelIsUseful }
        if (count == 0) continue
        val named = sideExits.filter { it.labelIsUseful }.map { it.label }.toSet()
        val destination = cluster.destinationsBySide[side]
            ?.firstOrNull { d -> named.none { it.contains(d.name) } }?.name
        // Alongside a named road on the same side "keep left" no longer says which one to take,
        // so the paths are described as additional to it.
        val dir = when (side) {
            JunctionSide.LEFT -> "left"
            JunctionSide.AHEAD -> "ahead"
            JunctionSide.RIGHT -> "right"
        }
        if ((destination == null) && (count == 1)) {
            // Nothing better to say than what is said now - "Service goes left".
            val label = sideExits.first { !it.labelIsUseful }.label
            lines.add(
                when (side) {
                    JunctionSide.LEFT -> "$label goes left"
                    JunctionSide.AHEAD -> "$label continues ahead"
                    JunctionSide.RIGHT -> "$label goes right"
                }
            )
            continue
        }
        if (named.isNotEmpty()) {
            val what = if (count == 1) "Another path goes $dir" else "$count more paths go $dir"
            lines.add(what + (destination?.let { ", to $it" } ?: ""))
            continue
        }
        lines.add(
            when {
                destination != null -> when (side) {
                    JunctionSide.LEFT -> "Keep left for $destination"
                    JunctionSide.AHEAD -> "Carry on for $destination"
                    JunctionSide.RIGHT -> "Keep right for $destination"
                }
                else -> {
                    val what = if (count == 1) "1 path" else "$count paths"
                    when (side) {
                        JunctionSide.LEFT -> "$what to the left"
                        JunctionSide.AHEAD -> "$what ahead"
                        JunctionSide.RIGHT -> "$what to the right"
                    }
                }
            }
        )
    }
    return lines
}
