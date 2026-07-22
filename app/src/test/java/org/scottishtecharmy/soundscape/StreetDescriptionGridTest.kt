package org.scottishtecharmy.soundscape

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.scottishtecharmy.soundscape.MainActivity.Companion.MOBILITY_KEY
import org.scottishtecharmy.soundscape.MainActivity.Companion.PLACES_AND_LANDMARKS_KEY
import org.scottishtecharmy.soundscape.geoengine.GRID_SIZE
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.MAX_ZOOM_LEVEL
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.StreetDescription
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.TileSearch
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/**
 * Bounding box + spacing for the location grid. GitHub Actions runners only have 2 CPUs, so CI
 * uses a small, quick grid over central Glasgow; a full local run (more cores available) instead
 * covers the whole of the glasgow-gb.pmtiles extract, which spans a much wider area (Ayr to
 * Edinburgh to Perth - see .github/fixtures/glasgow-gb.pmtiles.geojson for the exact bounds).
 */
private data class GridBounds(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
    val spacingMeters: Double
)

// Central Glasgow (city centre, West End, Southside, East End) - used on CI.
private val CENTRAL_GLASGOW_BOUNDS = GridBounds(
    minLon = -4.40, maxLon = -4.15,
    minLat = 55.80, maxLat = 55.90,
    spacingMeters = 200.0
)

// The full glasgow-gb.pmtiles extract - used for local runs only.
private val FULL_EXTRACT_BOUNDS = GridBounds(
    minLon = -5.45, maxLon = -2.33,
    minLat = 54.99, maxLat = 56.85,
    spacingMeters = 500.0
)

private val isRunningOnGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"

private val gridBounds = CENTRAL_GLASGOW_BOUNDS //if (isRunningOnGitHubActions) CENTRAL_GLASGOW_BOUNDS else FULL_EXTRACT_BOUNDS

// Radius/count used to discover named streets near a grid point.
const val NEARBY_STREET_SEARCH_RADIUS_METERS = 150.0
const val NEARBY_STREET_SEARCH_MAX_COUNT = 20

private fun buildGlasgowGrid(bounds: GridBounds): List<LngLatAlt> {
    val meanLat = (bounds.minLat + bounds.maxLat) / 2.0
    val ruler = CheapRuler(meanLat)

    val southWest = LngLatAlt(bounds.minLon, bounds.minLat)
    val northDistance = ruler.distance(
        southWest,
        LngLatAlt(bounds.minLon, bounds.maxLat)
    )
    val eastDistance = ruler.distance(
        southWest,
        LngLatAlt(bounds.maxLon, bounds.minLat)
    )
    val rows = ceil(northDistance / bounds.spacingMeters).toInt()
    val columns = ceil(eastDistance / bounds.spacingMeters).toInt()

    val points = mutableListOf<LngLatAlt>()
    for (row in 0..rows) {
        val rowOrigin = ruler.destination(southWest, row * bounds.spacingMeters, 0.0)
        for (column in 0..columns) {
            points.add(ruler.destination(rowOrigin, column * bounds.spacingMeters, 90.0))
        }
    }
    return points
}

/**
 * Finds the distinct names of named roads within NEARBY_STREET_SEARCH_RADIUS_METERS of location,
 * using the same ROADS tree that TileSearch.findNearestNamedWay queries.
 */
private fun findNearbyStreetNames(gridState: GridState, location: LngLatAlt): List<String> {
    val nearestWays = gridState.getFeatureTree(TreeId.ROADS).getNearestCollection(
        location,
        NEARBY_STREET_SEARCH_RADIUS_METERS,
        NEARBY_STREET_SEARCH_MAX_COUNT,
        gridState.ruler
    )
    return nearestWays.mapNotNull { (it as MvtFeature?)?.name }.distinct()
}

/**
 * Bulk regression test for StreetDescription, extending the single hand-picked location in
 * interpolatedAddressSearchWithPolygonHouseNumber to a grid of real locations across central
 * Glasgow. For every named street found near every grid point, it drives StreetDescription
 * through the interpolation, reverse-lookup and callout-description paths against real
 * glasgow-gb.pmtiles data, to catch crashes that a single hand-picked address wouldn't surface.
 */
class StreetDescriptionGridTest {
    @Test
    fun streetDescriptionGridAcrossGlasgow() {
        val gridPoints = buildGlasgowGrid(gridBounds)
        val numWorkers = minOf(Runtime.getRuntime().availableProcessors(), 8)
        val chunkSize = (gridPoints.size + numWorkers - 1) / numWorkers
        val chunks = gridPoints.chunked(chunkSize)

        val createDescriptionCalls = AtomicLong(0)
        val getLocationFromStreetNumberCalls = AtomicLong(0)

        runBlocking {
            val jobs = chunks.map { chunk ->
                async(Dispatchers.Default) {
                    val gridState = FileGridState(MAX_ZOOM_LEVEL, GRID_SIZE)
                    gridState.start(null, offlineExtractPath)
                    val settlementGridState = FileGridState(12, 3)
                    settlementGridState.start(null, offlineExtractPath)
                    val tileSearch = TileSearch(offlineExtractPath, gridState, settlementGridState)
                    val enabledCategories = setOf(PLACES_AND_LANDMARKS_KEY, MOBILITY_KEY)

                    try {
                        for (point in chunk) {
                            gridState.locationUpdate(point, enabledCategories)
                            settlementGridState.locationUpdate(point, emptySet())

                            for (streetName in findNearbyStreetNames(gridState, point)) {
                                val nearestWay =
                                    tileSearch.findNearestNamedWay(point, streetName) ?: continue

                                try {
                                    val streetDescription = StreetDescription(streetName, gridState)
                                    streetDescription.createDescription(nearestWay, null)
                                    createDescriptionCalls.incrementAndGet()

                                    // 1. Interpolation path: every house number actually observed
                                    // on the street (interpolatedAddressSearchWithPolygonHouseNumber
                                    // only tries one hand-picked number on one street).
                                    val observedNumbers =
                                        (streetDescription.leftSortedNumbers.values +
                                            streetDescription.rightSortedNumbers.values)
                                            .mapNotNull { it.housenumber }
                                    for (number in observedNumbers) {
                                        streetDescription.getLocationFromStreetNumber(number)
                                        getLocationFromStreetNumberCalls.incrementAndGet()
                                    }

                                    // 2. Reverse-lookup path: location -> nearest house number.
                                    streetDescription.getStreetNumber(nearestWay, point)

                                    // 3. Callout-description path: ahead/behind description.
                                    streetDescription.describeLocation(point, null, nearestWay, null)
                                } catch (e: Throwable) {
                                    throw AssertionError(
                                        "StreetDescription failed at (${point.longitude}, " +
                                            "${point.latitude}) for street \"$streetName\": " +
                                            "${e.message}",
                                        e
                                    )
                                }
                            }
                        }
                    } finally {
                        gridState.stop()
                    }
                }
            }
            jobs.awaitAll()
        }

        println(
            "streetDescriptionGridAcrossGlasgow: createDescription calls = " +
                "${createDescriptionCalls.get()}, getLocationFromStreetNumber calls = " +
                "${getLocationFromStreetNumberCalls.get()}"
        )
    }
}
