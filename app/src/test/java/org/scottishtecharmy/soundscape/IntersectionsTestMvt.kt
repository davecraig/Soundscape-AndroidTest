package org.scottishtecharmy.soundscape

import org.junit.Assert
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geoengine.callouts.getRoadsDescriptionFromFov
import org.scottishtecharmy.soundscape.geoengine.filters.NearestRoadFilter
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection

class IntersectionsTestMvt {
    private fun setupTest(currentLocation: LngLatAlt,
                          deviceHeading: Double,
                          fovDistance: Double) : FeatureCollection {

        val gridState = getGridStateForLocation(currentLocation)
        val nearestRoadFilter = NearestRoadFilter()
        nearestRoadFilter.update(
            location = currentLocation,
            locationAccuracy = 0.1,
            bearing = deviceHeading,
            bearingAccuracy = 0.1,
            timeInMilliseconds = 1000,
            gridState = gridState
        )
        val userGeometry = UserGeometry(
            location = currentLocation,
            phoneHeading = deviceHeading,
            fovDistance = fovDistance,
            nearestRoad = nearestRoadFilter.get())
        return getRoadsDescriptionFromFov(
                    gridState,
                    userGeometry
                ).intersectionRoads
    }
    @Test
    fun intersectionsStraightAheadType(){
        // This is the same test but using GeoJSON generated from a .Mvt tile NOT the original
        // GeoJSON from the Soundscape backend
        //  Road Switch
        //
        //  | ↑ |
        //  | B |
        //  |   |
        //  | ↑ |
        //  | * |
        //  |   |
        //  | A |
        //  | ↓ |

        // Weston Road (A) to Long Ashton Road (B)
        // https://geojson.io/#map=17/51.430494/-2.657463

        // Fake device location and pretend the device is pointing East.
        // -2.6577997643930757, 51.43041390383118
        val currentLocation = LngLatAlt(-2.6573400576040456, 51.430456817236575)
        val deviceHeading = 90.0
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // should be two roads that make up the intersection
        Assert.assertEquals(2, roadRelativeDirections.features.size )
        // they are Weston Road and Long Ashton Road and should be behind (0) and the other ahead (4)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Weston Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(4, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[1].properties!!["name"])

    }

    @Test
    fun intersectionsRightTurn(){
        //  Turn Right
        //   _____________
        //  |          B →
        //  | ↑  _________
        //  | * |
        //  |   |
        //  | A |
        //  | ↓ |

        // Belgrave Place (A) to Codrington Place (B)
        //https://geojson.io/#map=19/51.4579043/-2.6156923

        // Fake device location and pretend the device is pointing South West and we are located on:
        // Belgrave Place (A)
        val currentLocation = LngLatAlt(-2.615585745757045,51.457957257918395)
        val deviceHeading = 225.0 // South West
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // should be two roads that make up the intersection
        Assert.assertEquals(2, roadRelativeDirections.features.size )
        // they are Belgrave Place and Codrington Place and should be behind (0) and right (6)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Belgrave Place", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(6, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Codrington Place", roadRelativeDirections.features[1].properties!!["name"])

    }

    @Test
    fun intersectionsLeftTurn(){
        //  Turn Left
        //  _____________
        //  ← B          |
        //  _________  ↑ |
        //           | * |
        //           |   |
        //           | A |
        //           | ↓ |

        // same again just depends what road you are standing on
        // Codrington Place (A) to Belgrave Place (B)
        //https://geojson.io/#map=19/51.4579382/-2.6157338
        // Fake device location and pretend the device is pointing South East and we are standing on:
        // Codrington Place
        val currentLocation = LngLatAlt(-2.6159411752634583, 51.45799104056931)
        val deviceHeading = 135.0 // South East
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // should be two roads that make up the intersection
        Assert.assertEquals(2, roadRelativeDirections.features.size )
        // they are Codrington Place and Belgrave Place and should be behind (0) and left (2)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Codrington Place", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Belgrave Place", roadRelativeDirections.features[1].properties!!["name"])

    }

