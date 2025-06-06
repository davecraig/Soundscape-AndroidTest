package org.scottishtecharmy.soundscape.screens.home.home

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.components.NavigationButton
import org.scottishtecharmy.soundscape.geoengine.StreetPreviewEnabled
import org.scottishtecharmy.soundscape.geoengine.StreetPreviewState
import org.scottishtecharmy.soundscape.geoengine.utils.calculateHeadingOffset
import org.scottishtecharmy.soundscape.screens.home.StreetPreviewFunctions
import org.scottishtecharmy.soundscape.ui.theme.mediumPadding
import org.scottishtecharmy.soundscape.ui.theme.spacing

@Composable
fun StreetPreview(
    state: StreetPreviewState,
    heading: Float,
    streetPreviewFunctions: StreetPreviewFunctions
) {
    if(state.enabled == StreetPreviewEnabled.INITIALIZING) {
        Text(text = stringResource(R.string.general_loading_start),
            Modifier.mediumPadding())
    } else {
        // Find best match
        var bestChoice = -1
        var bestHeading = Double.POSITIVE_INFINITY
        val roads = emptySet<String>().toMutableSet()
        for ((index, choice) in state.choices.withIndex()) {
            val headingDelta = calculateHeadingOffset(choice.heading, heading.toDouble())
            if (headingDelta < bestHeading) {
                bestHeading = headingDelta
                bestChoice = index
            }
            roads.add(choice.name)
        }

        // Create intersection description
        var intersectionText = ""
        var lastName: String? = null
        for (road in roads) {
            if (lastName != null) {
                if (intersectionText.isNotEmpty()) intersectionText += ", "
                intersectionText += lastName
            }
            lastName = road
        }
        if (lastName != null) {
            if (intersectionText.isEmpty()) {
                intersectionText = lastName
            } else {
                intersectionText += " and $lastName"
            }
        }

        var text = stringResource(R.string.preview_go_title)
        // TODO: Internationalization
        text += if (bestChoice != -1) " - " + state.choices[bestChoice].name
        else " to nearest intersection"

        // TODO: Internationalization
        if (intersectionText.isNotEmpty()) {
            Text(text = "At $intersectionText")
        }
        NavigationButton(
            onClick = {
                streetPreviewFunctions.go()
            },
            text = text,
            modifier = Modifier.defaultMinSize(minHeight = spacing.targetSize * 3)
        )
    }
    NavigationButton(
        onClick = {
            streetPreviewFunctions.exit()
        },
        // TODO: Internationalization
        text = stringResource(R.string.street_preview_exit),
        horizontalPadding = spacing.large
    )
}