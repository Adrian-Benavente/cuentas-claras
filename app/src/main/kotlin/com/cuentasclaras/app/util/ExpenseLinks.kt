package com.cuentasclaras.app.util

import android.content.Intent
import android.net.Uri

data class ExpenseDeepLink(
    val groupId: String,
    val expenseId: String,
)

object ExpenseLinks {
    const val HOST_GROUP = "group"
    const val EXTRA_GROUP_ID = "groupId"
    const val EXTRA_EXPENSE_ID = "expenseId"

    fun uri(groupId: String, expenseId: String): String =
        "${InviteShare.SCHEME}://$HOST_GROUP/$groupId/expense/$expenseId"

    fun parse(intent: Intent?): ExpenseDeepLink? {
        if (intent == null) return null
        parse(intent.data)?.let { return it }
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID)?.trim().orEmpty()
        val expenseId = intent.getStringExtra(EXTRA_EXPENSE_ID)?.trim().orEmpty()
        if (groupId.isBlank() || expenseId.isBlank()) return null
        return ExpenseDeepLink(groupId, expenseId)
    }

    fun parse(uri: Uri?): ExpenseDeepLink? {
        if (uri == null) return null
        return parse(
            scheme = uri.scheme,
            host = uri.host,
            pathSegments = uri.pathSegments,
        )
    }

    fun parse(
        scheme: String?,
        host: String?,
        pathSegments: List<String>,
    ): ExpenseDeepLink? {
        if (scheme != InviteShare.SCHEME) return null
        if (host != HOST_GROUP) return null
        if (pathSegments.size != 3) return null
        if (pathSegments[1] != "expense") return null
        val groupId = pathSegments[0].trim()
        val expenseId = pathSegments[2].trim()
        if (groupId.isBlank() || expenseId.isBlank()) return null
        return ExpenseDeepLink(groupId, expenseId)
    }
}
