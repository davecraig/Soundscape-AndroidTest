package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Open areas - pedestrian precincts and squares, railway platforms - where there's no line for a
 * blind user to follow: no kerb, no edge of pavement. Street-by-street callouts don't fit, so
 * these describe the area by its edge instead: where the ways out are, and what lies ahead on the
 * far side in the direction the user is heading.
 *
 * Nothing here is ever about the user's distance from the edge sideways. GPS can't resolve a 3m
 * platform edge, and announcing one would be dangerous false confidence. The heading-based ray is
 * different: along a 150m platform or a 100m precinct a 10m position error still leaves the
 * answer right.
 *
 * Geometry is done on a local flat projection in metres centred on the area, which is exact
 * enough over the few hundred metres an area spans.
 */

enum class OpenAreaKind { PEDESTRIAN_AREA, PLATFORM }

/** Where a way crosses the area's edge - somewhere the user can leave by. */
data class OpenAreaExit(
    val location: LngLatAlt,
    /** Named ways leaving here, in order of first seen. Empty when every way is unnamed. */
    val names: List<String>,
    /** The featureValue (footway, steps, service...) of each way leaving, for unnamed exits. */
    val kinds: List<String>,
    /**
     * Names made up for the unnamed ways leaving here - "path to Mugdock Road" - see Way.getName.
     * Only used when no way leaving is named.
     */
    val confectedNames: List<String> = emptyList(),
    /**
     * A road that traffic uses crosses the edge here. Sometimes that's just a street the precinct
     * opens onto, but it can also be a road cutting across a corner of the area.
     */
    val road: Boolean = false,
) {
    /** What to call this exit when speaking it, or null when it has nothing better than "path". */
    fun spokenName(): String? = names.firstOrNull() ?: confectedNames.firstOrNull()

    fun description(): String =
        spokenName() ?: kinds.firstOrNull()?.let { "unnamed $it" } ?: "unnamed way"
}

data class OpenArea(
    val feature: MvtFeature,
    val kind: OpenAreaKind,
    /**
     * Pedestrian areas and platforms reach us without names (planetiler doesn't put one on the
     * transportation layer's polygons), so this is borrowed from the named pedestrian way running
     * furthest through the area - for a precinct that's normally the street it is.
     */
    val name: String?,
    /**
     * A platform's numbers. Usually one, but an island platform is often mapped as one platform
     * per face, sharing a long side - Exhibition Centre's 1 and 2 - and a 5m face is too narrow for GPS to
     * say which of them the user is on, so the faces alongside are counted in. Sorted, and empty
     * when the platform has no ref.
     */
    val refs: List<String>,
    val exits: List<OpenAreaExit>,
    /** Length and width of the smallest rectangle around the area, and its long axis bearing. */
    val length: Double,
    val width: Double,
    val axisBearing: Double,
    private val projection: LocalProjection,
    private val rings: List<List<Point2>>,
    private val boundary: List<Pair<Point2, Point2>>,
) {
    /** 0 inside the area, otherwise how far outside its edge [location] is in metres. */
    fun distanceOutside(location: LngLatAlt): Double {
        val p = projection.toLocal(location)
        if (insideRings(p, rings)) return 0.0
        return boundary.minOf { (a, b) -> pointSegmentDistance(p, a, b) }
    }

    /** What the user is heading towards: the first edge crossed travelling along [bearing]. */
    fun castRay(from: LngLatAlt, bearing: Double, maxDistance: Double = 1000.0): OpenAreaRayHit? {
        val o = projection.toLocal(from)
        val rad = bearing * PI / 180.0
        val dir = Point2(sin(rad), cos(rad))
        var best: Double? = null
        for ((a, b) in boundary) {
            val t = raySegment(o, dir, a, b) ?: continue
            if (t > 0.5 && t <= maxDistance && (best == null || t < best)) best = t
        }
        val t = best ?: return null
        val hit = Point2(o.x + dir.x * t, o.y + dir.y * t)
        val hitLocation = projection.toLngLat(hit)
        val exit = exits
            .map { it to projection.toLocal(it.location).distanceTo(hit) }
            .filter { it.second <= EXIT_HIT_RADIUS }
            .minByOrNull { it.second }
        return OpenAreaRayHit(hitLocation, t, exit?.first, exit?.second)
    }

    /**
     * The exit the user is heading for: of the exits within [cone] degrees either side of
     * [heading], the one most nearly straight ahead. A cone rather than [castRay]'s single line
     * because heading wanders by several degrees fix to fix while walking, and the ray flicks
     * between an exit and the bare edge beside it.
     *
     * [holding] is the exit last reported: it's kept while it stays within [holdCone], so that
     * two exits in much the same direction don't take turns as the heading wobbles.
     */
    fun exitAhead(
        from: LngLatAlt,
        heading: Double,
        cone: Double = 20.0,
        holding: OpenAreaExit? = null,
        holdCone: Double = 30.0,
    ): Pair<OpenAreaExit, Double>? {
        val o = projection.toLocal(from)
        val candidates = exits.map { exit ->
            val p = projection.toLocal(exit.location)
            val bearing = (atan2(p.x - o.x, p.y - o.y) * 180 / PI + 360) % 360
            Triple(exit, angleBetween(bearing, heading), p.distanceTo(o))
        }.filter { it.third > 1.0 }
        candidates.firstOrNull { it.first == holding && it.second <= holdCone }
            ?.let { return it.first to it.third }
        return candidates
            .filter { it.second <= cone }
            .minByOrNull { it.second }
            ?.let { it.first to it.third }
    }

    companion object {
        /** How close a ray's hit on the edge must come to an exit for it to be heading for it. */
        const val EXIT_HIT_RADIUS = 12.0
    }
}

