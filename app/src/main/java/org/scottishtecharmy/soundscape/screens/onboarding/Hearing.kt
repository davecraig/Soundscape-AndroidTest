package org.scottishtecharmy.soundscape.screens.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.components.OnboardButton
import org.scottishtecharmy.soundscape.ui.theme.IntroTypography
import org.scottishtecharmy.soundscape.ui.theme.IntroductionTheme
import org.scottishtecharmy.soundscape.viewmodels.HearingViewModel

@Composable
private fun LocalImage() {
    Image(
        painter = painterResource(R.drawable.ic_surroundings),
        contentDescription = null
    )
}

@Composable
fun Hearing(onNavigate: (String) -> Unit, useView : Boolean) {

    var viewModel : HearingViewModel? = null
    if(useView)
        viewModel = hiltViewModel<HearingViewModel>()

    val landscape = (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)
    val alignment = if(landscape)
        Alignment.CenterVertically
    else
        Alignment.Top

    IntroductionTheme {
        MaterialTheme(typography = IntroTypography) {
            Row(
                verticalAlignment = alignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (landscape) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight(),
                    ) {
                        LocalImage()
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    if (!landscape) {
                        LocalImage()
                        Spacer(modifier = Modifier.height(50.dp))
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.first_launch_callouts_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(50.dp))
                        Text(
                            text = stringResource(R.string.first_launch_callouts_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(30.dp))
                        Text(
                            text = stringResource(R.string.first_launch_callouts_listen),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // <string name="first_launch_callouts_example_1">Cafe</string>
                        // <string name="first_launch_callouts_example_3">Main Street goes left</string>
                        // <string name="first_launch_callouts_example_4">Main Street goes right</string>

                        //val speechText = stringResource(R.string.first_launch_callouts_example_4)

                        // This is a bodged version to introduce pauses between translation strings
                        // Not sure if this will work with some of the other languages, for example, Japanese
                        val speechText = buildString {
                            append(stringResource(R.string.first_launch_callouts_example_1))
                            append(".")
                            append(stringResource(R.string.first_launch_callouts_example_3))
                            append(".")
                            append(stringResource(R.string.first_launch_callouts_example_4))
                        }

                        Button(
                            onClick = {
                                viewModel?.playSpeech(speechText)
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(
                                    alpha = 0.2f
                                )
                            )
                        )
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = stringResource(R.string.first_launch_callouts_listen_accessibility_label))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        OnboardButton(
                            text = stringResource(R.string.ui_continue),
                            onClick = {
                                viewModel?.silenceSpeech()
                                onNavigate(OnboardingScreens.AudioBeacons.route)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Preview
@Composable
fun HearingPreview() {
    Hearing(onNavigate = {}, false)
}