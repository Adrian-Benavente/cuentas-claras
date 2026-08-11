package com.cuentasclaras.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.app.util.UserFacingError
import com.cuentasclaras.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeContent(
    val groups: List<Group>,
    val fromCache: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val state: StateFlow<UiState<HomeContent>> = _state.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _state.value !is UiState.Content && _state.value !is UiState.Empty) {
                _state.value = UiState.Loading
            }
            runCatching { groupRepository.listMyGroups() }
                .onSuccess { result ->
                    _state.value = if (result.data.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Content(
                            HomeContent(groups = result.data, fromCache = result.fromCache),
                        )
                    }
                }
                .onFailure { error ->
                    if (_state.value !is UiState.Content && _state.value !is UiState.Empty) {
                        _state.value = UiState.Error(
                            UserFacingError.from(error, UserFacingError.Context.LoadGroups),
                        )
                    }
                }
        }
    }
}
