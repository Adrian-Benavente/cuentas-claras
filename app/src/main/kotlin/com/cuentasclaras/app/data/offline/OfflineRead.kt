package com.cuentasclaras.app.data.offline

data class OfflineReadResult<T>(
    val data: T,
    val fromCache: Boolean,
)

object OfflineRead {
    fun isLikelyNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (matches(current)) return true
            current = current.cause
        }
        return false
    }

    private fun matches(error: Throwable): Boolean {
        val name = error::class.java.name.lowercase()
        if (name.contains("unknownhost") ||
            name.contains("connectexception") ||
            name.contains("sockettimeout") ||
            name.contains("sslhandshake") ||
            name.contains("httphostconnect")
        ) {
            return true
        }
        val message = error.message.orEmpty().lowercase()
        return message.contains("network") ||
            message.contains("unable to resolve") ||
            message.contains("timeout") ||
            message.contains("failed to connect") ||
            message.contains("connection reset") ||
            message.contains("connection refused") ||
            message.contains("unreachable") ||
            message.contains("no address associated") ||
            message.contains("software caused connection abort")
    }

    /**
     * Network-first read with Room fallback when offline or on network failure.
     */
    suspend fun <T> networkFirst(
        isOnline: Boolean,
        remote: suspend () -> T,
        readCache: suspend () -> T?,
        writeCache: suspend (T) -> Unit,
    ): OfflineReadResult<T> {
        if (!isOnline) {
            val cached = readCache()
                ?: throw IllegalStateException("network unreachable")
            return OfflineReadResult(cached, fromCache = true)
        }
        try {
            val data = remote()
            writeCache(data)
            return OfflineReadResult(data, fromCache = false)
        } catch (error: Throwable) {
            if (!isLikelyNetworkFailure(error)) throw error
            val cached = readCache() ?: throw error
            return OfflineReadResult(cached, fromCache = true)
        }
    }
}
