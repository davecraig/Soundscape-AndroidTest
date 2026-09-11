package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.database.local.model.MARKER_SOURCE_JOURNEY
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.createCheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import org.scottishtecharmy.soundscape.i18n.StringKey
import kotlin.math.abs

/**
 * RoutePlayer advances to the next waypoint within 12m, so two waypoints closer than this are
 * announced one after the other and immediately superseded: a burst of speech and no guidance.
 * This is the floor for a turn, which always earns a waypoint however soon it comes.
 */
const val MIN_WAYPOINT_SPACING_METRES = 25.0

/**
 * How far apart waypoints are along a stretch where the direction of travel doesn't change.
 *
 * Someone walking straight on doesn't need to be told anything every thirty metres - they need a
 * beacon far enough ahead to walk towards. Replaying a walk up Buchanan Street gave nine shop
 * fronts before the first turn; the same walk with this rule gives a handful of real landmarks.
 */
const val MIN_STRAIGHT_SPACING_METRES = 200.0

/** A landmark this close to a turn adds nothing the turn does not already say, and crowds it. */
const val LANDMARK_TURN_CLEARANCE_METRES = 30.0

/**
 * RoutePlayer says "N of M" at every waypoint. A route with two hundred of them is not guidance,
 * it is a recital - and the user is handed this without having reviewed it first.
 */
const val MAX_WAYPOINTS = 50

/** Below this a turn is a bear, above [SHARP_TURN_DEGREES] it is sharp, in between it is a turn. */
const val BEAR_TURN_DEGREES = 60.0
const val SHARP_TURN_DEGREES = 120.0

/**
 * Turns a recorded journey into the ordered list of markers that make up a Route.
 *
 * Pure: no database, no grid, no service. Everything it needs was resolved while the journey was
 * being recorded, because by now the tile grid has moved on and the Ways the road names came from
 * are gone.
 */
object JourneyToRoute {

    /** A candidate waypoint, still paired with what it came from so culling can tell them apart. */
    private class Candidate(val marker: MarkerEntity, val isTurn: Boolean) {
        val location: LngLatAlt get() = marker.getLngLatAlt()
    }

    /**
     * @param journey the events of one journey, oldest first, from [JourneySegmenter.lastJourney]
     * @param startName what to call the place the journey set off from, or null for a plain default
     * @param endName what to call where it finished, or null for a plain default
     */
    fun waypoints(
        journey: List<JourneyEvent>,
        strings: LocalizedStrings?,
        startName: String? = null,
        endName: String? = null,
    ): List<MarkerEntity> {
        val anchors = journey.filterIsInstance<JourneyEvent.Anchor>()
        if (anchors.size < 2) return emptyList()

        val ruler = anchors.first().location.createCheapRuler()
        val turnLocations = journey.filterIsInstance<JourneyEvent.Turn>().map { it.location }
        val namesUsed = mutableSetOf<String>()

        val middle = journey.mapNotNull { event ->
            when (event) {
                is JourneyEvent.Anchor -> null

                is JourneyEvent.Turn -> Candidate(
                    marker(
                        // The junction, not the instruction: a place reads the same whichever way
                        // the route is walked, and it says where you are rather than only what to
                        // do when you get there.
                        name = junctionName(event.fromRoad, event.toRoad, strings),
                        at = event.location,
                        direction = turnInstruction(event.signedAngleDegrees, strings),
                        // Mirrored now, while the angle is still to hand: walked the other way,
                        // this junction is a turn the other way.
                        reverseDirection = turnInstruction(-event.signedAngleDegrees, strings),
                    ),
                    isTurn = true,
                )

                is JourneyEvent.Landmark -> {
                    // A turn already says everything about where it is. A shop announced on the
                    // way into it is just another thing said in the same few metres.
                    val crowdsATurn = turnLocations.any {
                        ruler.distance(it, event.location) < LANDMARK_TURN_CLEARANCE_METRES
                    }
                    // One place, announced again and again as it is walked past and around. A
                    // replayed route of "Tesco, Tesco, Tesco Main entrance, Tesco" says nothing
                    // about where anyone is - see MvtTileTest.testJourneys, where exactly that
                    // came out of a real walk. The first time is where you passed it.
                    val alreadyNamed = !namesUsed.add(event.name)
                    if (crowdsATurn || alreadyNamed) null
                    else Candidate(marker(name = event.name, at = event.location), isTurn = false)
                }
            }
        }

        // The start and the destination are the two points the user most wants, and neither is
        // likely to have been a turn, so they are added rather than found.
        val start = Candidate(
            marker(startName ?: strings.orFallback(StringKey.JourneyStart, "Journey start"),
                anchors.first().location),
            isTurn = false,
        )
        val end = Candidate(
            marker(endName ?: strings.orFallback(StringKey.JourneyEnd, "Destination"),
                anchors.last().location),
            isTurn = false,
        )

        return cap(spaceOut(listOf(start) + middle + listOf(end), ruler)).map { it.marker }
    }

