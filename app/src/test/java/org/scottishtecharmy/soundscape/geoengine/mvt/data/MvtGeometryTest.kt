package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

class MvtGeometryTest {

    @Test
    fun `MvtPoint returns correct center`() {
        val point = MvtPoint(LngLatAlt(-4.25, 55.85))

        val center = point.computeCenter()

        assertEquals(-4.25, center.longitude, 0.0001)
        assertEquals(55.85, center.latitude, 0.0001)
    }

    @Test
    fun `MvtPoint allCoordinates returns single coordinate`() {
        val coord = LngLatAlt(-4.25, 55.85)
        val point = MvtPoint(coord)

        assertEquals(1, point.allCoordinates.size)
        assertEquals(coord, point.allCoordinates[0])
    }

    @Test
    fun `MvtMultiPoint computes center as average`() {
        val multiPoint = MvtMultiPoint(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.0, 56.0),
            LngLatAlt(-5.0, 55.0),
            LngLatAlt(-5.0, 56.0)
        ))

        val center = multiPoint.computeCenter()

        assertEquals(-4.5, center.longitude, 0.0001)
        assertEquals(55.5, center.latitude, 0.0001)
    }

    @Test
    fun `MvtMultiPoint empty returns default LngLatAlt`() {
        val multiPoint = MvtMultiPoint(emptyList())

        val center = multiPoint.computeCenter()

        assertEquals(0.0, center.longitude, 0.0001)
        assertEquals(0.0, center.latitude, 0.0001)
    }

    @Test
    fun `MvtLineString returns midpoint as center`() {
        val lineString = MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1),
            LngLatAlt(-4.2, 55.2),
            LngLatAlt(-4.3, 55.3),
            LngLatAlt(-4.4, 55.4)
        ))

        val center = lineString.computeCenter()

        // Midpoint is index 2 (5/2 = 2)
        assertEquals(-4.2, center.longitude, 0.0001)
        assertEquals(55.2, center.latitude, 0.0001)
    }

    @Test
    fun `MvtLineString start and end return correct coordinates`() {
        val lineString = MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.5, 55.5)
        ))

        assertEquals(-4.0, lineString.start?.longitude ?: 0.0, 0.0001)
        assertEquals(55.0, lineString.start?.latitude ?: 0.0, 0.0001)
        assertEquals(-4.5, lineString.end?.longitude ?: 0.0, 0.0001)
        assertEquals(55.5, lineString.end?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `MvtMultiLineString returns center of first line`() {
        val multiLine = MvtMultiLineString(listOf(
            MvtLineString(listOf(
                LngLatAlt(-4.0, 55.0),
                LngLatAlt(-4.2, 55.2),
                LngLatAlt(-4.4, 55.4)
            )),
            MvtLineString(listOf(
                LngLatAlt(-5.0, 56.0),
                LngLatAlt(-5.2, 56.2)
            ))
        ))

        val center = multiLine.computeCenter()

        // Should be midpoint of first line (index 1)
        assertEquals(-4.2, center.longitude, 0.0001)
        assertEquals(55.2, center.latitude, 0.0001)
    }

    @Test
    fun `MvtPolygon computes centroid of exterior ring`() {
        // Square polygon
        val polygon = MvtPolygon(
            exteriorRing = listOf(
                LngLatAlt(-4.0, 55.0),
                LngLatAlt(-4.0, 56.0),
                LngLatAlt(-5.0, 56.0),
                LngLatAlt(-5.0, 55.0),
                LngLatAlt(-4.0, 55.0)  // Closing point
            )
        )

        val center = polygon.computeCenter()

        // Centroid should be at center of square
        assertEquals(-4.5, center.longitude, 0.0001)
        assertEquals(55.5, center.latitude, 0.0001)
    }

    @Test
    fun `MvtPolygon with interior rings includes all coordinates`() {
        val polygon = MvtPolygon(
            exteriorRing = listOf(
                LngLatAlt(-4.0, 55.0),
                LngLatAlt(-4.0, 56.0),
                LngLatAlt(-5.0, 56.0),
                LngLatAlt(-5.0, 55.0),
                LngLatAlt(-4.0, 55.0)
            ),
            interiorRings = listOf(
                listOf(
                    LngLatAlt(-4.2, 55.2),
                    LngLatAlt(-4.2, 55.8),
                    LngLatAlt(-4.8, 55.8),
                    LngLatAlt(-4.8, 55.2),
                    LngLatAlt(-4.2, 55.2)
                )
            )
        )

        // 5 exterior + 5 interior = 10 total
        assertEquals(10, polygon.allCoordinates.size)
    }

    @Test
    fun `MvtMultiPolygon returns center of first polygon`() {
        val multiPolygon = MvtMultiPolygon(listOf(
            MvtPolygon(
                exteriorRing = listOf(
                    LngLatAlt(-4.0, 55.0),
                    LngLatAlt(-4.0, 56.0),
                    LngLatAlt(-5.0, 56.0),
                    LngLatAlt(-5.0, 55.0),
                    LngLatAlt(-4.0, 55.0)
                )
            ),
            MvtPolygon(
                exteriorRing = listOf(
                    LngLatAlt(-6.0, 57.0),
                    LngLatAlt(-6.0, 58.0),
                    LngLatAlt(-7.0, 58.0),
                    LngLatAlt(-7.0, 57.0),
                    LngLatAlt(-6.0, 57.0)
                )
            )
        ))

        val center = multiPolygon.computeCenter()

        // Should be centroid of first polygon
        assertEquals(-4.5, center.longitude, 0.0001)
        assertEquals(55.5, center.latitude, 0.0001)
    }

    @Test
    fun `sealed interface allows exhaustive when`() {
        val geometries: List<MvtGeometry> = listOf(
            MvtPoint(LngLatAlt(0.0, 0.0)),
            MvtMultiPoint(listOf(LngLatAlt(0.0, 0.0))),
            MvtLineString(listOf(LngLatAlt(0.0, 0.0))),
            MvtMultiLineString(listOf(MvtLineString(listOf(LngLatAlt(0.0, 0.0))))),
            MvtPolygon(listOf(LngLatAlt(0.0, 0.0))),
            MvtMultiPolygon(listOf(MvtPolygon(listOf(LngLatAlt(0.0, 0.0)))))
        )

        for (geom in geometries) {
            // This when is exhaustive due to sealed interface
            val typeName = when (geom) {
                is MvtPoint -> "point"
                is MvtMultiPoint -> "multipoint"
                is MvtLineString -> "linestring"
                is MvtMultiLineString -> "multilinestring"
                is MvtPolygon -> "polygon"
                is MvtMultiPolygon -> "multipolygon"
            }
            assertTrue(typeName.isNotEmpty())
        }
    }
}
