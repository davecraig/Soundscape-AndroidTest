package org.scottishtecharmy.soundscape.screens.markers_routes.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.ui.theme.spacing

@Composable
fun CustomButton(
    modifier: Modifier = Modifier, // Modifier for the button
    onClick: () -> Unit,
    text: String = "", // Button text
    buttonColor: Color, // Button background color
    contentColor: Color, // Button text color
    shape: Shape,
    textStyle: TextStyle? = MaterialTheme.typography.labelSmall, // TextStyle for button text with set default
    fontWeight: FontWeight
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor, // Customizable button background color
            contentColor = contentColor // Customizable text color
        )
    ) {
        Text(
            text = text,
            style = textStyle ?: MaterialTheme.typography.labelSmall,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center
            )
    }
}

@Preview
@Composable
fun CustomButtonPreview() {
    CustomButton(
        onClick = { /*TODO*/ },
        buttonColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        shape = RoundedCornerShape(spacing.small),
        text = stringResource(R.string.route_detail_edit_waypoints_button),
        textStyle = MaterialTheme.typography.titleLarge
    )
}
