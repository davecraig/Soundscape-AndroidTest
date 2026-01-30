package org.scottishtecharmy.soundscape.screens.home.streetview

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId

/**
 * Enum representing which side of the street an item is on
 */
enum class StreetViewSide {
    LEFT,
    RIGHT,
    CENTER
}

/**
 * Data class representing a displayable street item in the Street View UI
 */
data class StreetViewItem(
    val distance: Double,           // Distance along street (meters)
    val name: String,               // Display name
    val side: StreetViewSide,       // LEFT, RIGHT, or CENTER (intersections)
    val category: SuperCategoryId,  // For icon selection
    val houseNumber: String?,       // If it's a house number
    val isIntersection: Boolean,    // For intersections
    val feature: MvtFeature?        // Original feature for callouts
)
