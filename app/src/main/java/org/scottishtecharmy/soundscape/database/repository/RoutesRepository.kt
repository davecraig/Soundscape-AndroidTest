package org.scottishtecharmy.soundscape.database.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.database.local.dao.RoutesDao
import org.scottishtecharmy.soundscape.database.local.model.Location
import org.scottishtecharmy.soundscape.database.local.model.RouteData
import org.scottishtecharmy.soundscape.database.local.model.MarkerData

class RoutesRepository(private val routesDao: RoutesDao) {

    suspend fun insertRoute(route: RouteData) = withContext(Dispatchers.IO) {
        routesDao.insertRoute(route)
    }

    suspend fun insertMarker(waypoint: MarkerData) = withContext(Dispatchers.IO) {
        routesDao.insertWaypoint(waypoint)
    }

    suspend fun getRoute(name: String) = withContext(Dispatchers.IO){
        routesDao.getRoute(name)
    }

    suspend fun getRoutes() = withContext(Dispatchers.IO){
        routesDao.getRoutes()
    }

    suspend fun deleteRoute(name: String) = withContext(Dispatchers.IO) {
        routesDao.deleteRoute(name)
    }

    suspend fun updateRoute(name: RouteData) = withContext(Dispatchers.IO) {
        routesDao.updateRoute(name)
    }

    suspend fun getMarkers() = withContext(Dispatchers.IO){
        routesDao.getWaypoints()
    }

    suspend fun getMarkersNear(location: Location?, kilometre: Double) = withContext(Dispatchers.IO){
        routesDao.getWaypointsNear(location, kilometre)
    }
}