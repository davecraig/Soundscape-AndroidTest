package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * Spatial utility functions for working with MvtGeometry and SpatialFeature.
 */

/**
 * Checks if a geometry is a point-like geometry (Point or MultiPoint with single coordinate).
 */
fun MvtGeometry.isPointLike(): Boolean {
    return when (this) {
        is MvtPoint -> true
        is MvtMultiPoint -> coordinates.size <= 1
        else -> false
    }
}

/**
 * Checks if a SpatialFeature has point-like geometry.
 */
fun SpatialFeature.isPointLike(): Boolean = mvtGeometry.isPointLike()

/**
 * Gets coordinates for iteration, handling all geometry types.
 * Returns exterior ring coordinates for polygons, all coordinates for other types.
 */
fun MvtGeometry.getIterableCoordinates(): List<LngLatAlt> {
    return when (this) {
        is MvtPoint -> listOf(coordinate)
        is MvtMultiPoint -> coordinates
        is MvtLineString -> coordinates
        is MvtMultiLineString -> lines.flatMap { it.coordinates }
        is MvtPolygon -> exteriorRing
        is MvtMultiPolygon -> polygons.flatMap { it.exteriorRing }
    }
}

/**
 * Gets the first coordinate of the geometry.
 */
fun MvtGeometry.firstCoordinate(): LngLatAlt? {
    return when (this) {
        is MvtPoint -> coordinate
        is MvtMultiPoint -> coordinates.firstOrNull()
        is MvtLineString -> coordinates.firstOrNull()
        is MvtMultiLineString -> lines.firstOrNull()?.coordinates?.firstOrNull()
        is MvtPolygon -> exteriorRing.firstOrNull()
        is MvtMultiPolygon -> polygons.firstOrNull()?.exteriorRing?.firstOrNull()
    }
}

/**
 * Calculates the simple (squared Euclidean) distance between two coordinates.
 * Useful for comparing relative distances without needing actual measurements.
 */
fun LngLatAlt.squaredDistanceTo(other: LngLatAlt): Double {
    val dx = longitude - other.longitude
    val dy = latitude - other.latitude
    return dx * dx + dy * dy
}

/**
 * Finds the nearest coordinate in a geometry to a given location using simple squared distance.
 * This is fast but approximate - use CheapRuler for accurate distances.
 */
fun MvtGeometry.nearestCoordinateTo(location: LngLatAlt): LngLatAlt {
    return when (this) {
        is MvtPoint -> coordinate
        is MvtMultiPoint -> {
            coordinates.minByOrNull { it.squaredDistanceTo(location) } ?: location
        }
        is MvtLineString -> {
            coordinates.minByOrNull { it.squaredDistanceTo(location) } ?: computeCenter()
        }
        is MvtMultiLineString -> {
            lines.flatMap { it.coordinates }
                .minByOrNull { it.squaredDistanceTo(location) } ?: computeCenter()
        }
        is MvtPolygon -> {
            exteriorRing.minByOrNull { it.squaredDistanceTo(location) } ?: computeCenter()
        }
        is MvtMultiPolygon -> {
            polygons.flatMap { it.exteriorRing }
                .minByOrNull { it.squaredDistanceTo(location) } ?: computeCenter()
        }
    }
}

/**
 * Finds the nearest coordinate on a SpatialFeature to a given location.
 */
fun SpatialFeature.nearestCoordinateTo(location: LngLatAlt): LngLatAlt {
    return mvtGeometry.nearestCoordinateTo(location)
}

/**
 * Checks if the geometry matches a specific type using the enum.
 */
fun MvtGeometry.isType(type: GeometryType): Boolean = geometryType == type

/**
 * Extension to check if geometry is a Point.
 */
val MvtGeometry.isPoint: Boolean get() = geometryType == GeometryType.POINT

/**
 * Extension to check if geometry is a LineString.
 */
val MvtGeometry.isLineString: Boolean get() = geometryType == GeometryType.LINE_STRING

/**
 * Extension to check if geometry is a Polygon.
 */
val MvtGeometry.isPolygon: Boolean get() = geometryType == GeometryType.POLYGON
