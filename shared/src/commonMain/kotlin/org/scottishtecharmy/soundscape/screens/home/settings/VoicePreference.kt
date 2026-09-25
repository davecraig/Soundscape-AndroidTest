package org.scottishtecharmy.soundscape.screens.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import me.zhanghai.compose.preference.LocalPreferenceTheme
import me.zhanghai.compose.preference.rememberPreferenceState
import org.jetbrains.compose.resources.stringResource
import org.scottishtecharmy.soundscape.audio.VoiceDescriptor
import org.scottishtecharmy.soundscape.audio.VoiceLanguageSection
import org.scottishtecharmy.soundscape.audio.VoiceProvider
import org.scottishtecharmy.soundscape.audio.VoiceQuality
import org.scottishtecharmy.soundscape.audio.buildVoiceCatalogue
import org.scottishtecharmy.soundscape.platform.localizedLanguageName
import org.scottishtecharmy.soundscape.preferences.PreferencesProvider
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.general_alert_cancel
import org.scottishtecharmy.soundscape.resources.settings_collapse_section
import org.scottishtecharmy.soundscape.resources.settings_collapsed
import org.scottishtecharmy.soundscape.resources.settings_expand_section
import org.scottishtecharmy.soundscape.resources.settings_expanded
import org.scottishtecharmy.soundscape.resources.voice_group_novelty
import org.scottishtecharmy.soundscape.resources.voice_group_other
import org.scottishtecharmy.soundscape.resources.voice_quality_default
import org.scottishtecharmy.soundscape.resources.voice_quality_enhanced
import org.scottishtecharmy.soundscape.resources.voice_quality_premium
import org.scottishtecharmy.soundscape.resources.voice_voices
import org.scottishtecharmy.soundscape.ui.theme.extraSmallPadding
import org.scottishtecharmy.soundscape.ui.theme.spacing

/**
 * The Voices setting, on both platforms.
 *
 * Replaces the flat list the two apps used to show. A phone with a few
 * languages' voices installed runs to dozens of entries, and on iOS most of
 * them are the Eloquence set the system bundles - so the voices anyone actually
 * wants were buried. Voices are now grouped by language, with the language the
 * app is running in first, and the groups that are only occasionally wanted
 * (novelty voices, and whole synthesisers like Eloquence and eSpeak) collapsed
 * behind a row each.
 *
 * Only iOS fills in [VoiceDescriptor.provider]: Android picks a synthesiser
 * through its separate Text-to-Speech Engine preference, one level up from
 * here, so an Android voice list is single-provider already and collapses to
 * plain language sections.
 *
 * [systemDefaultValue] is the stored value meaning "whatever the phone's own
 * settings say" - "" on iOS, "Default" on Android - and is always offered as
 * the first row, so the choice is reversible. [systemDefaultLabel] is what that
 * row reads; iOS names the voice being followed.
 */
@Composable
fun VoicePreference(
    voices: List<VoiceDescriptor>,
    appLanguageTag: String,
    preferenceKey: String,
    systemDefaultValue: String,
    systemDefaultLabel: String,
    preferencesProvider: PreferencesProvider?,
    modifier: Modifier,
    textColor: Color,
) {
    val savedValue by rememberPreferenceState(preferenceKey, systemDefaultValue)

    var showDialog by rememberSaveable { mutableStateOf(false) }
    // At most one group is open at a time, so a long list doesn't turn into a
    // wall of voices the moment two groups are expanded.
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    val sections = remember(voices, appLanguageTag, savedValue) {
        buildVoiceCatalogue(
            voices = voices,
            appLanguageTag = appLanguageTag,
            selectedIdentifier = savedValue,
            titleFor = ::localizedLanguageName,
        )
    }
    val selected = remember(sections, savedValue) {
        sections.firstNotNullOfOrNull { section ->
            section.allVoices.firstOrNull { it.identifier == savedValue }
        }
    }

    val theme = LocalPreferenceTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { showDialog = true },
    ) {
        Column(modifier = Modifier.padding(theme.padding)) {
            SettingTitle(Res.string.voice_voices, textColor)
            ClickableOption(
                text = selected?.let { voiceLabel(it) } ?: systemDefaultLabel,
                textColor = textColor,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = stringResource(Res.string.voice_voices),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item(key = "system_default") {
                        VoiceRow(
                            label = systemDefaultLabel,
                            isSelected = savedValue == systemDefaultValue,
                            onClick = {
                                preferencesProvider?.putString(preferenceKey, systemDefaultValue)
                                showDialog = false
                            },
                        )
                    }

                    sections.forEach { section ->
                        voiceLanguageSection(
                            section = section,
                            selectedIdentifier = savedValue,
                            expandedGroup = expandedGroup,
                            onToggleGroup = { key ->
                                expandedGroup = if (expandedGroup == key) null else key
                            },
                            onSelect = { identifier ->
                                preferencesProvider?.putString(preferenceKey, identifier)
                                showDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.general_alert_cancel))
                }
            },
        )
    }
}

