package org.scottishtecharmy.soundscape.screens.home.home

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.scottishtecharmy.soundscape.platform.urlDecode
import org.scottishtecharmy.soundscape.platform.urlEncode
import org.scottishtecharmy.soundscape.resources.Res

/**
 * A help page, read from the Markdown in composeResources/files/help, or one question and answer
 * on an FAQ page.
 *
 * Routes are "page:<file>" and "faq:<file>:<n>", where n counts the page's questions from 0.
 * Questions go by number rather than by text so that a route means the same in every language.
 */
sealed class HelpTopic(fileName: String) {

    /** The file name of the English page, e.g. "help-routes.md" */
    val fileName: String = if (fileName.endsWith(".md")) fileName else "$fileName.md"

    abstract fun toRouteParam(): String

    class Page(fileName: String) : HelpTopic(fileName) {
        override fun toRouteParam(): String = "page:${urlEncode(fileName)}"
    }

    class Faq(fileName: String, val questionIndex: Int) : HelpTopic(fileName) {
        override fun toRouteParam(): String = "faq:${urlEncode(fileName)}:$questionIndex"
    }

    /**
     * The page in the language [languageTag] (e.g. "pt-BR", "zh-Hans-CN"), or in English if it
     * isn't translated, with its front matter removed and its conditions applied for this
     * platform. [readBytes] reads a Compose Resources file.
     */
    suspend fun load(
        languageTag: String,
        readBytes: suspend (String) -> ByteArray = { Res.readBytes(it) },
    ): String =
        processMarkdownContent(
            loadMarkdown(languageTag, readBytes) ?: "# Error\n\nFailed to load help content"
        )

    private suspend fun loadMarkdown(languageTag: String, readBytes: suspend (String) -> ByteArray): String? {
        for (path in candidatePaths(fileName, languageTag)) {
            try {
                return readBytes(path).decodeToString()
            } catch (_: Exception) {
                // Try next path
            }
        }
        return null
    }

    companion object {
        const val HELP_AND_TUTORIALS_FILENAME = "help-and-tutorials.md"
        const val ABOUT_SOUNDSCAPE_FILENAME = "help-about-soundscape.md"

        /**
         * Translations sit next to the English page as help-<slug>.<lang>.md, where <lang> is
         * language-REGION for regional variants (pt-BR, fr-CA, zh-CN) and the bare language
         * otherwise.
         */
        internal fun candidatePaths(fileName: String, languageTag: String): List<String> {
            val baseName = fileName.removeSuffix(".md")
            val parts = languageTag.replace('_', '-').split('-')
            // Indonesian still reports itself with the legacy code "in" in places
            val language = parts[0].lowercase().let { if (it == "in") "id" else it }
            // Skip any script, as in zh-Hans-CN. Chinese is only translated as zh-CN, so that is
            // where Simplified Chinese with no region goes.
            val region = parts.drop(1).lastOrNull { it.length == 2 && it.all(Char::isLetter) }
                ?.uppercase()
                ?: if (language == "zh" && parts.getOrNull(1) != "Hant") "CN" else null
            return listOfNotNull(
                region?.let { "files/help/$baseName.$language-$it.md" },
                "files/help/$baseName.$language.md",
                "files/help/$baseName.md",
            ).distinct()
        }

        /** The page title is its first level 1 heading */
        fun titleOf(content: String): String? {
            val tree = MarkdownParser(CommonMarkFlavourDescriptor(), true, CancellationToken.NonCancellable)
                .buildMarkdownTreeFromString(content as CharSequence)
            return tree.children.find { it.type == MarkdownElementTypes.ATX_1 }
                ?.children?.find { it.type == MarkdownTokenTypes.ATX_CONTENT }
                ?.getTextInNode(content)?.toString()?.trim()
        }

        /** Anything unrecognised, including an empty route, is the Help index */
        fun fromRouteParam(param: String): HelpTopic {
            val home = Page(HELP_AND_TUTORIALS_FILENAME)
            return when {
                param.startsWith("page:") ->
                    param.substring(5).takeIf { it.isNotEmpty() }?.let { Page(urlDecode(it)) }
                        ?: home

                param.startsWith("faq:") -> {
                    val parts = param.substring(4).split(":")
                    val index = parts.getOrNull(1)?.toIntOrNull()
                    if (parts.size == 2 && index != null) Faq(urlDecode(parts[0]), index) else home
                }

                else -> home
            }
        }
    }
}
