package org.scottishtecharmy.soundscape.platform

import androidx.compose.runtime.Composable
import org.scottishtecharmy.soundscape.screens.onboarding.language.getAppLocale
import org.scottishtecharmy.soundscape.screens.onboarding.language.getSystemLocale

// The app's own language setting if there is one, otherwise the system's
@Composable
actual fun currentLanguageTag(): String {
    val locale = getAppLocale() ?: getSystemLocale()
    return listOfNotNull(locale.language, locale.region).joinToString("-")
}
