package org.scottishtecharmy.soundscape.geoengine.types

import org.scottishtecharmy.soundscape.geoengine.mvt.data.SpatialFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection

/**
 * Adds a feature to the list and returns the list for chaining.
 */
fun FeatureList.addFeature(feature: SpatialFeature): FeatureList {
    add(feature)
    return this
}

/**
 * Adds all features from another list, deduplicating by reference.
 * Replaces FeatureCollection.plusAssignDeduplicate()
 */
fun FeatureList.addAllDeduplicated(other: FeatureList) {
    for (feature in other) {
        if (!contains(feature)) {
            add(feature)
        }
    }
}

/**
 * Adds all features from another list, deduplicating by OSM ID.
 */
fun FeatureList.addAllDeduplicatedByOsmId(other: FeatureList) {
    val existingIds = this.mapNotNull { it.osmId }.toMutableSet()
    for (feature in other) {
        val osmId = feature.osmId
        if (osmId !in existingIds) {
            add(feature)
            existingIds.add(osmId)
        }
    }
}

/**
 * Converts a FeatureList to a FeatureCollection for JSON serialization boundaries.
 * MvtFeatures are converted to Feature using toFeature().
 */
fun FeatureList.toFeatureCollection(): FeatureCollection {
    val fc = FeatureCollection()
    for (feature in this) {
        when (feature) {
            is Feature -> fc.features.add(feature)
            is MvtFeature -> fc.features.add(feature.toFeature())
        }
    }
    return fc
}

/**
 * Converts a FeatureCollection to a FeatureList for internal processing.
 * Features must implement SpatialFeature (like MvtFeature does).
 */
fun FeatureCollection.toFeatureList(): FeatureList {
    val result = emptyFeatureList()
    for (feature in features) {
        if (feature is SpatialFeature) {
            result.add(feature)
        }
    }
    return result
}

/**
 * Extension to add a FeatureList to another FeatureList (operator +=).
 */
operator fun FeatureList.plusAssign(other: FeatureList) {
    addAll(other)
}

/**
 * Extension to add a single feature to a FeatureList (operator +=).
 */
operator fun FeatureList.plusAssign(feature: SpatialFeature) {
    add(feature)
}
