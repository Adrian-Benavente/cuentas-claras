package com.cuentasclaras.app.data.auth

import com.cuentasclaras.app.data.mapper.toDomain
import com.cuentasclaras.app.data.local.LocalCache
import com.cuentasclaras.app.data.remote.ProfileDto
import com.cuentasclaras.domain.model.User
import com.cuentasclaras.domain.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

@Singleton
class AuthRepository @Inject constructor(
    private val client: SupabaseClient,
    private val localCache: LocalCache,
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    suspend fun initialize() {
        runCatching {
            val session = client.auth.currentSessionOrNull()
            if (session == null) {
                _sessionState.value = SessionState.SignedOut
            } else {
                _sessionState.value = SessionState.SignedIn(loadCurrentUser())
            }
        }.onFailure {
            _sessionState.value = SessionState.SignedOut
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        ensureProfile()
        _sessionState.value = SessionState.SignedIn(loadCurrentUser())
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        ensureProfile(displayName.trim())
        _sessionState.value = SessionState.SignedIn(loadCurrentUser())
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        require(idToken.isNotBlank()) { "Google ID token vacío" }
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
        ensureProfile()
        _sessionState.value = SessionState.SignedIn(loadCurrentUser())
    }

    suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email.trim())
    }

    suspend fun signOut() {
        // Mark signed-out first so LoginScreen does not bounce back to Home
        // while the network sign-out is still in flight.
        _sessionState.value = SessionState.SignedOut
        runCatching { localCache.clearAll() }
        runCatching { client.auth.signOut() }
    }

    fun currentUserId(): UserId? {
        val id = client.auth.currentUserOrNull()?.id ?: return null
        return UserId(id)
    }

    private suspend fun loadCurrentUser(): User {
        val authUser = client.auth.currentUserOrNull()
            ?: error("No hay sesión activa")
        val profiles = client.from("profiles")
            .select {
                filter {
                    eq("id", authUser.id)
                }
            }
            .decodeList<ProfileDto>()

        return profiles.firstOrNull()?.toDomain() ?: User(
            id = UserId(authUser.id),
            name = authUser.email?.substringBefore("@") ?: "Usuario",
            email = authUser.email,
            avatarUrl = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }

    private suspend fun ensureProfile(displayName: String? = null) {
        val authUser = client.auth.currentUserOrNull() ?: return
        val existing = client.from("profiles")
            .select {
                filter { eq("id", authUser.id) }
            }
            .decodeList<ProfileDto>()
            .firstOrNull()

        if (existing == null) {
            val name = displayName
                ?: authUser.email?.substringBefore("@")
                ?: "Usuario"
            client.from("profiles").insert(
                ProfileDto(
                    id = authUser.id,
                    displayName = name,
                    email = authUser.email,
                    avatarUrl = null,
                ),
            )
        } else if (!displayName.isNullOrBlank() && existing.displayName != displayName) {
            client.from("profiles").update(
                mapOf("display_name" to displayName),
            ) {
                filter { eq("id", authUser.id) }
            }
        }
    }
}
