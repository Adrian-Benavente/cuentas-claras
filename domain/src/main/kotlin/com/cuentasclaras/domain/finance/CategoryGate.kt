package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.UserId

/**
 * Client-side gates for category edit/delete.
 * Server enforcement remains authoritative.
 */
object CategoryGate {
    fun canManage(
        createdBy: UserId,
        currentUserId: UserId?,
        isOwner: Boolean,
        isUncategorized: Boolean = false,
    ): Boolean {
        if (isUncategorized) return false
        if (currentUserId == null) return false
        return isOwner || createdBy == currentUserId
    }
}
