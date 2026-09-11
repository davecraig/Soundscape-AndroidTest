package org.scottishtecharmy.soundscape.geoengine.journey

import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.IntersectionType
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turn detection driven by a hand-built Way graph. No tiles, no grid, no GPS - the detector reads
 * junction geometry off the graph rather than off the fix, so this is the whole of its input.
 *
 * The junction sits at (0,0) and roads radiate from it, which makes every expected angle readable
 * from the compass bearing in the helper call.
 */
class TurnDetectorTest {

    private val junction = intersection(0.0, 0.0)

    // A metre is about 9e-6 degrees of latitude, so these are roads a few hundred metres long.
    private val degreesPerMetre = 1.0 / 111_320.0

    private fun intersection(
        lng: Double,
        lat: Double,
        type: IntersectionType = IntersectionType.REGULAR,
    ) = Intersection().apply {
        location = LngLatAlt(lng, lat)
        intersectionType = type
    }

    /**
     * A road leaving [from] on the given compass bearing. The Way's START is [from], so
     * Way.heading(from) is the bearing given here.
     */
    private fun road(
        from: Intersection,
        bearingDegrees: Double,
        name: String? = null,
        lengthMetres: Double = 200.0,
        type: WayType = WayType.REGULAR,
    ): Way {
        val radians = bearingDegrees * kotlin.math.PI / 180.0
        val far = intersection(
            from.location.longitude + kotlin.math.sin(radians) * lengthMetres * degreesPerMetre,
            from.location.latitude + kotlin.math.cos(radians) * lengthMetres * degreesPerMetre,
        )
        val way = Way().apply {
            this.name = name
            this.length = lengthMetres
            this.wayType = type
            geometry = LineString(from.location.clone(), far.location.clone())
            intersections[WayEnd.START.id] = from
            intersections[WayEnd.END.id] = far
        }
        from.members.add(way)
        far.members.add(way)
        return way
    }

    private fun geometry(
        way: Way?,
        location: LngLatAlt = LngLatAlt(0.0, 0.0),
        speed: Double = 1.4,
        timestampMillis: Long = 0L,
    ) = UserGeometry(
        location = location,
        speed = speed,
        mapMatchedWay = way,
        timestampMilliseconds = timestampMillis,
    )

    /**
     * Drive the detector along [way] for long enough to satisfy the hold-down, returning the one
     * turn it emitted (or null). Each step moves 10m so the distance and fix counts both build up.
     */
    private fun TurnDetector.travel(way: Way?, steps: Int = 4, speed: Double = 1.4): JourneyEvent.Turn? {
        var emitted: JourneyEvent.Turn? = null
        for (step in 0 until steps) {
            val moved = LngLatAlt(0.0, step * 10.0 * degreesPerMetre)
            update(geometry(way, moved, speed, step * 1000L), null, null)?.let { emitted = it }
        }
        return emitted
    }

    // --- the basic manoeuvres -------------------------------------------------------------

    @Test
    fun reportsALeftTurnWithANegativeAngle() {
        val detector = TurnDetector()
        val northbound = road(junction, 180.0, "Beech Avenue") // heads south from the junction
        val west = road(junction, 270.0, "Roselea Drive")

        detector.travel(northbound)
        val turn = detector.travel(west)

        assertNotNull(turn)
        assertEquals(-90.0, turn.signedAngleDegrees, 1.0)
        assertEquals("Beech Avenue", turn.fromRoad)
        assertEquals("Roselea Drive", turn.toRoad)
        assertEquals(0.0, turn.location.latitude, 1e-9)
    }

    @Test
    fun reportsARightTurnWithAPositiveAngle() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Beech Avenue"))
        val turn = detector.travel(road(junction, 90.0, "Roselea Drive"))

