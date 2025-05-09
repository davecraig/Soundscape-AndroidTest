package org.scottishtecharmy.soundscape.geojsonparser.geojson

import com.squareup.moshi.JsonClass
import org.maplibre.android.geometry.LatLng
import org.scottishtecharmy.soundscape.geoengine.utils.CheapRuler
import org.scottishtecharmy.soundscape.geoengine.utils.EARTH_RADIUS_METERS
import org.scottishtecharmy.soundscape.geoengine.utils.PointAndDistanceAndHeading
import org.scottishtecharmy.soundscape.geoengine.utils.distance
import org.scottishtecharmy.soundscape.geoengine.utils.metres
import org.scottishtecharmy.soundscape.geoengine.utils.toRadians
import java.io.Serializable
import java.lang.Math.toDegrees
import kotlin.math.cos
import kotlin.math.min

@JsonClass(generateAdapter = true)
open class LngLatAlt(
    var longitude: Double = 0.toDouble(),
    var latitude: Double = 0.toDouble(),
    var altitude: Double? = null
) : Serializable {
    fun hasAltitude(): Boolean = altitude != null && altitude?.isNaN() == false

    fun clone() : LngLatAlt {
        return LngLatAlt(longitude, latitude, altitude)
    }

    // Problems with array of LngLatAlt comparisons. Attempting to fix here:
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (other is LngLatAlt) {
            return longitude == other.longitude && latitude == other.latitude &&
                    (altitude == null && other.altitude == null ||
                            altitude != null && other.altitude != null && altitude == other.altitude)
        }
        return false
    }

    override fun hashCode(): Int {
        var result = longitude.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + (altitude?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "$longitude,$latitude"
    }

    fun toLatLng(): LatLng {
        return LatLng(latitude, longitude)
    }

    fun createCheapRuler() : CheapRuler {
        return CheapRuler(latitude, metres)
    }

    fun distance(other: LngLatAlt, ruler: CheapRuler): Double {
        return ruler.distance(this, other)// ?: distance(latitude, longitude, other.latitude, other.longitude)
    }

    /**
     * Distance to a LineString from current location.
     * @param lineString LineString that we are working out the distance from
     * @return The distance of the point to the LineString, the nearest point on the line and the
     * heading of the line at that point.
     */
    fun distanceToLineString(
        lineString: LineString,
        ruler: CheapRuler = CheapRuler(lineString.coordinates[0].latitude, metres)
    ): PointAndDistanceAndHeading {
        return ruler.pointOnLine(lineString, this)
    }
}

fun fromLatLng(loc:LatLng): LngLatAlt {
    return LngLatAlt(loc.longitude, loc.latitude)
}
