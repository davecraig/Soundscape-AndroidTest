package org.scottishtecharmy.soundscape.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.ui.theme.Foreground2
import org.scottishtecharmy.soundscape.ui.theme.IntroductionTheme
import org.scottishtecharmy.soundscape.ui.theme.PaleBlue

data class EnabledFunction(
    var enabled: Boolean = false,
    var function: (String) -> Unit = {}
)
data class LocationItemDecoration(
    val location: Boolean = false,
    val addToRoute:EnabledFunction = EnabledFunction(),
    val removeFromRoute:EnabledFunction = EnabledFunction()
)

@Composable
fun LocationItem(
    item: LocationDescription,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    decoration: LocationItemDecoration = LocationItemDecoration(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if(decoration.location) {
            Icon(
                Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier.padding(start = 18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item.addressName?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight(700),
                    fontSize = 22.sp,
                    color = Color.White,
                )
            }
            item.distance?.let {
                Text(
                    text = it,
                    color = Foreground2,
                    fontWeight = FontWeight(450),
                )
            }
            item.fullAddress?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight(400),
                    fontSize = 18.sp,
                    color = PaleBlue,
                )
            }
        }
        if(decoration.addToRoute.enabled or decoration.removeFromRoute.enabled) {
            Button(
                onClick = {
                    if(decoration.addToRoute.enabled) {
                        decoration.addToRoute.function(item.addressName!!)
                    } else if(decoration.removeFromRoute.enabled) {
                        decoration.removeFromRoute.function(item.addressName!!)
                    }
                },
            ) {
                if(decoration.addToRoute.enabled) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White,
                    )
                } else if(decoration.removeFromRoute.enabled) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = Color.Red,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light Mode")
@Composable
fun PreviewSearchItemButton() {
    IntroductionTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val test =
                LocationDescription(
                    addressName = "Bristol",
                    fullAddress = "18 Street \n59000 Lille\nFrance",
                    distance = "17 Km",
                    location = LngLatAlt(8.00, 9.55)
                )
            LocationItem(
                item = test,
                onClick = {},
                decoration = LocationItemDecoration(
                    location = true,
                    addToRoute = EnabledFunction(true, {}),
                    removeFromRoute = EnabledFunction(false, {}),
                ),
                modifier = Modifier.width(200.dp),
            )
        }
    }
}
