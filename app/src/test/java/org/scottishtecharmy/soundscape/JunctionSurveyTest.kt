package org.scottishtecharmy.soundscape

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.callouts.CalloutPoiSelection
import org.scottishtecharmy.soundscape.geoengine.callouts.addIntersectionCalloutFromDescription
import org.scottishtecharmy.soundscape.geoengine.callouts.getRoadsDescriptionFromFov
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.SignpostTrace
import org.scottishtecharmy.soundscape.geoengine.utils.bearingFromTwoPoints
import org.scottishtecharmy.soundscape.geoengine.utils.calculateHeadingOffset
import org.scottishtecharmy.soundscape.geoengine.utils.signpostObserver
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiPolygon
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every junction in a patch of each city, approached along each of its ways, and what the
 * junction callout says - for diffing one version of the callout code against another in places
 * the recorded walks never go, above all neighbourhoods where most streets have no name.
 *
 * Writes build/test-output/junction-survey/<area>.txt, one line per approach, and <area>.geojson:
 * each junction as a point carrying its callouts, and each signposted arm as the line of its route
 * to what it's signposted to - green with no turns on the way, orange with turns, red where the
 * target lies more than 90 degrees off the way the arm sets out - ending at a point on the target.
 * Areas outside the
 * committed extracts (Chennai, Delhi) need SOUNDSCAPE_TEST_EXTRACTS pointing at a directory that
 * also holds those extracts; an area with no map is written as empty and skipped.
 *
 * `./gradlew :app:nightlyUnitTest --tests '*JunctionSurveyTest' --rerun`, with JUNCTION_SURVEY=name
 * to survey just the areas whose name contains it.
 */
class JunctionSurveyTest {

    private class Area(val name: String, val latitude: Double, val longitude: Double)

    private val areas = listOf(
        // Chennai: old, dense, and with many lanes unnamed
        Area("chennai-mylapore", 13.0336, 80.2697),
        Area("chennai-triplicane", 13.0569, 80.2766),
        Area("chennai-t-nagar", 13.0405, 80.2337),
        Area("chennai-saidapet", 13.0213, 80.2231),
        Area("chennai-velachery", 12.9791, 80.2209),
        Area("chennai-royapuram", 13.1137, 80.2954),
        // Elsewhere, mostly where streets go unnamed in OSM
        Area("delhi-old-delhi", 28.6562, 77.2310),
        Area("tehran-south", 35.6600, 51.4200),
        Area("tehran-centre", 35.6997, 51.3380),
        Area("osaka-ikuno", 34.6550, 135.5300),
        Area("osaka-namba", 34.6660, 135.5010),
        Area("san-salvador", 13.6990, -89.1910),
        Area("buenos-aires-villa-31", -34.5830, -58.3800),
        Area("buenos-aires-palermo", -34.5880, -58.4300),
        Area("rio-gallegos", -51.6230, -69.2160),
        Area("paris-belleville", 48.8720, 2.3790),
        Area("ede", 52.0400, 5.6650),
        Area("milngavie", 55.9420, -4.3160),
    )

    @Category(NightlyOnlyTest::class)
    @Test
    fun surveyJunctions() {
        val only = System.getenv("JUNCTION_SURVEY")
        val outputDir = File("build/test-output/junction-survey").apply { mkdirs() }
        var surveyed = 0
        for (area in areas) {
            if ((only != null) && !area.name.contains(only)) continue
            val (lines, geoJson) = survey(area)
            File(outputDir, "${area.name}.txt").writeText(lines.joinToString("\n", postfix = "\n"))
            File(outputDir, "${area.name}.geojson").writeText(geoJson)
            println("${area.name}: ${lines.size} approaches")
            if (lines.isNotEmpty()) surveyed++
        }
        assertTrue(surveyed > 0, "No area had a map to survey")
    }

