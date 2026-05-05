package org.scottishtecharmy.soundscape.migration

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.scottishtecharmy.soundscape.database.local.dao.RouteDao
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteMarkerCrossRef
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Exercises [importLegacyPayload] against a hand-rolled in-memory
 * [RouteDao] so the test stays in commonTest with zero platform deps.
 * The fake implements the four methods the importer touches and the few
 * accessors the assertions need; everything else throws so unexpected
 * usage surfaces loudly.
 */
class LegacyMigrationTest {

    private lateinit var dao: FakeRouteDao

    @BeforeTest
    fun setUp() {
        dao = FakeRouteDao()
    }

    @Test
    fun importsAllSavedMarkers() = runTest {
        val json = """
            {
              "markers": [
                {"legacyId": "a", "name": "Pub", "latitude": 55.95, "longitude": -3.19,
                 "fullAddress": "1 Royal Mile"},
                {"legacyId": "b", "name": "Castle", "latitude": 55.948, "longitude": -3.2,
                 "fullAddress": ""}
              ],
              "routes": []
            }
        """.trimIndent()

        val count = importLegacyPayload(json, dao)

        assertEquals(2, count)
        val markers = dao.getAllMarkers().sortedBy { it.name }
        assertEquals(listOf("Castle", "Pub"), markers.map { it.name })
        val pub = markers.first { it.name == "Pub" }
        assertEquals(55.95, pub.latitude)
        assertEquals(-3.19, pub.longitude)
        assertEquals("1 Royal Mile", pub.fullAddress)
    }

    @Test
    fun importsRoutesAndPreservesWaypointOrder() = runTest {
        val json = """
            {
              "markers": [
                {"legacyId": "m1", "name": "M1", "latitude": 0.0, "longitude": 0.0},
                {"legacyId": "m2", "name": "M2", "latitude": 0.0, "longitude": 0.0},
                {"legacyId": "m3", "name": "M3", "latitude": 0.0, "longitude": 0.0}
              ],
              "routes": [
                {"name": "Loop", "description": "scenic",
                 "waypointLegacyIds": ["m3", "m1", "m2"]}
              ]
            }
        """.trimIndent()

        val count = importLegacyPayload(json, dao)

        // 3 markers + 1 route
        assertEquals(4, count)
        val routes = dao.allRoutesWithMarkers()
        assertEquals(1, routes.size)
        val route = routes.single()
        assertEquals("Loop", route.route.name)
        assertEquals("scenic", route.route.description)
        assertEquals(listOf("M3", "M1", "M2"), route.markers.map { it.name })
    }

    @Test
    fun routesShareMarkersAndKeepIndependentWaypointOrders() = runTest {
        val json = """
            {
              "markers": [
                {"legacyId": "m1", "name": "M1", "latitude": 0.0, "longitude": 0.0},
                {"legacyId": "m2", "name": "M2", "latitude": 0.0, "longitude": 0.0}
              ],
              "routes": [
                {"name": "A", "description": "",
                 "waypointLegacyIds": ["m1", "m2"]},
                {"name": "B", "description": "",
                 "waypointLegacyIds": ["m2", "m1"]}
              ]
            }
        """.trimIndent()

        importLegacyPayload(json, dao)

        // Both markers are inserted exactly once and shared by both routes,
        // each in its own waypoint order.
        assertEquals(2, dao.getAllMarkers().size)
        val routes = dao.allRoutesWithMarkers().sortedBy { it.route.name }
        assertEquals(listOf("M1", "M2"), routes[0].markers.map { it.name })
        assertEquals(listOf("M2", "M1"), routes[1].markers.map { it.name })
    }

    @Test
    fun routeWithUnresolvableWaypointIsSkippedButOthersStillImport() = runTest {
        val json = """
            {
              "markers": [
                {"legacyId": "m1", "name": "M1", "latitude": 0.0, "longitude": 0.0}
              ],
              "routes": [
                {"name": "Broken", "description": "",
                 "waypointLegacyIds": ["m1", "ghost"]},
                {"name": "Fine", "description": "",
                 "waypointLegacyIds": ["m1"]}
              ]
            }
        """.trimIndent()

        val count = importLegacyPayload(json, dao)

        // 1 marker + 1 successful route. The "Broken" route is dropped.
        assertEquals(2, count)
        val routes = dao.allRoutesWithMarkers()
        assertEquals(1, routes.size)
        assertEquals("Fine", routes.single().route.name)
    }

    @Test
    fun emptyPayloadIsAcceptedAndImportsNothing() = runTest {
        val count = importLegacyPayload("""{"markers":[], "routes":[]}""", dao)
        assertEquals(0, count)
        assertEquals(0, dao.getAllMarkers().size)
        assertEquals(0, dao.routesById.size)
    }

