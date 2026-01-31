package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.scottishtecharmy.soundscape.geoengine.TreeId

/**
 * Represents the parsed data from a single MVT tile.
 *
 * This is the container for all features extracted from a tile, organized
 * by their tree ID (category). This replaces the Array<FeatureCollection>
 * pattern used in the current implementation.
 *
 * @property tileX The X coordinate of the tile
 * @property tileY The Y coordinate of the tile
 * @property zoom The zoom level of the tile
 * @property featuresByTree Features organized by their TreeId category
 * @property timestamp When this tile was parsed (for cache management)
 */
data class MvtTileData(
    val tileX: Int,
    val tileY: Int,
    val zoom: Int,
    val featuresByTree: Map<TreeId, List<MvtTileFeature>> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns the tile key used for caching and lookup.
     */
    val tileKey: TileKey
        get() = TileKey(tileX, tileY, zoom)

    /**
     * Returns all features in this tile, regardless of tree assignment.
     */
    val allFeatures: List<MvtTileFeature>
        get() = featuresByTree.values.flatten()

    /**
     * Returns features for a specific tree/category.
     */
    fun getFeatures(treeId: TreeId): List<MvtTileFeature> {
        return featuresByTree[treeId] ?: emptyList()
    }

    /**
     * Returns the total number of features in this tile.
     */
    val featureCount: Int
        get() = featuresByTree.values.sumOf { it.size }

    /**
     * Checks if this tile has any features for the given tree.
     */
    fun hasFeatures(treeId: TreeId): Boolean {
        return featuresByTree[treeId]?.isNotEmpty() == true
    }

    /**
     * Returns a new MvtTileData with additional features added to a tree.
     */
    fun withFeatures(treeId: TreeId, features: List<MvtTileFeature>): MvtTileData {
        val newMap = featuresByTree.toMutableMap()
        val existing = newMap[treeId] ?: emptyList()
        newMap[treeId] = existing + features
        return copy(featuresByTree = newMap)
    }

    companion object {
        /**
         * Creates an empty MvtTileData for a given tile location.
         */
        fun empty(tileX: Int, tileY: Int, zoom: Int): MvtTileData {
            return MvtTileData(tileX, tileY, zoom)
        }
    }
}

/**
 * Immutable key for identifying a tile by its coordinates and zoom level.
 */
data class TileKey(
    val x: Int,
    val y: Int,
    val zoom: Int
) {
    override fun toString(): String = "$zoom/$x/$y"

    companion object {
        /**
         * Parses a tile key from a string in the format "zoom/x/y".
         */
        fun fromString(str: String): TileKey? {
            val parts = str.split("/")
            if (parts.size != 3) return null
            return try {
                TileKey(
                    x = parts[1].toInt(),
                    y = parts[2].toInt(),
                    zoom = parts[0].toInt()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

/**
 * Builder for constructing MvtTileData during tile parsing.
 * Features are accumulated and then built into an immutable MvtTileData.
 */
class MvtTileDataBuilder(
    private val tileX: Int,
    private val tileY: Int,
    private val zoom: Int
) {
    private val featuresByTree = mutableMapOf<TreeId, MutableList<MvtTileFeature>>()

    /**
     * Adds a feature to the specified tree.
     */
    fun addFeature(treeId: TreeId, feature: MvtTileFeature) {
        featuresByTree.getOrPut(treeId) { mutableListOf() }.add(feature)
    }

    /**
     * Adds multiple features to the specified tree.
     */
    fun addFeatures(treeId: TreeId, features: List<MvtTileFeature>) {
        featuresByTree.getOrPut(treeId) { mutableListOf() }.addAll(features)
    }

    /**
     * Returns the current feature count for a tree.
     */
    fun getFeatureCount(treeId: TreeId): Int {
        return featuresByTree[treeId]?.size ?: 0
    }

    /**
     * Builds the immutable MvtTileData.
     */
    fun build(): MvtTileData {
        return MvtTileData(
            tileX = tileX,
            tileY = tileY,
            zoom = zoom,
            featuresByTree = featuresByTree.mapValues { it.value.toList() }
        )
    }
}
