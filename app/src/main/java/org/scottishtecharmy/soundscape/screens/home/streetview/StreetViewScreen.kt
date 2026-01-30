package org.scottishtecharmy.soundscape.screens.home.streetview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.ui.theme.SoundscapeTheme
import org.scottishtecharmy.soundscape.ui.theme.spacing
import org.scottishtecharmy.soundscape.viewmodels.StreetViewUiState
import org.scottishtecharmy.soundscape.viewmodels.StreetViewViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * Main Street View screen composable with ViewModel integration
 */
@Composable
fun StreetViewScreenVM(
    navController: NavController,
    streetName: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    viewModel: StreetViewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(streetName, latitude, longitude) {
        viewModel.loadStreetData(streetName, LngLatAlt(longitude, latitude))
    }

    StreetViewScreen(
        uiState = uiState,
        onNavigateBack = { navController.navigateUp() },
        onItemClick = { viewModel.announceItemManually(it) },
        onVisibleItemsChanged = { viewModel.onVisibleItemsChanged(it) },
        modifier = modifier
    )
}

/**
 * Street View screen composable (stateless for previews)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreetViewScreen(
    uiState: StreetViewUiState,
    onNavigateBack: () -> Unit,
    onItemClick: (StreetViewItem) -> Unit,
    onVisibleItemsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.streetName.isNotEmpty()) {
                            stringResource(R.string.street_view_title_with_name, uiState.streetName)
                        } else {
                            stringResource(R.string.street_view_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ui_back_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.error != null -> {
                    ErrorContent(error = uiState.error)
                }
                uiState.streetItems.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    StreetViewContent(
                        items = uiState.streetItems,
                        onItemClick = onItemClick,
                        onVisibleItemsChanged = onVisibleItemsChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                text = stringResource(R.string.general_loading_start),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorContent(error: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.medium)
        )
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.street_view_no_items),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.medium)
        )
    }
}

/**
 * Main scrollable content showing street items
 */
@Composable
private fun StreetViewContent(
    items: List<StreetViewItem>,
    onItemClick: (StreetViewItem) -> Unit,
    onVisibleItemsChanged: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    // Track visible items and notify when center item changes
    val centerIndex by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) -1
            else {
                val viewportCenter = listState.layoutInfo.viewportStartOffset +
                        (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) / 2
                visibleItems.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: -1
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .collect { index ->
                onVisibleItemsChanged(index)
            }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left side items
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            itemsIndexed(items) { index, item ->
                StreetItemRow(
                    item = item,
                    showOnLeft = true,
                    onClick = { onItemClick(item) },
                    isCenter = index == centerIndex
                )
            }
        }

        // Center dashed line
        StreetCenterLine(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
        )

        // Right side items (mirrored structure)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            itemsIndexed(items) { index, item ->
                StreetItemRow(
                    item = item,
                    showOnLeft = false,
                    onClick = { onItemClick(item) },
                    isCenter = index == centerIndex
                )
            }
        }
    }
}

/**
 * Individual street item row
 */
@Composable
private fun StreetItemRow(
    item: StreetViewItem,
    showOnLeft: Boolean,
    onClick: () -> Unit,
    isCenter: Boolean
) {
    val shouldShow = when {
        item.isIntersection -> true // Always show intersections on both sides
        showOnLeft && item.side == StreetViewSide.LEFT -> true
        !showOnLeft && item.side == StreetViewSide.RIGHT -> true
        else -> false
    }

    val contentDescription = buildContentDescription(item)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .then(
                if (isCenter) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                this.contentDescription = if (shouldShow) contentDescription else ""
            },
        contentAlignment = if (showOnLeft) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (shouldShow) {
            if (item.isIntersection) {
                IntersectionRow(item = item, isLeftSide = showOnLeft)
            } else {
                ItemContent(item = item, isLeftSide = showOnLeft)
            }
        }
    }
}

@Composable
private fun ItemContent(
    item: StreetViewItem,
    isLeftSide: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeftSide) Arrangement.End else Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.small)
    ) {
        if (isLeftSide) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "${item.distance.roundToInt()}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            Icon(
                imageVector = getIconForCategory(item.category),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = getIconForCategory(item.category),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(spacing.small))
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.distance.roundToInt()}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntersectionRow(
    item: StreetViewItem,
    isLeftSide: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.small)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.outline
        )
        if (isLeftSide) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = spacing.small)
            )
        }
    }
}