    @Test
    fun intersectionsSideRoadRight(){
        //  Side Road Right
        //
        //  | ↑ |
        //  | A |
        //  |   |_________
        //  |          B →
        //  | ↑  _________
        //  | * |
        //  |   |
        //  | A |
        //  | ↓ |
        //
        // Long Ashton Road (A) and St Martins (B)
        // https://geojson.io/#map=18/51.430741/-2.656311
        //

        // Fake device location and pretend the device is pointing South West and we are located on:
        // Long Ashton Road
        val currentLocation = LngLatAlt(-2.656109007812404,51.43079699441145)
        val deviceHeading = 250.0 // South West
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // There should now be three roads that make up the intersection:
        // The road that leads up to the intersection Long Ashton Road (0)
        // The road that continues on from the intersection Long Ashton Road (4)
        // The road that is the right turn St Martins (6)
        Assert.assertEquals(3, roadRelativeDirections.features.size )

        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(4, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(6, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("St Martins", roadRelativeDirections.features[2].properties!!["name"])

    }

    @Test
    fun intersectionsSideRoadLeft(){
        //  Side Road Left
        //
        //           | ↑ |
        //           | A |
        //  _________|   |
        //  ← B          |
        //  _________  ↑ |
        //           | * |
        //           |   |
        //           | A |
        //           | ↓ |

        // Long Ashton Road (A) and St Martins (B) same as above but location and device direction changed
        // https://geojson.io/#map=18/51.430741/-2.656311
        //
        // Fake device location and pretend the device is pointing North East and we are located on:
        // Long Ashton Road (A)
        val currentLocation = LngLatAlt(-2.656530323429564,51.43065207103919)
        val deviceHeading = 50.0 // North East
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // should be three roads that make up the intersection:
        // The road that lead up to the intersection Long Ashton Road (0)
        // The road that is the left turn St Martins (2)
        // The road that continues on from the intersection Long Ashton Road (4)
        Assert.assertEquals(3, roadRelativeDirections.features.size)

        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("St Martins", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(4, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[2].properties!!["name"])

    }

    @Test
    fun intersectionsT1Test(){
        //  T1
        //  ___________________
        //  ← B             B →
        //  _______     _______
        //         | ↑ |
        //         | * |
        //         |   |
        //         | A |
        //         | ↓ |

        // Standing on St Martins (A) with device pointing towards Long Ashton Road (B)
        // https://geojson.io/#map=18/51.430741/-2.656311


        // Fake device location and pretend the device is pointing South West and we are located on:
        // St Martins
        val currentLocation = LngLatAlt(-2.656540700657672,51.430978147982785)
        val deviceHeading = 140.0 // South East
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // should be three roads that make up the intersection:
        // The road that leads up to the intersection St Martins (0)
        // The road that is the T intersection Long Ashton Road left (2) and right (6)

        Assert.assertEquals(3, roadRelativeDirections.features.size )

        Assert.assertEquals(0, roadRelativeDirections.features[0].properties?.get("Direction") ?: "No idea")
        Assert.assertEquals("St Martins", roadRelativeDirections.features[0].properties?.get("name") ?: "No idea")
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties?.get("Direction") ?: "No idea")
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[1].properties?.get("name") ?: "No idea")
        Assert.assertEquals(6, roadRelativeDirections.features[2].properties?.get("Direction") ?: "No idea")
        Assert.assertEquals("Long Ashton Road", roadRelativeDirections.features[2].properties?.get("name") ?: "No idea")
    }

    @Test
    fun intersectionsT2Test(){
        //  T2
        //  ___________________
        //  ← B             C →
        //  _______     _______
        //         | ↑ |
        //         | * |
        //         |   |
        //         | A |
        //         | ↓ |

        // standing on Goodeve Road with device pointing towards SeaWalls Road (Left) and Knoll Hill (Right)
        // https://geojson.io/#map=18/51.472469/-2.637757

        // Fake device location and pretend the device is pointing South West and we are located on:
        // Goodeve Road  The Left is Seawalls Road and Right is Knoll Hill
        val currentLocation = LngLatAlt(-2.637514213827643, 51.472589063821175)
        val deviceHeading = 225.0 // South West
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        Assert.assertEquals(3, roadRelativeDirections.features.size )
        // Goodeve Road (0) Seawalls Road (2) and Knoll Hill (6)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Goodeve Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Seawalls Road", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(6, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("Knoll Hill", roadRelativeDirections.features[2].properties!!["name"])
    }

    @Test
    fun intersectionsCross1Test(){
        //  Cross1
        //         | ↑ |
        //         | A |
        //  _______|   |_______
        //  ← B             B →
        //  _______     _______
        //         | ↑ |
        //         | * |
        //         |   |
        //         | A |
        //         | ↓ |

        // Standing on Grange Road which continues on ahead and the left and right are Manilla Road
        // https://geojson.io/#map=18/51.4569979/-2.6185285

        // Fake device location and pretend the device is pointing North West and we are located on:
        // Grange Road  The Left and Right for the crossroad is Manilla Road and ahead is Grange Road
        val currentLocation = LngLatAlt(-2.61850147329568, 51.456953686378085)
        val deviceHeading = 340.0 // North North West
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        Assert.assertEquals(4, roadRelativeDirections.features.size )
        // Grange Road (0) and (4) Manilla Road Road (2) and (6)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Grange Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Manilla Road", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(4, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("Grange Road", roadRelativeDirections.features[2].properties!!["name"])
        Assert.assertEquals(6, roadRelativeDirections.features[3].properties!!["Direction"])
        Assert.assertEquals("Manilla Road", roadRelativeDirections.features[3].properties!!["name"])

    }

    @Test
    fun intersectionCross2Test(){
        //  Cross2
        //         | ↑ |
        //         | A |
        //  _______|   |_______
        //  ← B             C →
        //  _______     _______
        //         | ↑ |
        //         | * |
        //         |   |
        //         | A |
        //         | ↓ |

        // Standing on Lansdown Road which continues on ahead. Left is Manilla Road and Right is Vyvyan Road
        // https://geojson.io/#map=18/51.4571879/-2.6178348/-31.2/14
        // NOTE: This is a strange crossroads because it is actually made up of four different LineStrings
        // 2 x Lansdown Road LineStrings (would expect it to be made out of one)
        // and 1 Linestring  Manilla Road and 1 LineString Vyvyan Road

        // Fake device location and pretend the device is pointing North West and we are located on:
        // Lansdown Road and it continues on straight ahead.  The Left is Manilla Road and Right is Vyvyan Road
        val currentLocation = LngLatAlt(-2.6176822011131833, 51.457104175295484)
        val deviceHeading = 340.0 // North West
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        Assert.assertEquals(4, roadRelativeDirections.features.size )

        // Lansdown Road (0) and (4) Manilla Road (2) and Vyvyan Road(6)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Lansdown Road", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Manilla Road", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(4, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("Lansdown Road", roadRelativeDirections.features[2].properties!!["name"])
        Assert.assertEquals(6, roadRelativeDirections.features[3].properties!!["Direction"])
        Assert.assertEquals("Vyvyan Road", roadRelativeDirections.features[3].properties!!["name"])

    }

    @Test
    fun intersectionsCross3Test(){
        //  Cross3
        //         | ↑ |
        //         | D |
        //  _______|   |_______
        //  ← B             C →
        //  _______     _______
        //         | ↑ |
        //         | * |
        //         |   |
        //         | A |
        //         | ↓ |
        //
        // Example: Standing on St Mary's Butts with Oxford Road on Left, West Street Ahead and Broad Street on Right
        // https://geojson.io/#map=18/51.455426/-0.975279/-25.6
        // Fake device location and pretend the device is pointing North West and we are located on:
        //  Standing on St Mary's Butts with Oxford Road on Left, West Street Ahead and Broad Street on Right
        val currentLocation = LngLatAlt(-0.9752549546655587, 51.4553843453491)
        val deviceHeading = 320.0 // North West
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        Assert.assertEquals(4, roadRelativeDirections.features.size )

        // St Mary's Butts (0)  Oxford Road (2), West Street (4) and Broad Street (6)
        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("St Mary's Butts", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(2, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("Oxford Road", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(4, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("West Street", roadRelativeDirections.features[2].properties!!["name"])
        Assert.assertEquals(6, roadRelativeDirections.features[3].properties!!["Direction"])
        Assert.assertEquals("Broad Street", roadRelativeDirections.features[3].properties!!["name"])

    }

    @Test
    fun intersectionsLoopBackTest(){
        // Some intersections can contain the same road more than once,
        // for example if one road loops back to the intersection
        // https://geojson.io/#map=18/37.339112/-122.038756

        val currentLocation = LngLatAlt(-122.03856292573965,37.33916628666543)
        val deviceHeading = 270.0
        val fovDistance = 50.0

        val roadRelativeDirections = setupTest(currentLocation, deviceHeading, fovDistance)

        // Removed the duplicate osm_ids so we should be good to go...or not
        Assert.assertEquals(3, roadRelativeDirections.features.size )

        Assert.assertEquals(0, roadRelativeDirections.features[0].properties!!["Direction"])
        Assert.assertEquals("Kodiak Court", roadRelativeDirections.features[0].properties!!["name"])
        Assert.assertEquals(3, roadRelativeDirections.features[1].properties!!["Direction"])
        Assert.assertEquals("service", roadRelativeDirections.features[1].properties!!["name"])
        Assert.assertEquals(5, roadRelativeDirections.features[2].properties!!["Direction"])
        Assert.assertEquals("service", roadRelativeDirections.features[2].properties!!["name"])
    }

    /*@Test
    fun debugUtil(){
        val currentLocation = LngLatAlt(-0.9752549546655587, 51.4553843453491)
        // mvt tile so zoom at 15
        val slippyTileName = getXYTile(currentLocation.latitude, currentLocation.longitude, 15)
        // output wget to grab the tile from backend
        println("wget https://d1wzlzgah5gfol.cloudfront.net/protomaps/15/${slippyTileName.first}/${slippyTileName.second}.pbf -O ${slippyTileName.first}x${slippyTileName.second}.mvt")
        val geoJson = vectorTileToGeoJsonFromFile(slippyTileName.first, slippyTileName.second, "${slippyTileName.first}x${slippyTileName.second}.mvt")

        val adapter = GeoJsonObjectMoshiAdapter()
        val outputFile = FileOutputStream("${slippyTileName.first}x${slippyTileName.second}.geojson")
        outputFile.write(adapter.toJson(geoJson).toByteArray())
        outputFile.close()

    }

    private fun vectorTileToGeoJsonFromFile(
        tileX: Int,
        tileY: Int,
        filename: String,
        cropPoints: Boolean = true
    ): FeatureCollection {

        val path = "src/test/res/org/scottishtecharmy/soundscape/"
        val remoteTile = FileInputStream(path + filename)
        val tile: VectorTile.Tile = VectorTile.Tile.parseFrom(remoteTile)

        val featureCollection = vectorTileToGeoJson(tileX, tileY, tile, cropPoints, 15)

        return featureCollection
    }*/
}