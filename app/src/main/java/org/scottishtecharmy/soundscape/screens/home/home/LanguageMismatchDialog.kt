package org.scottishtecharmy.soundscape.screens.home.home

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import org.scottishtecharmy.soundscape.MainActivity.Companion.LANGUAGE_SUPPORTED_PROMPTED_KEY
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.screens.onboarding.language.Language

/**
 * Dialog shown when the phone's language differs from the app's configured language
 * and the phone language is supported by the app.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LanguageMismatchDialog(
    innerPadding: PaddingValues,
    sharedPreferences: SharedPreferences,
    showDialog: MutableState<Boolean>,
    phoneLanguage: Language,
) {
    AlertDialog(
        // AlertDialog renders in its own window, so it doesn't inherit
        // testTagsAsResourceId from the screen underneath - set it here too or
        // every testTag() on this dialog's content is invisible to Maestro/UIAutomator.
        modifier = Modifier
            .padding(innerPadding)
            .semantics { testTagsAsResourceId = true },
        title = {
            Text(text = stringResource(R.string.language_mismatch_title))
        },
        text = {
            Text(
                text = stringResource(R.string.language_mismatch_message, phoneLanguage.name)
            )
        },
        onDismissRequest = { },
        confirmButton = {
            TextButton(
                onClick = {
                    sharedPreferences.edit(commit = true) {
                        putBoolean(LANGUAGE_SUPPORTED_PROMPTED_KEY, true)
                    }
                    showDialog.value = false
                    val list = LocaleListCompat.forLanguageTags(
                        "${phoneLanguage.code}-${phoneLanguage.region}"
                    )
                    AppCompatDelegate.setApplicationLocales(list)
                },
                modifier = Modifier.testTag("languageMismatchSwitch")
            ) {
                Text(text = stringResource(R.string.language_mismatch_switch, phoneLanguage.name))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    sharedPreferences.edit(commit = true) {
                        putBoolean(LANGUAGE_SUPPORTED_PROMPTED_KEY, true)
                    }
                    showDialog.value = false
                },
                modifier = Modifier.testTag("languageMismatchKeep")
            ) {
                Text(text = stringResource(R.string.language_mismatch_keep))
            }
        }
    )
}
