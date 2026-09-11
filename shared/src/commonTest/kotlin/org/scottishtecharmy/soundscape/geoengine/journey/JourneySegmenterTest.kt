package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * With recording always on, "the journey I just took" has to be inferred rather than marked. These
 * pin down where one journey is judged to have ended and the next begun.
 */
class JourneySegmenterTest {

    private val degreesPerMetre = 1.0 / 111_320.0
    private val minute = 60_000L

    private fun anchor(northMetres: Double, atMillis: Long, speed: Double = 1.4) =
        JourneyEvent.Anchor(
            location = LngLatAlt(0.0, northMetres * degreesPerMetre),
            timestampMillis = atMillis,
            speedMps = speed,
            inVehicle = false,
        )

    private fun landmark(northMetres: Double, atMillis: Long, name: String) =
        JourneyEvent.Landmark(
            location = LngLatAlt(0.0, northMetres * degreesPerMetre),
            timestampMillis = atMillis,
            name = name,
        )

    /** Anchors every minute, walking steadily north from [fromMetres]. */
    private fun walk(fromMetres: Double, fromMillis: Long, minutes: Int, metresPerMinute: Double = 100.0) =
        (0 until minutes).map { i ->
            anchor(fromMetres + i * metresPerMinute, fromMillis + i * minute)
        }

    /** Anchors every minute at the same spot - what standing still looks like in the record. */
    private fun stand(atMetres: Double, fromMillis: Long, minutes: Int) =
        (0 until minutes).map { i -> anchor(atMetres, fromMillis + i * minute, speed = 0.0) }

    @Test
    fun returnsNothingWhenThereIsNoRecord() {
        assertNull(JourneySegmenter.lastJourney(emptyList()))
    }

    @Test
    fun returnsNothingWhenTheUserHasBarelyMoved() {
        // A hundred metres to the corner shop and back is not a route worth saving.
        val events = walk(0.0, 0L, minutes = 2, metresPerMinute = 50.0)
        assertNull(JourneySegmenter.lastJourney(events))
    }

    @Test
    fun takesTheWholeRecordWhenTheUserNeverStopped() {
        val events = walk(0.0, 0L, minutes = 10)
        val journey = assertNotNull(JourneySegmenter.lastJourney(events))
        assertEquals(events.size, journey.size)
    }

    @Test
    fun startsAfterALongStop() {
        // Out, a long sit down, then home again. Only the way home is "the last journey".
        val out = walk(0.0, 0L, minutes = 8)
        val sitting = stand(800.0, 8 * minute, minutes = 10)
        val back = walk(800.0, 18 * minute, minutes = 8)

        val journey = assertNotNull(JourneySegmenter.lastJourney(out + sitting + back))

        assertTrue(journey.first().timestampMillis >= (8 + 9) * minute)
        assertEquals(back.last().timestampMillis, journey.last().timestampMillis)
    }

    @Test
    fun doesNotSplitOnAShortStop() {
        // Waiting a couple of minutes at a crossing, or in traffic, is part of the journey.
        val first = walk(0.0, 0L, minutes = 5)
        val waiting = stand(500.0, 5 * minute, minutes = 2)
        val rest = walk(500.0, 7 * minute, minutes = 5)

        val journey = assertNotNull(JourneySegmenter.lastJourney(first + waiting + rest))
        assertEquals(0L, journey.first().timestampMillis)
    }

    @Test
    fun savesTheJourneyYouHaveJustArrivedFrom() {
        // The whole point of the feature: walk somewhere, sit down, then ask to save it. Anchors
        // keep coming on their timer while sitting, so the arrival looks exactly like the stop that
        // ends a journey - but it ends *this* one, and there is nothing after it to save instead.
        val walked = walk(0.0, 0L, minutes = 20)
        val satDown = stand(2000.0, 20 * minute, minutes = 6)

        val journey = assertNotNull(JourneySegmenter.lastJourney(walked + satDown))
        assertTrue(
            JourneySegmenter.travelledMetres(journey) > 1500.0,
            "only got ${JourneySegmenter.travelledMetres(journey).toInt()}m",
        )
    }

    @Test
    fun startsAfterAHoleInTheRecord() {
        // The service was killed, or the phone went in a bag underground. Whatever happened across
        // the gap was not recorded, so it cannot be part of the route.
        val before = walk(0.0, 0L, minutes = 5)
        val after = walk(5000.0, 30 * minute, minutes = 6)

        val journey = assertNotNull(JourneySegmenter.lastJourney(before + after))
        assertEquals(30 * minute, journey.first().timestampMillis)
    }

    @Test
    fun keepsTheWaypointsThatFallInsideTheJourney() {
        val out = walk(0.0, 0L, minutes = 6)
        val sitting = stand(600.0, 6 * minute, minutes = 8)
        val back = walk(600.0, 14 * minute, minutes = 6)

        val events = out + listOf(landmark(100.0, 1 * minute, "Tesco")) +
            sitting + back + listOf(landmark(900.0, 17 * minute, "Boots"))

        val journey = assertNotNull(JourneySegmenter.lastJourney(events.sortedBy { it.timestampMillis }))
        val names = journey.filterIsInstance<JourneyEvent.Landmark>().map { it.name }
        assertEquals(listOf("Boots"), names)
    }

    @Test
    fun neverReachesFurtherBackThanTheDurationCap() {
        val events = walk(0.0, 0L, minutes = 6 * 60)
        val journey = assertNotNull(JourneySegmenter.lastJourney(events))

        val span = journey.last().timestampMillis - journey.first().timestampMillis
        assertTrue(span <= JOURNEY_MAX_DURATION_MILLIS, "span was $span")
    }

    @Test
    fun measuresHowFarTheJourneyWent() {
        val events = walk(0.0, 0L, minutes = 11, metresPerMinute = 100.0)
        // 1% of tolerance because degreesPerMetre here is a round number rather than the exact
        // figure for this latitude that CheapRuler works with. The point is that the ten gaps are
        // summed, not that the constant is precise.
        assertEquals(1000.0, JourneySegmenter.travelledMetres(events), 10.0)
    }
}
