package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/**
 * Common interface for spatial features, enabling gradual migration from
 * GeoJSON-based Feature/MvtFeature to native MvtTileFeature.
 *
 * This interface abstracts the essential properties needed by consumers,
 * allowing them to work with either the old or new feature types.
 */
interface SpatialFeature {
    /**
     * The OSM ID of the feature (as Long for native features, parsed from String for GeoJSON).
     */
    val osmId: Long

    /**
     * The center point of the feature, used for distance calculations and R-tree indexing.
     */
    val center: LngLatAlt

    /**
     * The name of the feature (e.g., "Tesco Express", "High Street").
     */
    val name: String?

    /**
     * The feature class from MVT (e.g., "shop", "path", "primary").
     */
    val featureClass: String?

    /**
     * The feature subclass from MVT (e.g., "supermarket", "footway").
     */
    val featureSubClass: String?

    /**
     * Translated feature type for categorization (e.g., "highway", "amenity").
     */
    val featureType: String?

    /**
     * Translated feature value for categorization (e.g., "bus_stop", "crossing").
     */
    val featureValue: String?

    /**
     * The super category for filtering and display.
     */
    val superCategory: SuperCategoryId

    /**
     * House number if this is an address point.
     */
    val housenumber: String?

    /**
     * Street name if this is an address point.
     */
    val street: String?

    /**
     * Which side of the street (for house numbers).
     */
    val side: Boolean?

    /**
     * Whether the street assignment is confident.
     */
    val streetConfidence: Boolean

    /**
     * The geometry of the feature.
     */
    val geometry: MvtGeometry

    /**
     * Which R-tree this feature belongs to (may be null if not yet assigned).
     */
    val treeId: TreeId?

    /**
     * Returns a property value by key.
     */
    fun getProperty(key: String): Any?

    /**
     * Checks if a property exists.
     */
    fun hasProperty(key: String): Boolean
}

/**
 * Extension to make MvtTileFeature implement SpatialFeature.
 * Since MvtTileFeature is a data class and we can't add interfaces after the fact,
 * we provide this wrapper.
 */
class MvtTileFeatureWrapper(private val feature: MvtTileFeature) : SpatialFeature {
    override val osmId: Long get() = feature.osmId
    override val center: LngLatAlt get() = feature.center
    override val name: String? get() = feature.name
    override val featureClass: String? get() = feature.featureClass
    override val featureSubClass: String? get() = feature.featureSubClass
    override val featureType: String? get() = feature.featureType
    override val featureValue: String? get() = feature.featureValue
    override val superCategory: SuperCategoryId get() = feature.superCategory
    override val housenumber: String? get() = feature.housenumber
    override val street: String? get() = feature.street
    override val side: Boolean? get() = feature.side
    override val streetConfidence: Boolean get() = feature.streetConfidence
    override val geometry: MvtGeometry get() = feature.geometry
    override val treeId: TreeId? get() = feature.treeId

    override fun getProperty(key: String): Any? = feature.getProperty(key)
    override fun hasProperty(key: String): Boolean = feature.hasProperty(key)

    /**
     * Returns the underlying MvtTileFeature.
     */
    fun unwrap(): MvtTileFeature = feature
}

/**
 * Extension function to wrap MvtTileFeature as SpatialFeature.
 */
fun MvtTileFeature.asSpatialFeature(): SpatialFeature = MvtTileFeatureWrapper(this)
