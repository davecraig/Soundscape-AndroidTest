package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiLineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiPoint
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon

class FeatureConversionTest {

    // GeoJSON to MvtGeometry conversion tests

    @Test
    fun `Point converts to MvtPoint`() {
        val point = Point(LngLatAlt(-4.25, 55.85))

        val mvtGeom = point.toMvtGeometry()

        assertTrue(mvtGeom is MvtPoint)
        val mvtPoint = mvtGeom as MvtPoint
        assertEquals(-4.25, mvtPoint.coordinate.longitude, 0.0001)
        assertEquals(55.85, mvtPoint.coordinate.latitude, 0.0001)
    }

    @Test
    fun `MultiPoint converts to MvtMultiPoint`() {
        val multiPoint = MultiPoint(arrayListOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1)
        ))

        val mvtGeom = multiPoint.toMvtGeometry()

        assertTrue(mvtGeom is MvtMultiPoint)
        val mvtMultiPoint = mvtGeom as MvtMultiPoint
        assertEquals(2, mvtMultiPoint.coordinates.size)
    }

    @Test
    fun `LineString converts to MvtLineString`() {
        val lineString = LineString(arrayListOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1),
            LngLatAlt(-4.2, 55.2)
        ))

        val mvtGeom = lineString.toMvtGeometry()

        assertTrue(mvtGeom is MvtLineString)
        val mvtLineString = mvtGeom as MvtLineString
        assertEquals(3, mvtLineString.coordinates.size)
    }

    @Test
    fun `Polygon converts to MvtPolygon`() {
        val polygon = Polygon(arrayListOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.0, 56.0),
            LngLatAlt(-5.0, 56.0),
            LngLatAlt(-5.0, 55.0),
            LngLatAlt(-4.0, 55.0)
        ))

        val mvtGeom = polygon.toMvtGeometry()

        assertTrue(mvtGeom is MvtPolygon)
        val mvtPolygon = mvtGeom as MvtPolygon
        assertEquals(5, mvtPolygon.exteriorRing.size)
        assertTrue(mvtPolygon.interiorRings.isEmpty())
    }

    @Test
    fun `Polygon with hole converts correctly`() {
        val polygon = Polygon(arrayListOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.0, 56.0),
            LngLatAlt(-5.0, 56.0),
            LngLatAlt(-5.0, 55.0),
            LngLatAlt(-4.0, 55.0)
        ))
        polygon.addInteriorRing(arrayListOf(
            LngLatAlt(-4.2, 55.2),
            LngLatAlt(-4.2, 55.8),
            LngLatAlt(-4.8, 55.8),
            LngLatAlt(-4.8, 55.2),
            LngLatAlt(-4.2, 55.2)
        ))

        val mvtGeom = polygon.toMvtGeometry()

        assertTrue(mvtGeom is MvtPolygon)
        val mvtPolygon = mvtGeom as MvtPolygon
        assertEquals(1, mvtPolygon.interiorRings.size)
        assertEquals(5, mvtPolygon.interiorRings[0].size)
    }

    // MvtGeometry to GeoJSON conversion tests (round-trip)

    @Test
    fun `MvtPoint round-trips correctly`() {
        val original = MvtPoint(LngLatAlt(-4.25, 55.85))

        val geoJson = original.toGeoJsonGeometry()
        val roundTrip = geoJson.toMvtGeometry()

        assertTrue(roundTrip is MvtPoint)
        assertEquals(original, roundTrip)
    }

    @Test
    fun `MvtLineString round-trips correctly`() {
        val original = MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1)
        ))

        val geoJson = original.toGeoJsonGeometry()
        val roundTrip = geoJson.toMvtGeometry()

        assertTrue(roundTrip is MvtLineString)
        val rt = roundTrip as MvtLineString
        assertEquals(original.coordinates.size, rt.coordinates.size)
    }

    // MvtFeature to MvtTileFeature conversion tests

    @Test
    fun `MvtFeature converts to MvtTileFeature`() {
        val mvtFeature = MvtFeature()
        mvtFeature.setMvtGeometry(MvtPoint(LngLatAlt(-4.25, 55.85)))
        mvtFeature.osmId = 12345L
        mvtFeature.name = "Test POI"
        mvtFeature.featureClass = "shop"
        mvtFeature.featureSubClass = "supermarket"
        mvtFeature.superCategory = SuperCategoryId.PLACE

        val mvtTileFeature = mvtFeature.toMvtTileFeature(layer = "poi", treeId = TreeId.POIS)

        assertNotNull(mvtTileFeature)
        assertEquals(12345L, mvtTileFeature!!.osmId)
        assertEquals("Test POI", mvtTileFeature.name)
        assertEquals("shop", mvtTileFeature.featureClass)
        assertEquals("supermarket", mvtTileFeature.featureSubClass)
        assertEquals(SuperCategoryId.PLACE, mvtTileFeature.superCategory)
        assertEquals("poi", mvtTileFeature.layer)
        assertEquals(TreeId.POIS, mvtTileFeature.treeId)
    }

    @Test
    fun `MvtFeature with properties converts correctly`() {
        val mvtFeature = MvtFeature()
        mvtFeature.setMvtGeometry(MvtPoint(LngLatAlt(0.0, 0.0)))
        mvtFeature.osmId = 1L
        mvtFeature.setProperty("custom_key", "custom_value")
        mvtFeature.setProperty("number", 42)

        val mvtTileFeature = mvtFeature.toMvtTileFeature()

        assertNotNull(mvtTileFeature)
        assertEquals("custom_value", mvtTileFeature!!.properties["custom_key"])
        assertEquals(42, mvtTileFeature.properties["number"])
    }

    // MvtTileFeature to MvtFeature conversion tests (backward compatibility)

    @Test
    fun `MvtTileFeature converts back to MvtFeature`() {
        val mvtTileFeature = MvtTileFeature(
            osmId = 12345L,
            geometry = MvtPoint(LngLatAlt(-4.25, 55.85)),
            layer = "poi",
            name = "Test POI",
            featureClass = "shop",
            featureSubClass = "supermarket",
            superCategory = SuperCategoryId.PLACE
        )

        val mvtFeature = mvtTileFeature.toMvtFeature()

        assertEquals(12345L, mvtFeature.osmId)
        assertEquals("Test POI", mvtFeature.name)
        assertEquals("shop", mvtFeature.featureClass)
        assertEquals("supermarket", mvtFeature.featureSubClass)
        assertEquals(SuperCategoryId.PLACE, mvtFeature.superCategory)
        assertTrue(mvtFeature.mvtGeometry is MvtPoint)
    }

    // SpatialFeature adapter tests

    @Test
    fun `MvtFeatureAdapter provides correct values`() {
        val mvtFeature = MvtFeature()
        mvtFeature.setMvtGeometry(MvtPoint(LngLatAlt(-4.25, 55.85)))
        mvtFeature.osmId = 12345L
        mvtFeature.name = "Test POI"
        mvtFeature.featureClass = "shop"

        val spatial = mvtFeature.asSpatialFeature()

        assertEquals(12345L, spatial.osmId)
        assertEquals("Test POI", spatial.name)
        assertEquals("shop", spatial.featureClass)
        assertEquals(-4.25, spatial.center.longitude, 0.0001)
        assertEquals(55.85, spatial.center.latitude, 0.0001)
    }

    @Test
    fun `MvtFeatureAdapter getProperty works correctly`() {
        val mvtFeature = MvtFeature()
        mvtFeature.setMvtGeometry(MvtPoint(LngLatAlt(0.0, 0.0)))
        mvtFeature.osmId = 1L
        mvtFeature.name = "Test"
        mvtFeature.setProperty("custom", "value")

        val spatial = mvtFeature.asSpatialFeature()

        assertEquals("Test", spatial.getProperty("name"))
        assertEquals("value", spatial.getProperty("custom"))
        assertNull(spatial.getProperty("nonexistent"))
    }

    @Test
    fun `MvtFeatureAdapter hasProperty works correctly`() {
        val mvtFeature = MvtFeature()
        mvtFeature.setMvtGeometry(MvtPoint(LngLatAlt(0.0, 0.0)))
        mvtFeature.osmId = 1L
        mvtFeature.name = "Test"
        mvtFeature.setProperty("custom", "value")

        val spatial = mvtFeature.asSpatialFeature()

        assertTrue(spatial.hasProperty("name"))
        assertTrue(spatial.hasProperty("custom"))
        assertTrue(!spatial.hasProperty("class"))
        assertTrue(!spatial.hasProperty("nonexistent"))
    }

    @Test
    fun `MvtFeatureAdapter computes center for LineString`() {
        val mvtFeature = MvtFeature()
        mvtFeature.setMvtGeometry(MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.2, 55.2),
            LngLatAlt(-4.4, 55.4)
        )))
        mvtFeature.osmId = 1L

        val spatial = mvtFeature.asSpatialFeature()

        // Center should be midpoint (index 1)
        assertEquals(-4.2, spatial.center.longitude, 0.0001)
        assertEquals(55.2, spatial.center.latitude, 0.0001)
    }

    @Test
    fun `MvtTileFeatureWrapper provides correct values`() {
        val mvtTileFeature = MvtTileFeature(
            osmId = 12345L,
            geometry = MvtPoint(LngLatAlt(-4.25, 55.85)),
            layer = "poi",
            name = "Test POI",
            treeId = TreeId.POIS
        )

        val spatial = mvtTileFeature.asSpatialFeature()

        assertEquals(12345L, spatial.osmId)
        assertEquals("Test POI", spatial.name)
        assertEquals(TreeId.POIS, spatial.treeId)
        assertEquals(-4.25, spatial.center.longitude, 0.0001)
    }

    @Test
    fun `MvtTileFeatureWrapper unwrap returns original`() {
        val original = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(0.0, 0.0)),
            layer = "poi"
        )

        val wrapper = MvtTileFeatureWrapper(original)

        assertEquals(original, wrapper.unwrap())
    }

    @Test
    fun `MvtFeatureAdapter unwrap returns original`() {
        val original = MvtFeature()
        original.setMvtGeometry(MvtPoint(LngLatAlt(0.0, 0.0)))
        original.osmId = 1L

        val adapter = MvtFeatureAdapter(original)

        assertEquals(original, adapter.unwrap())
    }
}
