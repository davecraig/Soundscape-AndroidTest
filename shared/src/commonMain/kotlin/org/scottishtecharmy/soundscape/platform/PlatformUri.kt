package org.scottishtecharmy.soundscape.platform

// Percent-encoding with the same unreserved set as Android's Uri.encode, written in common code so
// that help routes encode identically on both platforms. FAQ questions contain "?" and ":", which
// would otherwise break the route.
private const val UNRESERVED = "_-!.~'()*"
private const val HEX = "0123456789ABCDEF"

private fun isUnreserved(c: Char) =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || UNRESERVED.indexOf(c) >= 0

fun urlEncode(s: String): String = buildString {
    for (byte in s.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (byte >= 0 && isUnreserved(c)) {
            append(c)
        } else {
            val b = byte.toInt() and 0xFF
            append('%')
            append(HEX[b shr 4])
            append(HEX[b and 0x0F])
        }
    }
}

fun urlDecode(s: String): String {
    if ('%' !in s) return s
    val bytes = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val hi = if (s[i] == '%' && i + 2 < s.length) s[i + 1].digitToIntOrNull(16) else null
        val lo = if (hi != null) s[i + 2].digitToIntOrNull(16) else null
        if (hi != null && lo != null) {
            bytes.add(((hi shl 4) or lo).toByte())
            i += 3
        } else {
            // Copy everything up to the next escape through unchanged, as UTF-8
            var end = s.indexOf('%', i + 1)
            if (end < 0) end = s.length
            s.substring(i, end).encodeToByteArray().forEach { bytes.add(it) }
            i = end
        }
    }
    return bytes.toByteArray().decodeToString()
}
