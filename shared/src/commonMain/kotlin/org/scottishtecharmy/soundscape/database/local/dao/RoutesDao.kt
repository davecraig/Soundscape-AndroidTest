package org.scottishtecharmy.soundscape.database.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteMarkerCrossRef
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers


@Dao
interface RouteDao {

    // --- Marker Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: MarkerEntity): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateMarker(marker: MarkerEntity)

    @Query("SELECT * FROM markers WHERE marker_id = :markerId")
    suspend fun getMarkerById(markerId: Long): MarkerEntity?

    @Query("SELECT * FROM markers WHERE latitude = :latitude AND longitude = :longitude")
    suspend fun getMarkerByLocation(longitude: Double, latitude: Double): MarkerEntity?

    /**
     * Every marker, including the waypoints derived from recorded journeys.
     *
     * Almost nothing wants this - see [getUserMarkers]. It is here for wiping the database and for
     * asking whether there is anything in it at all.
     */
    @Query("SELECT * FROM markers")
    suspend fun getAllMarkers(): List<MarkerEntity>

    /** As [getAllMarkers]; see [getUserMarkersFlow] for what almost everything should use instead. */
    @Query("SELECT * FROM markers")
    fun getAllMarkersFlow(): Flow<List<MarkerEntity>>

    /**
     * The markers the user saved themselves, which is what "my markers" means everywhere it is
     * shown or spoken.
     *
     * A route recorded from a journey leaves a waypoint at every turn along it. Those are route
     * furniture: they are not places the user chose, and they must not be announced when walking
     * past them off-route, nor listed among the handful of places they did choose. So the marker
     * tree, the Markers screen and the waypoint picker all read markers through here, and only
     * a marker with no [MarkerEntity.source] counts.
     */
    @Query("SELECT * FROM markers WHERE source IS NULL")
    suspend fun getUserMarkers(): List<MarkerEntity>

    /** The flow behind [getUserMarkers]. */
    @Query("SELECT * FROM markers WHERE source IS NULL")
    fun getUserMarkersFlow(): Flow<List<MarkerEntity>>

    // --- Route Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    // --- RouteMarkerCrossRef Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMarkerToRoute(crossRef: RouteMarkerCrossRef)

    @Query("DELETE FROM route_marker_cross_ref WHERE route_id = :routeId AND marker_id = :markerId")
    suspend fun removeMarkerFromRoute(routeId: Long, markerId: Long)

    @Query("DELETE FROM route_marker_cross_ref WHERE route_id = :routeId")
    suspend fun removeMarkersForRoute(routeId: Long)

    @Query("SELECT * FROM routes")
    suspend fun getAllRoutes(): List<RouteEntity>

    // --- Querying Routes With Their Markers ---
    @Query("SELECT * FROM routes WHERE route_id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?

    @Query("SELECT * FROM route_marker_cross_ref WHERE route_id = :routeId")
    suspend fun getMarkerCrossReference(routeId: Long): List<RouteMarkerCrossRef>

    @Transaction
    suspend fun getRouteWithMarkers(routeId: Long): RouteWithMarkers? {
        val route = getRouteById(routeId)
        val crossReferences = getMarkerCrossReference(routeId)

        if (route == null) return null

        val markers = mutableListOf<MarkerEntity>()
        for (crossRef in crossReferences.sortedBy { it.markerOrder }) {
            val id = getMarkerById(crossRef.markerId)
            if (id != null)
                markers.add(id)
        }
        return RouteWithMarkers(route, markers)
    }

    @Query("SELECT * FROM routes")
    fun getAllRoutesFlow(): Flow<List<RouteEntity>>

    fun getAllRoutesWithMarkersFlow(): Flow<List<RouteWithMarkers>> {
        return getAllRoutesFlow().map { routes ->
            routes.mapNotNull { route ->
                getRouteWithMarkers(route.routeId)
            }
        }
    }

    @Transaction
    suspend fun getAllRoutesWithMarkers(): List<RouteWithMarkers> {
        val routes = getAllRoutes()
        val result = mutableListOf<RouteWithMarkers>()

        for (route in routes) {
            val routeWithMarkers = getRouteWithMarkers(route.routeId)
            if (routeWithMarkers != null) {
                result.add(routeWithMarkers)
            }
        }
        return result
    }

    @Query("DELETE FROM routes WHERE route_id = :routeId")
    suspend fun removeRoute(routeId: Long)

    @Query("DELETE FROM markers WHERE marker_id = :markerId")
    suspend fun removeMarker(markerId: Long)

    @Query("DELETE FROM route_marker_cross_ref")
    suspend fun deleteAllRouteMarkerCrossRefs()

    @Query("DELETE FROM markers")
    suspend fun deleteAllMarkers()

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Transaction
    suspend fun clearAll() {
        deleteAllRouteMarkerCrossRefs()
        deleteAllRoutes()
        deleteAllMarkers()
    }

    @Transaction
    suspend fun insertRouteWithExistingMarkers(
        route: RouteEntity,
        markers: List<MarkerEntity>
    ): Long {
        val routeId = insertRoute(route)
        markers.forEachIndexed { index, marker ->
            addMarkerToRoute(RouteMarkerCrossRef(routeId, marker.markerId, index))
        }
        return routeId
    }

    @Transaction
    suspend fun insertRouteWithNewMarkers(route: RouteEntity, markers: List<MarkerEntity>): Long {

        var duplicateId = 0L
        val existingRoutes = getAllRoutesWithMarkers()
        for (existingRoute in existingRoutes) {
            if (route == existingRoute.route) {
                if (markers == existingRoute.markers) {
                    duplicateId = existingRoute.route.routeId
                    break
                }
            }
        }
        if (duplicateId == 0L) {
            val routeId = insertRoute(route)
            markers.forEachIndexed { index, marker ->
                val existingMarker = getMarkerByLocation(marker.longitude, marker.latitude)
                    // A waypoint recorded from a journey is never merged with one of the user's
                    // own places, in either direction. It would inherit the wrong name - losing
                    // its turn instruction, or gaining one - and a silent waypoint merged into a
                    // saved marker would start announcing itself. See MarkerEntity.source.
                    ?.takeIf { it.source == marker.source }
                val markerId = existingMarker?.markerId ?: insertMarker(marker)
                addMarkerToRoute(RouteMarkerCrossRef(routeId, markerId, index))
            }
            return routeId
        } else {
            return duplicateId
        }
    }

    /**
     * Insert a route recorded from a journey, always creating its own markers.
     *
     * Deliberately not [insertRouteWithNewMarkers]: that reuses any marker already at exactly the
     * same coordinates, which is right for hand-built routes sharing the user's saved places and
     * wrong here twice over. A journey waypoint would inherit the name of whatever the user had
     * saved at that junction, losing its turn instruction; and two journeys turning *different*
     * ways at one junction would share a marker, so one of them would recite the other's turn.
     */
    @Transaction
    suspend fun insertRouteWithJourneyMarkers(
        route: RouteEntity,
        markers: List<MarkerEntity>
    ): Long {
        val routeId = insertRoute(route)
        markers.forEachIndexed { index, marker ->
            addMarkerToRoute(RouteMarkerCrossRef(routeId, insertMarker(marker), index))
        }
        return routeId
    }
}
