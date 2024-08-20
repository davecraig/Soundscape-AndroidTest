package org.scottishtecharmy.soundscape.viewmodels

import android.util.Log
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.BuildConfig
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import org.scottishtecharmy.soundscape.utils.getNormalizedMapCoordinates
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.centerOnMarker
import ovh.plrapps.mapcompose.api.enableRotation
import ovh.plrapps.mapcompose.api.hasMarker
import ovh.plrapps.mapcompose.api.moveMarker
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.layout.Forced
import ovh.plrapps.mapcompose.ui.state.MapState
import java.net.URL
import javax.inject.Inject
import kotlin.math.pow

@HiltViewModel
class HomeViewModel @Inject constructor(soundscapeServiceConnection : SoundscapeServiceConnection): ViewModel() {

    private val tileStreamProvider = TileStreamProvider { row, col, zoomLvl ->
        val style = "atlas"
        val apikey = BuildConfig.TILE_PROVIDER_API_KEY

        try {
            URL("https://tile.thunderforest.com/$style/$zoomLvl/$col/$row.png?apikey=$apikey").openStream()
        } catch (e : Exception) {
            Log.e("TileProvider", "Exception $e")
            URL("https://tile.thunderforest.com/$style/$zoomLvl/$col/$row.png?apikey=$apikey").openStream()
        }
    }

    private var serviceConnection : SoundscapeServiceConnection? = null
    private var x : Double = 0.0
    private var y : Double = 0.0
    private var heading : Float = 0.0F

    private fun startMonitoringLocation() {
        viewModelScope.launch {
            // observe location updates from the service
            serviceConnection?.soundscapeService?.locationFlow?.collectLatest { value ->
                if (value != null) {
                    val coordinates = getNormalizedMapCoordinates(
                        value.latitude,
                        value.longitude
                    )
                    x = coordinates.first
                    y = coordinates.second


                    if (!state.hasMarker("position")) {
                        state.addMarker("position", x, y) {
                            Icon(
                                painter = painterResource(id = R.drawable.my_location_24px),
                                contentDescription = null,
                                tint = Color(0xCC2196F3)
                            )
                        }
                        state.centerOnMarker("position")
                    } else {
                        state.moveMarker("position", x, y)
                    }
                }
            }
        }
        viewModelScope.launch {
            // observe orientation updates from the service
            serviceConnection?.soundscapeService?.orientationFlow?.collectLatest { value ->
                if (value != null) {
                    heading = value.headingDegrees
                }
            }
        }
    }

    init {
        serviceConnection = soundscapeServiceConnection
        viewModelScope.launch {
            soundscapeServiceConnection.serviceBoundState.collect {
                if(it) {
                    startMonitoringLocation()
                }
            }
        }
    }

    private val maxLevel = 20
    private val minLevel = 5
    private val mapSize = mapSizeAtLevel(maxLevel, tileSize = 256)

    val state = MapState(levelCount = maxLevel + 1, mapSize, mapSize, workerCount = 16) {
        minimumScaleMode(Forced((1 / 2.0.pow(maxLevel - minLevel)).toFloat()))
    }.apply {
        addLayer(tileStreamProvider)
        enableRotation()
        scale = 0.5f
    }

    private fun mapSizeAtLevel(wmtsLevel: Int, tileSize: Int): Int {
        return tileSize * 2.0.pow(wmtsLevel).toInt()
    }
}