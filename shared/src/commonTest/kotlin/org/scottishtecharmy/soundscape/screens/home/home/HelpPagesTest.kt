package org.scottishtecharmy.soundscape.screens.home.home

import kotlinx.coroutines.test.runTest
import org.scottishtecharmy.soundscape.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks the real help pages in composeResources/files/help, in every language and for
 * both platforms, as the help screen will show them.
 */
class HelpPagesTest {

    // The web codes of the translated help pages, as in docs/_config.yml. "en" has no suffix.
    private val languages = listOf(
        "en", "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "en-GB", "es", "et", "fa", "fi",
        "fr", "fr-CA", "ha", "hi", "hr", "hu", "id", "is", "it", "ja", "ko", "mr", "nb", "nl",
        "pa", "pl", "pt", "pt-BR", "ro", "ru", "sk", "sl", "sr", "sv", "sw", "ta", "te", "th",
        "tr", "uk", "ur", "vi", "zh-CN",
    )

    private suspend fun read(fileName: String, language: String): String {
        val baseName = fileName.removeSuffix(".md")
        val path = if (language == "en") "files/help/$baseName.md" else "files/help/$baseName.$language.md"
        return try {
            Res.readBytes(path).decodeToString()
        } catch (e: Exception) {
            fail("Missing help page $path: $e")
        }
    }

    private suspend fun blocks(fileName: String, language: String, platform: String, faqQuestion: Int? = null) =
        MarkdownProcessor(processMarkdownContent(read(fileName, language), platform))
            .getBlocks(isFaqSubPage = faqQuestion != null, faqQuestionIndex = faqQuestion, fileName = fileName)

    private suspend fun pagesLinkedFromIndex(language: String, platform: String) =
        blocks(HelpTopic.HELP_AND_TUTORIALS_FILENAME, language, platform)
            .filterIsInstance<MarkdownBlock.Link>()
            .map { it.destination }

    @Test
    fun indexLinksTheSamePagesInEveryLanguage() = runTest {
        val android = pagesLinkedFromIndex("en", "android")
        val ios = pagesLinkedFromIndex("en", "ios")
        // The Siri page is only linked on iOS
        assertEquals(ios - "help-siri-voice-commands.md", android)
        assertTrue("help-siri-voice-commands.md" in ios)

        for (language in languages) {
            assertEquals(android, pagesLinkedFromIndex(language, "android"), language)
            assertEquals(ios, pagesLinkedFromIndex(language, "ios"), language)
        }
    }

    @Test
    fun everyPageRendersCleanly() = runTest {
        val pages = pagesLinkedFromIndex("en", "ios") +
            HelpTopic.HELP_AND_TUTORIALS_FILENAME + HelpTopic.ABOUT_SOUNDSCAPE_FILENAME
        for (language in languages) {
            for (platform in listOf("android", "ios")) {
                for (page in pages) {
                    val where = "$page ($language, $platform)"
                    val text = processMarkdownContent(read(page, language), platform)
                    assertFalse(text.contains("{%"), "Unprocessed Liquid tag in $where")
                    assertFalse(text.contains("%1\$s"), "String placeholder in $where")

                    val pageBlocks = blocks(page, language, platform)
                    assertTrue(pageBlocks.isNotEmpty(), "Nothing to show in $where")
                    for (link in pageBlocks.filterIsInstance<MarkdownBlock.Link>()) {
                        // A link button navigates within Help, so must be to another page
                        assertTrue(link.destination.endsWith(".md"), "Link to ${link.destination} in $where")
                        read(link.destination, "en")
                    }
                    for (question in pageBlocks.filterIsInstance<MarkdownBlock.FaqQuestion>()) {
                        val answer = blocks(page, language, platform, question.index)
                        assertEquals(MarkdownBlock.Heading(question.text, 3), answer.firstOrNull(), where)
                        assertTrue(answer.size > 1, "No answer to \"${question.text}\" in $where")
                    }
                }
            }
        }
    }

    @Test
    fun gpsAccuracyPageHasTheLiveValue() = runTest {
        for (language in languages) {
            val live = blocks("help-gps-accuracy.md", language, "android")
                .filterIsInstance<MarkdownBlock.GpsAccuracy>()
            assertEquals(1, live.size, language)
            assertTrue(live[0].template.contains(GPS_ACCURACY_TAG), language)
            assertTrue(live[0].template.contains(GPS_QUALITY_TAG), language)
        }
    }

    @Test
    fun platformPagesDiffer() = runTest {
        for (page in listOf("help-voices.md", "help-using-media-controls.md")) {
            for (language in languages) {
                val android = processMarkdownContent(read(page, language), "android")
                val ios = processMarkdownContent(read(page, language), "ios")
                assertTrue(android != ios, "$page ($language) is the same on both platforms")
            }
        }
    }
}
