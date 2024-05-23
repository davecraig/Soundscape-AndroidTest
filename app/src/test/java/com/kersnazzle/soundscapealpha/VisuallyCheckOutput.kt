package com.kersnazzle.soundscapealpha

import com.kersnazzle.soundscapealpha.geojsonparser.geojson.Feature
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.FeatureCollection
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.GeoMoshi
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.LineString
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.LngLatAlt
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.Point
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.Polygon
import com.kersnazzle.soundscapealpha.utils.Quadrant
import com.kersnazzle.soundscapealpha.utils.RelativeDirections
import com.kersnazzle.soundscapealpha.utils.circleToPolygon
import com.kersnazzle.soundscapealpha.utils.cleanTileGeoJSON
import com.kersnazzle.soundscapealpha.utils.createTriangleFOV
import com.kersnazzle.soundscapealpha.utils.getAheadBehindDirectionPolygons
import com.kersnazzle.soundscapealpha.utils.getBoundingBoxCorners
import com.kersnazzle.soundscapealpha.utils.getCenterOfBoundingBox
import com.kersnazzle.soundscapealpha.utils.getCombinedDirectionPolygons
import com.kersnazzle.soundscapealpha.utils.getDestinationCoordinate
import com.kersnazzle.soundscapealpha.utils.getEntrancesFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getFovIntersectionFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getFovPoiFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getFovRoadsFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getIndividualDirectionPolygons
import com.kersnazzle.soundscapealpha.utils.getIntersectionRoadNames
import com.kersnazzle.soundscapealpha.utils.getIntersectionsFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getLeftRightDirectionPolygons
import com.kersnazzle.soundscapealpha.utils.getNearestIntersection
import com.kersnazzle.soundscapealpha.utils.getPathsFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getPoiFeatureCollectionBySuperCategory
import com.kersnazzle.soundscapealpha.utils.getPointsOfInterestFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getPolygonOfBoundingBox
import com.kersnazzle.soundscapealpha.utils.getQuadrants
import com.kersnazzle.soundscapealpha.utils.getReferenceCoordinate
import com.kersnazzle.soundscapealpha.utils.getRelativeDirectionsPolygons
import com.kersnazzle.soundscapealpha.utils.getRoadsFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getTilesForRegion
import com.kersnazzle.soundscapealpha.utils.getXYTile
import com.kersnazzle.soundscapealpha.utils.polygonContainsCoordinates
import com.kersnazzle.soundscapealpha.utils.tileToBoundingBox
import com.squareup.moshi.Moshi
import org.junit.Assert
import org.junit.Test

// Functions to output GeoJSON strings that can be put into the very useful Geojson.io
// for a visual check. The GeoJSON parser that they use is also handy to make sure output
// is correct. However it seems to use markers for any Point object which can make the screen a bit busy
// https://geojson.io/#map=2/0/20
class VisuallyCheckOutput {

    @Test
    fun youAreHereTest(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // convert coordinates to tile - I'm cheating here as the coordinates are already
        // in the center of the tile.
        val tileXY = getXYTile(51.43860066718254, -2.69439697265625, 16 )
        val tileBoundingBox = tileToBoundingBox(tileXY.first, tileXY.second, 16.0)
        val tileBoundingBoxCorners = getBoundingBoxCorners(tileBoundingBox)
        val tilePolygon = getPolygonOfBoundingBox(tileBoundingBox)
        val tileBoundingBoxCenter = getCenterOfBoundingBox(tileBoundingBoxCorners)
        // Feature is the Polygon to display tile boundary
        val featurePolygon = Feature().also {
            val ars3: HashMap<String, Any?> = HashMap()
            ars3 += Pair("Polygon", "to display the tile bounding box")
            it.properties = ars3
        }
        featurePolygon.geometry = tilePolygon
        // Create a point to show center of tile
        val locationPoint = Point()
        locationPoint.coordinates = tileBoundingBoxCenter
        val featureHere = Feature().also {
            val ars3: HashMap<String, Any?> = HashMap()
            ars3 += Pair("Hello!", "World")
            it.properties = ars3
        }
        featureHere.geometry = locationPoint
        // create a new feature collection
        val newFeatureCollection = FeatureCollection()
        // add our two Features to the Feature Collection
        newFeatureCollection.addFeature(featurePolygon)
        newFeatureCollection.addFeature(featureHere)
        // convert FeatureCollection to string
        val youAreHere = moshi.adapter(FeatureCollection::class.java).toJson(newFeatureCollection)
        // copy and paste into GeoJSON.io
        println(youAreHere)
    }

