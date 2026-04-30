package org.scottishtecharmy.soundscape.screens.markers_routes.screens.routedetailsscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.scottishtecharmy.soundscape.database.local.dao.RouteDao
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.error_message_route_not_found
import org.scottishtecharmy.soundscape.services.ServiceConnection

open class RouteDetailsViewModel(
    private val routeDao: RouteDao,
    private val connection: ServiceConnection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteDetailsUiState())
    val uiState: StateFlow<RouteDetailsUiState> = _uiState.asStateFlow()

    fun getRouteById(routeId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val route = routeDao.getRouteWithMarkers(routeId)
                _uiState.value = _uiState.value.copy(route = route, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = runBlocking { getString(Res.string.error_message_route_not_found) },
                    isLoading = false,
                )
            }
        }
    }

    fun startRoute(routeId: Long) {
        connection.service?.routeStartById(routeId)
    }

    fun startRouteInReverse(routeId: Long) {
        connection.service?.routeStartReverse(routeId)
    }

    fun stopRoute() {
        connection.service?.routeStop()
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
