package org.scottishtecharmy.soundscape.geoengine.utils.geocoders

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.getTextForFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.getDistanceToFeature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription

/**
 * The LocalGeocoder class abstracts away the use of map tile data on the phone for geocoding and
 * reverse geocoding. If the map tiles are present on the device already, this can be used without
 * any Internet connection.
 */
class LocalGeocoder(
    val gridState: GridState,
    val settlementGrid: GridState,
) : SoundscapeGeocoder() {
    override suspend fun getAddressFromLocationName(
        locationName: String,
        nearbyLocation: LngLatAlt,
    ) : LocationDescription? {
        return null
    }

    private fun getNearestPointOnFeature(feature: Feature, location: LngLatAlt) : LngLatAlt {
        return getDistanceToFeature(location, feature, gridState.ruler).point
    }

    override suspend fun getAddressFromLngLat(userGeometry: UserGeometry) : LocationDescription? {

        val location = userGeometry.mapMatchedLocation?.point ?: userGeometry.location
        // We can only use the local geocoder for local locations
        if(!gridState.isLocationWithinGrid(location))
            return null

        //
        // The ideal scenario is that we return a house number and street name that we think match
        // our current location. However, there are many things that can make this harder:
        //
        //  * Missing house numbers
        //  * Location is close to more than one street
        //  * Map matched to an un-named street or Way
        //  * Large front gardens e.g. https://www.openstreetmap.org/way/443769054 where Strathblane
        //    Road is between 60m and 90m away from the actual house.
        //  * Numbers can be quite different on opposite sides of street e.g.
        //    https://www.openstreetmap.org/way/31094506 where there are three times more even than
        //    odd numbered houses.
        //
        val houseNumberTree = gridState.getFeatureTree(TreeId.HOUSENUMBER)

        if(userGeometry.mapMatchedWay != null) {
            // We're map matched to a street so we can be fairly confident that we are on it. Check
            // that it's a named street
            if(userGeometry.mapMatchedWay.name != null) {
                // Find the nearest house number for this street
                val nearbyHouseNumbers =
                    houseNumberTree.getNearbyCollection(
                        userGeometry.mapMatchedLocation?.point ?: userGeometry.location,
                        100.0,
                        gridState.ruler
                    )

                for(house in nearbyHouseNumbers) {
                    if ((house as MvtFeature).name == userGeometry.mapMatchedWay.name) {
                        // We have a match, but we need to check that it's really the nearest house
                        // and not several houses away.
                        val houseNumberText = getTextForFeature(null, house)
                        return LocationDescription(
                            name = houseNumberText.text,
                            location = getNearestPointOnFeature(house, location)
                        )
                    }
                }

                // No house numbers for this street, so try and describe our location on it in other
                // ways e.g. relative to POI on street, or distance from nearest named junction.
                userGeometry.mapMatchedLocation?.point
            }
        }

        // We weren't map matched to a street, so use our location instead. Find the nearest house
        // number and then check that the street for that is the nearest named street to our location.
        val nearestHouse =
            houseNumberTree.getNearestFeature(
                userGeometry.mapMatchedLocation?.point ?: userGeometry.location,
                userGeometry.ruler,
                50.0
            )

        // Find nearby Ways
        val nearbyWays = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
            .getNearbyCollection(
                location,
                50.0,
                userGeometry.ruler
            )

//        // Find nearby house numbers
//        if (nearbyHouseNumbers.features.isNotEmpty()) {
//            val street = nearestHouseNumber.properties?.get("street")
//            if(currentWay.name == street) {
//                val houseNumberText = getTextForFeature(null, nearestHouseNumber as MvtFeature)
//                return LocationDescription(
//                    name = houseNumberText.text,
//                    location = getNearestPointOnFeature(nearestHouseNumber, location)
//                )
//            }
//        }
//
//        if(currentWay != null) {
//            // Find nearest house number on this street
//            val houseNumberTree = gridState.getFeatureTree(TreeId.HOUSENUMBER)
//            val nearestHouseNumber =
//                houseNumberTree.getNearestFeature(location, gridState.ruler, 25.0)
//            if (nearestHouseNumber != null) {
//                val street = nearestHouseNumber.properties?.get("street")
//                if(currentWay.name == street) {
//                    val houseNumberText = getTextForFeature(null, nearestHouseNumber as MvtFeature)
//                    return LocationDescription(
//                        name = houseNumberText.text,
//                        location = getNearestPointOnFeature(nearestHouseNumber, location)
//                    )
//                }
//            }
//        }

        // Check if we're near a bus/tram/train stop. This is useful when travelling on public transport
        val busStopTree = gridState.getFeatureTree(TreeId.TRANSIT_STOPS)
        val nearestBusStop = busStopTree.getNearestFeature(location, gridState.ruler, 20.0)
        if(nearestBusStop != null) {
            val busStopText = getTextForFeature(null, nearestBusStop as MvtFeature)
            return LocationDescription(
                name = busStopText.text,
                location = getNearestPointOnFeature(nearestBusStop, location)
            )
        }

        // Check if we're inside a POI
        val gridPoiTree = gridState.getFeatureTree(TreeId.POIS)
        val insidePois = gridPoiTree.getContainingPolygons(location)
        insidePois.forEach { poi ->
            val mvt = poi as MvtFeature
            if(!mvt.name.isNullOrEmpty()) {
                val featureText = getTextForFeature(null, mvt)
                return LocationDescription(
                    name = featureText.text,
                    location = getNearestPointOnFeature(mvt, location)
                )
            }
        }

        // See if there are any nearby named POI
        val nearbyPois = gridPoiTree.getNearestCollection(location, 300.0, 10, gridState.ruler, null)
        nearbyPois.forEach { poi ->
            val mvt = poi as MvtFeature
            if(!mvt.name.isNullOrEmpty()) {
                return LocationDescription(
                    name = getTextForFeature(null, mvt).text,
                    location = getNearestPointOnFeature(mvt, location),
                )
            }
        }

        // Get the nearest settlements. Nominatim uses the following proximities, so we do the same:
        //
        // cities, municipalities, islands | 15 km
        // towns, boroughs                 | 4 km
        // villages, suburbs               | 2 km
        // hamlets, farms, neighbourhoods  |  1 km
        //
        var nearestSettlement = settlementGrid.getFeatureTree(TreeId.SETTLEMENT_HAMLET)
            .getNearestFeature(location, settlementGrid.ruler, 1000.0) as MvtFeature?
        var nearestSettlementName = nearestSettlement?.name
        if(nearestSettlementName == null) {
            nearestSettlement = settlementGrid.getFeatureTree(TreeId.SETTLEMENT_VILLAGE)
                .getNearestFeature(location, settlementGrid.ruler, 2000.0) as MvtFeature?
            nearestSettlementName = nearestSettlement?.name
            if(nearestSettlementName == null) {
                nearestSettlement = settlementGrid.getFeatureTree(TreeId.SETTLEMENT_TOWN)
                    .getNearestFeature(location, settlementGrid.ruler, 4000.0) as MvtFeature?
                nearestSettlementName = nearestSettlement?.name
                if (nearestSettlementName == null) {
                    nearestSettlement = settlementGrid.getFeatureTree(TreeId.SETTLEMENT_CITY)
                        .getNearestFeature(location, settlementGrid.ruler, 15000.0) as MvtFeature?
                    nearestSettlementName = nearestSettlement?.name
                }
            }
        }

        // Check if the location is alongside a road/path
        val nearestRoad = gridState.getNearestFeature(TreeId.ROADS_AND_PATHS, gridState.ruler, location, 100.0) as Way?
        if(nearestRoad != null) {
            // We only want 'interesting' non-generic names i.e. no "Path" or "Service"
            val roadName = nearestRoad.getName(null, gridState, null, true)
            if(roadName.isNotEmpty()) {
                if(nearestSettlementName != null) {
                    return LocationDescription(
                        name = roadName,
                        location = location
                    )
                } else {
                    return LocationDescription(
                        name = roadName,
                        location = location,
                    )
                }
            }
        }

        if(nearestSettlementName != null) {
            //val distanceToSettlement = settlementGrid.ruler.distance(location, (nearestSettlement?.geometry as Point).coordinates)
            return LocationDescription(
                name = nearestSettlementName,
                location = location,
            )
        }

        return null
    }

    companion object {
        const val TAG = "LocalGeocoder"
    }
}