package org.scottishtecharmy.soundscape.platform

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLocaleIdentifier

actual fun localizedLanguageName(tag: String): String {
    val identifier = tag.replace('-', '_')
    val displayName = NSLocale.currentLocale
        .localizedStringForLocaleIdentifier(identifier)
        .orEmpty()
    return displayName.ifBlank { tag }
}
