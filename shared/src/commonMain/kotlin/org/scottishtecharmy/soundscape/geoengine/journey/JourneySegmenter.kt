package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.geoengine.utils.rulers.createCheapRuler

/**
 * A journey ends where the user stopped for long enough that the next bit of travel is a separate
 * trip. Sitting in traffic or waiting at a platform is not stopping.
 */
const val JOURNEY_STATIONARY_GAP_MILLIS = 5 * 60 * 1000L
const val JOURNEY_STATIONARY_GAP_METRES = 40.0

/** No events at all for this long means the service restarted, or the phone lost the sky. */
const val JOURNEY_EVENT_GAP_MILLIS = 10 * 60 * 1000L

/** However long the user has been out, one Route is not worth more than this. */
const val JOURNEY_MAX_DURATION_MILLIS = 4 * 60 * 60 * 1000L

/** Below this a "journey" is not worth saving, and saying so is better than saving a stub. */
const val JOURNEY_MINIMUM_METRES = 200.0

/**
 * Picks "the journey I just took" out of the recorder's rolling buffer.
 *
 * With recording always on there is no start button to mark where a journey began, so it has to be
 * inferred: the journey starts at the end of the last time the user stayed put for a while.
 */
object JourneySegmenter {

    /**
     * @return the events belonging to the most recent journey, oldest first, or null when there
     * isn't one worth saving yet.
     */
    fun lastJourney(events: List<JourneyEvent>): List<JourneyEvent>? {
        if (events.isEmpty()) return null

        val endMillis = events.last().timestampMillis
        var startMillis = events.first().timestampMillis

        endOfLastStop(events.filterIsInstance<JourneyEvent.Anchor>())?.let {
            startMillis = maxOf(startMillis, it)
        }

        // A hole in the record is a break too, whatever the user was doing across it.
        for (i in 1 until events.size) {
            if (events[i].timestampMillis - events[i - 1].timestampMillis >= JOURNEY_EVENT_GAP_MILLIS) {
                startMillis = maxOf(startMillis, events[i].timestampMillis)
            }
        }

        startMillis = maxOf(startMillis, endMillis - JOURNEY_MAX_DURATION_MILLIS)

        val journey = events.filter { it.timestampMillis >= startMillis }
        return if (travelledMetres(journey) >= JOURNEY_MINIMUM_METRES) journey else null
    }

    /**
     * The timestamp of the last anchor of the last stop, or null if the user never stopped.
     *
     * A stop is a run of anchors that all stay within [JOURNEY_STATIONARY_GAP_METRES] of the one
     * that started the run, spanning at least [JOURNEY_STATIONARY_GAP_MILLIS]. Anchors are emitted
     * on a timer as well as on distance, so standing still still produces them.
     */
    private fun endOfLastStop(anchors: List<JourneyEvent.Anchor>): Long? {
        var result: Long? = null
        var i = 0
        while (i < anchors.size) {
            val head = anchors[i]
            val ruler = head.location.createCheapRuler()
            var last = i
            while (last + 1 < anchors.size &&
                ruler.distance(head.location, anchors[last + 1].location) <= JOURNEY_STATIONARY_GAP_METRES
            ) {
                last++
            }
            val longEnough =
                anchors[last].timestampMillis - head.timestampMillis >= JOURNEY_STATIONARY_GAP_MILLIS

            // A stop that runs to the end of the record is the user having arrived, not a break
            // between two journeys: it ends the journey being asked about rather than some earlier
            // one, and taking it as a boundary would leave nothing to save. This is the ordinary
            // case for the feature - walk somewhere, sit down, ask to save it - and anchors keep
            // arriving on their timer the whole time you are sitting there.
            val stillThere = last == anchors.lastIndex

            if (longEnough && !stillThere) {
                result = anchors[last].timestampMillis
            }
            i = maxOf(last, i + 1)
        }
        return result
    }

    /** Distance along the anchors - a rough length, which is all it is ever used for. */
    fun travelledMetres(journey: List<JourneyEvent>): Double {
        val anchors = journey.filterIsInstance<JourneyEvent.Anchor>()
        if (anchors.size < 2) return 0.0
        val ruler = anchors.first().location.createCheapRuler()
        var total = 0.0
        for (i in 1 until anchors.size) {
            total += ruler.distance(anchors[i - 1].location, anchors[i].location)
        }
        return total
    }
}
