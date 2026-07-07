package com.brokenhuskysledteam.spacetradersio.ui.galaxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.StarSystem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TravelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the galaxy browser. */
data class GalaxyUiState(
    val systems: List<StarSystem> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val canLoadMore: Boolean get() = systems.size < total && !isLoading
}

/** Events for the galaxy browser. */
sealed interface GalaxyEvent {
    data object RetryClicked : GalaxyEvent
    data object LoadMoreClicked : GalaxyEvent
}

/**
 * ViewModel for the galaxy browser — a paginated list of star systems (read-through). Pages are
 * appended on demand via LOAD MORE until [GalaxyUiState.total] is reached.
 */
@HiltViewModel
class GalaxyViewModel @Inject constructor(
    private val travelRepository: TravelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalaxyUiState())
    val uiState: StateFlow<GalaxyUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    init {
        loadNextPage(reset = true)
    }

    fun onEvent(event: GalaxyEvent) {
        when (event) {
            is GalaxyEvent.RetryClicked -> loadNextPage(reset = true)
            is GalaxyEvent.LoadMoreClicked -> loadNextPage(reset = false)
        }
    }

    private fun loadNextPage(reset: Boolean) {
        if (reset) nextPage = 1
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = travelRepository.getSystems(nextPage, limit = 20)
                _uiState.update { state ->
                    val merged = if (reset) page.systems else state.systems + page.systems
                    state.copy(systems = merged, total = page.total, isLoading = false)
                }
                nextPage++
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load systems") }
            }
        }
    }
}