/**
 * Vertical dashed line representing the street center
 */
@Composable
private fun StreetCenterLine(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        val pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(20f, 10f),
            0f
        )
        drawLine(
            color = lineColor,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 4f,
            pathEffect = pathEffect
        )
    }
}

/**
 * Get appropriate icon for a category
 */
private fun getIconForCategory(category: SuperCategoryId): ImageVector {
    return when (category) {
        SuperCategoryId.LANDMARK -> Icons.Filled.Place
        SuperCategoryId.PLACE -> Icons.Filled.Store
        SuperCategoryId.MOBILITY -> Icons.Filled.DirectionsBus
        SuperCategoryId.SAFETY -> Icons.Filled.LocalHospital
        SuperCategoryId.INFORMATION -> Icons.Filled.Info
        SuperCategoryId.HOUSENUMBER -> Icons.Filled.Home
        else -> Icons.Filled.LocationOn
    }
}

/**
 * Build accessibility content description for an item
 */
private fun buildContentDescription(item: StreetViewItem): String {
    return buildString {
        append(item.name)
        if (!item.houseNumber.isNullOrEmpty() && item.name != item.houseNumber) {
            append(", ${item.houseNumber}")
        }
        append(", ${item.distance.roundToInt()} meters")
        if (item.isIntersection) {
            append(", intersection")
        } else {
            val sideText = when (item.side) {
                StreetViewSide.LEFT -> ", on left side"
                StreetViewSide.RIGHT -> ", on right side"
                StreetViewSide.CENTER -> ""
            }
            append(sideText)
        }
    }
}

/**
 * Generate the route string for navigation
 */
fun generateStreetViewRoute(streetName: String, latitude: Double, longitude: Double): String {
    return "street_view/${java.net.URLEncoder.encode(streetName, "UTF-8")}/$latitude/$longitude"
}

// Previews

@Preview(showBackground = true)
@Composable
private fun StreetViewScreenLoadingPreview() {
    SoundscapeTheme {
        StreetViewScreen(
            uiState = StreetViewUiState(isLoading = true),
            onNavigateBack = {},
            onItemClick = {},
            onVisibleItemsChanged = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StreetViewScreenErrorPreview() {
    SoundscapeTheme {
        StreetViewScreen(
            uiState = StreetViewUiState(
                isLoading = false,
                error = "Could not load street data"
            ),
            onNavigateBack = {},
            onItemClick = {},
            onVisibleItemsChanged = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StreetViewScreenContentPreview() {
    val sampleItems = listOf(
        StreetViewItem(
            distance = 0.0,
            name = "1st Avenue",
            side = StreetViewSide.CENTER,
            category = SuperCategoryId.UNCATEGORIZED,
            houseNumber = null,
            isIntersection = true,
            feature = null
        ),
        StreetViewItem(
            distance = 25.0,
            name = "Bakery",
            side = StreetViewSide.LEFT,
            category = SuperCategoryId.PLACE,
            houseNumber = null,
            isIntersection = false,
            feature = null
        ),
        StreetViewItem(
            distance = 42.0,
            name = "42",
            side = StreetViewSide.RIGHT,
            category = SuperCategoryId.HOUSENUMBER,
            houseNumber = "42",
            isIntersection = false,
            feature = null
        ),
        StreetViewItem(
            distance = 75.0,
            name = "Coffee Shop",
            side = StreetViewSide.LEFT,
            category = SuperCategoryId.PLACE,
            houseNumber = null,
            isIntersection = false,
            feature = null
        ),
        StreetViewItem(
            distance = 100.0,
            name = "2nd Avenue",
            side = StreetViewSide.CENTER,
            category = SuperCategoryId.UNCATEGORIZED,
            houseNumber = null,
            isIntersection = true,
            feature = null
        )
    )

    SoundscapeTheme {
        StreetViewScreen(
            uiState = StreetViewUiState(
                streetName = "Main Street",
                streetItems = sampleItems,
                isLoading = false
            ),
            onNavigateBack = {},
            onItemClick = {},
            onVisibleItemsChanged = {}
        )
    }
}
