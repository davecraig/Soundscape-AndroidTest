import com.squareup.moshi.Moshi
import org.junit.Assert
import org.junit.Test
import org.scottishtecharmy.soundscape.GeoJSONDataComplexIntersection1
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.GeoMoshi
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.utils.RelativeDirections
import org.scottishtecharmy.soundscape.utils.createTriangleFOV
import org.scottishtecharmy.soundscape.utils.getCrossingsFromTileFeatureCollection
import org.scottishtecharmy.soundscape.utils.getDestinationCoordinate
import org.scottishtecharmy.soundscape.utils.getFovIntersectionFeatureCollection
import org.scottishtecharmy.soundscape.utils.getFovRoadsFeatureCollection
import org.scottishtecharmy.soundscape.utils.getIntersectionRoadNames
import org.scottishtecharmy.soundscape.utils.getIntersectionRoadNamesRelativeDirections
import org.scottishtecharmy.soundscape.utils.getIntersectionsFeatureCollectionFromTileFeatureCollection
import org.scottishtecharmy.soundscape.utils.getNearestIntersection
import org.scottishtecharmy.soundscape.utils.getNearestRoad
import org.scottishtecharmy.soundscape.utils.getQuadrants
import org.scottishtecharmy.soundscape.utils.getRelativeDirectionsPolygons
import org.scottishtecharmy.soundscape.utils.getRoadBearingToIntersection
import org.scottishtecharmy.soundscape.utils.getRoadsFeatureCollectionFromTileFeatureCollection
import org.scottishtecharmy.soundscape.utils.sortedByDistanceTo

class VisuallyCheckIntersectionLayers {

    // Checking how to process a complex intersection that also has a crossing
    @Test
    fun layeredIntersectionsFieldOfView1(){

        // Fake device location and device direction.
        val currentLocation = LngLatAlt(-2.6972713998905533,
            51.44374766171788)
        val deviceHeading = 340.0
        val fovDistance = 50.0

        // Get the tile feature collection from the GeoJSON
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJSONDataComplexIntersection1.complexintersection1GeoJSON)
        // Get all the intersections from the tile
        val testIntersectionsCollectionFromTileFeatureCollection =
            getIntersectionsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // Get all the roads from the tile
        val testRoadsCollectionFromTileFeatureCollection = getRoadsFeatureCollectionFromTileFeatureCollection(
            featureCollectionTest
        )
        // Get all the crossings from the tile
        val testCrossingsCollectionFromTileFeatureCollection = getCrossingsFromTileFeatureCollection(
            featureCollectionTest
        )