    /**
     * Drop anything too close to the waypoint kept before it. The first and last survive whatever
     * happens - they are the start and the destination - and the destination wins over anything
     * sitting almost on top of it.
     */
    private fun spaceOut(waypoints: List<Candidate>, ruler: Ruler): List<Candidate> {
        if (waypoints.size <= 2) return waypoints

        val kept = mutableListOf(waypoints.first())
        for (candidate in waypoints.subList(1, waypoints.size - 1)) {
            // A turn has to be marked wherever it falls; anything else is only worth a beacon once
            // the user has walked far enough to want the next one.
            val required =
                if (candidate.isTurn) MIN_WAYPOINT_SPACING_METRES else MIN_STRAIGHT_SPACING_METRES
            if (ruler.distance(kept.last().location, candidate.location) >= required) {
                kept.add(candidate)
            }
        }

        val last = waypoints.last()
        while (kept.size > 1 &&
            ruler.distance(kept.last().location, last.location) < MIN_WAYPOINT_SPACING_METRES
        ) {
            // The destination wins over anything sitting almost on top of it.
            kept.removeAt(kept.size - 1)
        }
        kept.add(last)
        return kept
    }

    /**
     * Shed the least useful waypoints until the route is a length a person can follow. Landmarks go
     * first - a turn carries an instruction, a landmark only says where you were - and from the
     * middle outwards, since the waypoints nearest either end are the ones a user checks themselves
     * against.
     */
    private fun cap(waypoints: List<Candidate>): List<Candidate> {
        if (waypoints.size <= MAX_WAYPOINTS) return waypoints

        val middleIndices = waypoints.indices.drop(1).dropLast(1)
        val byUsefulness = middleIndices.sortedWith(
            compareBy({ it.isTurnAt(waypoints) }, { abs(it - waypoints.size / 2) })
        )
        val dropping = byUsefulness.take(waypoints.size - MAX_WAYPOINTS).toSet()
        return waypoints.filterIndexed { index, _ -> index !in dropping }
    }

    private fun Int.isTurnAt(waypoints: List<Candidate>) = waypoints[this].isTurn

    private fun marker(
        name: String,
        at: LngLatAlt,
        direction: String = "",
        reverseDirection: String? = null,
    ) = MarkerEntity(
        name = name,
        longitude = at.longitude,
        latitude = at.latitude,
        // The instruction rides in the annotation, which is the marker's own free-text field -
        // shown in the route editor and exported to GPX - so RoutePlayer can speak the junction
        // and what to do at it without either needing a column of its own.
        fullAddress = direction,
        source = MARKER_SOURCE_JOURNEY,
        reverseDirection = reverseDirection,
    )

    /**
     * What to call the junction a turn was taken at: the two roads that meet there, or whichever
     * one of them has a name worth saying.
     */
    fun junctionName(fromRoad: String, toRoad: String, strings: LocalizedStrings?): String = when {
        // A sharp corner on one road is still a turn worth marking, but "Alexander Grove and
        // Alexander Grove" names it twice.
        fromRoad.isNotBlank() && fromRoad == toRoad -> fromRoad

        fromRoad.isNotBlank() && toRoad.isNotBlank() ->
            strings?.get(StringKey.JourneyJunctionOf, fromRoad, toRoad) ?: "$fromRoad and $toRoad"

        fromRoad.isNotBlank() -> fromRoad
        toRoad.isNotBlank() -> toRoad
        else -> strings.orFallback(StringKey.JourneyJunction, "Junction")
    }

    /**
     * The instruction for a turn - "Turn left", "Bear right", "Sharp left".
     *
     * No road in it: the waypoint is named after the junction, so naming a road here would say the
     * same thing twice, and the instruction then reads correctly whichever way the route is walked
     * once the sign is flipped.
     */
    fun turnInstruction(signedAngleDegrees: Double, strings: LocalizedStrings?): String {
        val left = signedAngleDegrees < 0.0
        val magnitude = abs(signedAngleDegrees)

        val (key, fallback) = when {
            magnitude < BEAR_TURN_DEGREES ->
                if (left) StringKey.JourneyBearLeft to "Bear left"
                else StringKey.JourneyBearRight to "Bear right"

            magnitude < SHARP_TURN_DEGREES ->
                if (left) StringKey.JourneyTurnLeft to "Turn left"
                else StringKey.JourneyTurnRight to "Turn right"

            else ->
                if (left) StringKey.JourneySharpLeft to "Sharp left"
                else StringKey.JourneySharpRight to "Sharp right"
        }
        return strings.orFallback(key, fallback)
    }

    private fun LocalizedStrings?.orFallback(key: StringKey, fallback: String) =
        this?.get(key) ?: fallback
}
