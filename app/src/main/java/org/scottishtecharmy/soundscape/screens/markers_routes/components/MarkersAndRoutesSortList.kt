package org.scottishtecharmy.soundscape.screens.markers_routes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.ui.theme.SoundscapeTheme

@Composable
fun MarkersAndRoutesListSort(
    isSortByName: Boolean,
    isAscending: Boolean,
    onToggleSortOrder: () -> Unit,
    onToggleSortByName: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(48.dp)
            .toggleable(
                value = isAscending,
                role = Role.Button,
                onValueChange = { onToggleSortOrder() }
            ),
            imageVector = Icons.Default.SwapVert,
            contentDescription = "" // TODO: Add ascending/descending hint
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (isSortByName) stringResource(R.string.markers_sort_button_sort_by_name)
                   else stringResource(R.string.markers_sort_button_sort_by_distance),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.toggleable(
                value = isSortByName,
                role = Role.Button,
                onValueChange = { onToggleSortByName() }
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (isSortByName) stringResource(R.string.routes_sort_by_distance)
                   else stringResource(R.string.routes_sort_by_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.toggleable(
                value = isSortByName,
                role = Role.Button,
                onValueChange = { onToggleSortByName() }
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MarkersAndRoutesListSortByNamePreview() {
    SoundscapeTheme {
        // Preview with sorting by name
        MarkersAndRoutesListSort(
            isSortByName = true,
            isAscending = true,
            onToggleSortOrder = { /* Handle toggle */ },
            onToggleSortByName = { /* Handle toggle */ }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MarkersAndRoutesListSortByDistancePreview() {
    SoundscapeTheme {
        // Preview with sorting by name
        MarkersAndRoutesListSort(
            isSortByName = false,
            isAscending = true,
            onToggleSortOrder = { /* Handle toggle */ },
            onToggleSortByName = { /* Handle toggle */ }
        )
    }
}
