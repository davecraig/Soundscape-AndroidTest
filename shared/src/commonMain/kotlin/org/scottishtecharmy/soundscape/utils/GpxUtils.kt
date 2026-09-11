package org.scottishtecharmy.soundscape.utils

import org.scottishtecharmy.soundscape.database.local.model.MARKER_SOURCE_JOURNEY
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers
import org.scottishtecharmy.soundscape.geoengine.utils.gpx.GpxWaypoint
import org.scottishtecharmy.soundscape.geoengine.utils.gpx.parseGpx

fun parseGpxFile(input: String): RouteWithMarkers? {
    println("gpx: Parsing GPX file")
    try {
        val parsedGpx = parseGpx(input)
        val waypoints = mutableListOf<MarkerEntity>()

        // We're parsing WayPoints and RoutePoints here. RoutePoints is the way that iOS
        // Soundscape GPX are written and is intended use. However apps like RideWithGps
        // generate WayPoints when exporting GPX and it's useful to support those if no
        // RoutePoints are found.

        parsedGpx.routes.forEach { route ->
            println("gpx: RoutePoint " + route.routeName)
            route.routePoints.forEach { waypoint ->
                waypoints.add(
                    MarkerEntity(
                        name = waypoint.name,
                        fullAddress = waypoint.desc,
                        longitude = waypoint.longitude,
                        latitude = waypoint.latitude,
                        // Only our own value counts. <type> is a standard GPX element that other
                        // tools put their own things in - Garmin, OsmAnd, geocaching.com's
                        // "Geocache|Traditional Cache" - and source is what decides whether a
                        // marker is the user's at all. Anything else landing here would hide the
                        // imported marker from the Markers screen, the beacon list and the
                        // callouts, with nothing to say why.
                        source = waypoint.journeySource(),
                        reverseDirection = waypoint.reverseDirection(),
                    )
                )
            }
        }
        if (waypoints.isEmpty()) {
            parsedGpx.waypoints.forEach { waypoint ->
                println("gpx: WayPoint " + waypoint.name)
                waypoints.add(
                    MarkerEntity(
                        name = waypoint.name,
                        fullAddress = waypoint.desc,
                        longitude = waypoint.longitude,
                        latitude = waypoint.latitude,
                        // Only our own value counts. <type> is a standard GPX element that other
                        // tools put their own things in - Garmin, OsmAnd, geocaching.com's
                        // "Geocache|Traditional Cache" - and source is what decides whether a
                        // marker is the user's at all. Anything else landing here would hide the
                        // imported marker from the Markers screen, the beacon list and the
                        // callouts, with nothing to say why.
                        source = waypoint.journeySource(),
                        reverseDirection = waypoint.reverseDirection(),
                    )
                )
            }
        }
        return RouteWithMarkers(
            RouteEntity(
                name = parsedGpx.metadata.name,
                description = parsedGpx.metadata.desc,
            ),
            waypoints
        )
    } catch (e: Exception) {
        println("gpx: Exception whilst parsing GPX file: ${e.message}")
        e.printStackTrace()
    }

    return null
}

/**
 * The Soundscape source in a GPX <type>, or null for a file from anywhere else.
 *
 * Only our own value counts. <type> is a standard element that other tools put their own things in
 * - Garmin, OsmAnd, geocaching.com's "Geocache|Traditional Cache" - and source is what decides
 * whether a marker is the user's at all. Anything else landing there would hide the imported marker
 * from the Markers screen, the beacon list and the callouts, with nothing to say why.
 */
private fun GpxWaypoint.journeySource(): String? =
    type?.substringBefore(GPX_TYPE_SEPARATOR)?.takeIf { it == MARKER_SOURCE_JOURNEY }

/** The instruction for walking the route backwards past this waypoint - see generateGpxString. */
private fun GpxWaypoint.reverseDirection(): String? {
    if (journeySource() == null) return null
    val separator = type?.indexOf(GPX_TYPE_SEPARATOR) ?: -1
    if (separator < 0) return null
    return type?.substring(separator + GPX_TYPE_SEPARATOR.length)?.takeIf { it.isNotBlank() }
}
