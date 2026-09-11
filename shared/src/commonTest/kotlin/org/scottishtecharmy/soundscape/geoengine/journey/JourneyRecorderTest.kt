package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JourneyRecorderTest {

    private val degreesPerMetre = 1.0 / 111_320.0

    private fun metres(m: Double) = m * degreesPerMetre

    private fun intersection(eastMetres: Double = 0.0, northMetres: Double = 0.0) = Intersection().apply {
        location = LngLatAlt(metres(eastMetres), metres(northMetres))
    }

    /** An intersection [distanceMetres] away from the origin on the given compass bearing. */
    private fun intersectionOnBearing(bearingDegrees: Double, distanceMetres: Double = 20.0) =
        intersection(
            eastMetres = kotlin.math.sin(bearingDegrees * kotlin.math.PI / 180.0) * distanceMetres,
            northMetres = kotlin.math.cos(bearingDegrees * kotlin.math.PI / 180.0) * distanceMetres,
        )

    /** A road leaving [from] on the given bearing, ending [lengthMetres] away. */
    private fun road(from: Intersection, bearingDegrees: Double, name: String, lengthMetres: Double = 50.0): Way {
        val radians = bearingDegrees * kotlin.math.PI / 180.0
        val far = Intersection().apply {
            location = LngLatAlt(
                from.location.longitude + kotlin.math.sin(radians) * metres(lengthMetres),
                from.location.latitude + kotlin.math.cos(radians) * metres(lengthMetres),
            )
        }
        val way = Way().apply {
            this.name = name
            this.length = lengthMetres
            geometry = LineString(from.location.clone(), far.location.clone())
            intersections[WayEnd.START.id] = from
            intersections[WayEnd.END.id] = far
        }
        from.members.add(way)
        far.members.add(way)
        return way
    }

    /** Joins [from] to [to], so that turns can be chained through real junctions. */
    private fun connect(from: Intersection, to: Intersection, name: String): Way {
        val way = Way().apply {
            this.name = name
            this.length = 20.0
            geometry = LineString(from.location.clone(), to.location.clone())
            intersections[WayEnd.START.id] = from
            intersections[WayEnd.END.id] = to
        }
        from.members.add(way)
        to.members.add(way)
        return way
    }

    private fun geometry(way: Way?, northMetres: Double, timestampMillis: Long, speed: Double = 1.4) =
        UserGeometry(
            location = LngLatAlt(0.0, metres(northMetres)),
            speed = speed,
            mapMatchedWay = way,
            timestampMilliseconds = timestampMillis,
        )

    private fun JourneyRecorder.hold(way: Way, northMetres: Double, fromMillis: Long, fixes: Int = 3) {
        for (i in 0 until fixes) {
            onLocation(geometry(way, northMetres, fromMillis + i * 1000L), null, null)
        }
    }

    private fun List<JourneyEvent>.turns() = filterIsInstance<JourneyEvent.Turn>()

    // --- anchors -------------------------------------------------------------------------

    @Test
    fun anchorsThePathOnDistance() {
        val recorder = JourneyRecorder()
        val way = road(intersection(), 0.0, "Beech Avenue", lengthMetres = 500.0)

        // Well inside the anchor interval, so only distance can be triggering these.
        recorder.onLocation(geometry(way, 0.0, 0L), null, null)
        recorder.onLocation(geometry(way, 50.0, 1_000L), null, null)
        recorder.onLocation(geometry(way, 120.0, 2_000L), null, null)

        assertEquals(2, recorder.snapshot().filterIsInstance<JourneyEvent.Anchor>().size)
    }

    @Test
    fun anchorsThePathOnTimeWhileStandingStill() {
        val recorder = JourneyRecorder()
        val way = road(intersection(), 0.0, "Beech Avenue")

        recorder.onLocation(geometry(way, 0.0, 0L, speed = 0.0), null, null)
        recorder.onLocation(geometry(way, 0.0, JOURNEY_ANCHOR_MILLIS, speed = 0.0), null, null)

        // Standing still still has to be recorded, or there is nothing to find the stop in.
        assertEquals(2, recorder.snapshot().filterIsInstance<JourneyEvent.Anchor>().size)
    }

    // --- landmarks -----------------------------------------------------------------------

    @Test
    fun recordsLandmarksAtTheUsersOwnPosition() {
        val recorder = JourneyRecorder()
        val where = LngLatAlt(0.0, metres(30.0))
        recorder.onLandmark("Tesco", where, 1_000L)

        val landmark = recorder.snapshot().filterIsInstance<JourneyEvent.Landmark>().single()
        assertEquals("Tesco", landmark.name)
        assertEquals(where.latitude, landmark.location.latitude, 1e-12)

        // Held by value: the caller's LngLatAlt is mutable and belongs to the geoengine.
        where.latitude = 0.0
        assertTrue(landmark.location.latitude != 0.0)
    }

    @Test
    fun ignoresAnUnnamedLandmark() {
        val recorder = JourneyRecorder()
        recorder.onLandmark("  ", LngLatAlt(0.0, 0.0), 1_000L)
        assertTrue(recorder.snapshot().isEmpty())
    }

    // --- the off switch ------------------------------------------------------------------

    @Test
    fun recordsNothingAndForgetsEverythingWhenTurnedOff() {
        val recorder = JourneyRecorder()
        recorder.onLandmark("Tesco", LngLatAlt(0.0, 0.0), 1_000L)
        assertTrue(recorder.snapshot().isNotEmpty())

        recorder.enabled = false
        assertTrue(recorder.snapshot().isEmpty())

        recorder.onLandmark("Boots", LngLatAlt(0.0, 0.0), 2_000L)
        assertTrue(recorder.snapshot().isEmpty())
    }

    @Test
    fun clearingForgetsTheJourneySoItCannotBeSavedTwice() {
        val recorder = JourneyRecorder()
        recorder.onLandmark("Tesco", LngLatAlt(0.0, 0.0), 1_000L)
        assertTrue(recorder.snapshot().isNotEmpty())

        // What GeoEngine.saveLastJourney does once a route has been written: someone who misses
        // the spoken confirmation and asks again must not get a second copy of it.
        recorder.clear()
        assertTrue(recorder.snapshot().isEmpty())
    }

    // --- folding a gyratory into one instruction ------------------------------------------

    /**
     * Two right turns 20m and a few seconds apart - a mini-roundabout, or two junctions nobody can
     * act on separately. Left as two waypoints they would be closer together than RoutePlayer's
     * 12m arrival radius.
     */
    @Test
    fun foldsTwoTurnsTheSameWayIntoOne() {
        val first = intersection()
        val second = intersectionOnBearing(40.0)

        // Northbound into `first`, then two 40 degree turns the same way 20m apart, which between
        // them are one turn right rather than two instructions anyone could act on separately.
        val arriving = road(first, 180.0, "Beech Avenue")
        val between = connect(first, second, "Middle Street")
        val leaving = road(second, 80.0, "Roselea Drive")

        val recorder = JourneyRecorder()
        recorder.hold(arriving, 0.0, 0L)
        recorder.hold(between, 10.0, 3_000L)
        recorder.hold(leaving, 20.0, 6_000L)

        val turns = recorder.snapshot().turns()
        assertEquals(1, turns.size)
        // Entry location kept - that is where the user needs to know which way to go...
        assertEquals(0.0, turns.single().location.latitude, 1e-9)
        // ...but the road they end up on.
        assertEquals("Roselea Drive", turns.single().toRoad)
    }

    /**
     * Walking straight up a street past a side lane: the matcher dips onto the lane and comes
     * straight back. Two turns that cancel out, on a street the user never left.
     */
    @Test
    fun foldsAwayATurnThatComesStraightBackOntoTheRoadItLeft() {
        val first = intersection()
        val second = intersection(eastMetres = 60.0)

        // Northbound up the street, right into the lane, then left back onto the street.
        val street = road(first, 180.0, "West Nile Street")
        val lane = connect(first, second, "West George Lane")
        val streetAgain = road(second, 0.0, "West Nile Street")

        val recorder = JourneyRecorder()
        recorder.hold(street, 0.0, 0L)
        recorder.hold(lane, 30.0, 3_000L)
        recorder.hold(streetAgain, 60.0, 6_000L)

        assertTrue(recorder.snapshot().turns().isEmpty(), "got ${recorder.snapshot().turns()}")
    }

    @Test
    fun keepsATurnThatEndsUpOnADifferentRoad() {
        val first = intersection()
        val second = intersection(eastMetres = 60.0)

        // The same shape, but it ends up somewhere else - so both turns are real.
        val street = road(first, 180.0, "West Nile Street")
        val lane = connect(first, second, "West George Lane")
        val elsewhere = road(second, 0.0, "Renfield Street")

        val recorder = JourneyRecorder()
        recorder.hold(street, 0.0, 0L)
        recorder.hold(lane, 30.0, 3_000L)
        recorder.hold(elsewhere, 60.0, 6_000L)

        assertTrue(recorder.snapshot().turns().isNotEmpty())
    }

    @Test
    fun keepsTwoTurnsThatBendOppositeWays() {
        val first = intersection()
        val second = intersectionOnBearing(40.0)

        val arriving = road(first, 180.0, "Beech Avenue")
        val between = connect(first, second, "Middle Street")
        val leaving = road(second, 0.0, "Roselea Drive")

        val recorder = JourneyRecorder()
        recorder.hold(arriving, 0.0, 0L)
        recorder.hold(between, 10.0, 3_000L)   // right, onto Middle Street
        recorder.hold(leaving, 20.0, 6_000L)   // then left - a chicane, not a gyratory

        assertEquals(2, recorder.snapshot().turns().size)
    }

    @Test
    fun keepsTwoTurnsFarApartInTime() {
        val first = intersection()
        val second = intersectionOnBearing(40.0)

        val arriving = road(first, 180.0, "Beech Avenue")
        val between = connect(first, second, "Middle Street")
        val leaving = road(second, 80.0, "Roselea Drive")

        val recorder = JourneyRecorder()
        recorder.hold(arriving, 0.0, 0L)
        recorder.hold(between, 10.0, 3_000L)
        // Same shape, but the user dawdled between the two - so they are two manoeuvres.
        recorder.hold(leaving, 20.0, 3_000L + ROUNDABOUT_COLLAPSE_MILLIS + 1_000L)

        assertEquals(2, recorder.snapshot().turns().size)
    }
}
