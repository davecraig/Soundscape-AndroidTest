package org.scottishtecharmy.soundscape.screens.home.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownProcessorTest {

    @Test
    fun testHeadingExtraction() {
        val content = """
            # Title
            ## Subtitle
            Paragraph text.
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(false)

        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlock.Heading("Subtitle", 2), blocks[0])
        assertEquals(MarkdownBlock.Paragraphs("<p>Paragraph text.</p>"), blocks[1])
    }

    @Test
    fun testFaqFiltering() {
        val content = """
            # FAQ
            ## Section 1
            ### Question 1?
            Answer 1.
            ### Question 2
            Answer 2.
            ## Section 2
        """.trimIndent()
        val processor = MarkdownProcessor(content)

        val blocks1 = processor.getBlocks(true, 0)
        assertEquals(2, blocks1.size)
        assertEquals(MarkdownBlock.Heading("Question 1?", 3), blocks1[0])
        assertEquals(MarkdownBlock.Paragraphs("<p>Answer 1.</p>"), blocks1[1])

        val blocks2 = processor.getBlocks(true, 1)
        assertEquals(2, blocks2.size)
        assertEquals(MarkdownBlock.Heading("Question 2", 3), blocks2[0])
        assertEquals(MarkdownBlock.Paragraphs("<p>Answer 2.</p>"), blocks2[1])
    }

    @Test
    fun testLinkButtonExtraction() {
        val content = """
            # Title
            [Link Text](destination.md)
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(false)

        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Link("Link Text", "destination.md"), blocks[0])
    }

    @Test
    fun testGroupingColonEnding() {
        val content = """
            # Title
            Label:
            value text
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(false)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraphs)
        val html = (blocks[0] as MarkdownBlock.Paragraphs).html
        assertTrue(html.contains("Label"))
        assertTrue(html.contains("value text"))
    }

    @Test
    fun testGroupingMediaControls() {
        val content = """
            # Title
            Media controls:
            ⏯ Play/Pause
            ⏭ Next
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(false)

        // "Media controls:" ends with colon so groups with following paragraphs
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraphs)
    }

    @Test
    fun testGroupingStrongEmphasisStartsNewGroup() {
        val content = """
            # Title

            Some intro text.
            Some more intro.

            **Bold start**
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(false)

        // Two paragraphs in one group (no blank line between them);
        // STRONG paragraph starts a new group
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraphs)
        assertTrue(blocks[1] is MarkdownBlock.Paragraphs)
    }

    @Test
    fun testFaqListPageDetection() {
        val content = """
            # FAQ
            ### Question 1?
            Answer 1.
            ### Question 2?
            Answer 2.
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(false, null, "help-faq.md")

        // H1 stripped, two H3 headings become FaqQuestion blocks
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.FaqQuestion)
        assertEquals("Question 1?", (blocks[0] as MarkdownBlock.FaqQuestion).text)
        assertEquals("help-faq.md", (blocks[0] as MarkdownBlock.FaqQuestion).fileName)
        assertEquals(0, (blocks[0] as MarkdownBlock.FaqQuestion).index)
        assertTrue(blocks[1] is MarkdownBlock.FaqQuestion)
        assertEquals("Question 2?", (blocks[1] as MarkdownBlock.FaqQuestion).text)
        assertEquals(1, (blocks[1] as MarkdownBlock.FaqQuestion).index)
    }

    @Test
    fun testFaqSubPageGroupsAllContent() {
        val content = """
            # FAQ
            ### Question 1?
            Answer paragraph 1.
            Answer paragraph 2.
        """.trimIndent()
        val processor = MarkdownProcessor(content)
        val blocks = processor.getBlocks(true, 0)

        // H3 heading + all answer paragraphs grouped into one block
        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlock.Heading("Question 1?", 3), blocks[0])
        assertTrue(blocks[1] is MarkdownBlock.Paragraphs)
        val html = (blocks[1] as MarkdownBlock.Paragraphs).html
        assertTrue(html.contains("Answer paragraph 1"))
        assertTrue(html.contains("Answer paragraph 2"))
    }

    @Test
    fun testFrontMatterStripped() {
        val raw = """
            ---
            title: Test Page
            layout: page
            parent: "Using Soundscape"
            has_toc: false
            ---

            # Test Page

            Content here.
        """.trimIndent()
        val processed = processMarkdownContent(raw)
        assertFalse(processed.startsWith("---"))
        assertFalse(processed.contains("layout: page"))
        assertTrue(processed.startsWith("# Test Page"))
        assertTrue(processed.contains("Content here."))
    }

    @Test
    fun testLinkTagsResolved() {
        val raw = "See [Voices]({% link voices/help-voices.md %}) for details."
        val processed = processMarkdownContent(raw)
        assertEquals("See [Voices](help-voices.md) for details.", processed)
    }

    @Test
    fun testPlatformConditions() {
        val raw = """
            Before.
            {% if platform == "ios" %}
            iPhone text.
            {% else %}
            Android text.
            {% endif %}
            After.
        """.trimIndent()
        val ios = processMarkdownContent(raw, platform = "ios")
        val android = processMarkdownContent(raw, platform = "android")
        assertTrue(ios.contains("iPhone text.") && !ios.contains("Android text."))
        assertTrue(android.contains("Android text.") && !android.contains("iPhone text."))
        for (text in listOf(ios, android)) {
            assertTrue(text.contains("Before.") && text.contains("After."))
            assertFalse(text.contains("{%"))
        }
    }

    @Test
    fun testAppAndUnknownConditions() {
        // The app is "app"; anything else is undefined, so false, as it is in Jekyll
        val raw = "{% if app %}A{% endif %}{% if site.thing %}B{% elsif platform != \"ios\" %}C{% else %}D{% endif %}"
        assertEquals("AC", applyConditions(raw, "android"))
        assertEquals("AD", applyConditions(raw, "ios"))
    }

    @Test
    fun testNestedConditions() {
        val raw = "{% if platform == \"ios\" %}1{% if app %}2{% else %}3{% endif %}{% else %}4{% if app %}5{% endif %}{% endif %}"
        assertEquals("12", applyConditions(raw, "ios"))
        assertEquals("45", applyConditions(raw, "android"))
    }

    @Test
    fun testGpsAccuracyIsItsOwnBlock() {
        val content = """
            # GPS accuracy

            If the figure below stays poor:

            Current accuracy: {{ accuracy }} ({{ quality }})
        """.trimIndent()
        val blocks = MarkdownProcessor(content).getBlocks(false)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraphs)
        assertEquals(MarkdownBlock.GpsAccuracy("Current accuracy: {{ accuracy }} ({{ quality }})"), blocks[1])
    }

    @Test
    fun testCrlfLineEndings() {
        val processed = processMarkdownContent("---\r\ntitle: T\r\n---\r\n\r\n# T\r\n\r\nText.\r\n")
        assertEquals("# T\n\nText.\n", processed)
    }

    @Test
    fun testCandidatePaths() {
        fun paths(tag: String) = HelpTopic.candidatePaths("help-routes.md", tag)
        assertEquals(
            listOf("files/help/help-routes.pt-BR.md", "files/help/help-routes.pt.md", "files/help/help-routes.md"),
            paths("pt-BR")
        )
        assertEquals(
            listOf("files/help/help-routes.zh-CN.md", "files/help/help-routes.zh.md", "files/help/help-routes.md"),
            paths("zh-Hans-CN")
        )
        assertEquals(listOf("files/help/help-routes.id.md", "files/help/help-routes.md"), paths("in"))
        assertEquals(
            listOf("files/help/help-routes.zh-CN.md", "files/help/help-routes.zh.md", "files/help/help-routes.md"),
            paths("zh-Hans")
        )
        assertEquals(listOf("files/help/help-routes.zh.md", "files/help/help-routes.md"), paths("zh-Hant"))
        assertEquals(listOf("files/help/help-routes.en.md", "files/help/help-routes.md"), paths("en"))
    }

    @Test
    fun testRoutes() {
        val faq = HelpTopic.fromRouteParam(HelpTopic.Faq("help-faq.md", 3).toRouteParam())
        assertTrue(faq is HelpTopic.Faq && faq.fileName == "help-faq.md" && faq.questionIndex == 3)
        val page = HelpTopic.fromRouteParam(HelpTopic.Page("help-routes").toRouteParam())
        assertTrue(page is HelpTopic.Page && page.fileName == "help-routes.md")
        for (unknown in listOf("", "page:", "faq:x.md", "faq:x.md:y", "pagehelp_routes")) {
            assertEquals(HelpTopic.HELP_AND_TUTORIALS_FILENAME, HelpTopic.fromRouteParam(unknown).fileName)
        }
    }
}