    @Test
    fun grid3x3Test(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // convert coordinates to tile
        val tileXY = getXYTile(51.43860066718254, -2.69439697265625, 16 )
        val tileBoundingBox = tileToBoundingBox(tileXY.first, tileXY.second, 16.0)
        val tileBoundingBoxCorners = getBoundingBoxCorners(tileBoundingBox)
        val tileBoundingBoxCenter = getCenterOfBoundingBox(tileBoundingBoxCorners)

        val surroundingTiles = getTilesForRegion(
            tileBoundingBoxCenter.latitude, tileBoundingBoxCenter.longitude, 200.0, 16 )

        val newFeatureCollection = FeatureCollection()
        // Create a bounding box/Polygon for each tile in the grid
        for(tile in surroundingTiles){
            val surroundingTileBoundingBox = tileToBoundingBox(tile.tileX, tile.tileY, 16.0)
            val polygonBoundingBox = getPolygonOfBoundingBox(surroundingTileBoundingBox)
            val boundingBoxFeature = Feature().also {
                val ars3: HashMap<String, Any?> = HashMap()
                ars3 += Pair("Tile X", tile.tileX)
                ars3 += Pair("Tile Y", tile.tileY)
                ars3 += Pair("quadKey", tile.quadkey)
                it.properties = ars3
                it.type = "Feature"
            }
            boundingBoxFeature.geometry = polygonBoundingBox
            newFeatureCollection.addFeature(boundingBoxFeature)
        }
        // Display the circle we are using for the grid radius
        val circlePolygon = circleToPolygon(
            30,
            tileBoundingBoxCenter.latitude,
            tileBoundingBoxCenter.longitude,
            200.0
        )
        val circlePolygonFeature = Feature().also {
            val ars3: HashMap<String, Any?> = HashMap()
            ars3 += Pair("Shape", "circle")
            ars3 += Pair("Radius", 200)
            it.properties = ars3
            it.type = "Feature"
        }
        circlePolygonFeature.geometry = circlePolygon
        newFeatureCollection.addFeature(circlePolygonFeature)

        val grid3x3String = moshi.adapter(FeatureCollection::class.java).toJson(newFeatureCollection)
        // copy and paste into GeoJSON.io
        println(grid3x3String)
    }

