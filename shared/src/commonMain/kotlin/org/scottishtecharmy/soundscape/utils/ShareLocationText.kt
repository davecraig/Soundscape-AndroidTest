package org.scottishtecharmy.soundscape.utils

import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription

/**
 * Build the body text for "Share location". The Soundscape link is universal
 * across platforms; the secondary maps URL differs (Google Maps on Android,
 * Apple Maps on iOS), which is what [mapsName] and [mapsUrlBuilder] are for.
 *
 * The template uses positional placeholders `%1$s` (name), `%2$s` (Soundscape
 * URL) and `%3$s` (maps URL); the literal text "Google Maps" inside the
 * template is rewritten to [mapsName] for platforms that prefer a different
 * mapping app.
 */
fun buildShareLocationText(
    desc: LocationDescription,
    messageTemplate: String,
    mapsName: String,
    mapsUrlBuilder: (latitude: String, longitude: String, encodedName: String) -> String,
): String {
    val latitude = formatCoordinate5(desc.location.latitude)
    val longitude = formatCoordinate5(desc.location.longitude)
    val encodedName = urlEncodeUtf8(desc.name)
    val soundscapeUrl =
        "https://links.soundscape.scottishtecharmy.org/v1/sharemarker?" +
                "lat=$latitude&lon=$longitude&name=$encodedName"
    val mapsUrl = mapsUrlBuilder(latitude, longitude, encodedName)
    return messageTemplate
        .replace("Google Maps", mapsName)
        .replace("%1\$s", desc.name)
        .replace("%2\$s", soundscapeUrl)
        .replace("%3\$s", mapsUrl)
}

/** Format a double to exactly 5 decimal places, no locale-specific separators. */
private fun formatCoordinate5(value: Double): String {
    val scaled = kotlin.math.round(value * 100000.0) / 100000.0
    val asString = scaled.toString()
    val dot = asString.indexOf('.')
    return when {
        dot < 0 -> "$asString.00000"
        asString.length - dot - 1 >= 5 -> asString.substring(0, dot + 6)
        else -> asString + "0".repeat(5 - (asString.length - dot - 1))
    }
}

/** RFC 3986 percent-encoding of UTF-8 bytes; encodes spaces as `%20`. */
private fun urlEncodeUtf8(value: String): String {
    val bytes = value.encodeToByteArray()
    val builder = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        val isUnreserved = (c in 0x30..0x39) || // 0-9
                (c in 0x41..0x5A) || // A-Z
                (c in 0x61..0x7A) || // a-z
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        if (isUnreserved) {
            builder.append(c.toChar())
        } else {
            builder.append('%')
            builder.append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
