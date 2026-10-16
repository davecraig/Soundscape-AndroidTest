package org.scottishtecharmy.soundscape.platform

import androidx.compose.runtime.Composable
import org.scottishtecharmy.soundscape.resources.Res

@Composable
actual fun rememberResourceFileReader(): suspend (path: String) -> ByteArray = { Res.readBytes(it) }
