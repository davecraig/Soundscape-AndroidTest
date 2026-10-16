package org.scottishtecharmy.soundscape.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun currentLanguageTag(): String = LocalConfiguration.current.locales[0].toLanguageTag()
