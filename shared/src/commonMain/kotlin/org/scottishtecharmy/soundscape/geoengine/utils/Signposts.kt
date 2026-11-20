package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiPolygon
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings

/**
 * Signposts for unnamed paths: "Path to Mugdock Castle" for an arm of a junction that would
 * otherwise be a bare "Path".
 *
 * The destinations confected when a grid loads (traverseIntersectionsConfectingNames,
 * addPoiDestinations) follow a path only until it forks, so in a park's mesh of paths - Mugdock
 * Country Park in mugdock-whw.gpx - every stretch between forks reaches nothing named and the
 * junction callouts were "Path goes left, Path goes right" for twenty junctions running. A
 * fingerpost doesn't say where the path ends but what it leads to, and that is what this does:
 * from the junction, a shortest-path search over the path network out to [SIGNPOST_RANGE_METRES],
 * remembering which arm everything was reached through. Each arm is then signposted to the nearest
 * landmark it is the way to - a named road or path, a castle, a car park, a café, a loch - and no
 * two arms to the same one.
 */
const val SIGNPOST_RANGE_METRES = 600.0

/** How near a path has to pass a landmark to lead to it - as addPoiDestinations looks for POIs. */
private const val SIGNPOST_REACH_METRES = 20.0

/** A landmark this close to the junction, or around it, is where the walker already is. */
private const val SIGNPOST_HERE_METRES = 30.0

/** Search limits, so that a dense city junction can't cost much more than a park one. */
private const val SIGNPOST_MAX_JUNCTIONS = 400
private const val SIGNPOST_MAX_POIS = 100

private const val SIGNPOST_SAMPLE_METRES = 10.0

/** The kinds of POI a path is signposted to: castles and parks, shops and cafés, car parks. */
private val SIGNPOST_CATEGORIES =
    setOf(SuperCategoryId.LANDMARK, SuperCategoryId.PLACE, SuperCategoryId.SAFETY)

private fun signpostKey(forwards: Boolean) = if (forwards) "signpost:forward" else "signpost:backward"

/**
 * What [way] leads to, setting off from its START when [forwards] and its END otherwise, or null
 * if nothing it leads to is near enough. Worked out for every arm of that junction at once the
 * first time one of them is asked about, and remembered on the ways.
 */
fun signpostDestination(
    way: Way,
    forwards: Boolean,
    gridState: GridState,
    strings: LocalizedStrings?,
): String? {
    val key = signpostKey(forwards)
    (way.properties?.get(key) as? String)?.let { return it.ifEmpty { null } }
    val node = way.intersections[if (forwards) WayEnd.START.id else WayEnd.END.id]
    if (node == null) {
        way.setProperty(key, "")
        return null
    }
    signpostJunction(node, gridState, strings)
    return (way.properties?.get(key) as? String)?.ifEmpty { null }
}

/**
 * A landmark the search can reach - [feature] is null for a named way, met at a junction. A
 * [fallback] one - a shop, a café, an office - is only signposted from an arm that leads to nothing
 * better: in town a path to Fulton Road is better signposted that way than to the Kwik Fit on it.
 */
private class Landmark(val text: String, val feature: Feature?, val fallback: Boolean)

private class Reached(val distance: Double, val arm: Int, val fallback: Boolean = false)

private fun signpostJunction(node: Intersection, gridState: GridState, strings: LocalizedStrings?) {
    val ruler = gridState.ruler
    val landmarks = landmarksAround(node.location, gridState, strings)
    val reachedLandmarks = HashMap<String, Reached>()
    // The roads and paths meeting here are where the walker is, not where an arm leads: a path
    // leaving the West Highland Way and rejoining it further on isn't the way to it
    val here = node.members.filter { isNamedLandmarkWay(it) }.mapNotNull { it.displayName }.toSet()

    fun reach(text: String, distance: Double, arm: Int, fallback: Boolean) {
        if ((distance > SIGNPOST_RANGE_METRES) || (text in here)) return
        val best = reachedLandmarks[text]
        if ((best == null) || (distance < best.distance))
            reachedLandmarks[text] = Reached(distance, arm, fallback && (best?.fallback != false))
    }

    // A shortest-path search out from the junction, not walking along named ways - they're
    // destinations - nor going on past a junction where one is met
    val reached = mutableListOf<Intersection>()
    val expanded = HashSet<Intersection>()
    dijkstraOnWaysWithLoops(
        node, null, ruler, SIGNPOST_RANGE_METRES, reached,
        traverse = { !isNamedLandmarkWay(it) },
        expandFrom = { at ->
            val expand = (expanded.size < SIGNPOST_MAX_JUNCTIONS) && at.members.none { isNamedLandmarkWay(it) }
            if (expand) expanded.add(at)
            expand
        },
    )
    expanded.add(node)

    // Which of node's arms each junction was reached through: the one its route back to node ends
    // on. reached is in the order the search settled them, so a junction's previous one is always
    // known before it.
    val armOf = HashMap<Intersection, Int>()
    for (at in reached) {
        if (at === node) continue
        val previous = at.dijkstraPrevious ?: continue
        armOf[at] = if (previous === node) {
            node.members.withIndex()
                .filter { (_, way) -> !isNamedLandmarkWay(way) && (way.getOtherIntersection(node) === at) }
                .minByOrNull { (_, way) -> way.length }?.index ?: continue
        } else {
            armOf[previous] ?: continue
        }
    }

    for (at in reached) {
        val distance = at.dijkstraDistance
        if (at === node) {
            // The landmarks along each arm itself
            node.members.forEachIndexed { arm, way ->
                if (!isNamedLandmarkWay(way)) reachAlong(way, node, 0.0, arm, landmarks, ruler, ::reach)
            }
            continue
        }
        val arm = armOf[at] ?: continue
        if (at !in expanded) {
            // A named road or path is a destination in itself, and as far as this arm needs to go
            at.members.firstOrNull { isNamedLandmarkWay(it) }?.let {
                reach(it.displayName!!, distance, arm, false)
            }
            continue
        }
        for (way in at.members) {
            if (!isNamedLandmarkWay(way)) reachAlong(way, at, distance, arm, landmarks, ruler, ::reach)
        }
    }

    // Each arm to the nearest landmark it was the way to, a fallback one only if there's nothing else
    val nearestByArm = HashMap<Int, Pair<String, Reached>>()
    for ((text, reached) in reachedLandmarks) {
        val best = nearestByArm[reached.arm]?.second
        val better = (best == null) ||
            (best.fallback && !reached.fallback) ||
            ((best.fallback == reached.fallback) && (reached.distance < best.distance))
        if (better) nearestByArm[reached.arm] = text to reached
    }
    node.members.forEachIndexed { arm, way ->
        val forwards = way.intersections[WayEnd.START.id] === node
        way.setProperty(signpostKey(forwards), nearestByArm[arm]?.first ?: "")
    }
}

