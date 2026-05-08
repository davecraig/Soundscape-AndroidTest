package org.scottishtecharmy.soundscape.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.talkback_double_tap_prefix

@Composable
actual fun Modifier.talkbackHint(hint: String): Modifier {
    val prefix = stringResource(Res.string.talkback_double_tap_prefix)
    return semantics {
        onClick(label = prefix + hint, action = { false })
    }
}
