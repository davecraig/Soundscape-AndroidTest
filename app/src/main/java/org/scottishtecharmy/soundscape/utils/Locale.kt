package org.scottishtecharmy.soundscape.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.scottishtecharmy.soundscape.screens.onboarding.language.Language
import java.util.Locale

/**
 * Return the application locale if there is one, otherwise return the default system one
 */
fun getCurrentLocale() : Locale {
    val appLocale = AppCompatDelegate.getApplicationLocales()[0]
    return appLocale ?: Locale.getDefault()
}

/**
 * Backwards-compatible thin wrapper around the shared [getLanguageMismatch].
 * The [context] parameter is now ignored — the application context is installed
 * once at startup via [installLocaleContext] so the shared algorithm can reach
 * `LocaleManager` on its own.
 */
@Suppress("UNUSED_PARAMETER")
fun getLanguageMismatch(context: Context): Language? =
    org.scottishtecharmy.soundscape.screens.onboarding.language.getLanguageMismatch()
