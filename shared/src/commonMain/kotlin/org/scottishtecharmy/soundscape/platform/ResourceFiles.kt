package org.scottishtecharmy.soundscape.platform

import androidx.compose.runtime.Composable

/**
 * Reads a Compose Resources file, as Res.readBytes does. On Android, Res.readBytes needs an
 * Android context which a preview only gets after its first frame, so this falls back to the
 * composition's own context there.
 */
@Composable
expect fun rememberResourceFileReader(): suspend (path: String) -> ByteArray
