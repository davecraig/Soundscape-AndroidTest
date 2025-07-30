package org.scottishtecharmy.soundscape.screens.home.home

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.sargunv.maplibrecompose.compose.MaplibreMap
import dev.sargunv.maplibrecompose.compose.rememberCameraState
import dev.sargunv.maplibrecompose.compose.rememberStyleState
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.io.File
import androidx.core.graphics.createBitmap
import androidx.preference.PreferenceManager
import dev.sargunv.maplibrecompose.compose.layer.SymbolLayer
import dev.sargunv.maplibrecompose.compose.source.rememberGeoJsonSource
import dev.sargunv.maplibrecompose.core.BaseStyle
import dev.sargunv.maplibrecompose.core.GestureOptions
import dev.sargunv.maplibrecompose.core.MapOptions
import dev.sargunv.maplibrecompose.core.OrnamentOptions
import dev.sargunv.maplibrecompose.core.source.GeoJsonData
import dev.sargunv.maplibrecompose.expressions.dsl.const
import dev.sargunv.maplibrecompose.expressions.dsl.image
import dev.sargunv.maplibrecompose.expressions.value.SymbolAnchor
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import org.maplibre.android.maps.MapLibreMap.OnMapLongClickListener
import org.scottishtecharmy.soundscape.MainActivity.Companion.ACCESSIBLE_MAP_DEFAULT
import org.scottishtecharmy.soundscape.MainActivity.Companion.ACCESSIBLE_MAP_KEY
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers

/**
 * Create a location marker drawable which has location_marker as it's background, and an integer
 * in the foreground. These are to mark on the map locations of waypoints within a route.
 * @param context The context to use
 * @param number The number to display within the drawable
 * @return A composited drawable
 */
fun createLocationMarkerImageBitmap(context: Context, number: Int): ImageBitmap {
    // Create a FrameLayout to hold the marker components
    val frameLayout = FrameLayout(context)
    frameLayout.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    )

    val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.location_marker)
    backgroundDrawable?.let {
        val imageView = ImageView(context)
        imageView.setImageDrawable(it)
        frameLayout.addView(imageView)
    }

    // Create the TextView for the number
    val numberTextView = TextView(context)
    numberTextView.apply {
        text = "$number"
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
    }

    // Add the TextView to the FrameLayout
    frameLayout.addView(numberTextView)

    // Measure and layout the FrameLayout
    frameLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)

    // Create a Bitmap from the FrameLayout
    val bitmap = createBitmap(frameLayout.measuredWidth, frameLayout.measuredHeight)
    val canvas = android.graphics.Canvas(bitmap)
    frameLayout.draw(canvas)

    // Create a Drawable from the Bitmap
    return bitmap.asImageBitmap()
}

@Composable
fun FullScreenMapFab(fullscreenMap: MutableState<Boolean>) {
    FloatingActionButton(onClick = { fullscreenMap.value = !fullscreenMap.value }) {
        Icon(
            imageVector = if(fullscreenMap.value) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            contentDescription = if(fullscreenMap.value)
                stringResource(R.string.location_detail_exit_full_screen_hint)
            else
                stringResource(R.string.location_detail_full_screen_hint)
        )
    }
}

/**
 * A map disable component that uses maplibre.
 *
 * @mapCenter The `LatLng` to center around
 * @mapHeading
 * @userLocation The `LatLng` to draw the user location symbol at
 * @userHeading
 * @beaconLocation An optional `LatLng` to show a beacon marker
 * @onMapLongClick Action to take if a long click is made on the map
 * @onMarkerClick Action to take if a beacon marker is clicked on
 */
@Composable
fun MapContainerLibre(
    mapCenter: LngLatAlt,
    allowScrolling: Boolean,
    userLocation: LngLatAlt?,
    userSymbolRotation: Float,
    beaconLocation: LngLatAlt?,
    routeData: RouteWithMarkers?,
    modifier: Modifier = Modifier,
    editBeaconLocation: Boolean = false,
    onMapLongClick: OnMapLongClickListener,
) {
    val cameraState = rememberCameraState(
        firstPosition = dev.sargunv.maplibrecompose.core.CameraPosition(

            target = Position(mapCenter.longitude, mapCenter.latitude),
            zoom = 15.0,
        )
    )
    val styleState = rememberStyleState()
    val context = LocalContext.current
    val sharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    val accessibleMapEnabled = sharedPreferences.getBoolean(ACCESSIBLE_MAP_KEY, ACCESSIBLE_MAP_DEFAULT)
    val styleName = if(accessibleMapEnabled) "processedStyle.json" else "processedOriginalStyle.json"
    val filesDir = context.filesDir.toString()
    val styleUri = Uri.fromFile(File("$filesDir/osm-liberty-accessible/$styleName")).toString()

    if(editBeaconLocation && (beaconLocation != null)) {
        beaconLocation.longitude = cameraState.position.target.longitude
        beaconLocation.latitude = cameraState.position.target.latitude
    }

    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = BaseStyle.Uri(styleUri),
            cameraState = cameraState,
            styleState = styleState,
            options = MapOptions(
                gestureOptions = GestureOptions(
                    isScrollEnabled = allowScrolling,
                    isTiltEnabled = false,
                    isRotateEnabled = false
                ),
                ornamentOptions = OrnamentOptions(
                    isLogoEnabled = false,
                    isAttributionEnabled = true,
                    attributionAlignment = Alignment.BottomEnd,
                    isCompassEnabled = true,
                    compassAlignment = Alignment.TopEnd,
                    isScaleBarEnabled = true,
                    scaleBarAlignment = Alignment.BottomEnd
                )
            )
        ) {
            if (userLocation != null) {
                val marker = painterResource(R.drawable.navigation)

                val position = Position(
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude
                )
                val userLocationGeoJson =
                    rememberGeoJsonSource(
                        data = GeoJsonData.Features(Point(position))
                    )

                SymbolLayer(
                    id = "user-location",
                    source = userLocationGeoJson,
                    iconImage = image(marker),
                    iconSize = const(1.2F),
                    iconRotate = const(userSymbolRotation),
                    iconAllowOverlap = const(true)
                )
            }
            if (beaconLocation != null) {
                val marker = painterResource(R.drawable.location_marker)

                val position = Position(
                    latitude = beaconLocation.latitude,
                    longitude = beaconLocation.longitude
                )
                val beaconLocationGeoJson =
                    rememberGeoJsonSource(
                        data = GeoJsonData.Features(Point(position))
                    )

                SymbolLayer(
                    id = "beacon-location",
                    source = beaconLocationGeoJson,
                    iconImage = image(marker),
                    iconSize = const(1.2F),
                    iconAllowOverlap = const(true),
                    iconAnchor = const(SymbolAnchor.Bottom)
                )
            }

            if (routeData != null) {
                val markers = remember {
                    Array(100) { index ->
                        createLocationMarkerImageBitmap(context, index + 1)
                    }
                }
                for ((index, marker) in routeData.markers.withIndex()) {
                    val position = Position(
                        latitude = marker.latitude,
                        longitude = marker.longitude
                    )
                    val markerLocationGeoJson =
                        rememberGeoJsonSource(
                            data = GeoJsonData.Features(Point(position))
                        )


                    SymbolLayer(
                        id = "marker-$index",
                        source = markerLocationGeoJson,
                        iconImage = image(markers[index]),
                        iconSize = const(1.2F),
                        iconAllowOverlap = const(true),
                        iconAnchor = const(SymbolAnchor.Bottom)
                    )
                }
            }
        }
    }
}
