package com.cuentasclaras.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Group>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Group>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { groupRepository.listMyGroups() }
                .onSuccess { groups ->
                    _state.value = if (groups.isEmpty()) UiState.Empty else UiState.Content(groups)
                }
                .onFailure {
                    _state.value = UiState.Error("No pudimos cargar tus grupos. Intentá de nuevo.")
                }
        }
    }
}
