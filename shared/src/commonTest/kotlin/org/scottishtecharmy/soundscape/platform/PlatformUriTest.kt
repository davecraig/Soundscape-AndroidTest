package org.scottishtecharmy.soundscape.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformUriTest {
    @Test
    fun encodesRouteSeparatorsAndKeepsUnreserved() {
        assertEquals("Why%3F%20a%3Ab%2Fc", urlEncode("Why? a:b/c"))
        assertEquals("help-about-soundscape.md", urlEncode("help-about-soundscape.md"))
        assertEquals("_-!.~'()*", urlEncode("_-!.~'()*"))
    }

    @Test
    fun encodesUtf8() {
        assertEquals("%C3%A9%E2%80%9C", urlEncode("é“"))
    }

    @Test
    fun roundTrips() {
        for (s in listOf("", "Plain", "Comment ça marche ?", "“Smart” quotes: 50%", "日本語", "👋 hi")) {
            assertEquals(s, urlDecode(urlEncode(s)))
        }
    }

    @Test
    fun leavesMalformedEscapesAlone() {
        assertEquals("50%", urlDecode("50%"))
        assertEquals("%zz 👋", urlDecode("%zz 👋"))
    }
}