data class OpenAreaRayHit(
    val location: LngLatAlt,
    val distance: Double,
    val exit: OpenAreaExit?,
    val exitOffset: Double?,
)

/**
 * The open area [location] is within, smallest first so that a platform within a station's
 * pedestrian concourse wins over the concourse, or null when it's in none.
 */
fun findOpenArea(
    gridState: GridState,
    location: LngLatAlt,
    localized: LocalizedStrings? = null
): OpenArea? {
    val (feature, kind) = findOpenAreaFeature(gridState, location) ?: return null
    return describeOpenArea(gridState, feature, kind, localized)
}

/** The outline of the open area [location] is within - see [findOpenArea] - without describing it. */
fun findOpenAreaFeature(gridState: GridState, location: LngLatAlt): Pair<MvtFeature, OpenAreaKind>? {
    val candidates = mutableListOf<Pair<MvtFeature, OpenAreaKind>>()
    for (f in gridState.getFeatureTree(TreeId.PLATFORMS).getContainingPolygons(location))
        candidates.add((f as MvtFeature) to OpenAreaKind.PLATFORM)
    for (f in gridState.getFeatureTree(TreeId.PEDESTRIAN_AREAS).getContainingPolygons(location))
        candidates.add((f as MvtFeature) to OpenAreaKind.PEDESTRIAN_AREA)
    return candidates.minByOrNull { polygonArea(it.first) }
}

