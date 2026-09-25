package org.scottishtecharmy.soundscape.platform

import java.util.Locale

actual fun localizedLanguageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag.replace('_', '-'))
    // An unrecognised tag parses into a Locale whose display name is empty or
    // just the raw subtag, so fall back rather than render a blank heading.
    val displayName = locale.getDisplayName(Locale.getDefault())
    return displayName.ifBlank { tag }
}
