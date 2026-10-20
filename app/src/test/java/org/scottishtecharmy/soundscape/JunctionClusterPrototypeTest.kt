package org.scottishtecharmy.soundscape

import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.callouts.CalloutVerbosity
import org.scottishtecharmy.soundscape.geoengine.callouts.IntersectionDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.JunctionCluster
import org.scottishtecharmy.soundscape.geoengine.callouts.JunctionSide
import org.scottishtecharmy.soundscape.geoengine.callouts.addIntersectionCalloutFromDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.buildJunctionCluster
import org.scottishtecharmy.soundscape.geoengine.callouts.describeJunctionCluster
import org.scottishtecharmy.soundscape.geoengine.callouts.isRoundabout
import org.scottishtecharmy.soundscape.geoengine.callouts.getRoadsDescriptionFromFov
import org.scottishtecharmy.soundscape.geoengine.filters.TrackedCallout
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.getCentralPointForFeature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.io.File

/**
 * PROTOTYPE comparison for JunctionCluster: what the app says at each junction now, next to what
 * the merged-junction "keep left / keep right" wording would say. Output only - no assertions.
 * Writes build/test-output/junction-prototype-*.txt.
 */
class JunctionClusterPrototypeTest {

    /** Beacons to try, found by name in the loaded tiles. */
    private val beaconNames = listOf("M&S Foodhall", "Tesco", "Milngavie Town Hall", "The Talbot Arms", "Barloch Moor", "Milngavie Enterprise Centre")

    private fun findBeacon(gridState: GridState, name: String): LngLatAlt? {
        for (tree in listOf(TreeId.POIS, TreeId.TRANSIT_STOPS, TreeId.RAILWAY_STOPS)) {
            val match = gridState.getFeatureTree(tree).getAllCollection()
                .firstOrNull { (it as MvtFeature).name == name } ?: continue
            return getCentralPointForFeature(match)
        }
        return null
    }

    private fun details(cluster: JunctionCluster): List<String> {
        val lines = mutableListOf<String>()
        val counts = JunctionSide.entries.joinToString(" ") { side ->
            "${side.name.first()}${cluster.exits.count { it.side == side }}"
        }
        lines.add(
            "    [cluster: %d node(s), %.0fm across, exits %s%s]".format(
                cluster.nodes.size, cluster.diameter, counts,
                if (cluster.isRoundabout) ", roundabout" else ""
            )
        )
        if (cluster.settlementByExit.isNotEmpty())
            lines.add("    [settlements: ${cluster.settlementByExit}]")
        for (side in JunctionSide.entries) {
            val list = cluster.destinationsBySide[side] ?: continue
            lines.add("    [${side.name.lowercase()}: " + list.take(4).joinToString(", ") {
                "%s%s %.0fm".format(it.name, if (it.isRoad) "" else "*", it.distanceBySide[side])
            } + "]")
        }
        return lines
    }

    private fun prototypeLines(
        description: IntersectionDescription,
        gridState: GridState,
    ): List<String> {
        val cluster = buildJunctionCluster(description, gridState, null)
            ?: return listOf("  PROTOTYPE: (no cluster)")
        val lines = mutableListOf("  PROTOTYPE:")
        describeJunctionCluster(cluster).forEach { lines.add("    $it") }
        lines.addAll(details(cluster))
        for (name in beaconNames) {
            val beacon = findBeacon(gridState, name) ?: continue
            val withBeacon = buildJunctionCluster(description, gridState, null, beacon) ?: continue
            val said = describeJunctionCluster(withBeacon, name)
            val beaconLine = said.firstOrNull { it.contains(name) } ?: "(no clear side)"
            val routes = withBeacon.beaconDistanceByExit.entries.sortedBy { it.value }
                .joinToString(", ") { "%s %.0fm".format(it.key.label, it.value) }
            lines.add("    beacon $name -> $beaconLine   [$routes]")
        }
        return lines
    }

    private fun replay(walk: String) {
        MvtTileTest().testMovingGrid(
            "src/test/res/org/scottishtecharmy/soundscape/gpxFiles/$walk.gpx",
            "junction-prototype-$walk.txt",
            "junction-prototype-$walk.geojson",
            onCallout = { userGeometry: UserGeometry, gridState: GridState, callout: TrackedCallout ->
                val first = callout.positionedStrings.firstOrNull()?.text ?: ""
                if (!first.startsWith("Intersection") && !first.startsWith("Approaching"))
                    emptyList()
                else {
                    // The callout doesn't carry its description, so search again, trying each
                    // verbosity's tier until one finds the junction that was announced.
                    val at = callout.location
                    val description = CalloutVerbosity.entries
                        .map { it.minimumIntersectionTier }.distinct()
                        .map { getRoadsDescriptionFromFov(gridState, userGeometry, null, it) }
                        .firstOrNull { it.intersection?.location == at }
                        ?: getRoadsDescriptionFromFov(gridState, userGeometry, null)
                    val header = "  at %.6f,%.6f".format(at.latitude, at.longitude)
                    if (description.intersection?.location != at)
                        listOf(header, "  PROTOTYPE: (search picked a different junction)")
                    else
                        listOf(header) + prototypeLines(description, gridState)
                }
            }
        )
    }

    @Test
    fun walkFromTesco() = replay("WalkFromTesco-pixel")

    @Test
    fun toTown() = replay("ToTown")


    /**
     * Walks in to the roundabout at [centre] along each of its arms and describes it both ways.
     * Builds the IntersectionDescription directly - arm and ring node - rather than through the
     * field-of-view search, as the point is the wording rather than the search.
     */
    private fun roundabout(centre: LngLatAlt, out: StringBuilder) {
        // With a settlement grid, as GeoEngine has, so that exits can be told apart by the place
        // they head towards.
        val settlementGrid = getGridStateForLocation(centre, 12, 3)
        val gridState = getGridStateForLocation(centre, MAX_ZOOM_LEVEL, 2, settlementGrid)
        val ring = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
            .getNearbyCollection(centre, 60.0, gridState.ruler)
            .map { it as Way }.filter { it.isRoundabout() }
        val ringNodes = ring.flatMap { it.intersections.filterNotNull() }.toSet()
        out.append("Roundabout at $centre: ${ring.size} ring ways, ${ringNodes.size} ring nodes\n")
        for (node in ringNodes) {
            for (arm in node.members) {
                if (arm.isRoundabout() || arm.isSidewalkOrCrossing()) continue
                val armName = arm.name ?: "unnamed ${arm.featureClass}"
                out.append("\nApproaching along $armName (heading %.0f)\n".format(
                    (arm.heading(node as Intersection) + 180.0) % 360.0))
                val description = IntersectionDescription(arm, UserGeometry(location = node.location), node, 40.0)
                val current = addIntersectionCalloutFromDescription(description, null, null, gridState, false)
                out.append("  CURRENT:\n")
                current?.positionedStrings?.forEach { out.append("    ${it.text.trim()}\n") }
                    ?: out.append("    (nothing)\n")
                prototypeLines(description, gridState).forEach { out.append("$it\n") }
            }
        }
    }

    @Test
    fun southMilngavieRoundabout() {
        val out = StringBuilder()
        roundabout(LngLatAlt(-4.3164344, 55.9339605), out)
        File(testOutputFile("junction-prototype-roundabout-south.txt").path).writeText(out.toString())
    }

    @Test
    fun milngavieRoundabout() {
        val out = StringBuilder()
        roundabout(LngLatAlt(-4.3180621, 55.9433263), out)
        File(testOutputFile("junction-prototype-roundabout.txt").path).writeText(out.toString())
    }
}