fun describeOpenArea(
    gridState: GridState,
    feature: MvtFeature,
    kind: OpenAreaKind,
    localized: LocalizedStrings? = null
): OpenArea {
    val polygon = feature.geometry as Polygon
    val outer = polygon.coordinates.first()
    val centre = LngLatAlt(
        outer.sumOf { it.longitude } / outer.size,
        outer.sumOf { it.latitude } / outer.size
    )
    val projection = LocalProjection(centre)
    val rings = polygon.coordinates.map { ring -> ring.map { projection.toLocal(it) } }
    val boundary = rings.flatMap { ring -> ring.zipWithNext() }
    val radius = rings.first().maxOf { hypot(it.x, it.y) }

    fun inside(p: Point2) = insideRings(p, rings)
    fun distanceToEdge(p: Point2): Double = boundary.minOf { (a, b) -> pointSegmentDistance(p, a, b) }

    data class RawExit(
        val p: Point2,
        val name: String?,
        val kind: String,
        val way: Way,
        /** Leaving the area means travelling along the way towards its END. */
        val outwardForward: Boolean,
        val road: Boolean,
    )
    val raw = mutableListOf<RawExit>()
    val nameLengths = mutableMapOf<String, Double>()

    val ways = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
        .getNearbyCollection(centre, radius + 10.0, gridState.ruler)
        .mapNotNull { f ->
            val way = f as? Way ?: return@mapNotNull null
            val line = (way.geometry as? LineString)?.coordinates ?: return@mapNotNull null
            if (line.size < 2) null else way to line.map { projection.toLocal(it) }
        }

    // Does the way cross the edge anywhere, on whatever level. Only part way along a segment: a
    // way that merely starts or stops on the edge doesn't cross it.
    fun crossesEdge(pts: List<Point2>) = pts.zipWithNext().any { (a, b) ->
        val segLength = a.distanceTo(b)
        segLength >= 0.01 && boundary.any { (c, d) ->
            val t = segmentIntersection(a, b, c, d)
            if (t == null || t * segLength < 0.1 || (1 - t) * segLength < 0.1) false
            else {
                val step = min(CROSSING_PROBE, min(t, 1 - t) * segLength) / segLength
                inside(Point2(a.x + (b.x - a.x) * (t - step), a.y + (b.y - a.y) * (t - step))) !=
                    inside(Point2(a.x + (b.x - a.x) * (t + step), a.y + (b.y - a.y) * (t + step)))
            }
        }
    }
    // The ends of ways crossing the edge. A way stopping at the edge where one of these starts
    // isn't a way out: it carries on across, and whether that's an exit is the crossing's to say.
    // Milngavie's Ellangowan Road runs under the precinct as a separate short way, its approaches
    // stopping either side of it on the edge.
    val crossingEnds = ways.filter { (_, pts) -> crossesEdge(pts) }
        .flatMap { (way, pts) -> listOf(way to pts.first(), way to pts.last()) }

    for ((way, pts) in ways) {
        // Somewhere the user can't walk isn't a way out. That's also all that tells us a road
        // runs underneath the area, when it's mapped with no tunnel or layer: Ellangowan Road
        // under Milngavie's precinct is tagged only foot=no and a maxheight.
        if (way.properties?.get("foot") == "no") continue
        val name = way.displayName
        val wayKind = way.featureValue ?: "way"
        val isRoad = way.featureClass != null && way.featureClass !in nonRoadClasses
        // A way on another level - a road bridging over a platform, a path in a tunnel beneath a
        // precinct - crosses the outline without being a way in or out of the area. One that
        // ends at the edge is still kept below: a footbridge landing on a platform is an exit.
        val sameLevel = levelOf(way) == levelOf(feature)

        // How far this way runs inside the area, for borrowing a name
        if (name != null && way.featureValue == "pedestrian") {
            var inLength = 0.0
            for ((a, b) in pts.zipWithNext())
                if (inside(Point2((a.x + b.x) / 2, (a.y + b.y) / 2))) inLength += a.distanceTo(b)
            if (inLength > 0) nameLengths[name] = (nameLengths[name] ?: 0.0) + inLength
        }

        // Where the way crosses from inside to outside the edge
        for ((a, b) in pts.zipWithNext()) {
            val segLength = a.distanceTo(b)
            if (segLength < 0.01) continue
            for ((c, d) in boundary) {
                val t = segmentIntersection(a, b, c, d) ?: continue
                val p = Point2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                // Probe a couple of metres either side along the way to make sure it really
                // crosses, rather than grazing a vertex of the edge
                val step = min(CROSSING_PROBE, segLength / 2) / segLength
                val before = Point2(p.x - (b.x - a.x) * step, p.y - (b.y - a.y) * step)
                val after = Point2(p.x + (b.x - a.x) * step, p.y + (b.y - a.y) * step)
                val beforeInside = inside(before)
                if (sameLevel && beforeInside != inside(after))
                    raw.add(RawExit(p, name, wayKind, way, beforeInside, isRoad))
            }
        }
        // A way that stops at the edge from outside - a path leading up to the precinct
        for ((end, next, forward) in listOf(
            Triple(pts.first(), pts[1], true),
            Triple(pts.last(), pts[pts.size - 2], false)
        )) {
            if (distanceToEdge(end) <= EDGE_TOUCH && !inside(next) && distanceToEdge(next) > EDGE_TOUCH &&
                crossingEnds.none { (other, p) -> other !== way && p.distanceTo(end) < SHARED_END }
            )
                raw.add(RawExit(end, name, wayKind, way, forward, isRoad))
        }
    }

    // Several ways often leave at the same point - a street and its pavements, a crossing - so
    // cluster them into one exit
    // cluster them into one exit. Ways of the same name are clustered over a longer distance:
    // a road cutting across a corner of the area crosses its edge twice close together, and a
    // wide street meets it along a long stretch.
    val clusters = mutableListOf<MutableList<RawExit>>()
    for (e in raw) {
        val cluster = clusters.firstOrNull { c ->
            c.any {
                val d = it.p.distanceTo(e.p)
                d <= EXIT_CLUSTER || (e.name != null && it.name == e.name && d <= SAME_NAME_CLUSTER)
            }
        }
        if (cluster != null) cluster.add(e) else clusters.add(mutableListOf(e))
    }
    val exits = clusters.map { c ->
        OpenAreaExit(
            location = projection.toLngLat(Point2(c.sumOf { it.p.x } / c.size, c.sumOf { it.p.y } / c.size)),
            names = c.mapNotNull { it.name }.distinct(),
            kinds = c.map { it.kind }.distinct(),
            confectedNames = if (c.any { it.name != null }) emptyList() else c.mapNotNull {
                it.way.getName(it.outwardForward, gridState, localized, nonGenericOnly = true)
                    .takeIf { n -> n.isNotBlank() }
            }.distinct(),
            road = c.any { it.road },
        )
    }

    val refs = mutableSetOf<String>()
    fun addRefs(f: MvtFeature) {
        f.ref?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { refs.addAll(it) }
    }
    if (kind == OpenAreaKind.PLATFORM) {
        addRefs(feature)
        for (f in gridState.getFeatureTree(TreeId.PLATFORMS)
            .getNearbyCollection(centre, radius + ADJACENT_PLATFORM, gridState.ruler)) {
            val other = f as? MvtFeature ?: continue
            if (other.osmId == feature.osmId) continue
            val otherRing = (other.geometry as? Polygon)?.coordinates?.firstOrNull()
                ?.map { projection.toLocal(it) } ?: continue
            if (otherRing.size < 2) continue
            val otherEdges = otherRing.zipWithNext()
            fun nearOther(p: Point2) =
                otherEdges.any { (a, b) -> pointSegmentDistance(p, a, b) <= ADJACENT_PLATFORM }
            // The faces of an island share a long side. Merely touching isn't enough: Milngavie's
            // two platforms are separate, meeting only across the 9m at the buffer end.
            var shared = 0.0
            for ((a, b) in rings.first().zipWithNext()) {
                val length = a.distanceTo(b)
                val steps = max(1, length.toInt())
                for (i in 0 until steps) {
                    val t = (i + 0.5) / steps
                    if (nearOther(Point2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)))
                        shared += length / steps
                }
            }
            if (shared >= ISLAND_SHARED_SIDE) addRefs(other)
        }
    }

    val (length, width, axis) = minimumRectangle(rings.first())
    return OpenArea(
        feature = feature,
        kind = kind,
        name = nameLengths.filter { it.value >= MIN_NAME_LENGTH }.maxByOrNull { it.value }?.key,
        refs = refs.sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })),
        exits = exits,
        length = length,
        width = width,
        axisBearing = axis,
        projection = projection,
        rings = rings,
        boundary = boundary,
    )
}

