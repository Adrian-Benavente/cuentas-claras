package com.cuentasclaras.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.auth.AuthRepository
import com.cuentasclaras.app.data.auth.SessionState
import com.cuentasclaras.app.data.push.PushRegistrar
import com.cuentasclaras.app.util.UserFacingError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiFormState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val pushRegistrar: PushRegistrar,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    private val _formState = MutableStateFlow(AuthUiFormState())
    val formState: StateFlow<AuthUiFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { authRepository.initialize() }
                .onFailure {
                    // Keep SignedOut so splash can proceed; login will surface config errors.
                }
        }
    }

    fun onEmailChange(value: String) {
        _formState.value = _formState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _formState.value = _formState.value.copy(password = value, errorMessage = null)
    }

    fun onDisplayNameChange(value: String) {
        _formState.value = _formState.value.copy(displayName = value, errorMessage = null)
    }

    fun login() {
        val state = _formState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _formState.value = state.copy(errorMessage = "Completá email y contraseña.")
            return
        }
        viewModelScope.launch {
            _formState.value = state.copy(isLoading = true, errorMessage = null)
            runCatching {
                authRepository.signInWithEmail(state.email, state.password)
            }.onSuccess {
                _formState.value = _formState.value.copy(isLoading = false)
            }.onFailure { error ->
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingError.from(error, UserFacingError.Context.Auth),
                )
            }
        }
    }

    fun register() {
        val state = _formState.value
        when {
            state.displayName.isBlank() -> {
                _formState.value = state.copy(errorMessage = "Ingresá tu nombre.")
                return
            }
            state.email.isBlank() || state.password.isBlank() -> {
                _formState.value = state.copy(errorMessage = "Completá email y contraseña.")
                return
            }
            state.password.length < 6 -> {
                _formState.value = state.copy(errorMessage = "La contraseña debe tener al menos 6 caracteres.")
                return
            }
        }
        viewModelScope.launch {
            _formState.value = state.copy(isLoading = true, errorMessage = null)
            runCatching {
                authRepository.signUpWithEmail(state.email, state.password, state.displayName)
            }.onSuccess {
                _formState.value = _formState.value.copy(isLoading = false)
            }.onFailure { error ->
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingError.from(error, UserFacingError.Context.Auth),
                )
            }
        }
    }

    fun resetPassword() {
        val email = _formState.value.email
        if (email.isBlank()) {
            _formState.value = _formState.value.copy(errorMessage = "Ingresá tu email para recuperar la contraseña.")
            return
        }
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            runCatching {
                authRepository.sendPasswordReset(email)
            }.onSuccess {
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    infoMessage = "Te enviamos un email para restablecer la contraseña.",
                )
            }.onFailure { error ->
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingError.from(error, UserFacingError.Context.Auth),
                )
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                authRepository.signInWithGoogleIdToken(idToken)
            }.onSuccess {
                _formState.value = _formState.value.copy(isLoading = false)
            }.onFailure { error ->
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingError.from(error, UserFacingError.Context.Auth),
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { logoutAndAwait() }
        }
    }

    suspend fun logoutAndAwait() {
        runCatching { pushRegistrar.unregisterCurrentDevice() }
        runCatching { authRepository.signOut() }
    }

    fun showError(message: String) {
        _formState.value = _formState.value.copy(isLoading = false, errorMessage = message)
    }
}
