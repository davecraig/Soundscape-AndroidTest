package org.scottishtecharmy.soundscape.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
expect fun Modifier.talkbackHint(hint: String): Modifier

@Composable
fun Modifier.talkbackDescription(contentDescription: String) =
    semantics {
        this.contentDescription = contentDescription
    }

@Composable
fun Modifier.talkbackHidden() =
    semantics {
        invisibleToUser()
    }

@Composable
fun Modifier.talkbackLive() =
    semantics {
        liveRegion = LiveRegionMode.Polite
    }
