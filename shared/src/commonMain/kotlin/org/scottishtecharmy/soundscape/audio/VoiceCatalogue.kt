package org.scottishtecharmy.soundscape.audio

/**
 * How good a voice sounds, where the platform tells us. iOS reports this per
 * voice; Android's `Voice.getQuality()` is a coarse int that doesn't
 * distinguish downloaded tiers of the same speaker, so Android leaves every
 * voice on [Default] and the de-duplication below is a no-op there.
 */
enum class VoiceQuality { Default, Enhanced, Premium }

/**
 * The synthesiser a voice comes from, where that is worth showing as a group of
 * its own. Only iOS populates this: Android reaches providers through its
 * separate Text-to-Speech Engine preference, and a voice list there is already
 * single-provider by construction.
 */
enum class VoiceProvider { Eloquence, ESpeak, Other }

/**
 * Classifies an Apple voice identifier into the group it should be filed under,
 * or null for Apple's own standard voices, which are shown ungrouped.
 *
 * Lives here rather than in iosMain so it can be tested on the JVM; nothing on
 * Android calls it.
 */
fun appleVoiceProvider(identifier: String): VoiceProvider? {
    val normalized = identifier.lowercase()
    return when {
        normalized.startsWith("com.apple.eloquence.") -> VoiceProvider.Eloquence
        normalized.contains("espeak") -> VoiceProvider.ESpeak
        !normalized.startsWith("com.apple.") -> VoiceProvider.Other
        else -> null
    }
}

/**
 * One voice as the picker needs to show it.
 *
 * [disambiguatingQuality] is not supplied by callers - [buildVoiceCatalogue]
 * sets it on the voices it could not tell apart by name alone, and the picker
 * renders it alongside the name.
 */
data class VoiceDescriptor(
    val identifier: String,
    val displayName: String,
    val languageTag: String,
    val quality: VoiceQuality = VoiceQuality.Default,
    val provider: VoiceProvider? = null,
    val isNovelty: Boolean = false,
    val disambiguatingQuality: VoiceQuality? = null,
)

data class VoiceProviderSection(
    val provider: VoiceProvider,
    val voices: List<VoiceDescriptor>,
)

/**
 * The voices for one language, split the way the picker draws them: standard
 * voices listed directly, everything else behind a collapsed group.
 */
data class VoiceLanguageSection(
    val languageTag: String,
    val standardVoices: List<VoiceDescriptor>,
    val noveltyVoices: List<VoiceDescriptor>,
    val providerSections: List<VoiceProviderSection>,
) {
    val allVoices: List<VoiceDescriptor>
        get() = standardVoices + noveltyVoices + providerSections.flatMap { it.voices }
}

/**
 * Normalises the many spellings of a language tag onto one: "en_gb", "en-gb"
 * and "en-GB" are the same section.
 */
fun normalizeLanguageTag(tag: String): String {
    val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    val language = parts[0].lowercase()
    val rest = parts.drop(1).map { part ->
        when {
            part.length == 2 && part.all { it.isLetter() } -> part.uppercase()
            part.length == 4 && part.all { it.isLetter() } ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            else -> part
        }
    }
    return (listOf(language) + rest).joinToString("-")
}

/** The language subtag on its own: "en-GB" -> "en". */
fun languageCodeOf(tag: String): String = normalizeLanguageTag(tag).substringBefore('-')

/**
 * Groups [voices] into the sections the voice picker draws, with the language
 * the app is running in first.
 *
 * Both platforms share this so the two pickers agree on ordering, grouping and
 * de-duplication. It is deliberately free of platform types: [titleFor] supplies
 * the localized language name used for sorting, so tests can pass a stub and
 * production passes the platform's locale lookup.
 *
 * De-duplication replaces the legacy iOS app's identifier string surgery, which
 * stripped "premium"/"compact" tokens from identifiers and compared the stubs.
 * That matched the old `com.apple.ttsbundle.Daniel-compact` naming and silently
 * stopped working when Apple moved to `com.apple.voice.compact.en-GB.Daniel`,
 * leaving the picker showing every quality tier of a speaker as separate rows
 * with identical names. Grouping on (name, language) and keeping the best tier
 * is what that code was reaching for. [selectedIdentifier] always survives, so
 * the voice a user is currently listening to can never vanish from its own list;
 * when that leaves two rows sharing a name, both are tagged with their quality.
 */
fun buildVoiceCatalogue(
    voices: List<VoiceDescriptor>,
    appLanguageTag: String,
    selectedIdentifier: String? = null,
    titleFor: (String) -> String = { it },
): List<VoiceLanguageSection> {
    val condensed = voices
        .groupBy { it.displayName to normalizeLanguageTag(it.languageTag) }
        .flatMap { (_, group) -> condenseQualityTiers(group, selectedIdentifier) }

    val appTag = normalizeLanguageTag(appLanguageTag)
    val appLanguage = languageCodeOf(appTag)

    return condensed
        .groupBy { normalizeLanguageTag(it.languageTag) }
        .map { (languageTag, sectionVoices) -> languageSection(languageTag, sectionVoices) }
        .sortedWith(
            compareBy(
                { if (it.languageTag == appTag) 0 else 1 },
                { if (languageCodeOf(it.languageTag) == appLanguage) 0 else 1 },
                { titleFor(it.languageTag) },
                { it.languageTag },
            )
        )
}

/**
 * Reduces one speaker's quality tiers to the best one available, keeping the
 * selected voice as well if it lost, and naming the survivors apart when more
 * than one is left.
 */
private fun condenseQualityTiers(
    group: List<VoiceDescriptor>,
    selectedIdentifier: String?,
): List<VoiceDescriptor> {
    if (group.size == 1) return group

    val best = group.maxBy { it.quality.ordinal }
    val kept = group.filter {
        it.identifier == best.identifier || it.identifier == selectedIdentifier
    }
    return if (kept.size == 1) {
        listOf(kept.single().copy(disambiguatingQuality = null))
    } else {
        kept.map { it.copy(disambiguatingQuality = it.quality) }
    }
}

private fun languageSection(
    languageTag: String,
    voices: List<VoiceDescriptor>,
): VoiceLanguageSection {
    val sorted = voices.sortedWith(compareBy({ it.displayName }, { it.identifier }))

    return VoiceLanguageSection(
        languageTag = languageTag,
        standardVoices = sorted.filter { it.provider == null && !it.isNovelty },
        noveltyVoices = sorted.filter { it.isNovelty },
        providerSections = VoiceProvider.entries.mapNotNull { provider ->
            val providerVoices = sorted.filter { it.provider == provider && !it.isNovelty }
            if (providerVoices.isEmpty()) null
            else VoiceProviderSection(provider, providerVoices)
        },
    )
}
