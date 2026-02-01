package org.scottishtecharmy.soundscape.geoengine.types

import org.scottishtecharmy.soundscape.geoengine.mvt.data.SpatialFeature

/**
 * Type alias for a mutable list of SpatialFeatures.
 * This replaces FeatureCollection throughout the geoengine layer,
 * providing a more efficient native Kotlin collection.
 */
typealias FeatureList = MutableList<SpatialFeature>

/**
 * Creates a FeatureList from the given features.
 */
fun featureListOf(vararg features: SpatialFeature): FeatureList = mutableListOf(*features)

/**
 * Creates an empty FeatureList.
 */
fun emptyFeatureList(): FeatureList = mutableListOf()