/** The layer and bridge/tunnel of a feature, for telling which ways are on the area's level. */
private fun levelOf(feature: MvtFeature): Pair<String, String?> =
    (feature.properties?.get("layer")?.toString() ?: "0") to
        feature.properties?.get("brunnel")?.toString()?.takeIf { it == "bridge" || it == "tunnel" }

private fun insideRings(p: Point2, rings: List<List<Point2>>): Boolean {
    var inside = false
    for (ring in rings) if (pointInRing(p, ring)) inside = !inside
    return inside
}

/**
 * Which open area the user is in, steadied against GPS wander. An 11m platform or a narrow
 * precinct is easily left and re-entered by a fix or two of position error, so once in an area
 * the user stays in it until they're clearly outside - more than [leaveDistance] beyond its edge
 * for [leaveFixes] fixes in a row - or step into another one.
 *
 * Nothing is reported while travelling faster than walking pace: a train runs alongside its
 * platforms, well within GPS error of them, and a bus through a precinct isn't in it.
 */
class OpenAreaTracker(
    private val leaveDistance: Double = 8.0,
    private val leaveFixes: Int = 3,
    private val maxSpeed: Double = 3.0,
    private val localized: LocalizedStrings? = null,
) {
    var current: OpenArea? = null
        private set
    private var outsideCount = 0

    fun clear() {
        current = null
        outsideCount = 0
    }

    /** Updates and returns the area the user is in. [speed] in metres per second, if known. */
    fun update(gridState: GridState, location: LngLatAlt, speed: Double?): OpenArea? {
        if (speed != null && speed > maxSpeed) {
            current = null
            outsideCount = 0
            return null
        }
        // Only the outline is looked up on every fix: describing the area, which walks every way
        // near it to find the exits, is done once on entering it.
        val found = findOpenAreaFeature(gridState, location)
        val held = current
        if (found != null && (held == null || found.first.osmId != held.feature.osmId)) {
            // A different area: take it unless this is a fix wandering into a neighbour while
            // still within the one we're in
            if (held == null || held.distanceOutside(location) > 0.0) {
                val area = describeOpenArea(gridState, found.first, found.second, localized)
                current = area
                outsideCount = 0
                return area
            }
        }
        if (held != null && found == null) {
            if (held.distanceOutside(location) > leaveDistance) outsideCount++ else outsideCount = 0
            if (outsideCount >= leaveFixes) {
                current = null
                outsideCount = 0
            }
        } else {
            outsideCount = 0
        }
        return current
    }
}

