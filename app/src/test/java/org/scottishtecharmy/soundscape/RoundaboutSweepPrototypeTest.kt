package org.scottishtecharmy.soundscape

import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.callouts.IntersectionDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.addIntersectionCalloutFromDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.buildJunctionCluster
import org.scottishtecharmy.soundscape.geoengine.callouts.describeJunctionCluster
import org.scottishtecharmy.soundscape.geoengine.callouts.describeRoundaboutInFull
import org.scottishtecharmy.soundscape.geoengine.callouts.isRoundabout
import org.scottishtecharmy.soundscape.geoengine.callouts.isOnewayInto
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * PROTOTYPE sweep for JunctionCluster at roundabouts: finds every roundabout ring in a handful of
 * areas, walks in on each of its arms, and flags descriptions that look wrong. Output only - no
 * assertions. Writes build/test-output/roundabout-sweep.txt (every description) and
 * roundabout-sweep-summary.txt (counts and the flagged cases).
 */
class RoundaboutSweepPrototypeTest {

    private val areas = listOf(
        "Milngavie/Bearsden" to LngLatAlt(-4.3170, 55.9380),
        "East Kilbride" to LngLatAlt(-4.1770, 55.7644),
        "Cumbernauld" to LngLatAlt(-3.9900, 55.9469),
        "Livingston" to LngLatAlt(-3.5230, 55.8865),
        "Swindon" to LngLatAlt(-1.7740, 51.5600),
        "Cribbs Causeway" to LngLatAlt(-2.5948, 51.5244),
        "Paris Etoile" to LngLatAlt(2.2950, 48.8738),
        "Saint-Quentin-en-Yvelines" to LngLatAlt(2.0450, 48.7800),
        "Tehran" to LngLatAlt(51.3380, 35.6997),
    )


