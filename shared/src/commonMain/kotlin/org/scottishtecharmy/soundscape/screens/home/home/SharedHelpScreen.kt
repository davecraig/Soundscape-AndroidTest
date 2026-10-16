package org.scottishtecharmy.soundscape.screens.home.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import org.scottishtecharmy.soundscape.geoengine.formatDistanceAndDirection
import org.scottishtecharmy.soundscape.i18n.ComposeLocalizedStrings
import org.scottishtecharmy.soundscape.navigation.SharedRoutes
import org.scottishtecharmy.soundscape.platform.currentLanguageTag
import org.scottishtecharmy.soundscape.platform.rememberResourceFileReader
import org.scottishtecharmy.soundscape.platform.runBlockingForResource
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.faq_title_abbreviated
import org.scottishtecharmy.soundscape.resources.help_gps_accuracy_good
import org.scottishtecharmy.soundscape.resources.help_gps_accuracy_ok
import org.scottishtecharmy.soundscape.resources.help_gps_accuracy_poor
import org.scottishtecharmy.soundscape.resources.help_gps_accuracy_waiting
import org.scottishtecharmy.soundscape.resources.menu_open_source_licenses
import org.scottishtecharmy.soundscape.resources.ui_back_button_title
import org.scottishtecharmy.soundscape.screens.markers_routes.components.CustomAppBar
import org.scottishtecharmy.soundscape.screens.markers_routes.components.CustomButton
import org.scottishtecharmy.soundscape.ui.theme.currentAppButtonColors
import org.scottishtecharmy.soundscape.ui.theme.mediumPadding
import org.scottishtecharmy.soundscape.ui.theme.smallPadding
import org.scottishtecharmy.soundscape.ui.theme.spacing

@Composable
fun SharedHelpScreen(
    topic: String,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenSourceLicenses: (() -> Unit)? = null,
    /** Horizontal accuracy of the current position in metres, for the GPS accuracy page. */
    locationAccuracy: Float? = null,
    modifier: Modifier = Modifier
) {
    val helpTopic = remember(topic) { HelpTopic.fromRouteParam(topic) }
    val languageTag = currentLanguageTag()

    val readBytes = rememberResourceFileReader()
    val content = remember(helpTopic, languageTag) {
        runBlockingForResource { helpTopic.load(languageTag, readBytes) }
    }
    val blocks = remember(content) {
        MarkdownProcessor(content).getBlocks(
            isFaqSubPage = helpTopic is HelpTopic.Faq,
            faqQuestionIndex = (helpTopic as? HelpTopic.Faq)?.questionIndex,
            fileName = helpTopic.fileName
        )
    }
    val displayTitle = if (helpTopic is HelpTopic.Faq) {
        stringResource(Res.string.faq_title_abbreviated)
    } else {
        HelpTopic.titleOf(content) ?: ""
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CustomAppBar(
                title = displayTitle,
                navigationButtonTitle = stringResource(Res.string.ui_back_button_title),
                onNavigateUp = onNavigateUp,
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier.padding(padding)
            ) {
                // Help topic page
                LazyColumn(
                    modifier = modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .mediumPadding(),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    items(blocks) { block ->
                        when (block) {
                            is MarkdownBlock.Heading -> {
                                Text(
                                    text = block.text,
                                    style = when (block.level) {
                                        2 -> MaterialTheme.typography.titleMedium
                                        else -> MaterialTheme.typography.titleSmall
                                    },
                                    modifier = Modifier
                                        .padding(top = if (block.level == 2) spacing.medium else spacing.small)
                                        .semantics {
                                            heading()
                                        },
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            is MarkdownBlock.Paragraphs -> {
                                val text = parseHtmlToAnnotatedString(block.html)
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            is MarkdownBlock.Link -> {
                                LinkButton(
                                    text = block.text,
                                    route = if (block.destination.endsWith(".md")) {
                                        "${SharedRoutes.HELP}/${HelpTopic.Page(block.destination).toRouteParam()}"
                                    } else {
                                        block.destination
                                    },
                                    onNavigate = onNavigate,
                                    modifier = Modifier.testTag("helpScreenTopic${block.destination.removeSuffix(".md")}"),
                                )
                            }
                            is MarkdownBlock.FaqQuestion -> {
                                val route = "${SharedRoutes.HELP}/${HelpTopic.Faq(block.fileName, block.index).toRouteParam()}"
                                LinkButton(
                                    text = block.text,
                                    route = route,
                                    onNavigate = onNavigate
                                )
                            }
                            is MarkdownBlock.GpsAccuracy -> {
                                GpsAccuracyText(
                                    accuracyMetres = locationAccuracy,
                                    template = block.template,
                                )
                            }
                        }
                    }
                    item {
                        if (helpTopic.fileName == HelpTopic.ABOUT_SOUNDSCAPE_FILENAME
                            && onOpenSourceLicenses != null
                        ) {
                            CustomButton(
                                onClick = onOpenSourceLicenses,
                                text = stringResource(Res.string.menu_open_source_licenses),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .smallPadding()
                                    .testTag("helpScreenOpenSourceLicenses"),
                                shape = RoundedCornerShape(spacing.extraSmall),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun LinkButton(
    text: String,
    route: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onNavigate(route) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.extraSmall),
        colors = if (!LocalInspectionMode.current) currentAppButtonColors else ButtonDefaults.buttonColors(),
    ) {
        Box(
            Modifier.weight(6f)
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Box(
            Modifier.weight(1f)
        ) {
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * The phone's current GPS accuracy, as a line of text which updates as the fix changes. [template]
 * is the line from the GPS accuracy page, with {{ accuracy }} and {{ quality }} tags.
 *
 * The thresholds are the ones the original Soundscape documentation quoted - 10 metres (30 feet)
 * or better is good, up to 20 metres (60 feet) is ok, worse than that is poor - and are applied to
 * the raw metres, while the figure itself is formatted in whatever units the user has chosen.
 *
 * A polite live region, so a screen reader reads new values while this page is open without
 * interrupting whatever it is saying.
 */
@Composable
private fun GpsAccuracyText(
    accuracyMetres: Float?,
    template: String,
) {
    val quality = when {
        accuracyMetres == null -> null
        accuracyMetres <= 10f -> stringResource(Res.string.help_gps_accuracy_good)
        accuracyMetres <= 20f -> stringResource(Res.string.help_gps_accuracy_ok)
        else -> stringResource(Res.string.help_gps_accuracy_poor)
    }
    val waiting = stringResource(Res.string.help_gps_accuracy_waiting)
    val formatted = remember(accuracyMetres) {
        accuracyMetres?.let {
            formatDistanceAndDirection(it.toDouble(), null, ComposeLocalizedStrings())
        }
    }
    val text = if ((formatted != null) && (quality != null)) {
        template.replace(GPS_ACCURACY_TAG, formatted).replace(GPS_QUALITY_TAG, quality)
    } else {
        waiting
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("helpScreenGpsAccuracy"),
    )
}