/**
 * Each of [landmarks] that [way] leads to, setting off along it from [from], [distance] metres out
 * from the junction being signposted.
 */
private fun reachAlong(
    way: Way,
    from: Intersection,
    distance: Double,
    arm: Int,
    landmarks: List<Landmark>,
    ruler: Ruler,
    reach: (String, Double, Int, Boolean) -> Unit,
) {
    val forwards = way.intersections[WayEnd.START.id] === from
    for (landmark in landmarks) {
        val along = alongToLandmark(way, landmark.feature!!, ruler) ?: continue
        reach(landmark.text, distance + (if (forwards) along else way.length - along), arm, landmark.fallback)
    }
}

/** A way that names itself: anything with a name but a pavement, which is named for its road. */
private fun isNamedLandmarkWay(way: Way): Boolean =
    (way.displayName != null) && !way.isSidewalkOrCrossing() &&
        (way.properties?.get("pavement") == null)

/**
 * The named POIs and lochs within range of [location] worth signposting, leaving out any the
 * walker is already at.
 */
private fun landmarksAround(
    location: LngLatAlt,
    gridState: GridState,
    strings: LocalizedStrings?,
): List<Landmark> {
    val ruler = gridState.ruler
    val result = mutableListOf<Landmark>()
    val pois = gridState.getFeatureTree(TreeId.POIS)
        .getNearestCollection(location, SIGNPOST_RANGE_METRES, SIGNPOST_MAX_POIS, ruler)
    for (poi in pois) {
        val feature = poi as? MvtFeature ?: continue
        if (feature.superCategory !in SIGNPOST_CATEGORIES) continue
        val text = feature.getText(strings)?.text
        if (text.isNullOrEmpty() || (feature.name == null)) continue
        if (distanceToFeature(location, feature, ruler) < SIGNPOST_HERE_METRES) continue
        result.add(Landmark(text, feature, fallback = feature.superCategory == SuperCategoryId.PLACE))
    }
    val water = gridState.getFeatureTree(TreeId.NAMED_WATER_POLYGONS)
        .getNearestCollection(location, SIGNPOST_RANGE_METRES, SIGNPOST_MAX_POIS, ruler)
    for (loch in water) {
        val feature = loch as? MvtFeature ?: continue
        val text = feature.displayName ?: continue
        if (distanceToFeature(location, feature, ruler) < SIGNPOST_HERE_METRES) continue
        result.add(Landmark(text, feature, fallback = false))
    }
    return result
}

/**
 * How far along [way] from its START it comes within [SIGNPOST_REACH_METRES] of [feature], or null
 * if it doesn't.
 */
private fun alongToLandmark(way: Way, feature: Feature, ruler: Ruler): Double? {
    val line = way.geometry as? LineString ?: return null
    if (line.coordinates.size < 2) return null
    val geometry = feature.geometry
    if (geometry is Point) {
        val nearest = ruler.distanceToLineString(geometry.coordinates, line)
        if (nearest.distance > SIGNPOST_REACH_METRES) return null
        return way.distanceAlongWay(nearest.point, ruler)
    }
    // A polygon: the first point along the way that is in it or near its edge
    val length = way.length
    var along = 0.0
    while (true) {
        val point = ruler.along(line, along)
        if (distanceToFeature(point, feature, ruler) <= SIGNPOST_REACH_METRES) return along
        if (along >= length) return null
        along = minOf(along + SIGNPOST_SAMPLE_METRES, length)
    }
}

/** Distance from [point] to [feature]: 0 inside a polygon, otherwise to its edge. */
private fun distanceToFeature(point: LngLatAlt, feature: Feature, ruler: Ruler): Double =
    when (val geometry = feature.geometry) {
        is Point -> ruler.distance(point, geometry.coordinates)
        is Polygon -> distanceToPolygon(point, geometry, ruler)
        is MultiPolygon -> geometry.coordinates.minOfOrNull { rings ->
            distanceToPolygon(point, Polygon(rings[0]), ruler)
        } ?: Double.MAX_VALUE
        is LineString -> ruler.distanceToLineString(point, geometry).distance
        else -> Double.MAX_VALUE
    }

private fun distanceToPolygon(point: LngLatAlt, polygon: Polygon, ruler: Ruler): Double {
    if (polygonContainsCoordinates(point, polygon)) return 0.0
    val ring = polygon.coordinates.firstOrNull() ?: return Double.MAX_VALUE
    if (ring.size < 2) return Double.MAX_VALUE
    return ruler.distanceToLineString(point, LineString(ring)).distance
}
