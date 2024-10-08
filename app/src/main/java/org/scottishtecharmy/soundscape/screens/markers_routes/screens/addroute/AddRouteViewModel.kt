package org.scottishtecharmy.soundscape.screens.markers_routes.screens.addroute

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AddRouteViewModel @Inject constructor(): ViewModel() {
    private val _uiState = MutableStateFlow(AddRouteUiState())
    val uiState: StateFlow<AddRouteUiState> = _uiState

    fun onNameChange(newText: String) {
        _uiState.value = _uiState.value.copy(name = newText)
    }

    fun onDescriptionChange(newText: String) {
        _uiState.value = _uiState.value.copy(description = newText)
    }

    fun onAddWaypointsClicked() {
        val (isValid, errorStates) = validateFields(_uiState.value.name, _uiState.value.description)

        _uiState.value = _uiState.value.copy(
            nameError = !errorStates.first,
            descriptionError = !errorStates.second
        )

        if (isValid) {
            _uiState.value = _uiState.value.copy(navigateToMarkersAndRoutes = true)
        }
        Log.d("Fanny","${_uiState.value}")
    }

    // Reset navigation state after navigation is handled
    fun resetNavigationState() {
        _uiState.value = _uiState.value.copy(navigateToMarkersAndRoutes = false)
    }

    // Validation logic for name and description fields
    private fun isFieldValid(field: String): Boolean {
        return field.isNotBlank()
    }

    private fun validateFields(name: String, description: String): Pair<Boolean, Pair<Boolean, Boolean>> {
        val isNameValid = isFieldValid(name)
        val isDescriptionValid = isFieldValid(description)
        return Pair(isNameValid && isDescriptionValid, Pair(isNameValid, isDescriptionValid))
    }

}