private const val CROSSING_PROBE = 2.0
private const val EDGE_TOUCH = 2.0
private const val SHARED_END = 0.5
/** How close another platform's outline must come to count as a face of the same island. */
private const val ADJACENT_PLATFORM = 1.5
/** How much of its side another platform must share to be the other face of an island. */
private const val ISLAND_SHARED_SIDE = 20.0
private const val EXIT_CLUSTER = 10.0
private const val SAME_NAME_CLUSTER = 25.0
private const val MIN_NAME_LENGTH = 10.0

/** OpenMapTiles transportation classes that aren't roads carrying traffic. */
private val nonRoadClasses = setOf("path", "track", "pier", "bridge", "ferry", "rail", "transit")

private fun angleBetween(a: Double, b: Double): Double = abs(((a - b) % 360 + 540) % 360 - 180)

data class Point2(val x: Double, val y: Double) {
    fun distanceTo(o: Point2) = hypot(x - o.x, y - o.y)
}

/** Flat projection in metres about [origin]: x east, y north. */
class LocalProjection(private val origin: LngLatAlt) {
    private val ky = 111_320.0
    private val kx = 111_320.0 * cos(origin.latitude * PI / 180.0)
    fun toLocal(p: LngLatAlt) = Point2((p.longitude - origin.longitude) * kx, (p.latitude - origin.latitude) * ky)
    fun toLngLat(p: Point2) = LngLatAlt(origin.longitude + p.x / kx, origin.latitude + p.y / ky)
}