        assertNotNull(turn)
        assertEquals(90.0, turn.signedAngleDegrees, 1.0)
    }

    @Test
    fun saysNothingWhenTheUserCarriesStraightOn() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Beech Avenue"))
        // Arriving northbound and leaving north is straight on, even though it is a different Way.
        assertNull(detector.travel(road(junction, 0.0, "Beech Avenue Continuation")))
    }

    // --- the things that are not turns ---------------------------------------------------

    @Test
    fun aGentleBendInOneRoadIsNotATurn() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Milngavie Road"))
        // 40 degrees is past the AHEAD sector, but it is the same named road, so it is a bend.
        assertNull(detector.travel(road(junction, 40.0, "Milngavie Road")))
    }

    @Test
    fun aSharpCornerInOneRoadIsStillATurn() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Milngavie Road"))
        val turn = detector.travel(road(junction, 90.0, "Milngavie Road"))

        assertNotNull(turn)
        assertTrue(abs(turn.signedAngleDegrees) >= SAME_ROAD_TURN_ANGLE_MIN_DEGREES)
    }

    @Test
    fun steppingBetweenARoadAndItsOwnPavementIsNotATurn() {
        val detector = TurnDetector()
        val carriageway = road(junction, 180.0, "Milngavie Road")
        val pavement = road(junction, 91.0).apply {
            setProperty("pavement", "Milngavie Road")
        }

        detector.travel(carriageway)
        assertNull(detector.travel(pavement))
    }

    @Test
    fun tileEdgeStitchingIsNotATurn() {
        val edge = intersection(0.0, 0.0, IntersectionType.TILE_EDGE)
        val detector = TurnDetector()
        detector.travel(road(edge, 180.0, "Milngavie Road"))
        assertNull(detector.travel(road(edge, 90.0, "Milngavie Road East")))
    }

    @Test
    fun aJoinerWayIsNotATurn() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Milngavie Road"))
        assertNull(detector.travel(road(junction, 90.0, "Other Road", type = WayType.JOINER)))
    }

    @Test
    fun aMatcherDirectionFlipIsNotAUTurn() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Milngavie Road"))
        // Leaving on almost the bearing we arrived on: the matcher has flipped, we have not.
        assertNull(detector.travel(road(junction, 175.0, "Milngavie Road Reversed")))
    }

    @Test
    fun turnsAreNotReportedWhileStandingStill() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Beech Avenue"))
        assertNull(detector.travel(road(junction, 90.0, "Roselea Drive"), speed = 0.0))
    }

    @Test
    fun turnsAreNotReportedOnATrain() {
        val line = road(junction, 45.0, "Argyle Line")
        val alongside = road(junction, 180.0, "Beech Avenue")
        val west = road(junction, 270.0, "Roselea Drive")

        // Travelled far enough and long enough that it would certainly be a turn on a road. The
        // road matcher is just latching onto whatever runs beside the line.
        fun onTrain(way: Way, step: Int) = UserGeometry(
            location = LngLatAlt(0.0, step * 40.0 * degreesPerMetre),
            speed = 20.0,
            mapMatchedWay = way,
            mapMatchedRailway = line,
            timestampMilliseconds = step * 1000L,
        )

        val detector = TurnDetector()
        var emitted: JourneyEvent.Turn? = null
        for (step in 0 until 4) {
            detector.update(onTrain(alongside, step), null, null)?.let { emitted = it }
        }
        for (step in 4 until 8) {
            detector.update(onTrain(west, step), null, null)?.let { emitted = it }
        }
        assertNull(emitted)
    }

    // --- what the matcher does that the user does not -------------------------------------

    @Test
    fun flappingToAParallelRoadAndBackIsNotATurn() {
        val detector = TurnDetector()
        val main = road(junction, 180.0, "Milngavie Road")
        val parallel = road(junction, 95.0, "Service Road")

        detector.travel(main)
        // Two fixes on the parallel road - not enough to be believed - then back onto the main one.
        assertNull(detector.update(geometry(parallel, timestampMillis = 5_000L), null, null))
        assertNull(detector.update(geometry(parallel, timestampMillis = 6_000L), null, null))
        assertNull(detector.update(geometry(main, timestampMillis = 7_000L), null, null))

        // ...and the main road is still the reference, so carrying on along it says nothing.
        assertNull(detector.travel(main))
    }

    @Test
    fun aTurnIsNotReportedUntilTheNewRoadHasBeenHeld() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Beech Avenue"))
        val west = road(junction, 270.0, "Roselea Drive")

        // The fix that creates the candidate, then one more holding it: still under both the fix
        // count and the distance, so still not a turn.
        assertNull(detector.update(geometry(west, LngLatAlt(0.0, 0.0), 1.4, 9_000L), null, null))
        assertNull(
            detector.update(
                geometry(west, LngLatAlt(0.0, 2.0 * degreesPerMetre), 1.4, 10_000L), null, null
            )
        )

        // A third fix, past the hold-down, and it is.
        assertNotNull(
            detector.update(
                geometry(west, LngLatAlt(0.0, 4.0 * degreesPerMetre), 1.4, 11_000L), null, null
            )
        )
    }

    @Test
    fun aMatcherJumpToAnUnconnectedRoadIsNotATurn() {
        val detector = TurnDetector()
        detector.travel(road(junction, 180.0, "Beech Avenue"))

        val elsewhere = intersection(1.0, 1.0)
        assertNull(detector.travel(road(elsewhere, 270.0, "Somewhere Else")))
    }

    @Test
    fun crossingASideRoadOnTheWayRoundReadsAsOneTurn() {
        // The pedestrian case: pavement -> a few metres of crossing -> pavement round the corner.
        // Without bridging this is two turns of roughly half the angle each.
        val detector = TurnDetector()
        val pavement = road(junction, 180.0, "Beech Avenue")
        val crossing = road(junction, 250.0, lengthMetres = 6.0)
        val farSide = crossing.intersections[WayEnd.END.id]!!
        val onwards = road(farSide, 270.0, "Roselea Drive")

        detector.travel(pavement)
        // Two fixes on the crossing is not enough to confirm it, so it becomes a bridge.
        detector.update(geometry(crossing, timestampMillis = 10_000L), null, null)
        detector.update(geometry(crossing, timestampMillis = 11_000L), null, null)
        val turn = detector.travel(onwards)

        assertNotNull(turn)
        assertEquals(-90.0, turn.signedAngleDegrees, 1.0)
        assertEquals("Roselea Drive", turn.toRoad)
    }
}
