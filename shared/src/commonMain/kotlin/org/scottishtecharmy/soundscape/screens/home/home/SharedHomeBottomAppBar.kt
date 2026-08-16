package org.scottishtecharmy.soundscape.screens.home.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.scottishtecharmy.soundscape.audio.TourButton
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.ahead_of_me_24px
import org.scottishtecharmy.soundscape.resources.around_me_24px
import org.scottishtecharmy.soundscape.resources.callouts_panel_title
import org.scottishtecharmy.soundscape.resources.my_location_24px
import org.scottishtecharmy.soundscape.resources.nearby_markers_24px
import org.scottishtecharmy.soundscape.resources.ui_action_button_ahead_of_me
import org.scottishtecharmy.soundscape.resources.ui_action_button_ahead_of_me_acc_hint
import org.scottishtecharmy.soundscape.resources.ui_action_button_around_me
import org.scottishtecharmy.soundscape.resources.ui_action_button_around_me_acc_hint
import org.scottishtecharmy.soundscape.resources.ui_action_button_my_location
import org.scottishtecharmy.soundscape.resources.ui_action_button_my_location_acc_hint
import org.scottishtecharmy.soundscape.resources.ui_action_button_nearby_markers
import org.scottishtecharmy.soundscape.resources.ui_action_button_nearby_markers_acc_hint
import org.scottishtecharmy.soundscape.ui.theme.currentAppButtonColors
import org.scottishtecharmy.soundscape.ui.theme.spacing

data class BottomButtonFunctions(
    val myLocation: () -> Unit = {},
    val aroundMe: () -> Unit = {},
    val aheadOfMe: () -> Unit = {},
    val nearbyMarkers: () -> Unit = {},
)

data class RouteFunctions(
    val skipPrevious: () -> Unit = {},
    val skipNext: () -> Unit = {},
    val mute: () -> Unit = {},
    val stop: () -> Unit = {},
)

data class SearchFunctions(
    val onTriggerSearch: (String) -> Unit = {},
)

data class StreetPreviewFunctions(
    val go: () -> Unit = {},
    val exit: () -> Unit = {},
)

@Composable
fun SharedHomeBottomAppBar(
    bottomButtonFunctions: BottomButtonFunctions,
    activeButton: TourButton? = null,
    modifier: Modifier = Modifier,
) {
    val myLocationHint = stringResource(Res.string.ui_action_button_my_location_acc_hint)
    val nearbyMarkersHint = stringResource(Res.string.ui_action_button_nearby_markers_acc_hint)
    val aroundMeHint = stringResource(Res.string.ui_action_button_around_me_acc_hint)
    val aheadOfMeHint = stringResource(Res.string.ui_action_button_ahead_of_me_acc_hint)

    Surface(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    spacing.extraSmall,
                    spacing.extraSmall,
                    spacing.none,
                    spacing.extraSmall,
                ),
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier
                .padding(bottom = spacing.small)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(
                textAlign = TextAlign.Start,
                text = stringResource(Res.string.callouts_panel_title).uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = spacing.medium, top = spacing.medium)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
            ) {
                HomeBottomAppBarButton(
                    icon = painterResource(Res.drawable.my_location_24px),
                    text = stringResource(Res.string.ui_action_button_my_location),
                    onClick = { bottomButtonFunctions.myLocation() },
                    isPlaying = activeButton == TourButton.MY_LOCATION,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { onClick(label = myLocationHint, action = { false }) }
                        .testTag("homeMyLocation"),
                )

                HomeBottomAppBarButton(
                    icon = painterResource(Res.drawable.around_me_24px),
                    text = stringResource(Res.string.ui_action_button_around_me),
                    onClick = { bottomButtonFunctions.aroundMe() },
                    isPlaying = activeButton == TourButton.AROUND_ME,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { onClick(label = aroundMeHint, action = { false }) }
                        .testTag("homeAroundMe"),
                )

                HomeBottomAppBarButton(
                    icon = painterResource(Res.drawable.ahead_of_me_24px),
                    text = stringResource(Res.string.ui_action_button_ahead_of_me),
                    onClick = { bottomButtonFunctions.aheadOfMe() },
                    isPlaying = activeButton == TourButton.AHEAD_OF_ME,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { onClick(label = aheadOfMeHint, action = { false }) }
                        .testTag("homeAheadOfMe"),
                )

                HomeBottomAppBarButton(
                    icon = painterResource(Res.drawable.nearby_markers_24px),
                    text = stringResource(Res.string.ui_action_button_nearby_markers),
                    onClick = { bottomButtonFunctions.nearbyMarkers() },
                    isPlaying = activeButton == TourButton.NEARBY_MARKERS,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { onClick(label = nearbyMarkersHint, action = { false }) }
                        .testTag("homeNearbyMarkers"),
                )
            }
        }
    }
}

@Composable
private fun HomeBottomAppBarButton(
    icon: Painter,
    text: String,
    onClick: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RectangleShape,
        modifier = modifier,
        contentPadding = PaddingValues(spacing.extraSmall),
        colors = currentAppButtonColors,
    ) {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        ) {
            Crossfade(
                targetState = isPlaying,
                modifier = Modifier
                    .size(spacing.icon)
                    .align(Alignment.CenterHorizontally),
                label = "iconOrEqualizer",
            ) { playing ->
                if (playing) {
                    EqualizerBars(modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Bars pulsing at staggered rates, shown in place of a Hear My Surroundings button's icon while
 * its callout is speaking. Mirrors the original iOS app's "now playing" indicator on these
 * buttons - it starts on press and is replaced by the real icon the instant the audio engine
 * reports that callout's handle has finished, so it never claims audio is playing after it has
 * actually stopped.
 */
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val barHeights = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400 + index * 150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "equalizerBar$index",
        )
    }
    val barColor = currentAppButtonColors.contentColor
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        barHeights.forEach { barHeight ->
            val fraction by barHeight
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .background(barColor),
            )
        }
    }
}
