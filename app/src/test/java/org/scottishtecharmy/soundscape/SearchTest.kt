package org.scottishtecharmy.soundscape

import ch.poole.geo.pmtiles.Reader
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.GRID_SIZE
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.formatDistanceAndDirection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.decompressTile
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.StreetDescription
import org.scottishtecharmy.soundscape.geoengine.utils.getXYTile
import org.scottishtecharmy.soundscape.geoengine.utils.searchFeaturesByName
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.utils.findExtractPaths
import org.scottishtecharmy.soundscape.utils.levenshteinDamerauRatio
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.collections.set
import kotlin.time.measureTime

class SearchTest {

    val stringCache = mutableMapOf<Long, List<String>>()

    private val apostrophes = setOf('\'', '’', '‘', '‛', 'ʻ', 'ʼ', 'ʹ', 'ꞌ', '＇')

    fun normalizeForSearch(input: String): String {
        // 1) Unicode normalize (decompose accents)
        val nfkd = Normalizer.normalize(input, Normalizer.Form.NFKD)

        val sb = StringBuilder(nfkd.length)
        var lastWasSpace = false

        for (ch in nfkd) {
            // Remove combining marks (diacritics)
            val type = Character.getType(ch)
            if (type == Character.NON_SPACING_MARK.toInt()) continue

            // Make apostrophes disappear completely (missing/extra apostrophes become irrelevant)
            if (ch in apostrophes) continue

            // Turn most punctuation into spaces (keeps token boundaries stable)
            val isLetterOrDigit = Character.isLetterOrDigit(ch)
            val outCh = when {
                isLetterOrDigit -> ch.lowercaseChar()
                Character.isWhitespace(ch) -> ' '
                else -> ' ' // punctuation -> space
            }

            if (outCh == ' ') {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(outCh)
                lastWasSpace = false
            }
        }

        return sb.toString().trim().lowercase(Locale.ROOT)
    }

    private fun localSearch(
        location: LngLatAlt,
        searchString: String
    ) : List<String> {

        val tileLocation = getXYTile(location, MAX_ZOOM_LEVEL)
        val extracts = findExtractPaths(offlineExtractPath).toMutableList()
        var reader : Reader? = null
        for(extract in extracts) {
            reader = Reader(File(extract))
            if(reader.getTile(MAX_ZOOM_LEVEL, tileLocation.first, tileLocation.second) != null)
                break
        }

        // We now have a PM tile reader
        var x = tileLocation.first
        var y = tileLocation.second

        var dx = 1 // Change in x per step
        var dy = 0 // Change in y per step

        var steps = 1 // Number of steps to take in the current direction
        var turnCount = 0
        var stepsTaken = 0

        // Set a limit to how far out you want to spiral
        val maxSearchRadius = 10
        val maxTurns = maxSearchRadius * 2

        val normalizedNeedle = normalizeForSearch(searchString)
        val searchResults = mutableListOf<String>()

        while (turnCount < maxTurns) {
            val tileIndex = x.toLong() + (y.toLong().shl(32))
            var cache = stringCache[tileIndex]
            if(cache == null) {
                // Load the tile and add all of its String to a cache
                cache = mutableListOf()
                println("Get tile: ($x, $y)")
                val tileData = reader?.getTile(MAX_ZOOM_LEVEL, x, y)
                if (tileData != null) {
                    val tile = decompressTile(reader.tileCompression, tileData)
                    if(tile != null) {
                        for(layer in tile.layersList) {
                            if(layer.name == "transportation") {
                                for (value in layer.valuesList) {
                                    if (value.hasStringValue()) {
                                        cache.add(normalizeForSearch(value.stringValue))
                                    }
                                }
                            }
                        }
                        stringCache[tileIndex] = cache
                    }
                }
            }
            for(string in cache) {

                val score = normalizedNeedle.levenshteinDamerauRatio(string)
                if(score < 0.3) {
                    println("Found $searchString as $string (score $score)")
                    searchResults += string
                }
            }
            // --- 2. Move to the next position in the spiral ---
            x += dx
            y += dy
            stepsTaken++

            // --- 3. Check if it's time to turn ---
            if (stepsTaken == steps) {
                stepsTaken = 0
                turnCount++

                // Rotate direction: (1,0) -> (0,1) -> (-1,0) -> (0,-1)
                val temp = dx
                dx = -dy
                dy = temp

                // After every two turns, increase the number of steps
                if (turnCount % 2 == 0) {
                    steps++
                }
            }
        }
        return emptyList()
    }

