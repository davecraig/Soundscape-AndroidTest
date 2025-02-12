package org.scottishtecharmy.soundscape.screens.markers_routes.screens.addroutescreen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.scottishtecharmy.soundscape.screens.home.HomeRoutes
import org.scottishtecharmy.soundscape.screens.markers_routes.components.CustomAppBar
import org.scottishtecharmy.soundscape.screens.markers_routes.components.CustomButton
import org.scottishtecharmy.soundscape.screens.markers_routes.components.CustomTextField
import org.scottishtecharmy.soundscape.ui.theme.SoundscapeTheme

@Composable
fun AddRouteScreenVM(
    navController: NavController,
    viewModel: AddRouteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AddRouteScreen(
        navController,
        uiState,
        onClearErrorMessage = { viewModel.clearErrorMessage() },
        onResetDoneAction = {
            viewModel.resetDoneActionState()
            viewModel.resetNavigationState()
        },
        onNameChange = { viewModel.onNameChange(it) },
        onDescriptionChange = { viewModel.onDescriptionChange(it) },
        onAddWaypointsClicked = { viewModel.onAddWaypointsClicked() },
        onDoneClicked = { viewModel.onDoneClicked() }
    )
}

@Composable
fun AddRouteScreen(
    navController: NavController,
    uiState: AddRouteUiState,
    onClearErrorMessage: () -> Unit,
    onResetDoneAction: () -> Unit,
    onNameChange: (newText: String) -> Unit,
    onDescriptionChange: (newText: String) -> Unit,
    onAddWaypointsClicked: () -> Unit,
    onDoneClicked: () -> Unit,
) {
    val context = LocalContext.current

    // Display error message if it exists
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onClearErrorMessage()
        }
    }

    // Determine if the "Done" button should be visible
    val showDoneButton = uiState.name.isNotBlank()

    // Observe navigation and trigger it if necessary
    LaunchedEffect(uiState.doneActionCompleted) {
        if (uiState.doneActionCompleted) {
            navController.navigate(HomeRoutes.MarkersAndRoutes.route) {
                popUpTo(HomeRoutes.MarkersAndRoutes.route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
            onResetDoneAction()
            Toast.makeText(context, "Route added successfully", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            CustomAppBar(
                title = stringResource(R.string.route_detail_action_create),
                navigationButtonTitle = stringResource(R.string.general_alert_cancel),
                onNavigateUp = { navController.popBackStack()},
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.route_no_waypoints_hint_1),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                )
                Text(
                    modifier = Modifier.padding(top = 20.dp, bottom = 5.dp),
                    text = stringResource(R.string.markers_sort_button_sort_by_name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.surfaceBright
                )
                CustomTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.name,
                    onValueChange = onNameChange
                )
                Text(
                    modifier = Modifier.padding(top = 20.dp, bottom = 5.dp),
                    text = stringResource(R.string.route_detail_edit_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.surfaceBright
                )
                CustomTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.description,
                    onValueChange = onDescriptionChange
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    thickness = 1.dp
                )
                CustomButton(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 20.dp, bottom = 10.dp),
                    onClick = onAddWaypointsClicked,
                    buttonColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(10.dp),
                    text = stringResource(R.string.route_detail_edit_waypoints_button),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                CustomButton(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 20.dp, bottom = 10.dp),
                    onClick = onDoneClicked,
                    buttonColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(10.dp),
                    text = stringResource(R.string.general_alert_done),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    thickness = 1.dp,
                )
                // TODO fix the formatting/alignment for the addition of the original iOS text below
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AddRouteScreenPreview() {
    SoundscapeTheme {
        AddRouteScreen(
            navController = rememberNavController(),
            AddRouteUiState(
                name = "Route one",
                description = "Description of route one",
                showDoneButton = false
            ),
            onClearErrorMessage = {},
            onResetDoneAction = {},
            onNameChange = {},
            onDescriptionChange = {},
            onAddWaypointsClicked = {},
            onDoneClicked = {},
        )
    }
}
