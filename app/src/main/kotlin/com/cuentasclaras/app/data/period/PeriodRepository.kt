package com.cuentasclaras.app.data.period

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
) {
    suspend fun listClosedPeriods(groupId: GroupId): Set<YearMonth> {
        return client.from("group_period_closures")
            .select {
                filter { eq("group_id", groupId.value) }
            }
            .decodeList<PeriodClosureDto>()
            .map { YearMonth.of(it.periodYear, it.periodMonth) }
            .toSet()
    }

    suspend fun isPeriodClosed(groupId: GroupId, period: YearMonth): Boolean {
        return client.from("group_period_closures")
            .select {
                filter {
                    eq("group_id", groupId.value)
                    eq("period_year", period.year)
                    eq("period_month", period.monthValue)
                }
            }
            .decodeList<PeriodClosureDto>()
            .isNotEmpty()
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
    }
}