    private fun survey(area: Area): Pair<List<String>, String> {
        val centre = LngLatAlt(area.longitude, area.latitude)
        val gridState = FileGridState()
        gridState.start(offlineExtractPath)
        gridState.checkOfflineMaps()
        runBlocking { gridState.locationUpdate(centre, CalloutPoiSelection.EVERYTHING, null) }
        val ruler = gridState.ruler

        val junctions = gridState.getFeatureTree(TreeId.INTERSECTIONS)
            .getNearestCollection(centre, SURVEY_RADIUS_METRES, 100000, ruler)
            .features
            .map { it as Intersection }
            .filter { it.members.size >= 3 }
            .sortedWith(compareBy({ it.location.latitude }, { it.location.longitude }))

        // Each signpost as it's worked out, while the search that found it still marks its route
        val surveyed = junctions.toHashSet()
        val signposts = mutableListOf<MappedSignpost>()
        signpostObserver = { trace -> if (trace.junction in surveyed) signposts.add(mapSignpost(trace, ruler)) }
        val approaches = LinkedHashMap<Intersection, MutableList<String>>()

        val lines = mutableListOf<String>()
        try {
            for (junction in junctions) {
                for (arm in junction.members.sortedBy { it.heading(junction) }) {
                    val start = pointBack(arm, junction, APPROACH_METRES, gridState.ruler) ?: continue
                    val userGeometry = UserGeometry(
                        location = start,
                        phoneHeading = bearingFromTwoPoints(start, junction.location),
                        fovDistance = 50.0,
                        mapMatchedWay = arm,
                        mapMatchedLocation = ruler.distanceToLineString(start, arm.geometry as LineString),
                    )
                    val description = getRoadsDescriptionFromFov(gridState, userGeometry, null)
                    val found = description.intersection ?: continue
                    // Only where the search lands on this junction - a nearer one on the way is
                    // surveyed from its own approaches
                    if ((found !== junction) && description.alsoAt.none { it.first === junction }) continue
                    val callout = addIntersectionCalloutFromDescription(description, null, null, gridState)
                        ?: continue
                    val arms = callout.positionedStrings.drop(1).joinToString(" | ") { it.text.trim() }
                    val from = arm.name ?: "unnamed ${arm.featureValue ?: arm.featureClass}"
                    approaches.getOrPut(junction) { mutableListOf() }.add("from $from: $arms")
                    lines.add(
                        "%.6f,%.6f from %s: %s%s".format(
                            junction.location.latitude, junction.location.longitude, from, arms,
                            // A junction of several nodes close together, described as one
                            if (description.alsoAt.isEmpty()) "" else " [${description.alsoAt.size + 1} nodes]"
                        )
                    )
                }
            }
        } finally {
            signpostObserver = null
        }
        return lines.distinct() to geoJson(approaches, signposts.distinctBy { it.key })
    }

    private class MappedSignpost(
        val key: String,
        val properties: Map<String, Any>,
        val route: List<LngLatAlt>,
        val target: LngLatAlt?,
    )

    private fun mapSignpost(trace: SignpostTrace, ruler: org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler): MappedSignpost {
        val junction = trace.junction
        // The junctions on the route, from the signposted one out
        val chain = mutableListOf<Intersection>()
        var at = trace.reachedAt
        while ((at != null) && (at !== junction)) { chain.add(at); at = at.dijkstraPrevious }
        chain.add(junction)
        chain.reverse()
        val route = mutableListOf(junction.location)
        val routeWays = mutableListOf<Way>()
        for (i in 1 until chain.size) {
            val from = chain[i - 1]
            // The search's first step is the arm itself; after that, the shortest way between
            val way = if (i == 1) trace.way
                else from.members.filter { it.getOtherIntersection(from) === chain[i] }.minByOrNull { it.length }
            val coordinates = (way?.geometry as? LineString)?.coordinates
            if ((way == null) || (coordinates == null)) { route.add(chain[i].location); continue }
            routeWays.add(way)
            route.addAll((if (way.intersections[WayEnd.START.id] === from) coordinates else coordinates.reversed()).drop(1))
        }
        // A landmark is reached part way along one of the last junction's ways
        val via = trace.via
        val viaLine = via?.geometry as? LineString
        var reachedPoint = chain.last().location
        if ((via != null) && (viaLine != null) && (trace.along > 0.0)) {
            val outward = if (via.intersections[WayEnd.START.id] === chain.last()) viaLine.coordinates
                else viaLine.coordinates.reversed()
            var travelled = 0.0
            for (i in 1 until outward.size) {
                val d = ruler.distance(outward[i - 1], outward[i])
                if (travelled + d >= trace.along) {
                    val f = if (d > 0.0) (trace.along - travelled) / d else 0.0
                    reachedPoint = LngLatAlt(
                        outward[i - 1].longitude + (outward[i].longitude - outward[i - 1].longitude) * f,
                        outward[i - 1].latitude + (outward[i].latitude - outward[i - 1].latitude) * f
                    )
                    route.add(reachedPoint)
                    break
                }
                route.add(outward[i])
                travelled += d
            }
            routeWays.add(via)
        }
        val target = trace.feature?.let { targetPoint(it, reachedPoint, ruler) } ?: chain.last().location
        if (trace.feature != null) route.add(target)

        var turns = 0
        for (i in 1 until chain.size - 1) {
            val inward = ruler.bearing(chain[i - 1].location, chain[i].location)
            val outward = ruler.bearing(chain[i].location, chain[i + 1].location)
            if (calculateHeadingOffset(inward, outward) > TURN_DEGREES) turns++
        }
        val offAngle = if (target == junction.location) null
            else calculateHeadingOffset(trace.way.heading(junction), ruler.bearing(junction.location, target))
        val colour = when {
            (offAngle != null) && (offAngle > 90.0) -> "#c62828"
            turns > 0 -> "#ef6c00"
            else -> "#2e7d32"
        }
        val properties = linkedMapOf<String, Any>(
            "kind" to "signpost",
            "signpost" to trace.text,
            "arm" to (trace.way.name ?: "unnamed ${trace.way.featureValue ?: trace.way.featureClass}"),
            "armClass" to (trace.way.featureClass ?: ""),
            "target" to if (trace.feature == null) "named way" else "landmark",
            "distanceMetres" to trace.distance.toInt(),
            "junctionsPassed" to (chain.size - 2).coerceAtLeast(0),
            "turns" to turns,
            "stroke" to colour,
            "stroke-width" to 3,
        )
        offAngle?.let { properties["offAngleDegrees"] = it.toInt() }
        if (routeWays.isEmpty()) routeWays.add(trace.way)
        properties["routeWays"] = routeWays.map { wayTags(it) }.distinct()
        if (trace.feature == null) {
            properties["targetWays"] = chain.last().members.filter { it.displayName == trace.text }.map { wayTags(it) }.distinct()
        }
        val key = "${junction.location.longitude},${junction.location.latitude},${trace.way.hashCode()}"
        return MappedSignpost(key, properties, route, target)
    }

