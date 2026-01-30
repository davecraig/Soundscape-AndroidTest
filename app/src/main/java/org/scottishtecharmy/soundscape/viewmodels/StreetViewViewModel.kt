package org.scottishtecharmy.soundscape.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import org.scottishtecharmy.soundscape.audio.AudioType
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.PositionedString
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.filters.TrackedCallout
import org.scottishtecharmy.soundscape.geoengine.getTextForFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geoengine.utils.geocoders.StreetDescription
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.streetview.StreetViewItem
import org.scottishtecharmy.soundscape.screens.home.streetview.StreetViewSide
import javax.inject.Inject

data class StreetViewUiState(
    val streetName: String = "",
    val streetItems: List<StreetViewItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StreetViewViewModel @Inject constructor(
    private val soundscapeServiceConnection: SoundscapeServiceConnection,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreetViewUiState())
    val uiState: StateFlow<StreetViewUiState> = _uiState.asStateFlow()

    private var lastAnnouncedIndex = -1

    /**
     * Load street data for the given street name and location
     */
    fun loadStreetData(streetName: String, location: LngLatAlt) {
        viewModelScope.launch {
            _uiState.value = StreetViewUiState(
                streetName = streetName,
                isLoading = true
            )

            try {
                val gridState = soundscapeServiceConnection.getGridStateFlow()?.value
                if (gridState == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Grid state not available"
                    )
                    return@launch
                }

                // Find the matching Way for this street using the grid context
                val matchedWay = withContext(gridState.treeContext) {
                    findNearestNamedWay(gridState, location, streetName)
                }

                if (matchedWay == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not find street"
                    )
                    return@launch
                }

                // Create street description and build items
                val items = withContext(gridState.treeContext) {
                    val description = StreetDescription(streetName, gridState)
                    description.createDescription(matchedWay, context)
                    buildStreetItems(description, context)
                }

                _uiState.value = StreetViewUiState(
                    streetName = streetName,
                    streetItems = items,
                    isLoading = false
                )

                Log.d(TAG, "Loaded ${items.size} street items for $streetName")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading street data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Find the nearest Way with the given name
     */
    private fun findNearestNamedWay(
        gridState: GridState,
        location: LngLatAlt,
        name: String
    ): Way? {
        val nearestWays = gridState.getFeatureTree(TreeId.ROADS).getNearestCollection(
            location,
            100.0,
            10,
            gridState.ruler
        )
        for (way in nearestWays) {
            val wayName = (way as MvtFeature?)?.name
            if (wayName == name) {
                return way as Way?
            }
        }
        return null
    }

    /**
     * Build list of StreetViewItems from a StreetDescription
     */
    private fun buildStreetItems(
        description: StreetDescription,
        localizedContext: Context?
    ): List<StreetViewItem> {
        val items = mutableListOf<StreetViewItem>()

        // Add descriptive points (intersections, POI)
        for ((distance, feature) in description.sortedDescriptivePoints) {
            val isIntersection = feature is Intersection
            val text = getTextForFeature(localizedContext, feature).text

            items.add(
                StreetViewItem(
                    distance = distance,
                    name = text,
                    side = when (feature.side) {
                        true -> StreetViewSide.RIGHT
                        false -> StreetViewSide.LEFT
                        null -> StreetViewSide.CENTER
                    },
                    category = feature.superCategory,
                    houseNumber = feature.housenumber,
                    isIntersection = isIntersection,
                    feature = feature
                )
            )
        }

        // Add house numbers (left)
        for ((distance, feature) in description.leftSortedNumbers) {
            items.add(
                StreetViewItem(
                    distance = distance,
                    name = feature.housenumber ?: "",
                    side = StreetViewSide.LEFT,
                    category = SuperCategoryId.HOUSENUMBER,
                    houseNumber = feature.housenumber,
                    isIntersection = false,
                    feature = feature
                )
            )
        }

        // Add house numbers (right)
        for ((distance, feature) in description.rightSortedNumbers) {
            items.add(
                StreetViewItem(
                    distance = distance,
                    name = feature.housenumber ?: "",
                    side = StreetViewSide.RIGHT,
                    category = SuperCategoryId.HOUSENUMBER,
                    houseNumber = feature.housenumber,
                    isIntersection = false,
                    feature = feature
                )
            )
        }

        // Sort by distance
        return items.sortedBy { it.distance }
    }

    /**
     * Called when visible items change during scrolling.
     * Announces the item at the center of the screen.
     */
    fun onVisibleItemsChanged(centerIndex: Int) {
        if (centerIndex != lastAnnouncedIndex && centerIndex >= 0) {
            lastAnnouncedIndex = centerIndex
            val item = _uiState.value.streetItems.getOrNull(centerIndex)
            item?.let { announceItem(it) }
        }
    }

    /**
     * Announce an item using text-to-speech
     */
    private fun announceItem(item: StreetViewItem) {
        val text = buildString {
            append(item.name)
            if (!item.houseNumber.isNullOrEmpty() && item.name != item.houseNumber) {
                append(" ${item.houseNumber}")
            }
            if (item.isIntersection) {
                append(", intersection")
            } else {
                val sideText = when (item.side) {
                    StreetViewSide.LEFT -> "on left"
                    StreetViewSide.RIGHT -> "on right"
                    StreetViewSide.CENTER -> ""
                }
                if (sideText.isNotEmpty()) {
                    append(", $sideText")
                }
            }
        }

        soundscapeServiceConnection.soundscapeService?.speakCallout(
            TrackedCallout(
                positionedStrings = listOf(
                    PositionedString(text = text, type = AudioType.STANDARD)
                ),
                filter = false
            ),
            false
        )
    }

    /**
     * Manually trigger announcement for an item (e.g., when tapped)
     */
    fun announceItemManually(item: StreetViewItem) {
        announceItem(item)
    }

    companion object {
        private const val TAG = "StreetViewViewModel"
    }
}
