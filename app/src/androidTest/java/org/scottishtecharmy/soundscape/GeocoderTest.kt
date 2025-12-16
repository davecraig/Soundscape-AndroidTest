package org.scottishtecharmy.soundscape

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.ProtomapsGridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.AndroidGeocoder
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.LocalGeocoder
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.PhotonGeocoder
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.SoundscapeGeocoder
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import java.text.Normalizer
import java.util.Locale
import kotlin.time.measureTime

@RunWith(AndroidJUnit4::class)
class GeocoderTest {

    private suspend fun describeLocation(geocoder: SoundscapeGeocoder, location: LngLatAlt): LocationDescription? {
        val description = geocoder.getAddressFromLngLat(UserGeometry(location))
        return description
    }

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

    fun search(query: String, names: List<String>, limit: Int = 10): List<Pair<String, Float>> {
        val q = normalizeForSearch(query)
        return emptyList()
    }

    private fun estimateNumberOfPlacenames(gridState: GridState) {

        /**
         * This is looking ahead to search to see how large the dictionaries will be and how quickly
         * we can search them.
         */
        var count = 0
        val dictionary = mutableListOf<String>()
        var timed = measureTime {
            val pois = gridState.getFeatureTree(TreeId.POIS).getAllCollection()
            val roads = gridState.getFeatureTree(TreeId.ROADS_AND_PATHS).getAllCollection()
            pois.forEach { poi ->
                if (!(poi as MvtFeature).name.isNullOrEmpty()) {
                    dictionary.add(normalizeForSearch(poi.name!!))
                    count++
                }
            }
            roads.forEach { road ->
                if (!(road as Way).name.isNullOrEmpty()) {
                    dictionary.add(normalizeForSearch(road.name!!))
                    count++
                }
            }
        }
        Log.e("GeocoderTest", "Estimated number of placenames: $count, in $timed")
        var results = listOf<Pair<String, Float>>()
        timed = measureTime {
            val query = normalizeForSearch("10 Roselea Drive")
            results = search(query, dictionary, 10)
        }
        results.forEach { Log.e("GeocoderTest", "${it.first} ${it.second}") }
        Log.e("GeocoderTest", "Search took $timed")
    }

    private fun geocodeLocation(
        list: List<SoundscapeGeocoder>,
        location: LngLatAlt,
        gridState: GridState,
        settlementState: GridState
    ) {
        val cheapRuler = CheapRuler(location.latitude)
        runBlocking {
            // Update the grid states for this location
            gridState.locationUpdate(location, emptySet(), true)
            settlementState.locationUpdate(location, emptySet(), true)

            estimateNumberOfPlacenames(gridState)

            // Run the geocoders in parallel and wait for them all to complete
            val deferredResults = list.map { geocoder ->
                async { describeLocation(geocoder, location) }
            }
            val results = deferredResults.awaitAll()

            // Handle the results
            results.forEachIndexed { index, result ->
                if(result != null) {
                    val distance = cheapRuler.distance(result.location, location)
                    Log.e("GeocoderTest", "${distance}m from ${list[index]}: $result")
                }
            }
        }
    }

    /**
     * geocodeTest takes a handful of locations and runs them through all of our various geocoders
     * and prints out the results. This is to aid debugging of the LocalGeocoder whilst also giving
     * a better understanding of what the Photon and Android geocoders provide.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun geocodeTest() {
        runBlocking {
            // Context of the app under test.
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext

            val gridState = ProtomapsGridState()
            val settlementGrid = ProtomapsGridState(zoomLevel = 12, gridSize = 3, gridState.treeContext)

            val geocoderList = listOf(
                AndroidGeocoder(appContext),
                PhotonGeocoder(),
                LocalGeocoder(gridState, settlementGrid)
            )

            gridState.validateContext = false
            gridState.start(ApplicationProvider.getApplicationContext())
            settlementGrid.validateContext = false
            settlementGrid.start(ApplicationProvider.getApplicationContext())

            // Corner between 10 Craigdhu Road and 1 Ferguson Avenue Milngavie
            // House number mapped on OSM and Google
            val wellKnownLocation = LngLatAlt(-4.3215166, 55.9404307)
            geocodeLocation(geocoderList, wellKnownLocation, gridState, settlementGrid)

            // Corner of 28 Dougalston Gardens North, Milngavie.
            // House number not mapped on OSM, but Google has it
            val lessWellKnownLocation = LngLatAlt(-4.3078777, 55.9394283)
            geocodeLocation(geocoderList, lessWellKnownLocation, gridState, settlementGrid)

            // Junction of driveway for Baldernock Lodge and Craigmaddie Road near Baldernock
            // OSM doesn't know much at all, Google has all the information
            val ruralLocation = LngLatAlt(-4.2791516, 55.9465324)
            geocodeLocation(geocoderList, ruralLocation, gridState, settlementGrid)

            // On A809 along from Queens View car park
            // OSM knows about the car park, but doesn't return the road. Google is accurate to the
            // point that the car park is returned in the second result because it's further away.
            val veryRuralLocation = LngLatAlt(-4.387525, 55.995528)
            geocodeLocation(geocoderList, veryRuralLocation, gridState, settlementGrid)

            // Next to St. Giles Cathedral on the Royal Mile in Edinburgh
            val busyLocation = LngLatAlt(-3.1917130, 55.9494934)
            geocodeLocation(geocoderList, busyLocation, gridState, settlementGrid)
        }
    }
}