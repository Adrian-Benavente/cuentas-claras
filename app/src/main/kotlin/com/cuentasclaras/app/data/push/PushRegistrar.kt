package com.cuentasclaras.app.data.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRegistrar @Inject constructor(
    private val pushTokenRepository: PushTokenRepository,
) {
    suspend fun registerCurrentDevice() {
        val token = FirebaseMessaging.getInstance().token.await()
        pushTokenRepository.upsertCurrentUserToken(token)
    }

    suspend fun unregisterCurrentDevice() {
        val token = FirebaseMessaging.getInstance().token.await()
        pushTokenRepository.deleteToken(token)
    }
}