/**
 * One language's voices: the standard ones listed directly, then a row for each
 * collapsed group. Group keys are qualified by language so the same provider
 * expanded under English doesn't also open under French.
 */
private fun LazyListScope.voiceLanguageSection(
    section: VoiceLanguageSection,
    selectedIdentifier: String,
    expandedGroup: String?,
    onToggleGroup: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    item(key = "header_${section.languageTag}") {
        Text(
            text = localizedLanguageName(section.languageTag),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .semantics { heading() }
                .fillMaxWidth()
                .extraSmallPadding(),
        )
    }

    items(section.standardVoices) { voice ->
        VoiceRow(
            label = voiceLabel(voice),
            isSelected = voice.identifier == selectedIdentifier,
            onClick = { onSelect(voice.identifier) },
        )
    }

    if (section.noveltyVoices.isNotEmpty()) {
        voiceGroup(
            key = "${section.languageTag}/novelty",
            titleResource = Res.string.voice_group_novelty,
            voices = section.noveltyVoices,
            selectedIdentifier = selectedIdentifier,
            expandedGroup = expandedGroup,
            onToggleGroup = onToggleGroup,
            onSelect = onSelect,
        )
    }

    section.providerSections.forEach { providerSection ->
        voiceGroup(
            key = "${section.languageTag}/${providerSection.provider.name}",
            titleResource = when (providerSection.provider) {
                // Eloquence and eSpeak are the synthesisers' own names, so they
                // are not translated and have no string resource.
                VoiceProvider.Eloquence, VoiceProvider.ESpeak -> null
                VoiceProvider.Other -> Res.string.voice_group_other
            },
            literalTitle = when (providerSection.provider) {
                VoiceProvider.Eloquence -> "Eloquence"
                VoiceProvider.ESpeak -> "eSpeak"
                VoiceProvider.Other -> null
            },
            voices = providerSection.voices,
            selectedIdentifier = selectedIdentifier,
            expandedGroup = expandedGroup,
            onToggleGroup = onToggleGroup,
            onSelect = onSelect,
        )
    }
}

private fun LazyListScope.voiceGroup(
    key: String,
    titleResource: org.jetbrains.compose.resources.StringResource?,
    voices: List<VoiceDescriptor>,
    selectedIdentifier: String,
    expandedGroup: String?,
    onToggleGroup: (String) -> Unit,
    onSelect: (String) -> Unit,
    literalTitle: String? = null,
) {
    val expanded = expandedGroup == key

    item(key = "group_$key") {
        val title = literalTitle ?: titleResource?.let { stringResource(it) }.orEmpty()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .extraSmallPadding()
                .defaultMinSize(minHeight = spacing.targetSize)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) {
                        stringResource(Res.string.settings_collapse_section)
                    } else {
                        stringResource(Res.string.settings_expand_section)
                    },
                ) { onToggleGroup(key) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = MaterialTheme.colorScheme.onSurface)
                // Naming the chosen voice on the closed row means a user can see
                // which group their selection is in without opening each one.
                voices.firstOrNull { it.identifier == selectedIdentifier }?.let { selected ->
                    Text(
                        text = voiceLabel(selected),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = if (expanded) {
                    stringResource(Res.string.settings_expanded)
                } else {
                    stringResource(Res.string.settings_collapsed)
                },
            )
        }
    }

    if (expanded) {
        items(voices) { voice ->
            VoiceRow(
                label = voiceLabel(voice),
                isSelected = voice.identifier == selectedIdentifier,
                onClick = { onSelect(voice.identifier) },
            )
        }
    }
}

@Composable
private fun VoiceRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    ListPreferenceItem(
        description = label,
        value = if (isSelected) 1 else 0,
        currentValue = 1,
        onClick = onClick,
        index = 0,
        listSize = 1,
    )
}

/**
 * A voice's name, with its quality appended only where the catalogue found two
 * of the same speaker and the names would otherwise be identical.
 */
@Composable
private fun voiceLabel(voice: VoiceDescriptor): String {
    val quality = voice.disambiguatingQuality ?: return voice.displayName
    return "${voice.displayName} (${qualityLabel(quality)})"
}

@Composable
private fun qualityLabel(quality: VoiceQuality): String = stringResource(
    when (quality) {
        VoiceQuality.Default -> Res.string.voice_quality_default
        VoiceQuality.Enhanced -> Res.string.voice_quality_enhanced
        VoiceQuality.Premium -> Res.string.voice_quality_premium
    }
)