        // Create a FOV triangle to pick up the intersections
        val fovIntersectionsFeatureCollection = getFovIntersectionFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testIntersectionsCollectionFromTileFeatureCollection
        )
        // Create a FOV triangle to pick up the roads
        val fovRoadsFeatureCollection = getFovRoadsFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testRoadsCollectionFromTileFeatureCollection
        )
        // Create a FOV triangle to pick up the crossings
        // (crossings are Points so we can use the same function as for intersections)
        val fovCrossingsFeatureCollection = getFovIntersectionFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testCrossingsCollectionFromTileFeatureCollection
        )
        // At this point we have three field of view FeatureCollections:
        // roads, intersections and crossings

        // *** This part is the intersection and road bothering ***
        // I will need a feature collection of all the intersections in the FOV sorted by distance to the current location
        val intersectionsSortedByDistance = sortedByDistanceTo(
            currentLocation.latitude,
            currentLocation.longitude,
            fovIntersectionsFeatureCollection
        )
        // Get the nearest Road in the FoV
        val testNearestRoad = getNearestRoad(currentLocation, fovRoadsFeatureCollection)
        val intersectionsNeedsFurtherCheckingFC = FeatureCollection()
        for (i in 0 until intersectionsSortedByDistance.features.size) {
            val testNearestIntersection = FeatureCollection()
            testNearestIntersection.addFeature(intersectionsSortedByDistance.features[i])
            val intersectionRoadNames = getIntersectionRoadNames(testNearestIntersection, fovRoadsFeatureCollection)
            val intersectionsNeedsFurtherChecking = checkIntersection(i, intersectionRoadNames, testNearestRoad)
            if(intersectionsNeedsFurtherChecking) {
                intersectionsNeedsFurtherCheckingFC.addFeature(intersectionsSortedByDistance.features[i])
            }
        }
        // Approach 1: find the intersection feature with the most osm_ids and use that?
        val featureWithMostOsmIds: Feature? = intersectionsNeedsFurtherCheckingFC.features.maxByOrNull {
                feature ->
            (feature.foreign?.get("osm_ids") as? List<*>)?.size ?: 0
        }
        val newIntersectionFeatureCollection = FeatureCollection()
        if (featureWithMostOsmIds != null) {
            newIntersectionFeatureCollection.addFeature(featureWithMostOsmIds)
        }
        val nearestIntersection = getNearestIntersection(currentLocation, fovIntersectionsFeatureCollection)
        val nearestRoadBearing = getRoadBearingToIntersection(nearestIntersection, testNearestRoad, deviceHeading)
        val intersectionLocation = newIntersectionFeatureCollection.features[0].geometry as Point
        val intersectionRelativeDirections = getRelativeDirectionsPolygons(
            LngLatAlt(intersectionLocation.coordinates.longitude,
                intersectionLocation.coordinates.latitude),
            nearestRoadBearing,
            //fovDistance,
            5.0,
            RelativeDirections.COMBINED
        )
        val intersectionRoadNames = getIntersectionRoadNames(newIntersectionFeatureCollection, fovRoadsFeatureCollection)
        val roadRelativeDirections = getIntersectionRoadNamesRelativeDirections(
            intersectionRoadNames,
            newIntersectionFeatureCollection,
            intersectionRelativeDirections
        )
        // *** End of Intersection and Road
        // *** Start of Crossing

        // This is interesting as this is the nearest crossing in the FoV
        // but the next nearest crossing is the one that contains additional information about
        // a traffic island for the road that we are currently on.
        // Original Soundscape doesn't flag that a crossing is a traffic island
        // or has tactile paving, etc.
        val nearestCrossing = getNearestIntersection(currentLocation, fovCrossingsFeatureCollection)
        // Confirm which road the crossing is on
        val crossingLocation = nearestCrossing.features[0].geometry as Point
        val nearestRoadToCrossing = getNearestRoad(
            LngLatAlt(crossingLocation.coordinates.longitude,crossingLocation.coordinates.latitude),
            fovRoadsFeatureCollection
        )
        // *** End of Crossing

        // Road with nearest crossing
        Assert.assertEquals("Flax Bourton Road", nearestRoadToCrossing.features[0].properties?.get("name"))
        Assert.assertEquals("yes", nearestCrossing.features[0].properties?.get("tactile_paving"))
        // Junction info
        Assert.assertEquals(3, roadRelativeDirections.features.size)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Flax Bourton Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(3, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Clevedon Road", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(7, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("Clevedon Road", roadRelativeDirections.features[2].properties!!["name"])


        // *************************************************************
        // *** Display Field of View triangle ***
        // Direction the device is pointing
        val quadrants = getQuadrants(deviceHeading)
        // get the quadrant index from the heading so we can construct a FOV triangle using the correct quadrant
        var quadrantIndex = 0
        for (quadrant in quadrants) {
            val containsHeading = quadrant.contains(deviceHeading)
            if (containsHeading) {
                break
            } else {
                quadrantIndex++
            }
        }
        // Get the coordinate for the "Left" of the FOV
        val destinationCoordinateLeft = getDestinationCoordinate(
            LngLatAlt(currentLocation.longitude, currentLocation.latitude),
            quadrants[quadrantIndex].left,
            fovDistance
        )

        //Get the coordinate for the "Right" of the FOV
        val destinationCoordinateRight = getDestinationCoordinate(
            LngLatAlt(currentLocation.longitude, currentLocation.latitude),
            quadrants[quadrantIndex].right,
            fovDistance
        )

        // We can now construct our FOV polygon (triangle)
        val polygonTriangleFOV = createTriangleFOV(
            destinationCoordinateLeft,
            currentLocation,
            destinationCoordinateRight
        )

        val featureFOVTriangle = Feature().also {
            val ars3: HashMap<String, Any?> = HashMap()
            ars3 += Pair("FoV", "45 degrees 35 meters")
            it.properties = ars3
        }
        featureFOVTriangle.geometry = polygonTriangleFOV

        fovIntersectionsFeatureCollection.addFeature(featureFOVTriangle)
        fovRoadsFeatureCollection.addFeature(featureFOVTriangle)
        fovCrossingsFeatureCollection.addFeature(featureFOVTriangle)

        val fovIntersections = moshi.adapter(FeatureCollection::class.java).toJson(fovIntersectionsFeatureCollection)
        // copy and paste into GeoJSON.io
        println("FOV Intersections: $fovIntersections")
        val fovRoads = moshi.adapter(FeatureCollection::class.java).toJson(fovRoadsFeatureCollection)
        // copy and paste into GeoJSON.io
        println("FOV Roads: $fovRoads")
        val fovCrossings = moshi.adapter(FeatureCollection::class.java).toJson(fovCrossingsFeatureCollection)
        // copy and paste into GeoJSON.io
        println("FOV Crossings: $fovCrossings")


    }

    private fun checkIntersection(
        intersectionNumber: Int,
        intersectionRoadNames: FeatureCollection,
        testNearestRoad:FeatureCollection
    ): Boolean {
        println("Number of roads that make up intersection ${intersectionNumber}: ${intersectionRoadNames.features.size}")
        for (road in intersectionRoadNames) {
            val roadName = road.properties?.get("name")
            val isOneWay = road.properties?.get("oneway") == "yes"
            val isMatch = testNearestRoad.features[0].properties?.get("name") == roadName

            println("The road name is: $roadName")
            if (isMatch && isOneWay) {
                println("Intersection $intersectionNumber is probably a compound roundabout or compound intersection and we don't want to call it out.")
                return false
            } else if (isMatch) {
                println("Intersection $intersectionNumber is probably a compound roundabout or compound intersection and we don't want to call it out.")
                return false
            } else {
                println("Intersection $intersectionNumber is probably NOT a compound roundabout or compound intersection and we DO want to call it out.")
                return true
            }

        }
        return false
    }
}