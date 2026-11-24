package org.scottishtecharmy.soundscape

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.callouts.CalloutPoiSelection
import org.scottishtecharmy.soundscape.geoengine.utils.OpenArea
import org.scottishtecharmy.soundscape.geoengine.utils.clockPosition
import org.scottishtecharmy.soundscape.geoengine.utils.OpenAreaTracker
import org.scottishtecharmy.soundscape.geoengine.utils.openAreaBearing
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import kotlin.math.roundToInt

/**
 * Not callouts yet: replays recorded walks through precincts and stations and writes what
 * open-area directions (see OpenArea) would know at each point - which area the user is in, its
 * ways out, and what they're heading towards on the far side - so the wording can be judged
 * before any of it reaches the audio. Writes gpxFiles/open-areas/<clip>.txt, and a .geojson of
 * each area visited with its exits.
 */
class OpenAreaTracerTest {

    private class TrackPoint(
        val location: LngLatAlt,
        val heading: Double?,
        val speed: Double?,
        /** Epoch milliseconds, from parseGpxTimestamp. */
        val time: Double?,
    )

    private class Clip(val name: String, val gpx: String, val start: Int, val end: Int)

    private val clips = listOf(
        // Milngavie's precinct: Douglas Street, Station Road, Main Street
        Clip("precinct-WalkFromTesco", "WalkFromTesco-pixel", 0, 330),
        Clip("precinct-tesco-out", "tesco", 100, 200),
        Clip("precinct-tesco-back", "tesco", 470, 620),
        Clip("precinct-TreeCoveredWalk", "TreeCoveredWalk-iphone", 800, 1000),
        Clip("mainstreet-pixel", "pixel", 1580, 1800),
        // Glasgow's Buchanan Street
        Clip("buchanan-travel-4", "travel-4", 2050, 2300),
        // Stations: off the train at Milngavie, and through Glasgow
        Clip("trainoff-pixel", "pixel", 3990, 4500),
        Clip("glasgowcross-train-2", "train-2", 80, 280),
        Clip("finnieston-train-2", "train-2", 1620, 1780),
        // Westerton's platforms and footbridge
        Clip("westerton-pixel", "pixel-merged", 7700, 8480),
        Clip("westerton-ios", "ios-merged", 5720, 6290),
    )

    @Test
    fun traceOpenAreas() {
        val summary = StringBuilder()
        var totalInside = 0
        for (clip in clips) {
            val (inside, line) = traceClip(clip)
            totalInside += inside
            summary.append(line).append('\n')
        }
        testOutputFile("gpxFiles/open-areas/summary.txt").writeText(summary.toString())
        println(summary)
        assertTrue("No fix in any clip was inside an open area", totalInside > 0)
    }

    private fun traceClip(clip: Clip): Pair<Int, String> {
        val track = parseGpxFromFile(
            "src/test/res/org/scottishtecharmy/soundscape/gpxFiles/${clip.gpx}.gpx"
        ).features.mapNotNull { f ->
            (f.geometry as? Point)?.coordinates?.let {
                TrackPoint(
                    it,
                    f.properties?.get("heading") as? Double,
                    f.properties?.get("speed") as? Double,
                    f.properties?.get("time") as? Double,
                )
            }
        }
        val gridState = FileGridState(MAX_ZOOM_LEVEL, 2)
        gridState.start(offlineExtractPath)

        val out = StringBuilder()
        val areasSeen = linkedMapOf<Any, OpenArea>()
        var current: OpenArea? = null
        val tracker = OpenAreaTracker()
        var lastTarget: String? = null
        var lastTargetIndex = -100
        var insideCount = 0
        val end = minOf(clip.end, track.size)

        for (i in clip.start until end) {
            val location = track[i].location
            val recordedHeading = track[i].heading
            runBlocking { gridState.locationUpdate(location, CalloutPoiSelection.EVERYTHING, null) }
            val heading = recordedHeading ?: headingFromTrack(track, i)

            val area = tracker.update(gridState, location, speedFromTrack(track, i))
            if (area?.feature?.osmId != current?.feature?.osmId) {
                current?.let { out.append("[$i] LEAVE ${label(it)}\n") }
                if (area != null) {
                    areasSeen.getOrPut(area.feature.osmId) { area }
                    out.append("[$i] ENTER ${label(area)}: ${area.length.roundToInt()}m x " +
                        "${area.width.roundToInt()}m, long axis ${area.axisBearing.roundToInt()}°, " +
                        "${area.exits.size} exits\n")
                    if (heading != null) out.append("      exits: ${exitList(area, location, heading)}\n")
                }
                current = area
                lastTarget = null
            }
            if (area == null) continue
            insideCount++
            if (heading == null) continue

            // Keep the exit already reported while it's still roughly ahead, so that a few
            // degrees of heading wobble don't swap it for its neighbour
            val ahead = area.exitAhead(
                location, heading, holding = area.exits.firstOrNull { it.description() == lastTarget }
            )
            val targetKey = ahead?.first?.description() ?: "edge"
            if (targetKey != lastTarget || i - lastTargetIndex >= 15) {
                val target = if (ahead != null) {
                    "${exitLabel(ahead.first)} ${ahead.second.roundToInt()}m"
                } else {
                    val hit = area.castRay(location, heading)
                    if (hit == null) "no edge ahead"
                    else "edge ${hit.distance.roundToInt()}m, nearest exit " + nearestExitTo(area, hit.location)
                }
                out.append("[$i] heading ${heading.roundToInt()}°: ahead $target\n")
                lastTarget = targetKey
                lastTargetIndex = i
            }
        }
        current?.let { out.append("[end] still in ${label(it)}\n") }

        testOutputFile("gpxFiles/open-areas/${clip.name}.txt").writeText(out.toString())
        testOutputFile("gpxFiles/open-areas/${clip.name}.geojson").writeText(geojson(areasSeen.values))
        val line = "${clip.name}: ${end - clip.start} fixes, $insideCount inside, " +
            "areas: ${areasSeen.values.joinToString { label(it) + " (" + it.exits.size + " exits)" }}"
        return insideCount to line
    }

