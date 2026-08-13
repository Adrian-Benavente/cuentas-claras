package com.cuentasclaras.app.data.push

import com.cuentasclaras.app.util.ExpenseLinks
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CuentasClarasMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenRepository: PushTokenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { pushTokenRepository.upsertCurrentUserToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val groupId = message.data[ExpenseLinks.EXTRA_GROUP_ID].orEmpty()
        val expenseId = message.data[ExpenseLinks.EXTRA_EXPENSE_ID].orEmpty()
        val title = message.notification?.title ?: message.data["title"].orEmpty()
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        if (groupId.isBlank() || expenseId.isBlank() || title.isBlank()) return
        ExpenseNotifications.show(
            context = this,
            title = title,
            body = body.ifBlank { title },
            groupId = groupId,
            expenseId = expenseId,
        )
    }
}
