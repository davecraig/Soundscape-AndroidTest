package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * Enum representing the types of MVT geometry.
 * Each type has a corresponding GeoJSON type string for compatibility.
 */
enum class GeometryType(val geoJsonType: String) {
    POINT("Point"),
    MULTI_POINT("MultiPoint"),
    LINE_STRING("LineString"),
    MULTI_LINE_STRING("MultiLineString"),
    POLYGON("Polygon"),
    MULTI_POLYGON("MultiPolygon");

    companion object {
        /**
         * Gets the GeometryType from a GeoJSON type string.
         */
        fun fromGeoJsonType(type: String): GeometryType? {
            return entries.find { it.geoJsonType == type }
        }
    }
}

/**
 * Sealed interface representing MVT geometry types.
 * This provides type-safe geometry handling without GeoJSON overhead.
 */
sealed interface MvtGeometry {
    /**
     * Returns the enum type for this geometry.
     * Enables type-safe dispatch without string matching.
     */
    val geometryType: GeometryType

    /**
     * Returns all coordinates that make up this geometry as a flat list.
     * Useful for bounding box calculations and center point computation.
     */
    val allCoordinates: List<LngLatAlt>

    /**
     * Computes the center point of this geometry.
     * For points, this is the point itself.
     * For lines and polygons, this is the centroid.
     */
    fun computeCenter(): LngLatAlt
}

/**
 * Extension property to get the GeoJSON-style type string.
 * Provided for backward compatibility with existing code that uses string matching.
 */
val MvtGeometry.type: String
    get() = geometryType.geoJsonType

/**
 * A single point geometry.
 */
data class MvtPoint(
    val coordinate: LngLatAlt
) : MvtGeometry {
    override val geometryType: GeometryType get() = GeometryType.POINT

    override val allCoordinates: List<LngLatAlt>
        get() = listOf(coordinate)

    override fun computeCenter(): LngLatAlt = coordinate
}

/**
 * A multi-point geometry (collection of discrete points).
 */
data class MvtMultiPoint(
    val coordinates: List<LngLatAlt>
) : MvtGeometry {
    override val geometryType: GeometryType get() = GeometryType.MULTI_POINT

    override val allCoordinates: List<LngLatAlt>
        get() = coordinates

    override fun computeCenter(): LngLatAlt {
        if (coordinates.isEmpty()) return LngLatAlt()
        if (coordinates.size == 1) return coordinates[0]

        var sumLng = 0.0
        var sumLat = 0.0
        for (coord in coordinates) {
            sumLng += coord.longitude
            sumLat += coord.latitude
        }
        return LngLatAlt(sumLng / coordinates.size, sumLat / coordinates.size)
    }
}

/**
 * A line string geometry (ordered sequence of points forming a line).
 */
data class MvtLineString(
    val coordinates: List<LngLatAlt>
) : MvtGeometry {
    override val geometryType: GeometryType get() = GeometryType.LINE_STRING

    override val allCoordinates: List<LngLatAlt>
        get() = coordinates

    override fun computeCenter(): LngLatAlt {
        if (coordinates.isEmpty()) return LngLatAlt()
        if (coordinates.size == 1) return coordinates[0]

        // For lines, use the midpoint of the line (by index, not by distance)
        val midIndex = coordinates.size / 2
        return coordinates[midIndex]
    }

    /**
     * Returns the first coordinate of the line.
     */
    val start: LngLatAlt?
        get() = coordinates.firstOrNull()

    /**
     * Returns the last coordinate of the line.
     */
    val end: LngLatAlt?
        get() = coordinates.lastOrNull()
}

/**
 * A multi-line string geometry (collection of line strings).
 */
data class MvtMultiLineString(
    val lines: List<MvtLineString>
) : MvtGeometry {
    override val geometryType: GeometryType get() = GeometryType.MULTI_LINE_STRING

    override val allCoordinates: List<LngLatAlt>
        get() = lines.flatMap { it.coordinates }

    override fun computeCenter(): LngLatAlt {
        if (lines.isEmpty()) return LngLatAlt()
        // Use center of first line as representative
        return lines[0].computeCenter()
    }
}

/**
 * A polygon geometry with an exterior ring and optional interior rings (holes).
 */
data class MvtPolygon(
    val exteriorRing: List<LngLatAlt>,
    val interiorRings: List<List<LngLatAlt>> = emptyList()
) : MvtGeometry {
    override val geometryType: GeometryType get() = GeometryType.POLYGON

    override val allCoordinates: List<LngLatAlt>
        get() = exteriorRing + interiorRings.flatten()

    override fun computeCenter(): LngLatAlt {
        if (exteriorRing.isEmpty()) return LngLatAlt()

        // Compute centroid of exterior ring
        var sumLng = 0.0
        var sumLat = 0.0
        // Don't include the closing point (which duplicates the first)
        val count = if (exteriorRing.size > 1 &&
            exteriorRing.first() == exteriorRing.last()) {
            exteriorRing.size - 1
        } else {
            exteriorRing.size
        }

        for (i in 0 until count) {
            sumLng += exteriorRing[i].longitude
            sumLat += exteriorRing[i].latitude
        }
        return LngLatAlt(sumLng / count, sumLat / count)
    }
}

/**
 * A multi-polygon geometry (collection of polygons).
 */
data class MvtMultiPolygon(
    val polygons: List<MvtPolygon>
) : MvtGeometry {
    override val geometryType: GeometryType get() = GeometryType.MULTI_POLYGON

    override val allCoordinates: List<LngLatAlt>
        get() = polygons.flatMap { it.allCoordinates }

    override fun computeCenter(): LngLatAlt {
        if (polygons.isEmpty()) return LngLatAlt()
        // Use center of first polygon as representative
        return polygons[0].computeCenter()
    }
}