    @Test
    fun missingMarkersOrRoutesArrayDefaultsToEmpty() = runTest {
        val count = importLegacyPayload("""{}""", dao)
        assertEquals(0, count)
    }

    @Test
    fun malformedJsonReturnsNegativeOne() = runTest {
        val count = importLegacyPayload("not json", dao)
        assertEquals(-1, count)
    }

    @Test
    fun markersMissingRequiredFieldsAreSkippedSilently() = runTest {
        val json = """
            {
              "markers": [
                {"legacyId": "m1", "name": "ok", "latitude": 0.0, "longitude": 0.0},
                {"legacyId": "m2", "name": "no lat", "longitude": 0.0},
                {"legacyId": "m3", "latitude": 0.0, "longitude": 0.0}
              ],
              "routes": []
            }
        """.trimIndent()

        val count = importLegacyPayload(json, dao)

        // Only the well-formed marker is imported; partial rows are dropped.
        assertEquals(1, count)
        val all = dao.getAllMarkers()
        assertEquals(1, all.size)
        assertNotNull(all.firstOrNull { it.name == "ok" })
    }

    @Test
    fun routeWithEmptyWaypointListIsSkipped() = runTest {
        val json = """
            {
              "markers": [
                {"legacyId": "m1", "name": "M1", "latitude": 0.0, "longitude": 0.0}
              ],
              "routes": [
                {"name": "Empty", "description": "", "waypointLegacyIds": []}
              ]
            }
        """.trimIndent()

        val count = importLegacyPayload(json, dao)

        assertEquals(1, count) // marker only
        assertEquals(0, dao.routesById.size)
    }
}

/**
 * Minimal in-memory [RouteDao] for the import tests. Only the methods the
 * importer (and the test assertions) actually use are implemented; the
 * rest throw to flag unexpected usage early.
 */
private class FakeRouteDao : RouteDao {

    private var nextMarkerId: Long = 1
    private var nextRouteId: Long = 1
    private val markersById = mutableMapOf<Long, MarkerEntity>()
    val routesById = mutableMapOf<Long, RouteEntity>()
    private val crossRefs = mutableListOf<RouteMarkerCrossRef>()

    override suspend fun insertMarker(marker: MarkerEntity): Long {
        val id = nextMarkerId++
        // MarkerEntity is immutable on its primary key, so re-create with
        // the assigned id rather than mutating in place.
        markersById[id] = MarkerEntity(
            markerId = id,
            name = marker.name,
            longitude = marker.longitude,
            latitude = marker.latitude,
            fullAddress = marker.fullAddress,
        )
        return id
    }

    override suspend fun insertRoute(route: RouteEntity): Long {
        val id = nextRouteId++
        routesById[id] = RouteEntity(
            routeId = id,
            name = route.name,
            description = route.description,
        )
        return id
    }

    override suspend fun addMarkerToRoute(crossRef: RouteMarkerCrossRef) {
        crossRefs.add(crossRef)
    }

    override suspend fun getAllMarkers(): List<MarkerEntity> = markersById.values.toList()

    /** Convenience for assertions; not part of the production DAO surface. */
    fun allRoutesWithMarkers(): List<RouteWithMarkers> = routesById.values.map { route ->
        val orderedMarkers = crossRefs
            .filter { it.routeId == route.routeId }
            .sortedBy { it.markerOrder ?: 0 }
            .mapNotNull { markersById[it.markerId] }
        RouteWithMarkers(route, orderedMarkers)
    }

    // --- unused ---------------------------------------------------------
    private fun nope(): Nothing = error("FakeRouteDao: not implemented for tests")

    override suspend fun updateMarker(marker: MarkerEntity) = nope()
    override suspend fun getMarkerById(markerId: Long): MarkerEntity? = nope()
    override suspend fun getMarkerByLocation(longitude: Double, latitude: Double): MarkerEntity? = nope()
    override fun getAllMarkersFlow(): Flow<List<MarkerEntity>> = flowOf(emptyList())
    override suspend fun removeMarkerFromRoute(routeId: Long, markerId: Long) = nope()
    override suspend fun removeMarkersForRoute(routeId: Long) = nope()
    override suspend fun getAllRoutes(): List<RouteEntity> = routesById.values.toList()
    override suspend fun getRouteById(routeId: Long): RouteEntity? = routesById[routeId]
    override suspend fun getMarkerCrossReference(routeId: Long): List<RouteMarkerCrossRef> = nope()
    override fun getAllRoutesFlow(): Flow<List<RouteEntity>> = flowOf(emptyList())
    override suspend fun removeRoute(routeId: Long) = nope()
    override suspend fun removeMarker(markerId: Long) = nope()
    override suspend fun deleteAllRouteMarkerCrossRefs() = nope()
    override suspend fun deleteAllMarkers() = nope()
    override suspend fun deleteAllRoutes() = nope()
}
