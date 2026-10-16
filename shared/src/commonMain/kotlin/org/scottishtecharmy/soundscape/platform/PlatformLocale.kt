package org.scottishtecharmy.soundscape.platform

import androidx.compose.runtime.Composable

/**
 * The BCP-47 tag of the language the UI is showing, e.g. "pt-BR". It comes from the same place
 * Compose Resources takes the language for strings, so Markdown help matches the rest of the UI,
 * including in previews and with the app's own language setting.
 */
@Composable
expect fun currentLanguageTag(): String