    @Test
    fun entireTileFeatureCollection(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // convert coordinates to tile
        val tileXY = getXYTile(51.43860066718254, -2.69439697265625, 16 )
        // Get the data for the entire tile
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonDataReal.featureCollectionJsonRealSoundscapeGeoJson)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            tileXY.first,
            tileXY.second,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(featureCollectionTest)
        )
        // copy and paste into GeoJSON.io
        println(cleanTileFeatureCollection)
    }

    @Test
    fun roadsFeatureCollection(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // convert coordinates to tile
        val tileXY = getXYTile(51.43860066718254, -2.69439697265625, 16 )
        // Get the data for the entire tile
        val entireFeatureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonDataReal.featureCollectionJsonRealSoundscapeGeoJson)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            tileXY.first,
            tileXY.second,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(entireFeatureCollectionTest)
        )
        // get the roads Feature Collection.
        // Crossings are counted as roads by original Soundscape
        val testRoadsCollection = getRoadsFeatureCollectionFromTileFeatureCollection(
            moshi.adapter(FeatureCollection::class.java)
                .fromJson(cleanTileFeatureCollection)!!
            )
        val roads = moshi.adapter(FeatureCollection::class.java).toJson(testRoadsCollection)
        // copy and paste into GeoJSON.io
        println(roads)
    }

    @Test
    fun intersectionsFeatureCollection(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // convert coordinates to tile
        val tileXY = getXYTile(51.43860066718254, -2.69439697265625, 16 )
        // Get the data for the entire tile
        val entireFeatureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonDataReal.featureCollectionJsonRealSoundscapeGeoJson)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            tileXY.first,
            tileXY.second,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(entireFeatureCollectionTest)
        )
        // get the Intersections Feature Collection.
        val testIntersectionsCollection = getIntersectionsFeatureCollectionFromTileFeatureCollection(
            moshi.adapter(FeatureCollection::class.java)
                .fromJson(cleanTileFeatureCollection)!!
        )
        val intersections = moshi.adapter(FeatureCollection::class.java).toJson(testIntersectionsCollection)
        // copy and paste into GeoJSON.io
        println(intersections)
    }

    @Test
    fun poiFeatureCollection(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // convert coordinates to tile
        val tileXY = getXYTile(51.43860066718254, -2.69439697265625, 16 )
        // Get the data for the entire tile
        val entireFeatureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonDataReal.featureCollectionJsonRealSoundscapeGeoJson)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            tileXY.first,
            tileXY.second,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(entireFeatureCollectionTest)
        )
        // get the POI Feature Collection.
        val testPoiCollection = getPointsOfInterestFeatureCollectionFromTileFeatureCollection(
            moshi.adapter(FeatureCollection::class.java)
                .fromJson(cleanTileFeatureCollection)!!
        )
        val poi = moshi.adapter(FeatureCollection::class.java).toJson(testPoiCollection)
        // copy and paste into GeoJSON.io
        println(poi)
    }

    @Test
    fun pathsFeatureCollection(){
        // The tile that I've been using above doesn't have any paths mapped in it
        // so I'm swapping to a different tile.
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // Get the data for the entire tile
        val entireFeatureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonEntrancesEtcData.featureCollectionWithEntrances)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            32295,
            21787,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(entireFeatureCollectionTest)
        )
        // get the paths Feature Collection.
        val testPathCollection = getPathsFeatureCollectionFromTileFeatureCollection(
            moshi.adapter(FeatureCollection::class.java)
                .fromJson(cleanTileFeatureCollection)!!
        )
        val paths = moshi.adapter(FeatureCollection::class.java).toJson(testPathCollection)
        // copy and paste into GeoJSON.io
        println(paths)
    }

    @Test
    fun entrancesFeatureCollection(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // Get the data for the entire tile
        val entireFeatureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonEntrancesEtcData.featureCollectionWithEntrances)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            32295,
            21787,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(entireFeatureCollectionTest)
        )
        // get the entrances Feature Collection.
        // Entrances are weird as it is a single Feature made up of MultiPoints and osm_ids
        // I'm assuming osm_ids correlate to the polygon which has the entrance but haven't checked this yet
        val testEntrancesCollection = getEntrancesFeatureCollectionFromTileFeatureCollection(
            moshi.adapter(FeatureCollection::class.java)
                .fromJson(cleanTileFeatureCollection)!!
        )
        val entrances = moshi.adapter(FeatureCollection::class.java).toJson(testEntrancesCollection)
        // copy and paste into GeoJSON.io
        println(entrances)
    }

    @Test
    fun poiSuperCategory(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        // Get the data for the entire tile
        val entireFeatureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonEntrancesEtcData.featureCollectionWithEntrances)
        // clean it
        val cleanTileFeatureCollection = cleanTileGeoJSON(
            32295,
            21787,
            16.0,
            moshi.adapter(FeatureCollection::class.java).toJson(entireFeatureCollectionTest)
        )
        // get the poi Feature Collection.
        val testPoiCollection = getPointsOfInterestFeatureCollectionFromTileFeatureCollection(
            moshi.adapter(FeatureCollection::class.java)
                .fromJson(cleanTileFeatureCollection)!!
        )
        // select super category
        //"information", "object", "place", "landmark", "mobility", "safety"
        val testSuperCategoryPoiCollection =
            getPoiFeatureCollectionBySuperCategory("mobility", testPoiCollection)
        val superCategory = moshi.adapter(FeatureCollection::class.java).toJson(testSuperCategoryPoiCollection)
        // copy and paste into GeoJSON.io
        println(superCategory)
    }

    @Test
    fun intersectionsFieldOfView(){
        // Above I've just been filtering the entire tile into broad categories: roads, intersections, etc.
        // Now going to filter the tile by:
        // (1) where the device is located
        // (2) the direction the device is pointing,
        // (3) the distance that the "field of view" extends out.
        // Initially a right angle triangle but Soundscape is a bit more sophisticated which I'll get to
        // (4) whatever we are interested in -> roads, intersections, etc.

        // Fake device location and pretend the device is pointing East.
        // -2.6577997643930757, 51.43041390383118
        val currentLocation = LngLatAlt(-2.6573400576040456, 51.430456817236575)
        val deviceHeading = 90.0
        val fovDistance = 50.0

        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonIntersectionStraight.intersectionStraightAheadFeatureCollection)
        // Get the intersections from the tile
        val testIntersectionsCollectionFromTileFeatureCollection =
            getIntersectionsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // ********* This is the only line that is useful. The rest of it is
        // ********* outputting the triangle that represents the FoV
        //
        // Create a FOV triangle to pick up the intersection (this intersection is a transition from
        // Weston Road to Long Ashton Road)
        val fovIntersectionsFeatureCollection = getFovIntersectionFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testIntersectionsCollectionFromTileFeatureCollection
        )
        // *************************************************************

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

        val fovIntersections = moshi.adapter(FeatureCollection::class.java).toJson(fovIntersectionsFeatureCollection)
        // copy and paste into GeoJSON.io
        println(fovIntersections)
    }

    @Test
    fun roadsFieldOfView(){
        // Above I've just been filtering the entire tile into broad categories: roads, intersections, etc.
        // Now going to filter the tile by:
        // (1) where the device is located
        // (2) the direction the device is pointing,
        // (3) the distance that the "field of view" extends out.
        // Initially a right angle triangle but Soundscape is a bit more sophisticated which I'll get to
        // (4) whatever we are interested in -> roads, intersections, etc.

        // Fake device location and pretend the device is pointing East.
        // -2.6577997643930757, 51.43041390383118
        val currentLocation = LngLatAlt(-2.6573400576040456, 51.430456817236575)
        val deviceHeading = 90.0
        val fovDistance = 50.0

        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonIntersectionStraight.intersectionStraightAheadFeatureCollection)
        // Get the roads from the tile
        val testRoadsCollectionFromTileFeatureCollection =
            getRoadsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // ********* This is the only line that is useful. The rest of it is
        // ********* outputting the triangle that represents the FoV
        //
        // Create a FOV triangle to pick up the roads in the FoV roads.
        // In this case Weston Road and Long Ashton Road
        val fovRoadsFeatureCollection = getFovRoadsFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testRoadsCollectionFromTileFeatureCollection
        )
        // *************************************************************

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

        fovRoadsFeatureCollection.addFeature(featureFOVTriangle)

        val fovRoads = moshi.adapter(FeatureCollection::class.java).toJson(fovRoadsFeatureCollection)
        // copy and paste into GeoJSON.io
        println(fovRoads)

    }

    @Test
    fun poiFieldOfView(){
        // Above I've just been filtering the entire tile into broad categories: roads, intersections, etc.
        // Now going to filter the tile by:
        // (1) where the device is located
        // (2) the direction the device is pointing,
        // (3) the distance that the "field of view" extends out.
        // Initially a right angle triangle but Soundscape is a bit more sophisticated which I'll get to
        // (4) whatever we are interested in -> roads, intersections, etc.

        // Fake device location and pretend the device is pointing East.
        // -2.6577997643930757, 51.43041390383118
        val currentLocation = LngLatAlt(-2.6573400576040456, 51.430456817236575)
        val deviceHeading = 90.0
        val fovDistance = 50.0

        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonIntersectionStraight.intersectionStraightAheadFeatureCollection)
        // Get the poi from the tile
        val testPoiCollectionFromTileFeatureCollection =
            getPointsOfInterestFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // ********* This is the only line that is useful. The rest of it is
        // ********* outputting the triangle that represents the FoV
        //
        // Create a FOV triangle to pick up the poi in the FoV.
        // In this case a couple of buildings
        val fovPoiFeatureCollection = getFovPoiFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testPoiCollectionFromTileFeatureCollection
        )
        // *************************************************************

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

        fovPoiFeatureCollection.addFeature(featureFOVTriangle)

        val fovPoi = moshi.adapter(FeatureCollection::class.java).toJson(fovPoiFeatureCollection)
        // copy and paste into GeoJSON.io
        println(fovPoi)

    }

    // Trying to understand how relative headings "ahead", "ahead left", etc. work
    // Displaying Soundscape COMBINED Direction types with this
    @Test
    fun relativeDirectionsCombined(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val location = LngLatAlt(-2.657279900280031, 51.430461188129385)
        val deviceHeading = 0.0
        val distance = 50.0

        val combinedDirectionPolygons  = getCombinedDirectionPolygons(location, deviceHeading, distance)

        val relativeDirectionTrianglesString = moshi.adapter(FeatureCollection::class.java)
            .toJson(combinedDirectionPolygons)

        println(relativeDirectionTrianglesString)

    }

    //Displaying Soundscape INDIVIDUAL Direction types
    @Test
    fun relativeDirectionsIndividual(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val location = LngLatAlt(-2.657279900280031, 51.430461188129385)
        val deviceHeading = 25.0
        val distance = 50.0

        val individualRelativeDirections = getIndividualDirectionPolygons(location, deviceHeading, distance)

        val relativeDirectionTrianglesString = moshi.adapter(FeatureCollection::class.java)
            .toJson(individualRelativeDirections)

        println(relativeDirectionTrianglesString)
    }

    //Displaying Soundscape AHEAD_BEHIND Direction types
    @Test
    fun relativeDirectionsAheadBehind(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val location = LngLatAlt(-2.657279900280031, 51.430461188129385)
        val deviceHeading = 25.0
        val distance = 50.0

        // Issue here is because of the bias towards "ahead" and "behind" you end up with a wide but shallow field of view
        // Probably better to do some trig to provide the distance to keep the depth of the field of view constant?
        val aheadBehindRelativeDirections = getAheadBehindDirectionPolygons(location, deviceHeading, distance)

        val relativeDirectionTrianglesString = moshi.adapter(FeatureCollection::class.java)
            .toJson(aheadBehindRelativeDirections)

        println(relativeDirectionTrianglesString)

    }

    //Displaying Soundscape LEFT_RIGHT Direction types
    @Test
    fun relativeDirectionsLeftRight(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val location = LngLatAlt(-2.657279900280031, 51.430461188129385)
        val deviceHeading = 0.0
        val distance = 50.0

        val leftRightRelativeDirections = getLeftRightDirectionPolygons(location, deviceHeading, distance)

        val relativeDirectionTrianglesString = moshi.adapter(FeatureCollection::class.java)
            .toJson(leftRightRelativeDirections)

        println(relativeDirectionTrianglesString)
    }

    @Test
    fun relativeDirectionsAll(){
        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val location = LngLatAlt(-2.657279900280031, 51.430461188129385)
        val deviceHeading = 0.0
        val distance = 50.0

        // A wrapper around the individual functions
        val relativeDirections = getRelativeDirectionsPolygons(
            location, deviceHeading, distance, RelativeDirections.COMBINED
        )
        val relativeDirectionTrianglesString = moshi.adapter(FeatureCollection::class.java)
            .toJson(relativeDirections)

        println(relativeDirectionTrianglesString)

    }

    @Test
    fun intersectionRelativeDirections(){
        // Fake device location and pretend the device is pointing East.
        // -2.6577997643930757, 51.43041390383118
        val currentLocation = LngLatAlt(-2.6573400576040456, 51.430456817236575)
        val deviceHeading = 90.0
        val fovDistance = 50.0


        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonIntersectionStraight.intersectionStraightAheadFeatureCollection)
        // Get the roads from the tile
        val testRoadsCollectionFromTileFeatureCollection =
            getRoadsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // create FOV to pickup the roads
        val fovRoadsFeatureCollection = getFovRoadsFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testRoadsCollectionFromTileFeatureCollection
        )
        // Get the intersections from the tile
        val testIntersectionsCollectionFromTileFeatureCollection =
            getIntersectionsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // Create a FOV triangle to pick up the intersection (this intersection is a transition from
        // Weston Road to Long Ashton Road)
        val fovIntersectionsFeatureCollection = getFovIntersectionFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testIntersectionsCollectionFromTileFeatureCollection
        )
        val testNearestIntersection = getNearestIntersection(currentLocation,fovIntersectionsFeatureCollection)
        val testIntersectionRoadNames = getIntersectionRoadNames(testNearestIntersection, fovRoadsFeatureCollection)
        // what relative direction(s) are the road(s) that make up the nearest intersection?

        // first create a relative direction polygon and put it on the intersection node with the same
        // heading as the device
        val intersectionLocation = testNearestIntersection.features[0].geometry as Point
        val relativeDirections = getRelativeDirectionsPolygons(
            LngLatAlt(intersectionLocation.coordinates.longitude, intersectionLocation.coordinates.latitude),
            deviceHeading,
            fovDistance,
            RelativeDirections.COMBINED
        )

        // this should be clockwise from 6 o'clock
        // so the first will be the road we are on (direction 0)
        // the second road which makes up the intersection is ahead left (direction 3) etc.
        for (direction in relativeDirections){
            for (road in testIntersectionRoadNames) {
                val testReferenceCoordinateForward = getReferenceCoordinate(
                    road.geometry as LineString, 25.0, false)
                val iAmHere1 = polygonContainsCoordinates(
                    testReferenceCoordinateForward, (direction.geometry as Polygon))
                if (iAmHere1){
                    println("Road name: ${road.properties!!["name"]}")
                    println("Road direction: ${direction.properties!!["Direction"]}")
                }
                val testReferenceCoordinateReverse = getReferenceCoordinate(
                    road.geometry as LineString, 25.0, true
                )
                val iAmHere2 = polygonContainsCoordinates(testReferenceCoordinateReverse, (direction.geometry as Polygon))
                if (iAmHere2){
                    println("Road name: ${road.properties!!["name"]}")
                    println("Road direction: ${direction.properties!!["Direction"]}")
                }
            }
        }

    }
}