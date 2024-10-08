package org.scottishtecharmy.soundscape.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun DrawerMenuItem(onClick: () -> Unit, label: String, icon: ImageVector) {
    NavigationDrawerItem(
        label = { Text(
            text = label,
            color = Color.White
        ) },
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.background
        ),
        icon = {
            Icon(
                icon,
                null,
                tint = Color.White
            )
        },
        selected = false,
        onClick = { onClick() }
    )
}