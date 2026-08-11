package com.cuentasclaras.app.data.offline

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.UnknownHostException

class OfflineReadTest {

    @Test
    fun isLikelyNetworkFailure_detectsUnknownHost() {
        assertThat(OfflineRead.isLikelyNetworkFailure(UnknownHostException("host")))
            .isTrue()
        assertThat(OfflineRead.isLikelyNetworkFailure(RuntimeException("network unreachable")))
            .isTrue()
        assertThat(OfflineRead.isLikelyNetworkFailure(IllegalStateException("period is closed")))
            .isFalse()
    }

    @Test
    fun networkFirst_remoteSuccess_writesCache() = runTest {
        var written: String? = null
        val result = OfflineRead.networkFirst(
            isOnline = true,
            remote = { "remote" },
            readCache = { "cache" },
            writeCache = { written = it },
        )
        assertThat(result.data).isEqualTo("remote")
        assertThat(result.fromCache).isFalse()
        assertThat(written).isEqualTo("remote")
    }

    @Test
    fun networkFirst_offlineWithCache_returnsCache() = runTest {
        val result = OfflineRead.networkFirst(
            isOnline = false,
            remote = { error("should not call") },
            readCache = { "cached" },
            writeCache = {},
        )
        assertThat(result.data).isEqualTo("cached")
        assertThat(result.fromCache).isTrue()
    }

    @Test
    fun networkFirst_networkFailureWithCache_returnsCache() = runTest {
        val result = OfflineRead.networkFirst(
            isOnline = true,
            remote = { throw UnknownHostException("offline") },
            readCache = { "cached" },
            writeCache = {},
        )
        assertThat(result.data).isEqualTo("cached")
        assertThat(result.fromCache).isTrue()
    }

    @Test
    fun networkFirst_nonNetworkFailure_rethrows() = runTest {
        runCatching {
            OfflineRead.networkFirst(
                isOnline = true,
                remote = { error("period is closed") },
                readCache = { "cached" },
                writeCache = {},
            )
        }.onFailure { error ->
            assertThat(error.message).contains("period is closed")
        }.onSuccess {
            error("expected failure")
        }
    }

    @Test
    fun networkFirst_offlineWithoutCache_throws() = runTest {
        val result = runCatching {
            OfflineRead.networkFirst<String>(
                isOnline = false,
                remote = { error("unused") },
                readCache = { null },
                writeCache = {},
            )
        }
        assertThat(result.exceptionOrNull()?.message).contains("network")
    }
}
