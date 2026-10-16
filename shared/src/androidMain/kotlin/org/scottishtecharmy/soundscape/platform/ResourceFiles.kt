package org.scottishtecharmy.soundscape.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.scottishtecharmy.soundscape.resources.Res

// Where Compose Resources puts its files in the Android assets
private const val ASSETS_DIRECTORY = "composeResources/org.scottishtecharmy.soundscape.resources"

@Composable
actual fun rememberResourceFileReader(): suspend (path: String) -> ByteArray {
    val assets = LocalContext.current.assets
    return remember(assets) {
        { path ->
            try {
                Res.readBytes(path)
            } catch (e: Exception) {
                assets.open("$ASSETS_DIRECTORY/$path").use { it.readBytes() }
            }
        }
    }
}
