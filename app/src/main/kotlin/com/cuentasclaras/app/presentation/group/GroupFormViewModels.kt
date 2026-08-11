package com.cuentasclaras.app.presentation.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.util.InviteShare
import com.cuentasclaras.app.util.OfflineMessages
import com.cuentasclaras.app.util.UserFacingError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val name: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdGroupId: String? = null,
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateGroupUiState())
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    fun onNameChange(value: String) {
        _state.value = _state.value.copy(name = value, errorMessage = null)
    }

    fun create() {
        val name = _state.value.name.trim()
        if (name.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Ingresá un nombre para el grupo.")
            return
        }
        if (!connectivityMonitor.currentlyOnline()) {
            _state.value = _state.value.copy(errorMessage = OfflineMessages.NEED_CONNECTION)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching { groupRepository.createGroup(name) }
                .onSuccess { group ->
                    _state.value = _state.value.copy(isLoading = false, createdGroupId = group.id.value)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = UserFacingError.from(error, UserFacingError.Context.CreateGroup),
                    )
                }
        }
    }
}

data class JoinGroupUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val joinedGroupId: String? = null,
)

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(
        JoinGroupUiState(code = InviteShare.normalizeCode(savedStateHandle.get<String>("code").orEmpty())),
    )
    val state: StateFlow<JoinGroupUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(
            code = InviteShare.normalizeCode(value),
            errorMessage = null,
        )
    }

    fun prefillCode(raw: String) {
        val normalized = InviteShare.normalizeCode(raw)
        if (normalized.isBlank()) return
        _state.value = _state.value.copy(code = normalized, errorMessage = null)
    }

    fun onPasteEmpty() {
        _state.value = _state.value.copy(errorMessage = "No hay texto en el portapapeles.")
    }

    fun join() {
        val code = InviteShare.normalizeCode(_state.value.code)
        if (code.length < 4) {
            _state.value = _state.value.copy(errorMessage = "Ingresá el código de invitación.")
            return
        }
        if (!connectivityMonitor.currentlyOnline()) {
            _state.value = _state.value.copy(errorMessage = OfflineMessages.NEED_CONNECTION)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching { groupRepository.joinGroup(code) }
                .onSuccess { groupId ->
                    _state.value = _state.value.copy(isLoading = false, joinedGroupId = groupId.value)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = UserFacingError.from(error, UserFacingError.Context.JoinGroup),
                    )
                }
        }
    }
}
