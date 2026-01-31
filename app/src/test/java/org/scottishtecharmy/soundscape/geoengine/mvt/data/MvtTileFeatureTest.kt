package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

class MvtTileFeatureTest {

    @Test
    fun `create factory method builds feature correctly`() {
        val feature = MvtTileFeature.create(
            osmId = 12345L,
            geometry = MvtPoint(LngLatAlt(-4.25, 55.85)),
            layer = "poi",
            name = "Test POI",
            featureClass = "shop",
            featureSubClass = "supermarket"
        )

        assertEquals(12345L, feature.osmId)
        assertEquals("poi", feature.layer)
        assertEquals("Test POI", feature.name)
        assertEquals("shop", feature.featureClass)
        assertEquals("supermarket", feature.featureSubClass)
    }

    @Test
    fun `center is computed lazily from geometry`() {
        val feature = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(-4.25, 55.85)),
            layer = "poi"
        )

        val center = feature.center

        assertEquals(-4.25, center.longitude, 0.0001)
        assertEquals(55.85, center.latitude, 0.0001)
    }

    @Test
    fun `getProperty returns typed fields`() {
        val feature = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(0.0, 0.0)),
            layer = "poi",
            name = "Test Name",
            featureClass = "shop",
            featureSubClass = "bakery",
            housenumber = "42",
            street = "Main Street"
        )

        assertEquals("Test Name", feature.getProperty("name"))
        assertEquals("shop", feature.getProperty("class"))
        assertEquals("bakery", feature.getProperty("subclass"))
        assertEquals("42", feature.getProperty("housenumber"))
        assertEquals("Main Street", feature.getProperty("street"))
    }

    @Test
    fun `getProperty returns from properties map for unknown keys`() {
        val feature = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(0.0, 0.0)),
            layer = "poi",
            properties = mapOf("custom_key" to "custom_value", "number" to 42)
        )

        assertEquals("custom_value", feature.getProperty("custom_key"))
        assertEquals(42, feature.getProperty("number"))
    }

    @Test
    fun `hasProperty returns true for set fields`() {
        val feature = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(0.0, 0.0)),
            layer = "poi",
            name = "Test",
            properties = mapOf("custom" to "value")
        )

        assertTrue(feature.hasProperty("name"))
        assertTrue(feature.hasProperty("custom"))
        assertFalse(feature.hasProperty("class"))
        assertFalse(feature.hasProperty("nonexistent"))
    }

    @Test
    fun `withTreeId returns copy with new treeId`() {
        val original = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(0.0, 0.0)),
            layer = "poi",
            name = "Test"
        )

        val updated = original.withTreeId(TreeId.POIS)

        assertNull(original.treeId)
        assertEquals(TreeId.POIS, updated.treeId)
        assertEquals(original.name, updated.name)
    }

    @Test
    fun `withTranslation returns copy with translated properties`() {
        val original = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(0.0, 0.0)),
            layer = "poi",
            featureClass = "shop"
        )

        val updated = original.withTranslation(
            newFeatureType = "amenity",
            newFeatureValue = "supermarket",
            newSuperCategory = SuperCategoryId.PLACE
        )

        assertNull(original.featureType)
        assertEquals("amenity", updated.featureType)
        assertEquals("supermarket", updated.featureValue)
        assertEquals(SuperCategoryId.PLACE, updated.superCategory)
    }

    @Test
    fun `builder accumulates properties correctly`() {
        val builder = MvtTileFeatureBuilder(osmId = 123L, layer = "transportation")
        builder.geometry = MvtLineString(listOf(
            LngLatAlt(-4.0, 55.0),
            LngLatAlt(-4.1, 55.1)
        ))
        builder.addProperty("name", "Main Street")
        builder.addProperty("class", "primary")
        builder.addProperty("subclass", "road")
        builder.addProperty("surface", "asphalt")

        val feature = builder.build()

        assertEquals(123L, feature.osmId)
        assertEquals("transportation", feature.layer)
        assertEquals("Main Street", feature.name)
        assertEquals("primary", feature.featureClass)
        assertEquals("road", feature.featureSubClass)
        assertEquals("asphalt", feature.properties["surface"])
    }

    @Test(expected = IllegalStateException::class)
    fun `builder throws if geometry not set`() {
        val builder = MvtTileFeatureBuilder(osmId = 1L, layer = "poi")
        builder.name = "Test"

        builder.build() // Should throw
    }

    @Test
    fun `data class equality works correctly`() {
        val feature1 = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(-4.0, 55.0)),
            layer = "poi",
            name = "Test"
        )

        val feature2 = MvtTileFeature(
            osmId = 1L,
            geometry = MvtPoint(LngLatAlt(-4.0, 55.0)),
            layer = "poi",
            name = "Test"
        )

        val feature3 = MvtTileFeature(
            osmId = 2L,
            geometry = MvtPoint(LngLatAlt(-4.0, 55.0)),
            layer = "poi",
            name = "Test"
        )

        assertEquals(feature1, feature2)
        assertFalse(feature1 == feature3)
    }
}