    private fun wayTags(way: Way) = listOfNotNull(
        way.featureClass,
        if (way.isRamp) "ramp" else null,
        way.properties?.get("expressway")?.let { "expressway" },
        way.properties?.get("foot")?.let { "foot=$it" },
        way.properties?.get("access")?.let { "access=$it" },
    ).joinToString(" ")

    /**
     * Where to mark a landmark: a point as it is, anything bigger - a river, a park - at the
     * nearest point of its outline to [from], the last junction on the way to it.
     */
    private fun targetPoint(
        feature: org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature,
        from: LngLatAlt,
        ruler: org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler,
    ): LngLatAlt? {
        val ring = when (val geometry = feature.geometry) {
            is Point -> return geometry.coordinates
            is Polygon -> geometry.coordinates.firstOrNull()
            is MultiPolygon -> geometry.coordinates.firstOrNull()?.firstOrNull()
            is LineString -> geometry.coordinates
            else -> null
        } ?: return null
        if (ring.size < 2) return ring.firstOrNull()
        return ruler.distanceToLineString(from, LineString(ring)).point
    }

    private fun geoJson(approaches: Map<Intersection, List<String>>, signposts: List<MappedSignpost>): String {
        val features = mutableListOf<String>()
        for ((junction, callouts) in approaches) {
            features.add(feature(point(junction.location), linkedMapOf(
                "kind" to "junction",
                "ways" to junction.members.size,
                "callouts" to callouts.distinct(),
                "marker-color" to "#1565c0",
                "marker-size" to "small",
            )))
        }
        for (signpost in signposts) {
            features.add(feature(
                "{\"type\":\"LineString\",\"coordinates\":[${signpost.route.joinToString(",") { coordinate(it) }}]}",
                signpost.properties
            ))
            signpost.target?.let {
                features.add(feature(point(it), linkedMapOf(
                    "kind" to "target",
                    "signpost" to signpost.properties["signpost"]!!,
                    "marker-color" to signpost.properties["stroke"]!!,
                    "marker-size" to "small",
                )))
            }
        }
        return "{\"type\":\"FeatureCollection\",\"features\":[\n${features.joinToString(",\n")}\n]}\n"
    }

    private fun coordinate(at: LngLatAlt) = "[%.7f,%.7f]".format(at.longitude, at.latitude)
    private fun point(at: LngLatAlt) = "{\"type\":\"Point\",\"coordinates\":${coordinate(at)}}"
    private fun feature(geometry: String, properties: Map<String, Any>) =
        "{\"type\":\"Feature\",\"geometry\":$geometry,\"properties\":${json(properties)}}"

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { "${json(it.key.toString())}:${json(it.value)}" }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> buildString {
            append('"')
            for (c in value.toString()) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
    }

    /** The point [metres] back along [way] from [junction], or null where the way is too short. */
    private fun pointBack(
        way: Way,
        junction: Intersection,
        metres: Double,
        ruler: org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler,
    ): LngLatAlt? {
        val coordinates = (way.geometry as? LineString)?.coordinates ?: return null
        val outward = if (way.intersections[WayEnd.START.id] === junction) coordinates
            else if (way.intersections[WayEnd.END.id] === junction) coordinates.reversed()
            else return null
        var travelled = 0.0
        for (i in 1 until outward.size) {
            val d = ruler.distance(outward[i - 1], outward[i])
            if (travelled + d >= metres) {
                val f = if (d > 0.0) (metres - travelled) / d else 0.0
                return LngLatAlt(
                    outward[i - 1].longitude + (outward[i].longitude - outward[i - 1].longitude) * f,
                    outward[i - 1].latitude + (outward[i].latitude - outward[i - 1].latitude) * f
                )
            }
            travelled += d
        }
        return if (travelled >= MIN_APPROACH_METRES) outward.last() else null
    }

    private companion object {
        const val SURVEY_RADIUS_METRES = 350.0
        const val APPROACH_METRES = 25.0
        const val MIN_APPROACH_METRES = 10.0
        const val TURN_DEGREES = 45.0
    }
}
