package org.scottishtecharmy.soundscape.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.scottishtecharmy.soundscape.database.local.dao.RouteDao
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteMarkerCrossRef

/**
 * Imports legacy markers and routes (read by the platform layer from the
 * legacy Realm database) into the new app's Room database.
 *
 * The platform layer encodes its findings as JSON of the form:
 *
 *   {
 *     "markers": [
 *       {"legacyId": "...uuid...", "name": "...", "latitude": 0.0,
 *        "longitude": 0.0, "fullAddress": "..."}
 *     ],
 *     "routes": [
 *       {"name": "...", "description": "...",
 *        "waypointLegacyIds": ["uuid1", "uuid2", ...]}
 *     ]
 *   }
 *
 * Inserts every marker, mapping its legacy UUID to the freshly generated
 * `marker_id` in a local map, then inserts each route and connects its
 * waypoints to the new marker ids in their original order. Routes whose
 * waypoints can't all be resolved are skipped rather than persisted in a
 * broken state.
 *
 * Returns the number of markers + routes successfully imported, or -1 on
 * parse failure. The platform caller uses a non-negative return as the
 * cue to delete the legacy artefacts.
 */
suspend fun importLegacyPayload(payloadJson: String, dao: RouteDao): Int {
    val root = try {
        Json.parseToJsonElement(payloadJson).jsonObject
    } catch (t: Throwable) {
        return -1
    }

    val markers = (root["markers"] as? JsonArray) ?: JsonArray(emptyList())
    val routes = (root["routes"] as? JsonArray) ?: JsonArray(emptyList())

    val legacyToNewMarkerId = mutableMapOf<String, Long>()
    var imported = 0

    for (element in markers) {
        val obj = element as? JsonObject ?: continue
        val legacyId = obj["legacyId"]?.jsonPrimitive?.contentOrNull ?: continue
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
        val latitude = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: continue
        val longitude = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: continue
        val fullAddress = obj["fullAddress"]?.jsonPrimitive?.contentOrNull ?: ""

        val newId = dao.insertMarker(
            MarkerEntity(
                name = name,
                longitude = longitude,
                latitude = latitude,
                fullAddress = fullAddress,
            ),
        )
        legacyToNewMarkerId[legacyId] = newId
        imported++
    }

    for (element in routes) {
        val obj = element as? JsonObject ?: continue
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val waypointIdsJson = obj["waypointLegacyIds"]?.jsonArray ?: continue

        val waypointIds = waypointIdsJson.mapNotNull { it.jsonPrimitive.contentOrNull }
        val resolvedMarkerIds = waypointIds.mapNotNull { legacyToNewMarkerId[it] }
        if (resolvedMarkerIds.size != waypointIds.size || resolvedMarkerIds.isEmpty()) {
            // At least one waypoint refers to a marker we didn't import,
            // or the route has no resolvable waypoints — skip rather than
            // persist a broken route.
            continue
        }

        val newRouteId = dao.insertRoute(
            RouteEntity(name = name, description = description),
        )
        resolvedMarkerIds.forEachIndexed { index, markerId ->
            dao.addMarkerToRoute(
                RouteMarkerCrossRef(
                    routeId = newRouteId,
                    markerId = markerId,
                    markerOrder = index,
                ),
            )
        }
        imported++
    }

    return imported
}
