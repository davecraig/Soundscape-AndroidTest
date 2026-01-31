package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * Represents a single feature from an MVT tile.
 *
 * This is the native Kotlin representation of MVT data, designed to be more
 * efficient than the GeoJSON-based Feature class. Properties are stored as
 * typed fields where possible, with a generic map for additional properties.
 *
 * @property osmId The OpenStreetMap ID of the feature
 * @property geometry The geometry of the feature
 * @property layer The MVT layer this feature came from (e.g., "transportation", "poi")
 * @property treeId Which R-tree index this feature belongs to (null if not yet assigned)
 * @property name The name of the feature (extracted from tags)
 * @property featureClass The class of the feature (e.g., "path", "bus")
 * @property featureSubClass The subclass of the feature (e.g., "footway", "cycleway")
 * @property featureType Translated feature type for categorization
 * @property featureValue Translated feature value for categorization
 * @property superCategory The super category for filtering/display
 * @property housenumber House number if this is an address point
 * @property street Street name if this is an address point
 * @property side Which side of the street (for house numbers)
 * @property streetConfidence Whether the street assignment is confident
 * @property properties Additional properties not captured in typed fields
 */
data class MvtTileFeature(
    val osmId: Long,
    val geometry: MvtGeometry,
    val layer: String,
    val treeId: TreeId? = null,
    val name: String? = null,
    val featureClass: String? = null,
    val featureSubClass: String? = null,
    val featureType: String? = null,
    val featureValue: String? = null,
    val superCategory: SuperCategoryId = SuperCategoryId.UNCATEGORIZED,
    val housenumber: String? = null,
    val street: String? = null,
    val side: Boolean? = null,
    val streetConfidence: Boolean = false,
    val properties: Map<String, Any?> = emptyMap()
) {
    /**
     * Lazily computed center point of this feature's geometry.
     * Used for distance calculations and R-tree indexing.
     */
    val center: LngLatAlt by lazy {
        geometry.computeCenter()
    }

    /**
     * Returns a property value by key, checking typed fields first,
     * then falling back to the properties map.
     */
    fun getProperty(key: String): Any? {
        return when (key) {
            "name" -> name
            "class" -> featureClass
            "subclass" -> featureSubClass
            "housenumber" -> housenumber
            "street" -> street
            else -> properties[key]
        }
    }

    /**
     * Checks if a property exists (either as a typed field or in the properties map).
     */
    fun hasProperty(key: String): Boolean {
        return when (key) {
            "name" -> name != null
            "class" -> featureClass != null
            "subclass" -> featureSubClass != null
            "housenumber" -> housenumber != null
            "street" -> street != null
            else -> properties.containsKey(key)
        }
    }

    /**
     * Creates a copy with updated treeId assignment.
     */
    fun withTreeId(newTreeId: TreeId): MvtTileFeature {
        return copy(treeId = newTreeId)
    }

    /**
     * Creates a copy with translated feature type/value and super category.
     */
    fun withTranslation(
        newFeatureType: String?,
        newFeatureValue: String?,
        newSuperCategory: SuperCategoryId
    ): MvtTileFeature {
        return copy(
            featureType = newFeatureType,
            featureValue = newFeatureValue,
            superCategory = newSuperCategory
        )
    }

    companion object {
        /**
         * Creates an MvtTileFeature from parsed MVT data.
         * This is the primary factory method used during tile parsing.
         */
        fun create(
            osmId: Long,
            geometry: MvtGeometry,
            layer: String,
            name: String? = null,
            featureClass: String? = null,
            featureSubClass: String? = null,
            housenumber: String? = null,
            street: String? = null,
            properties: Map<String, Any?> = emptyMap()
        ): MvtTileFeature {
            return MvtTileFeature(
                osmId = osmId,
                geometry = geometry,
                layer = layer,
                name = name,
                featureClass = featureClass,
                featureSubClass = featureSubClass,
                housenumber = housenumber,
                street = street,
                properties = properties
            )
        }
    }
}

/**
 * Builder class for creating MvtTileFeature instances incrementally.
 * Useful during MVT parsing where properties are discovered one at a time.
 */
class MvtTileFeatureBuilder(
    private val osmId: Long,
    private val layer: String
) {
    var geometry: MvtGeometry? = null
    var name: String? = null
    var featureClass: String? = null
    var featureSubClass: String? = null
    var featureType: String? = null
    var featureValue: String? = null
    var superCategory: SuperCategoryId = SuperCategoryId.UNCATEGORIZED
    var housenumber: String? = null
    var street: String? = null
    var side: Boolean? = null
    var streetConfidence: Boolean = false
    var treeId: TreeId? = null
    private val properties = mutableMapOf<String, Any?>()

    /**
     * Adds a property, automatically extracting known fields.
     */
    fun addProperty(key: String, value: Any?) {
        when (key) {
            "name" -> name = value?.toString()
            "class" -> featureClass = value?.toString()
            "subclass" -> featureSubClass = value?.toString()
            "housenumber" -> housenumber = value?.toString()
            "street" -> street = value?.toString()
            else -> properties[key] = value
        }
    }

    /**
     * Builds the immutable MvtTileFeature.
     * @throws IllegalStateException if geometry is not set
     */
    fun build(): MvtTileFeature {
        val geom = geometry ?: throw IllegalStateException("Geometry must be set before building")
        return MvtTileFeature(
            osmId = osmId,
            geometry = geom,
            layer = layer,
            treeId = treeId,
            name = name,
            featureClass = featureClass,
            featureSubClass = featureSubClass,
            featureType = featureType,
            featureValue = featureValue,
            superCategory = superCategory,
            housenumber = housenumber,
            street = street,
            side = side,
            streetConfidence = streetConfidence,
            properties = properties.toMap()
        )
    }
}
