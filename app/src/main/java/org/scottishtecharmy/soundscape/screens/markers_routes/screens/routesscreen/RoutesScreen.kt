package org.scottishtecharmy.soundscape.screens.markers_routes.screens.routesscreen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.database.local.model.RouteData
import org.scottishtecharmy.soundscape.screens.home.HomeRoutes
import org.scottishtecharmy.soundscape.screens.markers_routes.components.CustomFloatingActionButton
import org.scottishtecharmy.soundscape.screens.markers_routes.components.MarkersAndRoutesListSort
import org.scottishtecharmy.soundscape.ui.theme.SoundscapeTheme

@Composable
fun RoutesScreenVM(
    homeNavController: NavController,
    viewModel: RoutesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RoutesScreen(
        homeNavController,
        uiState,
        clearErrorMessage = { viewModel.clearErrorMessage()},
        onToggleSortOrder = { viewModel.toggleSortOrder() },
        onToggleSortByName = { viewModel.toggleSortByName() }
    )
}


@Composable
fun RoutesScreen(
    homeNavController: NavController,
    uiState: RoutesUiState,
    clearErrorMessage: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleSortByName: () -> Unit
) {
    val context = LocalContext.current

    // Display error message if it exists
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            clearErrorMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Display loading state
        if (uiState.isLoading) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.routes.isEmpty()) {
                    // Display UI when no routes are available
                        Box(modifier = Modifier.padding(top = 40.dp)) {
                            Icon(
                                painter = painterResource(
                                    id = R.drawable.ic_routes
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                stringResource(R.string.routes_no_routes_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                stringResource(R.string.routes_no_routes_hint_1),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(modifier = Modifier.padding(top = 10.dp, bottom = 40.dp)) {
                            Text(
                                stringResource(R.string.routes_no_routes_hint_2),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MarkersAndRoutesListSort(
                            isSortByName = uiState.isSortByName,
                            isAscending = uiState.isSortAscending,
                            onToggleSortOrder = onToggleSortOrder,
                            onToggleSortByName = onToggleSortByName
                        )
                    }
                    // Display the list of routes
                    RouteList(
                        uiState = uiState,
                        navController = homeNavController
                    )
                }
            }
        }
        CustomFloatingActionButton(
            onClick = { homeNavController.navigate("${HomeRoutes.AddAndEditRoute.route}?command=new") },
            modifier = Modifier.align(Alignment.BottomCenter),
            icon = Icons.Rounded.AddCircleOutline,
            contentDescription = stringResource(R.string.general_alert_add),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoutesScreenPreview() {
    SoundscapeTheme {
        RoutesScreen(
            homeNavController = rememberNavController(),
            uiState = RoutesUiState(),
            clearErrorMessage = {},
            onToggleSortOrder = {},
            onToggleSortByName = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoutesScreenPopulatedPreview() {
    SoundscapeTheme {
        RoutesScreen(
            homeNavController = rememberNavController(),
            uiState = RoutesUiState(
                routes = listOf(
                    RouteData("Route 1", "Description 1"),
                    RouteData("Route 2", "Description 2"),
                    RouteData("Route 3", "Description 3"),
                )
            ),
            clearErrorMessage = {},
            onToggleSortOrder = {},
            onToggleSortByName = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoutesScreenLoadingPreview() {
    SoundscapeTheme {
        RoutesScreen(
            homeNavController = rememberNavController(),
            uiState = RoutesUiState(isLoading = true),
            clearErrorMessage = {},
            onToggleSortOrder = {},
            onToggleSortByName = {}
        )
    }
}