    private fun label(area: OpenArea) =
        "${area.kind.name.lowercase()} ${area.name ?: "(unnamed)"} [osm ${area.feature.osmId / 10}]"

    private fun exitList(area: OpenArea, from: LngLatAlt, heading: Double): String =
        area.exits
            .map { it to gridDistance(from, it.location) }
            .sortedBy { it.second }
            .joinToString("; ") { (exit, d) ->
                "${clockPosition(heading, openAreaBearing(from, exit.location))} o'clock " +
                    "${exitLabel(exit)} ${d.roundToInt()}m"
            }

    private fun exitLabel(exit: org.scottishtecharmy.soundscape.geoengine.utils.OpenAreaExit) =
        exit.description() + if (exit.road) " (road)" else ""

    private fun nearestExitTo(area: OpenArea, p: LngLatAlt): String =
        area.exits.minByOrNull { gridDistance(p, it.location) }
            ?.let { "${it.description()} ${gridDistance(p, it.location).roundToInt()}m along the edge" }
            ?: "none"

    private fun gridDistance(a: LngLatAlt, b: LngLatAlt): Double {
        val dx = (b.longitude - a.longitude) * 111_320.0 * kotlin.math.cos(Math.toRadians(a.latitude))
        val dy = (b.latitude - a.latitude) * 111_320.0
        return kotlin.math.hypot(dx, dy)
    }

    /**
     * The recorded speed, or failing that the average over the last ~10 fixes from their
     * timestamps, in metres per second.
     */
    private fun speedFromTrack(track: List<TrackPoint>, i: Int): Double? {
        track[i].speed?.let { return it }
        val j = maxOf(0, i - 10)
        val t0 = track[j].time ?: return null
        val t1 = track[i].time ?: return null
        if (t1 <= t0) return null
        var d = 0.0
        for (k in j until i) d += gridDistance(track[k].location, track[k + 1].location)
        return d / ((t1 - t0) / 1000.0)
    }

    /** Direction of travel from the track: the bearing from the last fix at least 5m back. */
    private fun headingFromTrack(track: List<TrackPoint>, i: Int): Double? {
        for (j in i - 1 downTo maxOf(0, i - 15)) {
            if (gridDistance(track[j].location, track[i].location) >= 5.0)
                return openAreaBearing(track[j].location, track[i].location)
        }
        return null
    }

    private fun geojson(areas: Collection<OpenArea>): String {
        val features = mutableListOf<String>()
        for (area in areas) {
            val rings = (area.feature.geometry as Polygon).coordinates.joinToString(",") { ring ->
                "[" + ring.joinToString(",") { "[${it.longitude},${it.latitude}]" } + "]"
            }
            features.add("""{"type":"Feature","properties":{"kind":"${area.kind}","name":"${area.name ?: ""}",""" +
                """"osm":${area.feature.osmId / 10}},"geometry":{"type":"Polygon","coordinates":[$rings]}}""")
            for (exit in area.exits) {
                features.add("""{"type":"Feature","properties":{"exit":"${exitLabel(exit).replace("\"", "'")}",""" +
                    """"kinds":"${exit.kinds.joinToString()}"},"geometry":{"type":"Point","coordinates":""" +
                    "[${exit.location.longitude},${exit.location.latitude}]}}")
            }
        }
        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }
}