private fun polygonArea(feature: MvtFeature): Double {
    val ring = (feature.geometry as? Polygon)?.coordinates?.firstOrNull() ?: return Double.MAX_VALUE
    val projection = LocalProjection(ring.first())
    val pts = ring.map { projection.toLocal(it) }
    var sum = 0.0
    for ((a, b) in pts.zipWithNext()) sum += a.x * b.y - b.x * a.y
    return abs(sum) / 2
}

private fun pointInRing(p: Point2, ring: List<Point2>): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x)
            inside = !inside
        j = i
    }
    return inside
}

private fun pointSegmentDistance(p: Point2, a: Point2, b: Point2): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0.0) return p.distanceTo(a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    return p.distanceTo(Point2(a.x + dx * t, a.y + dy * t))
}

/** Fraction along a-b where it crosses c-d, or null. */
private fun segmentIntersection(a: Point2, b: Point2, c: Point2, d: Point2): Double? {
    val r = Point2(b.x - a.x, b.y - a.y)
    val s = Point2(d.x - c.x, d.y - c.y)
    val denom = r.x * s.y - r.y * s.x
    if (abs(denom) < 1e-12) return null
    val qp = Point2(c.x - a.x, c.y - a.y)
    val t = (qp.x * s.y - qp.y * s.x) / denom
    val u = (qp.x * r.y - qp.y * r.x) / denom
    return if (t in 0.0..1.0 && u in 0.0..1.0) t else null
}

/** Distance along the ray o + t*dir where it meets segment a-b, or null. */
private fun raySegment(o: Point2, dir: Point2, a: Point2, b: Point2): Double? {
    val s = Point2(b.x - a.x, b.y - a.y)
    val denom = dir.x * s.y - dir.y * s.x
    if (abs(denom) < 1e-12) return null
    val qp = Point2(a.x - o.x, a.y - o.y)
    val t = (qp.x * s.y - qp.y * s.x) / denom
    val u = (qp.x * dir.y - qp.y * dir.x) / denom
    return if (t >= 0 && u in 0.0..1.0) t else null
}

/** Length, width and long-axis bearing (0-180) of the smallest rectangle around [ring]. */
private fun minimumRectangle(ring: List<Point2>): Triple<Double, Double, Double> {
    var best = Triple(0.0, 0.0, 0.0)
    var bestArea = Double.MAX_VALUE
    for (step in 0 until 180) {
        val rad = step * PI / 180.0
        val ux = sin(rad)
        val uy = cos(rad)
        var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
        var minB = Double.MAX_VALUE; var maxB = -Double.MAX_VALUE
        for (p in ring) {
            val a = p.x * ux + p.y * uy
            val b = -p.x * uy + p.y * ux
            minA = min(minA, a); maxA = max(maxA, a)
            minB = min(minB, b); maxB = max(maxB, b)
        }
        val along = maxA - minA
        val across = maxB - minB
        if (along * across < bestArea) {
            bestArea = along * across
            best = if (along >= across) Triple(along, across, step.toDouble())
            else Triple(across, along, ((step + 90) % 180).toDouble())
        }
    }
    return best
}

/** Relative clock position (1-12) of [bearing] for someone facing [heading]. */
fun clockPosition(heading: Double, bearing: Double): Int {
    val rel = ((bearing - heading) % 360 + 360) % 360
    val hour = ((rel + 15) / 30).toInt() % 12
    return if (hour == 0) 12 else hour
}

/** Bearing from [from] to [to] in degrees, 0-360. */
fun openAreaBearing(from: LngLatAlt, to: LngLatAlt): Double {
    val p = LocalProjection(from).toLocal(to)
    return (atan2(p.x, p.y) * 180 / PI + 360) % 360
}
