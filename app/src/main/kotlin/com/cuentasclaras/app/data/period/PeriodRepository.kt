package com.cuentasclaras.app.data.period

import com.cuentasclaras.app.data.local.LocalCache
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.data.offline.OfflineRead
import com.cuentasclaras.app.data.offline.OfflineReadResult
import com.cuentasclaras.app.data.remote.PeriodClosureDto
import com.cuentasclaras.domain.model.GroupId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodRepository @Inject constructor(
    private val client: SupabaseClient,
    private val localCache: LocalCache,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    suspend fun listClosedPeriods(groupId: GroupId): OfflineReadResult<Set<YearMonth>> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("group_period_closures")
                    .select {
                        filter { eq("group_id", groupId.value) }
                    }
                    .decodeList<PeriodClosureDto>()
                    .map { YearMonth.of(it.periodYear, it.periodMonth) }
                    .toSet()
            },
            readCache = {
                if (localCache.hasClosuresSnapshot(groupId)) {
                    localCache.listClosedPeriods(groupId)
                } else {
                    null
                }
            },
            writeCache = { localCache.replaceClosures(groupId, it) },
        )
    }

    suspend fun isPeriodClosed(groupId: GroupId, period: YearMonth): OfflineReadResult<Boolean> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("group_period_closures")
                    .select {
                        filter {
                            eq("group_id", groupId.value)
                            eq("period_year", period.year)
                            eq("period_month", period.monthValue)
                        }
                    }
                    .decodeList<PeriodClosureDto>()
                    .isNotEmpty()
            },
            readCache = {
                if (localCache.hasClosuresSnapshot(groupId)) {
                    localCache.isPeriodClosed(groupId, period)
                } else {
                    null
                }
            },
            writeCache = { closed -> localCache.setPeriodClosed(groupId, period, closed) },
        )
    }

    suspend fun closePeriod(groupId: GroupId, period: YearMonth) {
        client.postgrest.rpc(
            function = "close_group_period",
            parameters = buildJsonObject {
                put("p_group_id", groupId.value)
                put("p_year", period.year)
                put("p_month", period.monthValue)
            },
        )
        localCache.setPeriodClosed(groupId, period, closed = true)
    }

    suspend fun reopenPeriod(groupId: GroupId, period: YearMonth) {
        client.postgrest.rpc(
            function = "reopen_group_period",
            parameters = buildJsonObject {
                put("p_group_id", groupId.value)
                put("p_year", period.year)
                put("p_month", period.monthValue)
            },
        )
        localCache.setPeriodClosed(groupId, period, closed = false)
    }
}
