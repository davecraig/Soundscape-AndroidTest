package org.scottishtecharmy.soundscape.screens.home.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.database.local.dao.RouteDao
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers
import org.scottishtecharmy.soundscape.utils.MarkersAndRoutesIo
import org.scottishtecharmy.soundscape.utils.NamedGpx
import org.scottishtecharmy.soundscape.utils.parseGpxFile

open class AdvancedMarkersAndRoutesSettingsViewModel(
    private val routeDao: RouteDao,
    private val io: MarkersAndRoutesIo,
) : ViewModel() {

    private val _userFeedback = MutableStateFlow("")
    val userFeedback: StateFlow<String> = _userFeedback

    fun deleteAllMarkersAndRoutes(successString: String) {
        viewModelScope.launch {
            routeDao.clearAll()
            _userFeedback.value = successString
        }
    }

    fun exportMarkersAndRoutes(shareTitle: String) {
        viewModelScope.launch {
            // Export a zip containing one GPX per route plus a single GPX with
            // all standalone markers, so users can inspect the data in other
            // tools rather than dealing with an opaque database file.
            val routes = routeDao.getAllRoutesWithMarkers()
            val markers = routeDao.getAllMarkers()
            val allMarkersRoute = RouteWithMarkers(
                route = RouteEntity(0, GLOBAL_MARKERS_NAME, ""),
                markers = markers,
            )

            val files = mutableListOf<NamedGpx>()
            val usedNames = mutableMapOf<String, Int>()
            files += namedGpxFor(allMarkersRoute, usedNames)
            for (route in routes) {
                files += namedGpxFor(route, usedNames)
            }

            io.exportGpxZip(
                files = files,
                suggestedFilename = "soundscape-routes-export",
                shareTitle = shareTitle,
            )
        }
    }

    fun importMarkersAndRoutes(successString: String, failureString: String) {
        viewModelScope.launch {
            val files = try {
                io.pickGpxZip()
            } catch (e: Exception) {
                println("Failed to pick zip: ${e.message}")
                null
            }
            if (files == null) return@launch
            try {
                var routeCount = 0
                for (file in files) {
                    val parsed = parseGpxFile(file.content) ?: continue
                    if (file.filename.contains(GLOBAL_MARKERS_NAME)) {
                        // Merge standalone markers first so per-route imports
                        // can reuse them via insertRouteWithNewMarkers below.
                        for (it in parsed.markers) {
                            val existingMarker =
                                routeDao.getMarkerByLocation(it.longitude, it.latitude)
                            if (existingMarker == null) {
                                routeDao.insertMarker(it)
                            } else {
                                routeDao.updateMarker(
                                    MarkerEntity(
                                        markerId = existingMarker.markerId,
                                        name = it.name,
                                        fullAddress = it.fullAddress,
                                        longitude = existingMarker.longitude,
                                        latitude = existingMarker.latitude,
                                    ),
                                )
                            }
                        }
                        if (parsed.markers.isNotEmpty()) routeCount += 1
                    } else {
                        val newRoute = RouteEntity(
                            name = parsed.route.name,
                            description = parsed.route.description,
                        )
                        routeDao.insertRouteWithNewMarkers(newRoute, parsed.markers)
                        if (parsed.markers.isNotEmpty()) routeCount += 1
                    }
                }
                _userFeedback.value = if (routeCount > 0) successString else failureString
            } catch (e: Exception) {
                println("Failed to import zip: ${e.message}")
                _userFeedback.value = failureString
            }
        }
    }

    fun userFeedbackShown() {
        _userFeedback.value = ""
    }

    private fun namedGpxFor(
        route: RouteWithMarkers,
        usedNames: MutableMap<String, Int>,
    ): NamedGpx {
        var fileRoot = sanitizeFilename(route.route.name)
        val current = usedNames[fileRoot]
        if (current == null) {
            usedNames[fileRoot] = 0
        } else {
            usedNames[fileRoot] = current + 1
            fileRoot = "${fileRoot}_${current + 1}"
        }
        return NamedGpx(filename = "$fileRoot.gpx", content = generateGpxString(route))
    }

    private fun generateGpxString(route: RouteWithMarkers): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Soundscape\">\n")
        append("  <metadata>\n")
        append("    <name>${route.route.name}</name>\n")
        append("    <desc>${route.route.description}</desc>\n")
        append("  </metadata>\n")
        for (marker in route.markers) {
            append("      <wpt lat=\"${marker.latitude}\" lon=\"${marker.longitude}\">\n")
            append("        <name>${marker.name}</name>\n")
            append("        <desc>${marker.fullAddress}</desc>\n")
            append("      </wpt>\n")
        }
        append("</gpx>")
    }

    companion object {
        const val GLOBAL_MARKERS_NAME = "AllSoundscapeDatabaseMarkersInASingleRoute"
    }
}

private val INVALID_FILENAME_CHARS = Regex("[/\\\\:*?\"<>|\\x00]")

private fun sanitizeFilename(name: String): String =
    name.replace(INVALID_FILENAME_CHARS, "_").take(100)
