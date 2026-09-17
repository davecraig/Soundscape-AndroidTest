package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point

/**
 * Hand-built Way/Intersection graphs for tests of the along-way queries.
 *
 * The graphs are laid out along a west-to-east line through [origin] and measured in metres from
 * it, so a test reads as "the user is at 40, the junction is at 50" rather than as coordinates.
 * Nothing here touches the MVT tile pipeline, following StreetPreviewTest and RoutingUtilsTest.
 *
 * AlongWayTest and UserGeometryCursorTest predate this and carry their own copies of the same
 * helpers; they were deliberately left alone when the walker underneath them was refactored, so
 * that "those tests pass untouched" stayed a real statement about behaviour.
 */
class WayGraph {
    val origin = LngLatAlt(-4.3231, 55.9461)
    val ruler = CheapRuler(origin.latitude)

    /** A point [metres] due east of [origin]. */
    fun east(metres: Double): LngLatAlt = ruler.offset(origin, metres, 0.0)

    fun intersectionAt(location: LngLatAlt) = Intersection().apply {
        this.location = location
        geometry = Point(location)
    }

    /**
     * A straight west-to-east Way from [fromMetres] to [toMetres] east of the origin.
     *
     * [name] null makes an unnamed Way, and [featureValue] carries the OpenMapTiles road class -
     * "service" and "track" are what [JunctionArms.NamedRoads] exists to walk past.
     */
    fun straightWay(
        name: String?,
        fromMetres: Double,
        toMetres: Double,
        featureValue: String = "residential",
    ): Way {
        val start = east(fromMetres)
        val end = east(toMetres)
        return Way().apply {
            this.name = name
            this.featureType = "highway"
            this.featureValue = featureValue
            geometry = LineString(start, end)
            length = ruler.distance(start, end)
        }
    }

    /**
     * A Way covering the same ground as [straightWay] but digitised the other way round, so its
     * START is the eastern end.
     *
     * OSM says nothing about which way traffic runs by the order of a way's nodes, so the pieces
     * one road is split into routinely disagree like this - which is the case the walk's direction
     * arithmetic has to get right.
     */
    fun reversedWay(
        name: String?,
        fromMetres: Double,
        toMetres: Double,
        featureValue: String = "residential",
    ): Way {
        val start = east(toMetres)
        val end = east(fromMetres)
        return Way().apply {
            this.name = name
            this.featureType = "highway"
            this.featureValue = featureValue
            geometry = LineString(start, end)
            length = ruler.distance(start, end)
        }
    }

    /** A Way running north from [atMetres] east of the origin - a side road or a stub. */
    fun sideWay(
        name: String?,
        atMetres: Double,
        lengthMetres: Double,
        featureValue: String = "residential",
    ): Way {
        val start = east(atMetres)
        val end = ruler.offset(start, 0.0, lengthMetres)
        return Way().apply {
            this.name = name
            this.featureType = "highway"
            this.featureValue = featureValue
            geometry = LineString(start, end)
            length = ruler.distance(start, end)
        }
    }

    /** Joins [before]'s END to [after]'s START, as two pieces digitised the same way round meet. */
    fun join(before: Way, after: Way): Intersection =
        joinAll(
            (after.geometry as LineString).coordinates.first(),
            before to WayEnd.END,
            after to WayEnd.START,
        )

    /** Joins two Ways which meet END to END, as two pieces digitised towards each other do. */
    fun joinEndToEnd(before: Way, after: Way): Intersection =
        joinAll(
            (after.geometry as LineString).coordinates.last(),
            before to WayEnd.END,
            after to WayEnd.END,
        )

    /** Builds an intersection at [location] out of any number of Ways and the end each meets by. */
    fun joinAll(location: LngLatAlt, vararg wayEnds: Pair<Way, WayEnd>): Intersection {
        val intersection = intersectionAt(location)
        for ((way, end) in wayEnds) {
            way.intersections[end.id] = intersection
            intersection.members.add(way)
        }
        return intersection
    }
}
