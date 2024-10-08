package org.scottishtecharmy.soundscape.screens.home.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.switchPreference
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.screens.onboarding.MockHearingPreviewData
import org.scottishtecharmy.soundscape.viewmodels.SettingsViewModel

// This code uses the library https://github.com/zhanghai/ComposePreference
// The UI changes the SharedPreference reference by the `key` which can then be accessed
// anywhere else in the app.

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Preview
@Composable
fun SettingsPreview() {
    Settings({}, MockHearingPreviewData)
}

@Composable
fun Settings(
    onNavigateUp: () -> Unit,
    mockData : MockHearingPreviewData? // TODO set SettingsViewModel in param of SettingsScreen, and create SettingContent
) {

    var viewModel : SettingsViewModel? = null
    if(mockData == null)
        viewModel = hiltViewModel<SettingsViewModel>()

    var beacons : List<String> = emptyList()
    if(viewModel == null) {
        // Preview operation
        if(mockData != null)
            beacons = mockData.names
    }
    else {
        val uiState: SettingsViewModel.SettingsUiState by viewModel.state.collectAsStateWithLifecycle()
        beacons = uiState.beaconTypes
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(),
    ) {
        IconButton(
            onClick = {
                onNavigateUp()
            },
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                modifier = Modifier
                    .size(32.dp)
                    .padding(start = 4.dp),
                contentDescription = stringResource(R.string.ui_menu_close),
                tint = Color.White
            )
        }

        Text(
            text = stringResource(R.string.menu_manage_callouts),
            textAlign = TextAlign.Left,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
        ProvidePreferenceLocals {
            LazyColumn {

                // TODO : Add strings translations and use them

                switchPreference(
                    key = MainActivity.ALLOW_CALLOUTS_KEY,
                    defaultValue = MainActivity.ALLOW_CALLOUTS_DEFAULT,
                    title = { Text(text = stringResource(R.string.callouts_allow_callouts)) },
                    summary = { Text(text = if (it) stringResource(R.string.callouts_callouts_on) else stringResource(R.string.callouts_callouts_off)) }
                )
                switchPreference(
                    key = MainActivity.PLACES_AND_LANDMARKS_KEY,
                    defaultValue = MainActivity.PLACES_AND_LANDMARKS_DEFAULT,
                    title = { Text(text = stringResource(R.string.callouts_places_and_landmarks)) },
                    summary = { Text(text = if (it) "On" else "Off") }
                )
                switchPreference(
                    key = MainActivity.MOBILITY_KEY,
                    defaultValue = MainActivity.MOBILITY_DEFAULT,
                    title = { Text(text = stringResource(R.string.callouts_mobility)) },
                    summary = { Text(text = if (it) "On" else "Off") }
                )
                switchPreference(
                    key = MainActivity.DISTANCE_TO_BEACON_KEY,
                    defaultValue = MainActivity.DISTANCE_TO_BEACON_DEFAULT,
                    title = { Text(text = stringResource(R.string.callouts_audio_beacon)) },
                    summary = { Text(text = if (it) "On" else "Off") }
                )
                switchPreference(
                    key = MainActivity.UNNAMED_ROADS_KEY,
                    defaultValue = MainActivity.UNNAMED_ROADS_DEFAULT,
                    title = { Text(text = stringResource(R.string.preview_include_unnamed_roads_title)) },
                    summary = { Text(text = if (it) "On" else "Off") }
                )
            }
        }
        Text(
            text = "MANAGE BEACONS",
            textAlign = TextAlign.Left,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
        ProvidePreferenceLocals {
            LazyColumn {
                listPreference(
                    key = MainActivity.BEACON_TYPE_KEY,
                    defaultValue = MainActivity.BEACON_TYPE_DEFAULT,
                    values = beacons,
                    title = { Text(text = "Beacon type") },
                    summary = { Text(text = it) },
                )
            }
        }
    }
}