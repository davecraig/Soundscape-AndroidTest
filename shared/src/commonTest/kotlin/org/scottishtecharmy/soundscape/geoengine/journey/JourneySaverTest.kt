package org.scottishtecharmy.soundscape.geoengine.journey

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.scottishtecharmy.soundscape.database.local.dao.FakeRouteDao
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JourneySaverTest {

    private val degreesPerMetre = 1.0 / 111_320.0

    private fun at(northMetres: Double) = LngLatAlt(0.0, northMetres * degreesPerMetre)

    private fun anchor(northMetres: Double, atMillis: Long) =
        JourneyEvent.Anchor(at(northMetres), atMillis, speedMps = 1.4, inVehicle = false)

    /** A kilometre walked, with a turn and a shop along the way. */
    private fun aJourney(): List<JourneyEvent> =
        (0..1000 step 100).map { anchor(it.toDouble(), it * 10L) } +
            JourneyEvent.Turn(at(400.0), 4_001L, "Beech Avenue", "Roselea Drive", -90.0) +
            JourneyEvent.Landmark(at(700.0), 7_001L, "Tesco")

    private fun saver(dao: FakeRouteDao) = JourneySaver(dao, strings = null)

    private suspend fun JourneySaver.saveNamed(
        events: List<JourneyEvent>,
        names: Map<Double, String> = emptyMap(),
    ) = save(
        events,
        nameFor = { point -> names[point.latitude] },
        dateStamp = "11 September",
    )

    @Test
    fun savesTheJourneyAsARouteWithItsWaypointsInOrder() = runTest {
        val dao = FakeRouteDao()
        val result = saver(dao).saveNamed(aJourney())

        val saved = assertIs<JourneySaveResult.Saved>(result)
        assertEquals(4, saved.waypointCount)   // start, turn, Tesco, destination
        assertTrue(saved.distanceMetres > 900.0, "distance was ${saved.distanceMetres}")

        val route = dao.getRouteWithMarkers(saved.routeId)!!
        assertEquals(
            listOf("Journey start", "Beech Avenue and Roselea Drive", "Tesco", "Destination"),
            route.markers.map { it.name },
        )
    }

    @Test
    fun namesTheRouteAfterWhereItEnded() = runTest {
        val dao = FakeRouteDao()
        val result = saver(dao).saveNamed(
            aJourney(),
            names = mapOf(at(1000.0).latitude to "Milngavie"),
        )
        assertEquals("Journey to Milngavie", assertIs<JourneySaveResult.Saved>(result).name)
    }

    @Test
    fun fallsBackToTheDateWhenNothingCanBeNamed() = runTest {
        val result = saver(FakeRouteDao()).saveNamed(
            (0..1000 step 100).map { anchor(it.toDouble(), it * 10L) }
        )
        assertEquals("Journey to 11 September", assertIs<JourneySaveResult.Saved>(result).name)
    }

    @Test
    fun saysSoWhenThereIsNoJourneyToSave() = runTest {
        assertIs<JourneySaveResult.NoJourney>(saver(FakeRouteDao()).saveNamed(emptyList()))

        // A hundred metres to the corner shop is travel, but it is not a route.
        val tooShort = (0..100 step 50).map { anchor(it.toDouble(), it * 10L) }
        assertIs<JourneySaveResult.NoJourney>(saver(FakeRouteDao()).saveNamed(tooShort))
    }

    // --- the requirement that shaped the design -------------------------------------------

    @Test
    fun theWaypointsNeverShowUpAmongTheUsersOwnMarkers() = runTest {
        val dao = FakeRouteDao()
        dao.insertMarker(MarkerEntity(name = "Home", longitude = 0.0, latitude = 0.5))

        saver(dao).saveNamed(aJourney())

        // The user still has exactly the one place they saved. The journey's waypoints are route
        // furniture: listing them would bury the real markers, and - because the marker tree feeds
        // the POI callouts with a 50m trigger range - walking past one off-route would announce it.
        assertEquals(listOf("Home"), dao.getUserMarkers().map { it.name })
        assertEquals(listOf("Home"), dao.getUserMarkersFlow().first().map { it.name })

        // They are in the database, though - the route needs them.
        assertTrue(dao.getAllMarkers().size > 1)
    }

    @Test
    fun twoJourneysThroughOneJunctionDoNotShareItsWaypoint() = runTest {
        val dao = FakeRouteDao()

        // Same junction, turned different ways. Reusing a marker by location - which is what
        // hand-built routes do - would make one of these recite the other's instruction.
        val leftThere = aJourney()
        val rightThere = (0..1000 step 100).map { anchor(it.toDouble(), 100_000L + it * 10L) } +
            JourneyEvent.Turn(at(400.0), 104_001L, "Beech Avenue", "Milngavie Road", 90.0)

        val first = assertIs<JourneySaveResult.Saved>(saver(dao).saveNamed(leftThere))
        val second = assertIs<JourneySaveResult.Saved>(saver(dao).saveNamed(rightThere))

        val firstNames = dao.getRouteWithMarkers(first.routeId)!!.markers.map { it.name }
        val secondNames = dao.getRouteWithMarkers(second.routeId)!!.markers.map { it.name }

        assertTrue(firstNames.any { it == "Beech Avenue and Roselea Drive" }, "got $firstNames")
        assertTrue(secondNames.any { it == "Beech Avenue and Milngavie Road" }, "got $secondNames")
    }

    @Test
    fun twoJourneysSavedTheSameDayStayTwoRoutes() = runTest {
        val dao = FakeRouteDao()
        // Identical in every way a route is compared by - RouteEntity.equals is name and
        // description only - except when they happened.
        val first = assertIs<JourneySaveResult.Saved>(saver(dao).saveNamed(aJourney()))
        val later = aJourney().map {
            when (it) {
                is JourneyEvent.Anchor -> it.copy(timestampMillis = it.timestampMillis + 3_600_000L)
                is JourneyEvent.Turn -> it.copy(timestampMillis = it.timestampMillis + 3_600_000L)
                is JourneyEvent.Landmark -> it.copy(timestampMillis = it.timestampMillis + 3_600_000L)
            }
        }
        val second = assertIs<JourneySaveResult.Saved>(saver(dao).saveNamed(later))

        assertTrue(first.routeId != second.routeId, "both saved as route ${first.routeId}")
        assertEquals(2, dao.getAllRoutes().size)
    }

    @Test
    fun aTurnWaypointCarriesItsReverseWordingIntoTheDatabase() = runTest {
        val dao = FakeRouteDao()
        val saved = assertIs<JourneySaveResult.Saved>(saver(dao).saveNamed(aJourney()))

        val turn = dao.getRouteWithMarkers(saved.routeId)!!.markers
            .single { it.reverseDirection != null }
        assertEquals("Beech Avenue and Roselea Drive", turn.name)
        assertEquals("Turn left", turn.fullAddress)
        assertEquals("Turn right", turn.reverseDirection)
    }
}
