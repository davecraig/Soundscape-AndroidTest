package org.scottishtecharmy.soundscape.services

import android.util.Log
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.scottishtecharmy.soundscape.database.local.RealmConfiguration
import org.scottishtecharmy.soundscape.database.local.dao.RoutesDao
import org.scottishtecharmy.soundscape.database.local.model.RouteData
import org.scottishtecharmy.soundscape.database.repository.RoutesRepository
import org.scottishtecharmy.soundscape.utils.distance

class RoutePlayer(val service: SoundscapeService) {
    private var currentRouteData: RouteData? = null
    private var currentMarker = -1
    private val coroutineScope = CoroutineScope(Job())

    fun setupCurrentRoute() {
        val realm = RealmConfiguration.getMarkersInstance()
        val routesDao = RoutesDao(realm)
        val routesRepository = RoutesRepository(routesDao)

        Log.e(TAG, "setupCurrentRoute")
        coroutineScope.launch {
            val dbRoutes = routesRepository.getRoutes()
            if(dbRoutes.isNotEmpty()) {
                currentRouteData = dbRoutes[0].copyFromRealm()
            }
            currentMarker = 0
            play()
            Log.d(TAG, toString())
        }

        coroutineScope.launch {
            // Observe location updates from the service
            service.locationProvider.locationFlow.collectLatest { value ->
                if (value != null) {
                    currentRouteData?.let { route ->
                        if(currentMarker < route.waypoints.size) {
                            val location = route.waypoints[currentMarker].location!!
                            if(distance(location.latitude, location.longitude, value.latitude, value.longitude) < 10) {
                                // We're within 10m of the marker, move on to the next one
                                moveToNext()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createBeaconAtWaypoint(index: Int) {
        currentRouteData?.let {route ->
            assert(index < route.waypoints.size)
            val location = route.waypoints[index].location!!

            service.audioEngine.clearTextToSpeechQueue()
            service.audioEngine.createTextToSpeech(location.latitude, location.longitude, "Move to marker ${index+1}, ${route.waypoints[index].name}")

            service.createBeacon(location.latitude, location.longitude)
}
    }

    fun play() {
        createBeaconAtWaypoint(currentMarker)
        Log.d(TAG, toString())
    }

    private fun moveToNext() {
        currentRouteData?.let {route ->
            if((currentMarker + 1) < route.waypoints.size) {
                currentMarker++

                createBeaconAtWaypoint(currentMarker)
            }
        }
        Log.d(TAG, toString())
    }

    fun moveToPrevious() {
        if(currentMarker > 0) {
            currentMarker--
            createBeaconAtWaypoint(currentMarker)
        }
        Log.d(TAG, toString())
    }

    override fun toString(): String {
        currentRouteData?.let { route ->
            var state = ""
            state += "Route : ${route.name}\n"
            for((index, waypoint) in route.waypoints.withIndex()) {
                state += "  ${waypoint.name} at ${waypoint.location?.latitude},${waypoint.location?.longitude}"
                state += if(index == currentMarker) {
                    " <current>\n"
                } else {
                    "\n"
                }
            }

            return state
        }
        return "No current route set."
    }


    companion object {
        private const val TAG = "RoutePlayer"
    }
}