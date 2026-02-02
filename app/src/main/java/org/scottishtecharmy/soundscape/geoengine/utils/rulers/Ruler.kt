package org.scottishtecharmy.soundscape.geoengine.utils.rulers

import org.scottishtecharmy.soundscape.geoengine.mvt.data.MvtLineString
import org.scottishtecharmy.soundscape.geoengine.utils.PointAndDistanceAndHeading
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

abstract class Ruler() {

    abstract fun distance(a: LngLatAlt, b: LngLatAlt) : Double
    abstract fun bearing(a: LngLatAlt, b: LngLatAlt) : Double
    abstract fun destination(p: LngLatAlt, dist: Double, bearing: Double) : LngLatAlt
    abstract fun along(line: MvtLineString, dist: Double) : LngLatAlt
    abstract fun pointToSegmentDistance(p: LngLatAlt, a: LngLatAlt, b: LngLatAlt) : Double
    abstract fun distanceToLineString(p: LngLatAlt, line: MvtLineString) : PointAndDistanceAndHeading
    abstract fun lineLength(line: MvtLineString) : Double
}
