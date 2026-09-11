package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.database.local.dao.RouteDao
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import org.scottishtecharmy.soundscape.i18n.StringKey

/**
 * What came of asking to save the last journey, so the caller can say it out loud.
 *
 * There is no silent failure here: a blind user who has just got home and asked for this needs to
 * be told either what was saved or why nothing was.
 */
sealed class JourneySaveResult {
    data class Saved(
        val routeId: Long,
        val name: String,
        val waypointCount: Int,
        val distanceMetres: Double,
        /** The last waypoint's name, so the user can tell it caught the right journey. */
        val destination: String,
    ) : JourneySaveResult()

    /** Nothing has been travelled since the last long stop, or since the service started. */
    data object NoJourney : JourneySaveResult()
}

/**
 * Turns the recorded journey into a saved Route.
 *
 * The naming of places along the way happened as the journey was recorded; what is left to do here
 * is name the route itself, which needs the destination geocoded and so can only happen now.
 */
class JourneySaver(
    private val routeDao: RouteDao,
    private val strings: LocalizedStrings?,
) {

    /**
     * @param events everything the recorder holds, from JourneyRecorder.snapshot()
     * @param nameFor names a point - the platform's reverse geocoder - or returns null
     * @param dateStamp a human-readable date for the fallback route name
     */
    suspend fun save(
        events: List<JourneyEvent>,
        nameFor: suspend (LngLatAlt) -> String?,
        dateStamp: String,
    ): JourneySaveResult {
        val journey = JourneySegmenter.lastJourney(events) ?: return JourneySaveResult.NoJourney

        val anchors = journey.filterIsInstance<JourneyEvent.Anchor>()
        val startName = nameFor(anchors.first().location)
        val endName = nameFor(anchors.last().location)

        val waypoints = JourneyToRoute.waypoints(journey, strings, startName, endName)
        if (waypoints.size < 2) return JourneySaveResult.NoJourney

        val routeName = routeName(endName, journey, dateStamp)
        val route = RouteEntity(
            name = routeName,
            // The start time, and not decoration: RouteEntity.equals is name-and-description only,
            // and insertRouteWithNewMarkers hands back the *existing* route when both match. Two
            // journeys to the same place on one day would otherwise silently become one route.
            description = "$JOURNEY_DESCRIPTION_PREFIX${journey.first().timestampMillis}",
        )

        val routeId = routeDao.insertRouteWithJourneyMarkers(route, waypoints)
        return JourneySaveResult.Saved(
            routeId = routeId,
            name = routeName,
            waypointCount = waypoints.size,
            distanceMetres = JourneySegmenter.travelledMetres(journey),
            destination = waypoints.last().name,
        )
    }

    /**
     * "Journey to Milngavie" where the destination could be named, otherwise the last place passed,
     * otherwise the date. Something recognisable matters more than something precise: this is what
     * the user will pick the route out of a list by.
     */
    private fun routeName(
        endName: String?,
        journey: List<JourneyEvent>,
        dateStamp: String,
    ): String {
        val destination = endName
            ?: journey.filterIsInstance<JourneyEvent.Landmark>().lastOrNull()?.name
            ?: dateStamp
        return strings?.get(StringKey.JourneyRouteName, destination) ?: "Journey to $destination"
    }
}

/**
 * Marks a route as having been recorded rather than built by hand. The timestamp of the journey's
 * first event follows it - see JourneySaver.save.
 */
const val JOURNEY_DESCRIPTION_PREFIX = "journey:"
