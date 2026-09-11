package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.database.local.model.MARKER_SOURCE_JOURNEY
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.createCheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyToRouteTest {

    private val degreesPerMetre = 1.0 / 111_320.0

    private fun at(northMetres: Double) = LngLatAlt(0.0, northMetres * degreesPerMetre)

    private fun anchor(northMetres: Double, atMillis: Long) =
        JourneyEvent.Anchor(at(northMetres), atMillis, speedMps = 1.4, inVehicle = false)

    private fun turn(northMetres: Double, atMillis: Long, angle: Double, from: String, to: String) =
        JourneyEvent.Turn(at(northMetres), atMillis, fromRoad = from, toRoad = to, signedAngleDegrees = angle)

    private fun landmark(northMetres: Double, atMillis: Long, name: String) =
        JourneyEvent.Landmark(at(northMetres), atMillis, name)

    /** A kilometre of walking with anchors every hundred metres. */
    private fun spine(metres: Int = 1000, step: Int = 100) =
        (0..metres step step).map { anchor(it.toDouble(), it * 1000L / step) }

    // --- the shape of the route ------------------------------------------------------------

    @Test
    fun aRouteAlwaysStartsWhereTheJourneyDidAndEndsWhereItFinished() {
        val waypoints = JourneyToRoute.waypoints(spine(), strings = null)

        assertEquals("Journey start", waypoints.first().name)
        assertEquals("Destination", waypoints.last().name)
        assertEquals(0.0, waypoints.first().latitude, 1e-9)
        assertEquals(at(1000.0).latitude, waypoints.last().latitude, 1e-9)
    }

    @Test
    fun theStartAndDestinationCanBeNamed() {
        val waypoints = JourneyToRoute.waypoints(
            spine(), strings = null, startName = "Home", endName = "Milngavie Station"
        )
        assertEquals("Home", waypoints.first().name)
        assertEquals("Milngavie Station", waypoints.last().name)
    }

    @Test
    fun everyWaypointIsMarkedAsComingFromAJourney() {
        val journey = spine() + turn(500.0, 5_000L, 90.0, "Beech Avenue", "Roselea Drive")
        val waypoints = JourneyToRoute.waypoints(journey, strings = null)

        assertTrue(waypoints.isNotEmpty())
        // This is what keeps them out of the user's markers and stops them being called out.
        assertTrue(waypoints.all { it.source == MARKER_SOURCE_JOURNEY })
    }

    @Test
    fun aJourneyWithNoPathAtAllProducesNoRoute() {
        assertTrue(JourneyToRoute.waypoints(emptyList(), strings = null).isEmpty())
        assertTrue(JourneyToRoute.waypoints(listOf(anchor(0.0, 0L)), strings = null).isEmpty())
    }

    // --- turn wording ------------------------------------------------------------------------

    @Test
    fun aTurnIsNamedAfterTheJunctionAndCarriesTheInstructionAlongside() {
        val journey = spine() + turn(500.0, 5_000L, -90.0, "Beech Avenue", "Roselea Drive")
        val turnWaypoint = JourneyToRoute.waypoints(journey, strings = null)
            .single { it.reverseDirection != null }

        // The junction is a place, so it reads the same whichever way the route is walked, and
        // says where you are rather than only what to do when you get there.
        assertEquals("Beech Avenue and Roselea Drive", turnWaypoint.name)
        assertEquals("Turn left", turnWaypoint.fullAddress)
    }

    @Test
    fun aTurnCarriesTheMirroredInstructionForPlayingTheRouteBackwards() {
        val journey = spine() + turn(500.0, 5_000L, -90.0, "Beech Avenue", "Roselea Drive")
        val turnWaypoint = JourneyToRoute.waypoints(journey, strings = null)
            .single { it.reverseDirection != null }

        // Only the instruction flips; the junction it names does not.
        assertEquals("Turn right", turnWaypoint.reverseDirection)
    }

    @Test
    fun theSharpnessOfATurnChangesTheWording() {
        assertEquals("Bear left", JourneyToRoute.turnInstruction(-40.0, null))
        assertEquals("Turn left", JourneyToRoute.turnInstruction(-90.0, null))
        assertEquals("Sharp left", JourneyToRoute.turnInstruction(-140.0, null))
        assertEquals("Bear right", JourneyToRoute.turnInstruction(40.0, null))
        assertEquals("Turn right", JourneyToRoute.turnInstruction(90.0, null))
        assertEquals("Sharp right", JourneyToRoute.turnInstruction(140.0, null))
    }

    @Test
    fun aJunctionIsNamedByWhicheverOfItsRoadsHasAName() {
        assertEquals(
            "Beech Avenue and Roselea Drive",
            JourneyToRoute.junctionName("Beech Avenue", "Roselea Drive", null),
        )
        assertEquals("Roselea Drive", JourneyToRoute.junctionName("", "Roselea Drive", null))
        assertEquals("Beech Avenue", JourneyToRoute.junctionName("Beech Avenue", "  ", null))
        // A sharp corner on one road shouldn't name it twice.
        assertEquals(
            "Alexander Grove",
            JourneyToRoute.junctionName("Alexander Grove", "Alexander Grove", null),
        )
        // Two unnamed paths meeting still have to be called something.
        assertEquals("Junction", JourneyToRoute.junctionName("", "", null))
    }

    @Test
    fun aLandmarkKeepsItsOwnNameAndHasNoReverseWording() {
        val journey = spine() + landmark(500.0, 5_000L, "Tesco")
        val waypoint = JourneyToRoute.waypoints(journey, strings = null).single { it.name == "Tesco" }
        assertNull(waypoint.reverseDirection)
    }

    // --- culling -----------------------------------------------------------------------------

    @Test
    fun waypointsCloserTogetherThanThePlayerCanUseAreDropped() {
        // Three landmarks within a few metres: the player advances at 12m and would rattle through
        // all of them.
        val journey = spine() +
            landmark(500.0, 5_000L, "Tesco") +
            landmark(505.0, 5_100L, "Boots") +
            landmark(510.0, 5_200L, "Greggs")

        val names = JourneyToRoute.waypoints(journey, strings = null).map { it.name }
        assertEquals(listOf("Journey start", "Tesco", "Destination"), names)
    }

    @Test
    fun aLandmarkSittingOnTopOfATurnIsDropped() {
        val journey = spine() +
            turn(500.0, 5_000L, 90.0, "Beech Avenue", "Roselea Drive") +
            landmark(510.0, 5_100L, "Tesco")

        val names = JourneyToRoute.waypoints(journey, strings = null).map { it.name }
        assertTrue(names.none { it == "Tesco" }, "got $names")
        assertTrue(names.any { it.contains("Roselea Drive") }, "got $names")
    }

    @Test
    fun aLandmarkWellClearOfATurnIsKept() {
        val journey = spine() +
            turn(500.0, 5_000L, 90.0, "Beech Avenue", "Roselea Drive") +
            landmark(800.0, 5_100L, "Tesco")

        val names = JourneyToRoute.waypoints(journey, strings = null).map { it.name }
        assertTrue(names.any { it == "Tesco" }, "got $names")
    }

    @Test
    fun alongAStraightStretchWaypointsAreSpacedOut() {
        // Someone walking straight on doesn't need to be told something every thirty metres - they
        // need a beacon far enough ahead to walk towards.
        val journey = spine() + (100..900 step 50).map {
            landmark(it.toDouble(), it * 10L + 1, "Place $it")
        }

        val kept = JourneyToRoute.waypoints(journey, strings = null)
            .filter { it.name.startsWith("Place ") }
        assertTrue(kept.size in 3..5, "kept ${kept.size}: ${kept.map { it.name }}")

        val ruler = at(0.0).createCheapRuler()
        for (i in 1 until kept.size) {
            val gap = ruler.distance(kept[i - 1].getLngLatAlt(), kept[i].getLngLatAlt())
            assertTrue(gap >= MIN_STRAIGHT_SPACING_METRES - 1.0, "gap of ${gap.toInt()}m")
        }
    }

    @Test
    fun aTurnIsMarkedHoweverSoonAfterTheLastWaypointItComes() {
        // The spacing rule is about not nagging on a straight stretch. A turn has to be marked
        // where it is, or the instruction arrives somewhere the user has already walked past.
        val journey = spine() +
            landmark(500.0, 5_000L, "Tesco") +
            turn(560.0, 5_600L, -90.0, "Beech Avenue", "Roselea Drive")

        val names = JourneyToRoute.waypoints(journey, strings = null).map { it.name }
        assertTrue(names.any { it == "Tesco" }, "got $names")
        assertTrue(names.any { it == "Beech Avenue and Roselea Drive" }, "got $names")
    }

    @Test
    fun aVeryBusyJourneyIsCutDownToSomethingFollowable() {
        // A landmark every 40m for 10km - far more than anyone can be read out.
        val anchors = (0..10000 step 100).map { anchor(it.toDouble(), it * 10L) }
        val landmarks = (0..10000 step 40).map { landmark(it.toDouble(), it * 10L + 1, "Place $it") }
        val journey = (anchors + landmarks).sortedBy { it.timestampMillis }

        val waypoints = JourneyToRoute.waypoints(journey, strings = null)
        assertTrue(waypoints.size <= MAX_WAYPOINTS, "got ${waypoints.size}")
        assertEquals("Journey start", waypoints.first().name)
        assertEquals("Destination", waypoints.last().name)
    }

    @Test
    fun everyTurnSurvivesTheCap() {
        // Far more waypoints than the cap allows, so something has to go. Turns carry the
        // instructions, so what goes is landmarks - every one of the twenty turns has to come
        // through. They are offset from the endpoints so none of them coincides with the start or
        // the destination.
        val anchors = (0..10000 step 100).map { anchor(it.toDouble(), it * 10L) }
        val landmarks = (0..10000 step 60).map { landmark(it.toDouble() + 10, it * 10L + 1, "Place $it") }
        val turns = (250..9750 step 500).map {
            turn(it.toDouble(), it * 10L + 2, 90.0, "From $it", "To $it")
        }
        val journey = (anchors + landmarks + turns).sortedBy { it.timestampMillis }

        val waypoints = JourneyToRoute.waypoints(journey, strings = null)
        assertTrue(waypoints.size <= MAX_WAYPOINTS, "got ${waypoints.size}")
        assertEquals(turns.size, waypoints.count { it.reverseDirection != null })
    }

    @Test
    fun noTwoWaypointsShareALocation() {
        // Room reuses a marker at exactly these coordinates and RouteMarkerCrossRef is keyed on
        // (route, marker), so a duplicate would silently swallow a waypoint and its ordering.
        val journey = spine() +
            landmark(500.0, 5_000L, "Tesco") +
            landmark(500.0, 5_100L, "Tesco again")

        val waypoints = JourneyToRoute.waypoints(journey, strings = null)
        val locations = waypoints.map { it.longitude to it.latitude }
        assertEquals(locations.size, locations.toSet().size, "duplicate location in $locations")
    }

    @Test
    fun waypointsStayInTheOrderTheyWereTravelled() {
        val journey = spine() +
            landmark(200.0, 2_000L, "First") +
            landmark(600.0, 6_000L, "Second") +
            landmark(900.0, 9_000L, "Third")

        val waypoints = JourneyToRoute.waypoints(journey, strings = null)
        val ruler = at(0.0).createCheapRuler()
        val distances = waypoints.map { ruler.distance(at(0.0), it.getLngLatAlt()) }
        assertEquals(distances.sorted(), distances, "waypoints out of travel order")

        assertNotNull(waypoints.singleOrNull { it.name == "Second" })
    }
}