    @Test
    fun offlineSearch() {
        runBlocking {

            val currentLocation = LngLatAlt(-4.3215166, 55.9404307)
            var cacheSize = 0
            stringCache.forEach { set -> cacheSize += set.value.size }
            var time = measureTime {
                localSearch(currentLocation, "Milverton Avenue")
            }
            println("Time taken round 1: $time (cache size $cacheSize strings)")
            cacheSize = 0
            stringCache.forEach { set -> cacheSize += set.value.size }
            time = measureTime {
                localSearch(currentLocation, "Milverto Avenue")
            }
            println("Time taken round 2: $time (cache size $cacheSize strings)")

            cacheSize = 0
            stringCache.forEach { set -> cacheSize += set.value.size }
            time = measureTime {
                localSearch(currentLocation, "Roselea Dr")
            }
            println("$time (cache size $cacheSize strings)")

            cacheSize = 0
            stringCache.forEach { set -> cacheSize += set.value.size }
            time = measureTime {
                localSearch(currentLocation, "Dirleton Gate")
            }
            println("$time (cache size $cacheSize strings)")

            cacheSize = 0
            stringCache.forEach { set -> cacheSize += set.value.size }
            time = measureTime {
                localSearch(currentLocation, "Dirleton Gate")
            }
            println("$time (cache size $cacheSize strings)")
        }
    }

    @Test
    fun testSearch() {
        // This does a really crude search through the "name" property of the POI features
        val gridState = getGridStateForLocation(sixtyAcresCloseTestLocation, MAX_ZOOM_LEVEL, GRID_SIZE)
        val testPoiCollection = gridState.getFeatureCollection(TreeId.POIS)

        // As there isn't much going on in the tiles then it should return the local village shop/coffee place
        val searchResults = searchFeaturesByName(testPoiCollection, "honey")
        Assert.assertEquals(2, searchResults.features.size)
    }

    @Test
    fun testHouseNumbers() {
        val bearsden = LngLatAlt(-4.3067702, 55.9473970)
        val gridState = getGridStateForLocation(bearsden, MAX_ZOOM_LEVEL, GRID_SIZE)

        // We have a tree per street and finding the nearest house is a case of looking up the
        // street and then doing a single search.
        val streetTrees = gridState.gridStreetNumberTreeMap

        val streetName = "Heathfield Drive"

        // Most of the code here is getting some locations along a known road to walk along
        val roadTree = gridState.getFeatureTree(TreeId.ROADS)
        var way : Way? = null
        for(road in roadTree.getAllCollection()) {
            if((road as Way).name == streetName) {
                way = road
                break
            }
        }
        if(way != null) {
            val ways = mutableListOf<Pair<Boolean, Way>>()
            val intersection = way.intersections[WayEnd.END.id] ?: way.intersections[WayEnd.START.id]
            if(intersection != null) {
                way.followWays(intersection, ways)

                for (segment in ways) {
                    for(point in (segment.second.geometry as LineString).coordinates) {
                        val nearestHouse = streetTrees[streetName]?.getNearestFeature(
                            point,
                            gridState.ruler
                        ) as? MvtFeature
                        if (nearestHouse != null) {
                            val distance = gridState.ruler.distance(
                                point,
                                (nearestHouse.geometry as Point).coordinates
                            )
                            println("${nearestHouse.name} $streetName - $distance from $point")
                        }
                    }
                }
            }
        }
    }

    fun streetDescription(location: LngLatAlt,
                          streetName: String,
                          describeLocations: List<LngLatAlt>) {
        val gridState = getGridStateForLocation(location, MAX_ZOOM_LEVEL, GRID_SIZE)
        val nearbyWays = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
            .getNearestCollection(
                location,
                100.0,
                5,
                gridState.ruler
            )
        var matchedWay: Way? = null
        for(way in nearbyWays) {
            if((way as Way).name == streetName) {
                matchedWay = way
                break
            }
        }
        if(matchedWay == null) return

        val description = StreetDescription(streetName, gridState)
        description.createDescription(matchedWay)
        description.describeStreet()
        for(location in describeLocations) {
            val describeFeature = MvtFeature()
            describeFeature.geometry = Point(location.longitude, location.latitude)
            val nearestWay = description.nearestWayOnStreet(describeFeature)
            if(nearestWay != null) {
                val houseNumber = description.getStreetNumber(nearestWay.first, location)
                println("Interpolated address: ${if(houseNumber.second) "Opposite" else ""} ${houseNumber.first} ${nearestWay.first.name}")
                val result = description.describeLocation(
                    UserGeometry(
                        location = location,
                        mapMatchedWay = nearestWay.first,
                        phoneHeading = 90.0
                    ),
                    null
                )

                if (
                    (result.ahead.distance < 10.0) &&
                    ((result.ahead.distance < result.behind.distance) || result.behind.name.isEmpty()))
                {
                    println("At ${result.ahead.name}")
                }
                else if (result.behind.distance < 10.0) {
                    println("At ${result.behind.name}")
                }
                else if(result.behind.name.isNotEmpty() && result.ahead.name.isNotEmpty()) {
                    // TODO: Ideally we'd know the user direction here so that we could give the fraction
                    //  as an increasing value e.g. half way, three quarters of the way etc.
                    val fraction = result.behind.distance/(result.behind.distance + result.ahead.distance)
                    when (fraction) {
                        in 0.2..0.3 -> println("Quarter of the way between: ${result.behind.name} and ${result.ahead.name}")
                        in 0.4..0.6 -> println("Half way between: ${result.behind.name} and ${result.ahead.name}")
                        in 0.7..0.8 -> println("Three quarters way between: ${result.behind.name} and ${result.ahead.name}")
                        else -> {
                            if(result.ahead.distance < result.behind.distance)
                                println("Between: ${result.ahead.name} and ${result.behind.name}, ${result.ahead.distance} from ${result.ahead.name}")
                            else
                                println("Between: ${result.behind.name} and ${result.ahead.name}, ${result.behind.distance} from ${result.behind.name}")
                        }
                    }
                }
                else if(result.behind.distance < result.ahead.distance) {
                    val distance = formatDistanceAndDirection(result.behind.distance, null, null)
                    println("$distance along from ${result.behind.name}")
                } else {
                    val distance = formatDistanceAndDirection(result.ahead.distance, null, null)
                    println("$distance along from ${result.ahead.name}")
                }
            }
        }
    }