    /** Groups the roundabout Ways in [ways] into rings - connected through shared nodes. */
    private fun rings(ways: List<Way>): List<Set<Way>> {
        val byNode = HashMap<Intersection, MutableList<Way>>()
        for (way in ways) for (node in way.intersections.filterNotNull()) byNode.getOrPut(node) { mutableListOf() }.add(way)
        val seen = HashSet<Way>()
        val result = mutableListOf<Set<Way>>()
        for (start in ways) {
            if (start in seen) continue
            val ring = HashSet<Way>()
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val way = queue.removeFirst()
                if (!seen.add(way)) continue
                ring.add(way)
                for (node in way.intersections.filterNotNull()) {
                    byNode[node]?.forEach { if (it !in seen) queue.add(it) }
                    // Tile-edge joiners split a ring; step across them.
                    for (member in node.members) {
                        if (member.wayType != WayType.JOINER) continue
                        member.getOtherIntersection(node)?.members?.forEach {
                            if (it.isRoundabout() && (it !in seen)) queue.add(it)
                        }
                    }
                }
            }
            result.add(ring)
        }
        return result
    }

    /** The node past a tile edge where [ring] carries on - see JunctionCluster's equivalent. */
    private fun acrossJoiners(node: Intersection, ring: Set<Way>): Intersection? {
        val seen = HashSet<Intersection>()
        val queue = ArrayDeque(listOf(node))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue
            if ((current !== node) && current.members.any { (it in ring) && (it.intersections[WayEnd.START.id] === current) })
                return current
            current.members.filter { it.wayType == WayType.JOINER }.forEach { j -> j.getOtherIntersection(current)?.let { queue.add(it) } }
        }
        return null
    }

    /**
     * The point [distance] along [way] away from [from], carrying on along Ways of the same name
     * (either direction, straightest first), and how far it actually got.
     */
    private fun pointOut(way: Way, from: Intersection, distance: Double,
                         ruler: org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler): Pair<LngLatAlt, Double> {
        var w = way
        var n = from
        var travelled = 0.0
        val seen = HashSet<Way>()
        while (seen.add(w)) {
            val line = w.geometry as org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
            if (travelled + w.length >= distance) {
                val along = distance - travelled
                val p = if (w.intersections[WayEnd.START.id] === n) ruler.along(line, along)
                    else ruler.along(line, maxOf(0.0, w.length - along))
                return p to distance
            }
            travelled += w.length
            val next = w.getOtherIntersection(n) ?: return n.location to travelled
            val heading = (w.heading(next) + 180.0) % 360.0
            val current = w
            w = next.members.filter { (it !== current) && (it !in seen) && (it.name != null) && (it.name == way.name) && !it.isRoundabout() }
                .minByOrNull { kotlin.math.abs(((it.heading(next) - heading + 540.0) % 360.0) - 180.0) }
                ?: return next.location to travelled
            n = next
        }
        return n.location to travelled
    }

    /** True if following the ring START to END from any node gets back to it. */
    private fun isClosed(ring: Set<Way>): Boolean {
        val first = ring.first().intersections[WayEnd.START.id] ?: return false
        var node: Intersection = first
        val visited = HashSet<Intersection>()
        repeat(2 * ring.size + 4) {
            visited.add(node)
            val next = node.members.firstOrNull {
                (it in ring) && (it.intersections[WayEnd.START.id] === node)
            }?.intersections?.get(WayEnd.END.id)
                ?: acrossJoiners(node, ring)
                ?: return false
            if (next === first) return true
            node = next
        }
        return false
    }

    /**
     * Why [ring] doesn't close: walks it as [isClosed] does and describes where it stops - what
     * is attached at that node, and which ring Ways have no successor at all.
     */
    private fun closeDiagnosis(ring: Set<Way>, ruler: org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler): String {
        val out = StringBuilder()
        val nodes = ring.flatMap { it.intersections.filterNotNull() }.toSet()
        fun ll(n: Intersection) = "%.6f,%.6f".format(n.location.latitude, n.location.longitude)
        fun describe(n: Intersection) = n.members.joinToString("; ") { m ->
            val end = when {
                m.intersections[WayEnd.START.id] === n -> "starts"
                m.intersections[WayEnd.END.id] === n -> "ends"
                else -> "?"
            }
            "%s%s %s class=%s oneway=%s len=%.1f %s".format(
                if (m in ring) "RING " else "", m.wayType.name.lowercase(), m.name, m.featureClass,
                m.properties?.get("oneway"), m.length, end)
        }
        // Ring Ways nothing follows on from, and ones nothing leads into.
        for (way in ring) {
            val end = way.intersections[WayEnd.END.id]
            val start = way.intersections[WayEnd.START.id]
            if ((end == null) || ((end.members.none { (it in ring) && (it.intersections[WayEnd.START.id] === end) }) &&
                    (acrossJoiners(end, ring) == null)))
                out.append("    dead end after ${way.name} (len %.0f) at ${end?.let { ll(it) }}: ${end?.let { describe(it) }}\n".format(way.length))
            if ((start == null) || ((start.members.none { (it in ring) && (it.intersections[WayEnd.END.id] === start) }) &&
                    start.members.none { it.wayType == WayType.JOINER }))
                out.append("    nothing leads into ${way.name} (len %.0f) at ${start?.let { ll(it) }}: ${start?.let { describe(it) }}\n".format(way.length))
        }
        // Two-way or opposite-direction pieces.
        ring.filter { it.properties?.get("oneway") == null }.forEach {
            out.append("    ring piece with no oneway: ${it.name} len %.0f\n".format(it.length))
        }
        var diameter = 0.0
        for (a in nodes) for (b in nodes) diameter = maxOf(diameter, ruler.distance(a.location, b.location))
        out.append("    ${ring.size} pieces, ${nodes.size} nodes, %.0fm across, joiners at ${nodes.count { n -> n.members.any { it.wayType == WayType.JOINER } }} nodes\n".format(diameter))
        return out.toString()
    }

    @Test
    fun sweep() {
        val detail = StringBuilder()
        val summary = StringBuilder()
        val flagged = mutableMapOf<String, MutableList<String>>()
        fun flag(kind: String, what: String) { flagged.getOrPut(kind) { mutableListOf() }.add(what) }
        var totalRings = 0
        var totalApproaches = 0
        val notClosed = StringBuilder()
        var reloaded = 0
        val uTurnCases = linkedMapOf<String, MutableList<String>>()

        for ((area, centre) in areas) {
            val settlementGrid = getGridStateForLocation(centre, 12, 3)
            val gridState = getGridStateForLocation(centre, MAX_ZOOM_LEVEL, 2, settlementGrid)
            val ways = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS).getAllCollection()
                .map { it as Way }.filter { it.isRoundabout() }
            val found = rings(ways).filter { ring ->
                ring.flatMap { it.intersections.filterNotNull() }.toSet().size >= 3
            }
            summary.append("$area: ${found.size} roundabouts\n")
            totalRings += found.size

            for (foundRing in found) {
                // A ring on the edge of the area loaded for the town is cut off there. Load the
                // map around it instead, as the app would around a user standing there.
                var ring = foundRing
                var grid = gridState
                if (!isClosed(foundRing)) {
                    val fc = foundRing.flatMap { it.intersections.filterNotNull() }.let { n ->
                        LngLatAlt(n.sumOf { it.location.longitude } / n.size, n.sumOf { it.location.latitude } / n.size)
                    }
                    val local = getGridStateForLocation(fc, MAX_ZOOM_LEVEL, 2, settlementGrid)
                    val localRing = rings(local.getFeatureTree(TreeId.ROADS_AND_PATHS)
                        .getNearbyCollection(fc, 300.0, local.ruler).map { it as Way }.filter { it.isRoundabout() })
                        .firstOrNull { r -> r.flatMap { it.intersections.filterNotNull() }.any { local.ruler.distance(it.location, fc) < 40.0 } }
                    if ((localRing != null) && isClosed(localRing)) {
                        ring = localRing
                        grid = local
                        reloaded++
                    }
                }
                val ringNodes = ring.flatMap { it.intersections.filterNotNull() }.toSet()
                val c = LngLatAlt(
                    ringNodes.sumOf { it.location.longitude } / ringNodes.size,
                    ringNodes.sumOf { it.location.latitude } / ringNodes.size
                )
                val where = "%s %.6f,%.6f".format(area, c.latitude, c.longitude)
                val ringName = ring.mapNotNull { it.name }.firstOrNull()
                detail.append("\n==== $where${ringName?.let { " ($it)" } ?: ""}: ${ring.size} ring ways, ${ringNodes.size} nodes\n")
                if (!isClosed(ring)) {
                    flag("ring not closed (tile edge or data)", where)
                    detail.append("  (ring does not close)\n")
                    val why = closeDiagnosis(ring, grid.ruler)
                    detail.append(why)
                    notClosed.append("\n$where${ringName?.let { " ($it)" } ?: ""}\n$why")
                }

                // Per road: the exit lists heard from each of its approaches.
                val heardByRoad = HashMap<String, MutableSet<String>>()
                for (node in ringNodes) {
                    for (arm in node.members) {
                        if (arm.isRoundabout() || arm.isSidewalkOrCrossing() || (arm.wayType == WayType.JOINER)) continue
                        // Only arms that let you in: a one-way exit carriageway isn't an approach.
                        val leaving = arm.getOtherIntersection(node)
                        if ((leaving != null) && arm.isOnewayInto(leaving)) continue
                        if (arm.featureClass in setOf("path", "track", "busway")) continue
                        totalApproaches++
                        val armName = arm.name ?: "unnamed ${arm.featureClass}"
                        val heading = (arm.heading(node) + 180.0) % 360.0
                        detail.append("\n  Approaching along $armName (heading %.0f)\n".format(heading))
                        val description = IntersectionDescription(arm, UserGeometry(location = node.location), node, 40.0)
                        val current = addIntersectionCalloutFromDescription(description, null, null, grid, false)
                        detail.append("    NOW:   " + (current?.positionedStrings?.drop(1)
                            ?.joinToString(" | ") { it.text.trim() } ?: "(nothing)") + "\n")
                        val cluster = buildJunctionCluster(description, grid, null)
                        if (cluster == null) {
                            detail.append("    PROTO: (no cluster)\n")
                            flag("no cluster", "$where from $armName")
                            continue
                        }
                        // The checks below read the full numbered list; what would be spoken is the
                        // brief form.
                        val lines = if (cluster.isRoundabout) describeRoundaboutInFull(cluster)
                            else describeJunctionCluster(cluster)
                        detail.append("    SAY:   " + describeJunctionCluster(cluster).joinToString(" | ") + "\n")
                        detail.append("    FULL:  " + lines.joinToString(" | ") + "\n")
                        val entryBearing = grid.ruler.bearing(c, node.location)
                        detail.append("    exits: " + cluster.exits.joinToString("; ") { e ->
                            val fromAngle = ((grid.ruler.bearing(c, e.from.location) - entryBearing + 540.0) % 360.0) - 180.0
                            "%d:%s rel=%.0f at=%.0f oneway=%s".format(
                                e.exitNumber ?: 0, e.way.name, e.relativeBearing, fromAngle, e.way.properties?.get("oneway"))
                        } + "  approach oneway=${arm.properties?.get("oneway")}\n")

                        val tag = "$where from $armName: ${lines.joinToString(" | ")}"
                        if (!cluster.isRoundabout) flag("not recognised as roundabout", tag)
                        when (cluster.exits.size) {
                            0 -> flag("0 exits", tag)
                            1 -> flag("1 exit", tag)
                        }
                        if (cluster.exits.size > 6) flag("more than 6 exits", tag)
                        val exitLines = lines.drop(1).map { it.substringAfter(" for ", "") }
                        if (exitLines.filter { it.isNotEmpty() }.let { it.size != it.toSet().size })
                            flag("duplicate exit description", tag)
                        if (lines.drop(1).any { !it.contains(" for ") })
                            flag("exit with no name", tag)
                        // The same road leaving in roughly the direction it arrived from.
                        val suspects = cluster.exits.filter { it.way.name == arm.name && (arm.name != null) &&
                                kotlin.math.abs(it.relativeBearing) > 100.0 }
                        if (suspects.isNotEmpty()) {
                            flag("possible U-turn listed (own road, behind)", tag)
                            // Where are the two roads 30m and 150m out? A real way back runs
                            // alongside the approach; the same road continuing leaves the far side.
                            val r = grid.ruler
                            val a30 = pointOut(arm, node, 30.0, r)
                            val a150 = pointOut(arm, node, 150.0, r)
                            val approachOneway = arm.properties?.get("oneway")
                            for (e in suspects) {
                                val e30 = pointOut(e.way, e.from, 30.0, r)
                                val e150 = pointOut(e.way, e.from, 150.0, r)
                                val gap150 = r.distance(a150.first, e150.first)
                                val angle = kotlin.math.abs(((r.bearing(c, e150.first) - r.bearing(c, a150.first) + 540.0) % 360.0) - 180.0)
                                val isLast = e === cluster.exits.last()
                                val kind = when {
                                    (approachOneway == null) && (arm.length < 40.0) -> "approach is a short two-way link"
                                    angle > 120.0 -> "false flag: same road continuing across"
                                    gap150 < 80.0 -> "missed way back: runs alongside the approach"
                                    angle < 45.0 -> "missed way back: heads the same way, further apart"
                                    else -> "unclear"
                                }
                                uTurnCases.getOrPut(kind) { mutableListOf() }.add(
                                    "$where from ${arm.name} (heading %.0f, approach oneway=%s len %.0f) -> exit %d of %d%s: gap at 30m %.0fm, at 150m %.0fm (followed %.0f/%.0fm), angle at centre %.0f\n      SAY: %s".format(
                                        (arm.heading(node) + 180.0) % 360.0, approachOneway, arm.length,
                                        e.exitNumber ?: 0, cluster.exits.size, if (isLast) " (last)" else "",
                                        r.distance(a30.first, e30.first), gap150, a150.second, e150.second, angle,
                                        describeJunctionCluster(cluster).joinToString(" | ")))
                            }
                        }
                        if (cluster.diameter > 100.0) flag("ring over 100m across", tag)
                        // Keyed by the arm's direction from the centre as well as its name, so that
                        // a road crossing the roundabout counts as two arms.
                        val far = grid.ruler.along(
                            arm.geometry as org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString,
                            if (arm.intersections[WayEnd.START.id] === node) minOf(30.0, arm.length)
                            else maxOf(0.0, arm.length - 30.0)
                        )
                        val octant = (((grid.ruler.bearing(c, far) + 360.0) % 360.0) / 45.0).toInt()
                        heardByRoad.getOrPut("$armName ~${octant * 45}") { mutableSetOf() }
                            .add(cluster.exits.joinToString(",") { "${it.exitNumber}:${it.way.name}" })
                    }
                }
                // Two carriageways of the same road should hear the same exits in the same order.
                for ((road, heard) in heardByRoad) {
                    if (heard.size > 1) flag("carriageways of one road disagree", "$where $road: $heard")
                }
            }
        }

        summary.append("\nTotal: $totalRings roundabouts, $totalApproaches approaches ($reloaded rings reloaded because they were cut off at the edge of the loaded area)\n")
        for ((kind, list) in flagged.entries.sortedByDescending { it.value.size }) {
            summary.append("\n## $kind: ${list.size}\n")
            list.take(12).forEach { summary.append("  $it\n") }
            if (list.size > 12) summary.append("  ... and ${list.size - 12} more\n")
        }
        testOutputFile("roundabout-sweep.txt").writeText(detail.toString())
        testOutputFile("roundabout-sweep-summary.txt").writeText(summary.toString())
        testOutputFile("roundabout-sweep-not-closed.txt").writeText(notClosed.toString())
        testOutputFile("roundabout-sweep-uturns.txt").writeText(
            uTurnCases.entries.sortedByDescending { it.value.size }.joinToString("") { (kind, list) ->
                "\n## $kind: ${list.size}\n" + list.joinToString("") { "  $it\n" }
            })
        println(summary)
    }
}
