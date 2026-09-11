@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.callouts.AutoCallout
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.utils.FeatureTree
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geoengine.utils.getDestinationCoordinate
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The places the user walks past reach the journey record through AutoCallout, at the moment each
 * one is announced. A hook that silently stopped firing would leave journeys with turns but no
 * landmarks and nothing to show for it, so this drives the real callout path rather than calling
 * JourneyRecorder.onLandmark directly.
 */
class JourneyLandmarkCaptureTest {

    private val origin = LngLatAlt(-2.657, 51.430)

    private fun poi(name: String, bearing: Double, metres: Double) = MvtFeature().apply {
        this.name = name
        superCategory = SuperCategoryId.LANDMARK
        geometry = Point(getDestinationCoordinate(origin, bearing, metres))
    }

    private fun gridWith(vararg pois: MvtFeature): GridState {
        val collection = FeatureCollection().apply { pois.forEach { addFeature(it) } }
        return GridState().apply {
            validateContext = false
            featureTrees[TreeId.SELECTED_SUPER_CATEGORIES.id] = FeatureTree(collection)
        }
    }

    /** Walking north at 1.4 m/s, which is the mode the pedestrian POI callout runs in. */
    private fun walking(at: LngLatAlt = origin, timestampMillis: Long = 1_000L) = UserGeometry(
        location = at,
        phoneHeading = 0.0,
        headingMode = UserGeometry.HeadingMode.Phone,
        speed = 1.4,
        timestampMilliseconds = timestampMillis,
    )

    @Test
    fun aPlaceAnnouncedWhileWalkingIsRememberedForTheJourney() {
        val recorder = JourneyRecorder()
        val autoCallout = AutoCallout(null, null).apply { journeyRecorder = recorder }
        val grid = gridWith(poi("Tesco", bearing = 0.0, metres = 20.0))

        val callout = autoCallout.updateLocation(walking(), grid, GridState())

        // The callout itself has to have happened, or this test proves nothing about the hook.
        assertNotNull(callout, "expected the POI to be called out")
        assertTrue(callout.trackedText.contains("Tesco"), "callout was '${callout.trackedText}'")

        val landmark = recorder.snapshot().filterIsInstance<JourneyEvent.Landmark>().single()
        assertEquals("Tesco", landmark.name)
    }

    @Test
    fun theLandmarkIsRecordedWhereTheUserWasNotWhereThePlaceIs() {
        val recorder = JourneyRecorder()
        val autoCallout = AutoCallout(null, null).apply { journeyRecorder = recorder }
        val grid = gridWith(poi("Tesco", bearing = 0.0, metres = 30.0))

        autoCallout.updateLocation(walking(), grid, GridState())

        val landmark = recorder.snapshot().filterIsInstance<JourneyEvent.Landmark>().single()
        // A replaying user's beacon has to point along the road they walked, not off it at a shop
        // they merely went past.
        assertEquals(origin.latitude, landmark.location.latitude, 1e-9)
        assertEquals(origin.longitude, landmark.location.longitude, 1e-9)
    }

    @Test
    fun nothingIsRememberedWhenTheRecorderIsTurnedOff() {
        val recorder = JourneyRecorder().apply { enabled = false }
        val autoCallout = AutoCallout(null, null).apply { journeyRecorder = recorder }
        val grid = gridWith(poi("Tesco", bearing = 0.0, metres = 20.0))

        assertNotNull(autoCallout.updateLocation(walking(), grid, GridState()))
        assertTrue(recorder.snapshot().isEmpty())
    }
}
