package org.scottishtecharmy.soundscape.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceCatalogueTest {

    private fun voice(
        identifier: String,
        name: String,
        tag: String,
        quality: VoiceQuality = VoiceQuality.Default,
        provider: VoiceProvider? = null,
        novelty: Boolean = false,
    ) = VoiceDescriptor(identifier, name, tag, quality, provider, novelty)

    @Test
    fun `normalizes the spellings of a language tag onto one`() {
        assertEquals("en-GB", normalizeLanguageTag("en_gb"))
        assertEquals("en-GB", normalizeLanguageTag("en-GB"))
        assertEquals("zh-Hans-CN", normalizeLanguageTag("zh_hans_cn"))
        assertEquals("en", normalizeLanguageTag("EN"))
        assertEquals("en", languageCodeOf("en_GB"))
    }

    @Test
    fun `classifies apple voice identifiers into provider groups`() {
        assertEquals(
            VoiceProvider.Eloquence,
            appleVoiceProvider("com.apple.eloquence.en-US.Reed"),
        )
        assertEquals(
            VoiceProvider.ESpeak,
            appleVoiceProvider("com.apple.speech.synthesis.espeak.en"),
        )
        assertEquals(VoiceProvider.Other, appleVoiceProvider("org.piper.tts.en-GB.Alba"))
        // Apple's own standard voices are shown ungrouped.
        assertNull(appleVoiceProvider("com.apple.voice.enhanced.en-GB.Stephanie"))
    }

    @Test
    fun `keeps only the best quality tier of a speaker`() {
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice("com.apple.voice.compact.en-GB.Daniel", "Daniel", "en-GB"),
                voice(
                    "com.apple.voice.enhanced.en-GB.Daniel", "Daniel", "en-GB",
                    quality = VoiceQuality.Enhanced,
                ),
            ),
            appLanguageTag = "en-GB",
        )

        val voices = sections.single().standardVoices
        assertEquals(1, voices.size)
        assertEquals("com.apple.voice.enhanced.en-GB.Daniel", voices.single().identifier)
        assertNull(voices.single().disambiguatingQuality)
    }

    @Test
    fun `never drops the voice the user is currently listening to`() {
        val compact = "com.apple.voice.compact.en-GB.Daniel"
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice(compact, "Daniel", "en-GB"),
                voice(
                    "com.apple.voice.premium.en-GB.Daniel", "Daniel", "en-GB",
                    quality = VoiceQuality.Premium,
                ),
            ),
            appLanguageTag = "en-GB",
            selectedIdentifier = compact,
        )

        val voices = sections.single().standardVoices
        assertEquals(2, voices.size)
        // Two rows would otherwise both read "Daniel", so both are tagged.
        assertEquals(
            listOf(VoiceQuality.Default, VoiceQuality.Premium),
            voices.map { it.disambiguatingQuality },
        )
    }

    @Test
    fun `same name in a different language is a different speaker`() {
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice("id.en.Alice", "Alice", "en-GB", quality = VoiceQuality.Enhanced),
                voice("id.fr.Alice", "Alice", "fr-FR"),
            ),
            appLanguageTag = "en-GB",
        )

        assertEquals(listOf("en-GB", "fr-FR"), sections.map { it.languageTag })
        assertEquals("id.fr.Alice", sections[1].standardVoices.single().identifier)
    }

    @Test
    fun `app language sorts first, then its other regions, then the rest by title`() {
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice("a", "A", "de-DE"),
                voice("b", "B", "en-US"),
                voice("c", "C", "en-GB"),
                voice("d", "D", "ar-SA"),
            ),
            appLanguageTag = "en-GB",
            titleFor = { tag ->
                when (languageCodeOf(tag)) {
                    "de" -> "German"
                    "ar" -> "Arabic"
                    else -> tag
                }
            },
        )

        assertEquals(listOf("en-GB", "en-US", "ar-SA", "de-DE"), sections.map { it.languageTag })
    }

    @Test
    fun `files novelty and provider voices out of the standard list`() {
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice("com.apple.voice.compact.en-US.Samantha", "Samantha", "en-US"),
                voice(
                    "com.apple.eloquence.en-US.Reed", "Reed", "en-US",
                    provider = VoiceProvider.Eloquence,
                ),
                voice(
                    "com.apple.speech.synthesis.voice.Bubbles", "Bubbles", "en-US",
                    novelty = true,
                ),
                voice("org.piper.en-US.Kai", "Kai", "en-US", provider = VoiceProvider.Other),
            ),
            appLanguageTag = "en-US",
        )

        val section = sections.single()
        assertEquals(listOf("Samantha"), section.standardVoices.map { it.displayName })
        assertEquals(listOf("Bubbles"), section.noveltyVoices.map { it.displayName })
        assertEquals(
            listOf(VoiceProvider.Eloquence, VoiceProvider.Other),
            section.providerSections.map { it.provider },
        )
        // Everything is reachable from the section, nothing is listed twice.
        assertEquals(4, section.allVoices.size)
        assertEquals(4, section.allVoices.distinctBy { it.identifier }.size)
    }

    @Test
    fun `a novelty voice is not also listed under its provider`() {
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice(
                    "com.apple.eloquence.en-US.Grandma", "Grandma", "en-US",
                    provider = VoiceProvider.Eloquence, novelty = true,
                ),
            ),
            appLanguageTag = "en-US",
        )

        val section = sections.single()
        assertTrue(section.providerSections.isEmpty())
        assertEquals(listOf("Grandma"), section.noveltyVoices.map { it.displayName })
    }

    @Test
    fun `android style input with no providers yields plain language sections`() {
        val sections = buildVoiceCatalogue(
            voices = listOf(
                voice("en-gb-x-gba-local", "en-gb-x-gba-local", "en-GB"),
                voice("en-gb-x-gbd-local", "en-gb-x-gbd-local", "en-GB"),
                voice("fr-fr-x-frd-local", "fr-fr-x-frd-local", "fr-FR"),
            ),
            appLanguageTag = "en-GB",
        )

        assertEquals(listOf("en-GB", "fr-FR"), sections.map { it.languageTag })
        assertEquals(2, sections[0].standardVoices.size)
        assertTrue(sections.all { it.providerSections.isEmpty() && it.noveltyVoices.isEmpty() })
    }
}
