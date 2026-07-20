package org.scottishtecharmy.soundscape

import android.content.SharedPreferences
import org.junit.Test
import org.scottishtecharmy.soundscape.MainActivity.Companion.SEARCH_LANGUAGE_KEY
import org.scottishtecharmy.soundscape.geoengine.getPhotonLanguage
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSharedPreferences(private val values: Map<String, String>) : SharedPreferences {
    override fun getString(key: String?, defValue: String?) = values[key] ?: defValue
    override fun getAll() = throw NotImplementedError()
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = throw NotImplementedError()
    override fun getInt(key: String?, defValue: Int) = throw NotImplementedError()
    override fun getLong(key: String?, defValue: Long) = throw NotImplementedError()
    override fun getFloat(key: String?, defValue: Float) = throw NotImplementedError()
    override fun getBoolean(key: String?, defValue: Boolean) = throw NotImplementedError()
    override fun contains(key: String?) = throw NotImplementedError()
    override fun edit(): SharedPreferences.Editor = throw NotImplementedError()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = throw NotImplementedError()
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = throw NotImplementedError()
}

/**
 * The Photon search server (https://photon.soundscape.scottishtecharmy.org) is only built with
 * English, French, German and each location's local language. Passing any other "lang" query
 * param makes Photon return an error, so getPhotonLanguage() must fall back to not passing a
 * language at all for unsupported languages (e.g. Arabic) - Photon then returns results matched
 * and named in the local language, which is exactly what's wanted.
 */
class GeoEngineLanguageTest {
    private fun withDefaultLocale(language: String, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.Builder().setLanguage(language).build())
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun unsupportedDeviceLanguageFallsBackToNull() {
        withDefaultLocale("ar") {
            assertNull(getPhotonLanguage(null))
            assertNull(getPhotonLanguage(FakeSharedPreferences(mapOf(SEARCH_LANGUAGE_KEY to "auto"))))
        }
    }

    @Test
    fun explicitSupportedLanguageOverridesDeviceLocale() {
        withDefaultLocale("ar") {
            assertEquals("en", getPhotonLanguage(FakeSharedPreferences(mapOf(SEARCH_LANGUAGE_KEY to "en"))))
        }
    }

    @Test
    fun supportedDeviceLanguageIsPassedThrough() {
        withDefaultLocale("de") {
            assertEquals("de", getPhotonLanguage(null))
        }
        withDefaultLocale("fr") {
            assertEquals("fr", getPhotonLanguage(FakeSharedPreferences(mapOf(SEARCH_LANGUAGE_KEY to "auto"))))
        }
    }
}
