package org.scottishtecharmy.soundscape.screens.home.home

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.scottishtecharmy.soundscape.platform.isIos

sealed class MarkdownBlock {
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Paragraphs(val html: String) : MarkdownBlock()
    data class Link(val text: String, val destination: String) : MarkdownBlock()
    /** The [index]th question on the FAQ page [fileName], counting from 0 */
    data class FaqQuestion(val text: String, val fileName: String, val index: Int) : MarkdownBlock()
    /** The live GPS accuracy line. [template] contains the {{ accuracy }} and {{ quality }} tags. */
    data class GpsAccuracy(val template: String) : MarkdownBlock()
}

private val LINK_TAG = Regex("""\{% link (?:[^/]+/)*([^ ]+\.md) %\}""")
private val CONDITION_TAG = Regex("""\{%-?\s*(if|elsif|else|endif)\b\s*(.*?)\s*-?%\}""")
private val PLATFORM_CONDITION = Regex("""platform\s*(==|!=)\s*"(\w+)"""")

const val GPS_ACCURACY_TAG = "{{ accuracy }}"
const val GPS_QUALITY_TAG = "{{ quality }}"

/**
 * The help pages are also the docs site's Jekyll pages, so platform and app-only text is written
 * with Liquid conditions, which Jekyll evaluates with `platform` and `app` undefined (so the site
 * gets the Android text and none of the app-only text):
 *
 *     {% if platform == "ios" %} ... {% else %} ... {% endif %}
 *     {% if app %} ... {% endif %}
 *
 * This evaluates the same conditions for the app. Any other condition is false, as an undefined
 * variable is in Jekyll.
 */
internal fun applyConditions(text: String, platform: String): String {
    fun isTrue(condition: String): Boolean {
        if (condition == "app") return true
        val match = PLATFORM_CONDITION.matchEntire(condition) ?: return false
        val (op, value) = match.destructured
        return (platform == value) == (op == "==")
    }

    // One entry per open {% if %}: whether its enclosing block is shown, and whether a branch of
    // it has already been taken.
    data class Level(val parentShown: Boolean, var taken: Boolean, var shown: Boolean)
    val levels = ArrayDeque<Level>()
    fun shown() = levels.lastOrNull()?.shown ?: true

    val out = StringBuilder()
    var position = 0
    for (tag in CONDITION_TAG.findAll(text)) {
        if (shown()) out.append(text, position, tag.range.first)
        position = tag.range.last + 1
        val (keyword, condition) = tag.destructured
        when (keyword) {
            "if" -> {
                val parentShown = shown()
                val taken = parentShown && isTrue(condition)
                levels.addLast(Level(parentShown, taken, taken))
            }
            "elsif", "else" -> levels.lastOrNull()?.let { level ->
                val now = level.parentShown && !level.taken &&
                    (keyword == "else" || isTrue(condition))
                level.shown = now
                if (now) level.taken = true
            }
            "endif" -> levels.removeLastOrNull()
        }
    }
    if (shown()) out.append(text, position, text.length)
    return out.toString()
}

fun processMarkdownContent(text: String, platform: String = if (isIos) "ios" else "android"): String {
    // A Windows checkout may have CRLF line endings
    val raw = text.replace("\r\n", "\n")
    val withoutFrontMatter = if (raw.startsWith("---")) {
        val endOfFrontMatter = raw.indexOf("---", 3)
        if (endOfFrontMatter != -1) {
            raw.substring(endOfFrontMatter + 3).trimStart()
        } else {
            raw
        }
    } else {
        raw
    }
    return applyConditions(withoutFrontMatter, platform).replace(LINK_TAG, "$1")
}

internal fun markdownToHtml(markdown: String): String {
    val flavour = CommonMarkFlavourDescriptor()
    val tree = MarkdownParser(flavour, true, CancellationToken.NonCancellable)
        .buildMarkdownTreeFromString(markdown as CharSequence)
    return HtmlGenerator(markdown, tree, flavour).generateHtml()
}

class MarkdownProcessor(content: String) {

    private val content = content
    private val mediaControlCharacters = listOf('⏯', '⏭', '⏮', '⏩', '⏪', '⏺')

    fun getBlocks(
        isFaqSubPage: Boolean,
        faqQuestionIndex: Int? = null,
        fileName: String? = null
    ): List<MarkdownBlock> {
        val flavour = CommonMarkFlavourDescriptor()
        val rootNode = MarkdownParser(flavour, true, CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(content as CharSequence)

        val nodes = rootNode.children

        val processedNodes = if (isFaqSubPage && faqQuestionIndex != null) {
            filterNodesForFaq(nodes, faqQuestionIndex)
        } else {
            withoutAnyRootHeading(nodes)
        }

        val isFaqListPage = !isFaqSubPage && processedNodes.any { isHeading(it, 3) }

        val grouped = groupNodesByHeadings(processedNodes, isFaqListPage, isFaqSubPage)

        return groupsToBlocks(grouped, isFaqListPage, fileName, flavour)
    }

    // The nodes of the [questionIndex]th question (counting from 0) and its answer
    private fun filterNodesForFaq(nodes: List<ASTNode>, questionIndex: Int): List<ASTNode> {
        val result = mutableListOf<ASTNode>()
        var questions = 0
        var found = false

        for (node in nodes) {
            if (node.type == MarkdownElementTypes.ATX_3) {
                if (found) break
                found = (questions == questionIndex)
                questions++
            } else if ((node.type == MarkdownElementTypes.ATX_1 || node.type == MarkdownElementTypes.ATX_2) && found) {
                break
            }
            if (found) {
                result.add(node)
            }
        }
        return result
    }

    private fun withoutAnyRootHeading(nodes: List<ASTNode>): List<ASTNode> {
        val firstNode = nodes.getOrNull(0) ?: return nodes
        if (firstNode.type == MarkdownElementTypes.ATX_1) {
            return nodes.drop(1)
        }
        return nodes
    }

    private fun groupNodesByHeadings(
        nodes: List<ASTNode>,
        isFaqListPage: Boolean,
        isFaqSubPage: Boolean
    ): MutableList<MutableList<ASTNode>> {
        val groupedNodes = mutableListOf<MutableList<ASTNode>>()

        for (node in nodes) {
            if (nodeStartsNewItem(node, isFaqListPage, isFaqSubPage, groupedNodes)) {
                groupedNodes.add(mutableListOf(node))
            } else {
                groupedNodes.last().add(node)
            }
        }
        return groupedNodes
    }

    private fun nodeStartsNewItem(
        node: ASTNode,
        isFaqListPage: Boolean,
        isFaqSubPage: Boolean,
        groupedNodes: MutableList<MutableList<ASTNode>>
    ): Boolean {
        return when {
            isHeading(node) -> !isFaqListPage || headingLevel(node) <= 3
            isStandaloneParagraph(node) -> true
            isParagraphStartingWithStrong(node) -> true
            else -> {
                if (groupedNodes.isEmpty()) {
                    true
                } else {
                    val lastGroup = groupedNodes.last()
                    val firstInLastGroup = lastGroup.first()
                    val lastNode = lastGroup.last()

                    if (isFaqListPage && isHeading(firstInLastGroup, 3)) {
                        false
                    } else if (isHeading(lastNode)) {
                        true
                    } else if (isStandaloneParagraph(lastNode)) {
                        true
                    } else if (isFaqSubPage) {
                        false
                    } else {
                        val lastText = nodeToText(lastNode).trim()
                        val currentText = nodeToText(node).trim()
                        val lastEndsInColon = lastText.endsWith(":")
                        val currentStartsInMediaControls =
                            currentText.isNotEmpty() && mediaControlCharacters.contains(currentText[0])

                        !lastEndsInColon && !currentStartsInMediaControls
                    }
                }
            }
        }
    }

    private fun groupsToBlocks(
        groups: MutableList<MutableList<ASTNode>>,
        isFaqListPage: Boolean,
        fileName: String?,
        flavour: CommonMarkFlavourDescriptor
    ): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var questionCount = 0

        for (group in groups) {
            val firstNode = group.first()

            if (isHeading(firstNode)) {
                val level = headingLevel(firstNode)
                if (level < 2) continue

                val text = headingText(firstNode)

                if (isFaqListPage && level == 3) {
                    val mdFileName = fileName ?: ""
                    blocks.add(MarkdownBlock.FaqQuestion(text, mdFileName, questionCount++))
                } else {
                    blocks.add(MarkdownBlock.Heading(text, level))
                }
            } else if (group.size == 1 && isGpsAccuracy(firstNode)) {
                blocks.add(MarkdownBlock.GpsAccuracy(firstNode.getTextInNode(content).toString().trim()))
            } else if (group.size == 1 && isParagraphWithOnlyLink(firstNode)) {
                val linkNode = findLinkNode(firstNode)!!
                val text = linkNode.children.find { it.type == MarkdownElementTypes.LINK_TEXT }
                    ?.getTextInNode(content)?.toString()
                    ?.removePrefix("[")?.removeSuffix("]")?.trim() ?: ""
                val destination = linkNode.children.find { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.getTextInNode(content)?.toString() ?: ""
                blocks.add(MarkdownBlock.Link(text, destination))
            } else {
                val html = group.joinToString("") { node ->
                    HtmlGenerator(content, node, flavour).generateHtml()
                }.trim()
                if (html.isNotEmpty() && html != "<p></p>") {
                    blocks.add(MarkdownBlock.Paragraphs(html))
                }
            }
        }
        return blocks
    }

    // --- AST inspection helpers ---

    private fun isHeading(node: ASTNode): Boolean =
        node.type == MarkdownElementTypes.ATX_1 ||
        node.type == MarkdownElementTypes.ATX_2 ||
        node.type == MarkdownElementTypes.ATX_3

    private fun isHeading(node: ASTNode, level: Int): Boolean =
        when (level) {
            1 -> node.type == MarkdownElementTypes.ATX_1
            2 -> node.type == MarkdownElementTypes.ATX_2
            3 -> node.type == MarkdownElementTypes.ATX_3
            else -> false
        }

    private fun headingLevel(node: ASTNode): Int =
        when (node.type) {
            MarkdownElementTypes.ATX_1 -> 1
            MarkdownElementTypes.ATX_2 -> 2
            MarkdownElementTypes.ATX_3 -> 3
            else -> 0
        }

    private fun headingText(node: ASTNode): String =
        node.children.find { it.type == MarkdownTokenTypes.ATX_CONTENT }
            ?.getTextInNode(content)?.toString()?.trim() ?: ""

    private fun isParagraphWithOnlyLink(node: ASTNode): Boolean =
        node.type == MarkdownElementTypes.PARAGRAPH && isOnlyLink(node)

    private fun isGpsAccuracy(node: ASTNode): Boolean =
        node.type == MarkdownElementTypes.PARAGRAPH &&
            node.getTextInNode(content).contains(GPS_ACCURACY_TAG)

    // A paragraph which is a block of its own, never grouped with its neighbours
    private fun isStandaloneParagraph(node: ASTNode): Boolean =
        isParagraphWithOnlyLink(node) || isGpsAccuracy(node)

    private fun isParagraphStartingWithStrong(node: ASTNode): Boolean {
        if (node.type != MarkdownElementTypes.PARAGRAPH) return false
        val children = node.children.filter { !isWhitespaceOrEol(it) }
        return children.isNotEmpty() && children[0].type == MarkdownElementTypes.STRONG
    }

    private fun isOnlyLink(node: ASTNode): Boolean {
        val children = node.children.filter { !isWhitespaceOrEol(it) }
        return children.size == 1 && children[0].type == MarkdownElementTypes.INLINE_LINK
    }

    private fun findLinkNode(node: ASTNode): ASTNode? =
        node.children.find { it.type == MarkdownElementTypes.INLINE_LINK }

    private fun isWhitespaceOrEol(node: ASTNode): Boolean {
        val type = node.type.toString()
        return type == "WHITE_SPACE" || type == "EOL"
    }

    private fun nodeToText(node: ASTNode): String {
        val flavour = CommonMarkFlavourDescriptor()
        return HtmlGenerator(content, node, flavour).generateHtml()
            .replace(Regex("<[^>]+>"), "")
    }
}
