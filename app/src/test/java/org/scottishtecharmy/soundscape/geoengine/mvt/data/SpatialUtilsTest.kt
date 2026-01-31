package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

class SpatialUtilsTest {

    @Test
    fun `MvtPoint isPointLike returns true`() {
        val point = MvtPoint(LngLatAlt(-4.0, 55.0))
        assertTrue(point.isPointLike())
    }

    @Test
    fun `MvtMultiPoint with one coordinate isPointLike returns true`() {
        val multiPoint = MvtMultiPoint(listOf(LngLatAlt(-4.0, 55.0)))
        assertTrue(multiPoint.isPointLike())
    }

    @Test
    fun `MvtMultiPoint with multiple coordinates isPointLike returns false`() {
        val multiPoint = MvtMultiPoint(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1)
        ))
        assertFalse(multiPoint.isPointLike())
    }

    @Test
    fun `MvtLineString isPointLike returns false`() {
        val lineString = MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1)
        ))
        assertFalse(lineString.isPointLike())
    }

    @Test
    fun `getIterableCoordinates returns correct coordinates for Point`() {
        val point = MvtPoint(LngLatAlt(-4.0, 55.0))
        val coords = point.getIterableCoordinates()
        assertEquals(1, coords.size)
        assertEquals(-4.0, coords[0].longitude, 0.0001)
    }

    @Test
    fun `getIterableCoordinates returns exterior ring for Polygon`() {
        val polygon = MvtPolygon(
            exteriorRing = listOf(
                LngLatAlt(-4.0, 55.0),
                LngLatAlt(-4.0, 56.0),
                LngLatAlt(-5.0, 55.0),
                LngLatAlt(-4.0, 55.0)
            ),
            interiorRings = listOf(
                listOf(
                    LngLatAlt(-4.2, 55.2),
                    LngLatAlt(-4.2, 55.5),
                    LngLatAlt(-4.5, 55.2),
                    LngLatAlt(-4.2, 55.2)
                )
            )
        )
        val coords = polygon.getIterableCoordinates()
        // Should only return exterior ring (4 coords), not interior ring
        assertEquals(4, coords.size)
    }

    @Test
    fun `firstCoordinate returns correct coordinate`() {
        val lineString = MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1),
            LngLatAlt(-4.2, 55.2)
        ))
        val first = lineString.firstCoordinate()
        assertEquals(-4.0, first?.longitude ?: 0.0, 0.0001)
        assertEquals(55.0, first?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `squaredDistanceTo calculates correctly`() {
        val a = LngLatAlt(0.0, 0.0)
        val b = LngLatAlt(3.0, 4.0)
        // Expected: 3^2 + 4^2 = 9 + 16 = 25
        assertEquals(25.0, a.squaredDistanceTo(b), 0.0001)
    }

    @Test
    fun `nearestCoordinateTo returns Point coordinate for MvtPoint`() {
        val point = MvtPoint(LngLatAlt(-4.0, 55.0))
        val nearest = point.nearestCoordinateTo(LngLatAlt(0.0, 0.0))
        assertEquals(-4.0, nearest.longitude, 0.0001)
        assertEquals(55.0, nearest.latitude, 0.0001)
    }

    @Test
    fun `nearestCoordinateTo finds closest coordinate in LineString`() {
        val lineString = MvtLineString(listOf(
            LngLatAlt(0.0, 0.0),
            LngLatAlt(10.0, 10.0),
            LngLatAlt(20.0, 20.0)
        ))
        // Point (1, 1) should be closest to (0, 0)
        val nearest = lineString.nearestCoordinateTo(LngLatAlt(1.0, 1.0))
        assertEquals(0.0, nearest.longitude, 0.0001)
        assertEquals(0.0, nearest.latitude, 0.0001)
    }

    @Test
    fun `nearestCoordinateTo finds closest coordinate in middle of LineString`() {
        val lineString = MvtLineString(listOf(
            LngLatAlt(0.0, 0.0),
            LngLatAlt(10.0, 10.0),
            LngLatAlt(20.0, 20.0)
        ))
        // Point (9, 9) should be closest to (10, 10)
        val nearest = lineString.nearestCoordinateTo(LngLatAlt(9.0, 9.0))
        assertEquals(10.0, nearest.longitude, 0.0001)
        assertEquals(10.0, nearest.latitude, 0.0001)
    }

    @Test
    fun `isType checks geometry type correctly`() {
        val point = MvtPoint(LngLatAlt(0.0, 0.0))
        val lineString = MvtLineString(listOf(LngLatAlt(0.0, 0.0)))
        val polygon = MvtPolygon(listOf(LngLatAlt(0.0, 0.0)))

        assertTrue(point.isType(GeometryType.POINT))
        assertFalse(point.isType(GeometryType.LINE_STRING))

        assertTrue(lineString.isType(GeometryType.LINE_STRING))
        assertFalse(lineString.isType(GeometryType.POINT))

        assertTrue(polygon.isType(GeometryType.POLYGON))
        assertFalse(polygon.isType(GeometryType.MULTI_POLYGON))
    }

    @Test
    fun `isPoint isLineString isPolygon extensions work correctly`() {
        val point = MvtPoint(LngLatAlt(0.0, 0.0))
        val lineString = MvtLineString(listOf(LngLatAlt(0.0, 0.0)))
        val polygon = MvtPolygon(listOf(LngLatAlt(0.0, 0.0)))

        assertTrue(point.isPoint)
        assertFalse(point.isLineString)
        assertFalse(point.isPolygon)

        assertFalse(lineString.isPoint)
        assertTrue(lineString.isLineString)
        assertFalse(lineString.isPolygon)

        assertFalse(polygon.isPoint)
        assertFalse(polygon.isLineString)
        assertTrue(polygon.isPolygon)
    }

    @Test
    fun `GeometryType geoJsonType returns correct strings`() {
        assertEquals("Point", GeometryType.POINT.geoJsonType)
        assertEquals("MultiPoint", GeometryType.MULTI_POINT.geoJsonType)
        assertEquals("LineString", GeometryType.LINE_STRING.geoJsonType)
        assertEquals("MultiLineString", GeometryType.MULTI_LINE_STRING.geoJsonType)
        assertEquals("Polygon", GeometryType.POLYGON.geoJsonType)
        assertEquals("MultiPolygon", GeometryType.MULTI_POLYGON.geoJsonType)
    }

    @Test
    fun `GeometryType fromGeoJsonType parses correctly`() {
        assertEquals(GeometryType.POINT, GeometryType.fromGeoJsonType("Point"))
        assertEquals(GeometryType.LINE_STRING, GeometryType.fromGeoJsonType("LineString"))
        assertEquals(GeometryType.POLYGON, GeometryType.fromGeoJsonType("Polygon"))
        assertEquals(null, GeometryType.fromGeoJsonType("Unknown"))
    }

    @Test
    fun `MvtGeometry type extension returns geoJsonType`() {
        val point = MvtPoint(LngLatAlt(0.0, 0.0))
        val lineString = MvtLineString(listOf(LngLatAlt(0.0, 0.0)))

        assertEquals("Point", point.type)
        assertEquals("LineString", lineString.type)
    }
}
