package com.cuentasclaras.app.data.push

import com.cuentasclaras.app.data.remote.DevicePushTokenDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun upsertCurrentUserToken(token: String) {
        if (token.isBlank()) return
        val userId = client.auth.currentUserOrNull()?.id ?: return
        client.from("device_push_tokens").upsert(
            DevicePushTokenDto(userId = userId, token = token),
        )
    }

    suspend fun deleteToken(token: String) {
        if (token.isBlank()) return
        client.from("device_push_tokens").delete {
            filter { eq("token", token) }
        }
    }
}