    @Test
    fun testStreetDescription() {
        streetDescription(
            LngLatAlt(-4.3060126, 55.9474004),
            "Roselea Drive",
            listOf(
                LngLatAlt(-4.3054676, 55.9469630) // 2-4 Roselea Drive
            )
        )

        streetDescription(
            LngLatAlt(-4.3133672, 55.9439536),
            "Buchanan Street",
            listOf(
                LngLatAlt(-4.3130768, 55.9446026)
            )
        )
        // Opposite test
        streetDescription(
            LngLatAlt(-4.3133672, 55.9439536),
            "Buchanan Street",
            listOf(
                LngLatAlt(-4.3135689, 55.9440448)
            )
        )
        streetDescription(
            LngLatAlt(-4.3177683, 55.9415574),
            "Douglas Street",
            listOf(
                LngLatAlt(-4.3186897, 55.9410192)
            )
        )
        streetDescription(
            LngLatAlt(-4.2627887, 55.8622846),
            "St Vincent Street",
            listOf(
                LngLatAlt(-4.2637612, 55.8622651)
            )
        )
        streetDescription(
            LngLatAlt(-4.2627887, 55.8622846),
            "St Vincent Street",
            listOf(
                LngLatAlt(-4.2642336, 55.8624708)
            )
        )

        streetDescription(
            LngLatAlt(-4.2559200, 55.8645353),
            "Sauchiehall Street",
            listOf(
                LngLatAlt(-4.2544240, 55.8644774),  // Ryman
                LngLatAlt(-4.2553615, 55.8643427),              // Jollibee
                LngLatAlt(-4.2559443, 55.8644524),              // Art piece
                LngLatAlt(-4.2566746, 55.8647423),              // Route One
                LngLatAlt(-4.2584339, 55.8647259),              // Waterstones
            )
        )
    }

    private fun testLineSearchLocation(location: LngLatAlt, streetName: String) {
        // Search for house numbers near Buchanan Street. There are two which don't have the street
        // address in the OSM data.
        val gridState = getGridStateForLocation(location, MAX_ZOOM_LEVEL, GRID_SIZE)

        val nearbyWays = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS)
            .getNearestCollection(
                location,
                100.0,
                5,
                gridState.ruler
            )
        var matchedWay: Way? = null
        for(way in nearbyWays) {
            if((way as Way).name == streetName) {
                matchedWay = way
                break
            }
        }
        assert(matchedWay != null)

        // Look for nearby street numbers which didn't have a named street
        val tree = gridState.gridStreetNumberTreeMap["null"]

        val results = tree!!.getNearbyLine(
            matchedWay!!.geometry as LineString,
            50.0,
            gridState.ruler
        )
        results.forEach { println((it as MvtFeature).name) }

        // Look POI near the road
        val poiTree = gridState.getFeatureTree(TreeId.POIS)
        val poiResults = poiTree.getNearbyLine(
            matchedWay.geometry as LineString,
            25.0,
            gridState.ruler
        )
        poiResults.forEach { println((it as MvtFeature).name) }

        // These searches form the basis of extending StreetDescription beyond Features which contain
        // the street address.
    }

    @Test
    fun testLineSearch() {
        val buchananStreet = LngLatAlt(-4.3134938, 55.9449487)
        testLineSearchLocation(buchananStreet, "Buchanan Street")

        val buchananStreet2 = LngLatAlt( -4.3136986, 55.9455014)
        testLineSearchLocation(buchananStreet2, "Buchanan Street")
    }
}