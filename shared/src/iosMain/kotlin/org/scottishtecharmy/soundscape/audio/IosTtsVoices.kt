package org.scottishtecharmy.soundscape.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityEnhanced
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityPremium
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import org.scottishtecharmy.soundscape.screens.onboarding.language.getAppLocale
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Voices the legacy Soundscape iOS app suppressed because they sound robotic
 * compared to the modern voices and can't be removed by the user.
 */
internal val DISALLOWED_VOICES = setOf(
    "com.apple.speech.synthesis.voice.Fred",
    "com.apple.speech.synthesis.voice.Victoria",
    "com.apple.speech.voice.Alex",
)

/**
 * The joke and sound-effect voices, which the picker files under Novelty.
 *
 * iOS 17 can be asked directly, via `AVSpeechSynthesisVoice.voiceTraits`, but we
 * deploy to iOS 16, and this set has not changed in years - they are the last of
 * the classic MacinTalk voices.
 */
private val NOVELTY_VOICES = setOf(
    "com.apple.speech.synthesis.voice.Albert",
    "com.apple.speech.synthesis.voice.BadNews",
    "com.apple.speech.synthesis.voice.Bahh",
    "com.apple.speech.synthesis.voice.Bells",
    "com.apple.speech.synthesis.voice.Boing",
    "com.apple.speech.synthesis.voice.Bubbles",
    "com.apple.speech.synthesis.voice.Cellos",
    "com.apple.speech.synthesis.voice.Deranged",
    "com.apple.speech.synthesis.voice.GoodNews",
    "com.apple.speech.synthesis.voice.Hysterical",
    "com.apple.speech.synthesis.voice.Organ",
    "com.apple.speech.synthesis.voice.Princess",
    "com.apple.speech.synthesis.voice.Trinoids",
    "com.apple.speech.synthesis.voice.Whisper",
    "com.apple.speech.synthesis.voice.Zarvox",
)

/**
 * Every voice iOS will speak with, as the shared picker wants them.
 *
 * Deliberately unfiltered by language and un-condensed: [buildVoiceCatalogue]
 * groups these by language and collapses the quality tiers of a speaker, so
 * that both platforms do it the same way and it can be tested off-device. The
 * one filter left here is [DISALLOWED_VOICES], which is about which voices
 * Soundscape will speak at all rather than about presentation.
 */
@OptIn(ExperimentalForeignApi::class)
fun availableTtsVoices(): List<VoiceDescriptor> =
    AVSpeechSynthesisVoice.speechVoices()
        .filterIsInstance<AVSpeechSynthesisVoice>()
        .filter { it.identifier !in DISALLOWED_VOICES }
        .map { voice ->
            VoiceDescriptor(
                identifier = voice.identifier,
                displayName = voice.name,
                languageTag = voice.language,
                quality = when (voice.quality) {
                    AVSpeechSynthesisVoiceQualityPremium -> VoiceQuality.Premium
                    AVSpeechSynthesisVoiceQualityEnhanced -> VoiceQuality.Enhanced
                    else -> VoiceQuality.Default
                },
                provider = appleVoiceProvider(voice.identifier),
                isNovelty = voice.identifier in NOVELTY_VOICES,
            )
        }

/**
 * The language "Auto" resolves its default voice for, and that the voice picker
 * sorts to the top.
 *
 * This is the language Soundscape itself is running in, which is not always the
 * phone's: iOS records a per-app override in the AppleLanguages default, and a
 * user running the app in Spanish on an English phone wants the Spanish voices
 * first. Falls back to the device locale when there is no override, which is
 * the usual case.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun currentAppLanguageTag(): String {
    val override = getAppLocale() ?: return NSLocale.currentLocale.languageCode
    return if (override.region != null) "${override.language}-${override.region}"
    else override.language
}

/** The language subtag of [currentAppLanguageTag], for the AVFoundation lookups. */
@OptIn(ExperimentalForeignApi::class)
internal fun currentTtsLanguageCode(): String = languageCodeOf(currentAppLanguageTag())

/**
 * The voice "Auto" plays in: whichever voice the user picked for [languageCode]
 * in iOS Settings > Accessibility > Spoken Content > Voices.
 *
 * `AVSpeechSynthesisVoice(language:)` is what surfaces that choice — this mirrors
 * the legacy `TTSConfigHelper.defaultVoice(forLocale:)`, including its fallback
 * for when the system default is one of the [DISALLOWED_VOICES] we suppress.
 * Callers must resolve this per utterance rather than caching it, otherwise
 * changing the system voice has no effect until the app is relaunched.
 */
@OptIn(ExperimentalForeignApi::class)
fun defaultTtsVoice(languageCode: String): AVSpeechSynthesisVoice? {
    val systemDefault = AVSpeechSynthesisVoice.voiceWithLanguage(languageCode)
    if (systemDefault != null && systemDefault.identifier !in DISALLOWED_VOICES) {
        return systemDefault
    }

    // Legacy compared `voice.language` against the full locale identifier here
    // ("en-GB" against "en_US"), which never matched. Compare language codes,
    // which is what it was reaching for.
    return AVSpeechSynthesisVoice.speechVoices()
        .filterIsInstance<AVSpeechSynthesisVoice>()
        .firstOrNull {
            it.identifier !in DISALLOWED_VOICES &&
                NSLocale(it.language).languageCode == languageCode
        }
}

/**
 * The voice list, re-enumerated each time the app returns to the foreground.
 *
 * A downloaded voice only joins `AVSpeechSynthesisVoice.speechVoices()` once it
 * has finished installing, and a user fetching one has left for the iOS Settings
 * app to do it — so the voice appears while we are backgrounded. Enumerating once
 * per process meant it never showed up without force-quitting Soundscape first
 * (Soundscape-Android#1100).
 */
@Composable
fun rememberAvailableTtsVoices(): List<VoiceDescriptor> {
    var voices by remember { mutableStateOf(availableTtsVoices()) }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification,
            null,
            NSOperationQueue.mainQueue,
        ) {
            voices = availableTtsVoices()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    return voices
}
