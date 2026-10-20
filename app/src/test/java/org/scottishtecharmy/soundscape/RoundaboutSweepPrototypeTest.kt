package org.scottishtecharmy.soundscape

import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.callouts.IntersectionDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.addIntersectionCalloutFromDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.buildJunctionCluster
import org.scottishtecharmy.soundscape.geoengine.callouts.describeJunctionCluster
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

    private fun Way.isRoundabout() = properties?.get("junction") == "roundabout"

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

    /** True if following the ring START to END from any node gets back to it. */
    private fun isClosed(ring: Set<Way>): Boolean {
        val first = ring.first().intersections[WayEnd.START.id] ?: return false
        var node: Intersection = first
        repeat(ring.size + 4) {
            val next = node.members.firstOrNull {
                (it in ring) && (it.intersections[WayEnd.START.id] === node)
            }?.intersections?.get(WayEnd.END.id)
                ?: node.members.firstOrNull { it.wayType == WayType.JOINER }?.getOtherIntersection(node)
                ?: return false
            if (next === first) return true
            node = next
        }
        return false
    }

    @Test
    fun sweep() {
        val detail = StringBuilder()
        val summary = StringBuilder()
        val flagged = mutableMapOf<String, MutableList<String>>()
        fun flag(kind: String, what: String) { flagged.getOrPut(kind) { mutableListOf() }.add(what) }
        var totalRings = 0
        var totalApproaches = 0

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

            for (ring in found) {
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
                }

                // Per road: the exit lists heard from each of its approaches.
                val heardByRoad = HashMap<String, MutableSet<String>>()
                for (node in ringNodes) {
                    for (arm in node.members) {
                        if (arm.isRoundabout() || arm.isSidewalkOrCrossing() || (arm.wayType == WayType.JOINER)) continue
                        // Only arms that let you in: a one-way exit carriageway isn't an approach.
                        val leaving = arm.getOtherIntersection(node)
                        if ((leaving != null) && arm.isOnewayInto(leaving)) continue
                        if (arm.featureClass in setOf("path", "track")) continue
                        totalApproaches++
                        val armName = arm.name ?: "unnamed ${arm.featureClass}"
                        val heading = (arm.heading(node) + 180.0) % 360.0
                        detail.append("\n  Approaching along $armName (heading %.0f)\n".format(heading))
                        val description = IntersectionDescription(arm, UserGeometry(location = node.location), node, 40.0)
                        val current = addIntersectionCalloutFromDescription(description, null, null, gridState, false)
                        detail.append("    NOW:   " + (current?.positionedStrings?.drop(1)
                            ?.joinToString(" | ") { it.text.trim() } ?: "(nothing)") + "\n")
                        val cluster = buildJunctionCluster(description, gridState, null)
                        if (cluster == null) {
                            detail.append("    PROTO: (no cluster)\n")
                            flag("no cluster", "$where from $armName")
                            continue
                        }
                        val lines = describeJunctionCluster(cluster)
                        detail.append("    PROTO: " + lines.joinToString(" | ") + "\n")
                        val entryBearing = gridState.ruler.bearing(c, node.location)
                        detail.append("    exits: " + cluster.exits.joinToString("; ") { e ->
                            val fromAngle = ((gridState.ruler.bearing(c, e.from.location) - entryBearing + 540.0) % 360.0) - 180.0
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
                        if (cluster.exits.any { it.way.name == arm.name && (arm.name != null) &&
                                kotlin.math.abs(it.relativeBearing) > 100.0 })
                            flag("possible U-turn listed (own road, behind)", tag)
                        if (cluster.diameter > 100.0) flag("ring over 100m across", tag)
                        // Keyed by the arm's direction from the centre as well as its name, so that
                        // a road crossing the roundabout counts as two arms.
                        val far = gridState.ruler.along(
                            arm.geometry as org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString,
                            if (arm.intersections[WayEnd.START.id] === node) minOf(30.0, arm.length)
                            else maxOf(0.0, arm.length - 30.0)
                        )
                        val octant = (((gridState.ruler.bearing(c, far) + 360.0) % 360.0) / 45.0).toInt()
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

        summary.append("\nTotal: $totalRings roundabouts, $totalApproaches approaches\n")
        for ((kind, list) in flagged.entries.sortedByDescending { it.value.size }) {
            summary.append("\n## $kind: ${list.size}\n")
            list.take(12).forEach { summary.append("  $it\n") }
            if (list.size > 12) summary.append("  ... and ${list.size - 12} more\n")
        }
        testOutputFile("roundabout-sweep.txt").writeText(detail.toString())
        testOutputFile("roundabout-sweep-summary.txt").writeText(summary.toString())
        println(summary)
    }
}
