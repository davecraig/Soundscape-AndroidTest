package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

class MvtTileDataTest {

    private fun createTestFeature(osmId: Long, name: String = "Test"): MvtTileFeature {
        return MvtTileFeature(
            osmId = osmId,
            geometry = MvtPoint(LngLatAlt(-4.0, 55.0)),
            layer = "poi",
            name = name
        )
    }

    @Test
    fun `empty tile data has zero features`() {
        val tileData = MvtTileData.empty(100, 200, 15)

        assertEquals(100, tileData.tileX)
        assertEquals(200, tileData.tileY)
        assertEquals(15, tileData.zoom)
        assertEquals(0, tileData.featureCount)
        assertTrue(tileData.allFeatures.isEmpty())
    }

    @Test
    fun `tileKey returns correct key`() {
        val tileData = MvtTileData(tileX = 100, tileY = 200, zoom = 15)

        val key = tileData.tileKey

        assertEquals(100, key.x)
        assertEquals(200, key.y)
        assertEquals(15, key.zoom)
        assertEquals("15/100/200", key.toString())
    }

    @Test
    fun `getFeatures returns features for tree`() {
        val features = listOf(
            createTestFeature(1L, "POI 1"),
            createTestFeature(2L, "POI 2")
        )
        val tileData = MvtTileData(
            tileX = 100,
            tileY = 200,
            zoom = 15,
            featuresByTree = mapOf(TreeId.POIS to features)
        )

        val result = tileData.getFeatures(TreeId.POIS)

        assertEquals(2, result.size)
        assertEquals("POI 1", result[0].name)
        assertEquals("POI 2", result[1].name)
    }

    @Test
    fun `getFeatures returns empty list for missing tree`() {
        val tileData = MvtTileData.empty(100, 200, 15)

        val result = tileData.getFeatures(TreeId.POIS)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `hasFeatures returns correct values`() {
        val tileData = MvtTileData(
            tileX = 100,
            tileY = 200,
            zoom = 15,
            featuresByTree = mapOf(
                TreeId.POIS to listOf(createTestFeature(1L)),
                TreeId.ROADS to emptyList()
            )
        )

        assertTrue(tileData.hasFeatures(TreeId.POIS))
        assertFalse(tileData.hasFeatures(TreeId.ROADS))
        assertFalse(tileData.hasFeatures(TreeId.INTERSECTIONS))
    }

    @Test
    fun `allFeatures returns flattened list`() {
        val tileData = MvtTileData(
            tileX = 100,
            tileY = 200,
            zoom = 15,
            featuresByTree = mapOf(
                TreeId.POIS to listOf(createTestFeature(1L), createTestFeature(2L)),
                TreeId.ROADS to listOf(createTestFeature(3L))
            )
        )

        val all = tileData.allFeatures

        assertEquals(3, all.size)
    }

    @Test
    fun `featureCount sums all trees`() {
        val tileData = MvtTileData(
            tileX = 100,
            tileY = 200,
            zoom = 15,
            featuresByTree = mapOf(
                TreeId.POIS to listOf(createTestFeature(1L), createTestFeature(2L)),
                TreeId.ROADS to listOf(createTestFeature(3L)),
                TreeId.INTERSECTIONS to listOf(createTestFeature(4L), createTestFeature(5L), createTestFeature(6L))
            )
        )

        assertEquals(6, tileData.featureCount)
    }

    @Test
    fun `withFeatures adds features to tree`() {
        val original = MvtTileData(
            tileX = 100,
            tileY = 200,
            zoom = 15,
            featuresByTree = mapOf(TreeId.POIS to listOf(createTestFeature(1L)))
        )

        val updated = original.withFeatures(TreeId.POIS, listOf(createTestFeature(2L)))

        assertEquals(1, original.getFeatures(TreeId.POIS).size)
        assertEquals(2, updated.getFeatures(TreeId.POIS).size)
    }

    @Test
    fun `withFeatures creates new tree if not exists`() {
        val original = MvtTileData.empty(100, 200, 15)

        val updated = original.withFeatures(TreeId.ROADS, listOf(createTestFeature(1L)))

        assertFalse(original.hasFeatures(TreeId.ROADS))
        assertTrue(updated.hasFeatures(TreeId.ROADS))
    }

    // TileKey tests

    @Test
    fun `TileKey fromString parses correctly`() {
        val key = TileKey.fromString("15/100/200")

        assertEquals(15, key?.zoom)
        assertEquals(100, key?.x)
        assertEquals(200, key?.y)
    }

    @Test
    fun `TileKey fromString returns null for invalid format`() {
        assertNull(TileKey.fromString("invalid"))
        assertNull(TileKey.fromString("15/100"))
        assertNull(TileKey.fromString("a/b/c"))
    }

    @Test
    fun `TileKey equality works correctly`() {
        val key1 = TileKey(100, 200, 15)
        val key2 = TileKey(100, 200, 15)
        val key3 = TileKey(101, 200, 15)

        assertEquals(key1, key2)
        assertFalse(key1 == key3)
    }

    // Builder tests

    @Test
    fun `builder accumulates features by tree`() {
        val builder = MvtTileDataBuilder(tileX = 100, tileY = 200, zoom = 15)

        builder.addFeature(TreeId.POIS, createTestFeature(1L))
        builder.addFeature(TreeId.POIS, createTestFeature(2L))
        builder.addFeature(TreeId.ROADS, createTestFeature(3L))

        val tileData = builder.build()

        assertEquals(2, tileData.getFeatures(TreeId.POIS).size)
        assertEquals(1, tileData.getFeatures(TreeId.ROADS).size)
        assertEquals(3, tileData.featureCount)
    }

    @Test
    fun `builder addFeatures adds multiple at once`() {
        val builder = MvtTileDataBuilder(tileX = 100, tileY = 200, zoom = 15)
        val features = listOf(createTestFeature(1L), createTestFeature(2L), createTestFeature(3L))

        builder.addFeatures(TreeId.POIS, features)

        val tileData = builder.build()
        assertEquals(3, tileData.getFeatures(TreeId.POIS).size)
    }

    @Test
    fun `builder getFeatureCount returns current count`() {
        val builder = MvtTileDataBuilder(tileX = 100, tileY = 200, zoom = 15)

        assertEquals(0, builder.getFeatureCount(TreeId.POIS))

        builder.addFeature(TreeId.POIS, createTestFeature(1L))
        assertEquals(1, builder.getFeatureCount(TreeId.POIS))

        builder.addFeature(TreeId.POIS, createTestFeature(2L))
        assertEquals(2, builder.getFeatureCount(TreeId.POIS))
    }

    @Test
    fun `timestamp is set on creation`() {
        val before = System.currentTimeMillis()
        val tileData = MvtTileData.empty(100, 200, 15)
        val after = System.currentTimeMillis()

        assertTrue(tileData.timestamp >= before)
        assertTrue(tileData.timestamp <= after)
    }
}
