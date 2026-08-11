package com.cuentasclaras.app.data.settlement

import com.cuentasclaras.app.data.local.LocalCache
import com.cuentasclaras.app.data.mapper.toDomain
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.data.offline.OfflineRead
import com.cuentasclaras.app.data.offline.OfflineReadResult
import com.cuentasclaras.app.data.remote.SettlementPaymentDto
import com.cuentasclaras.app.data.remote.SettlementPaymentInsertDto
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.SettlementPayment
import com.cuentasclaras.domain.model.SettlementPaymentId
import com.cuentasclaras.domain.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettlementRepository @Inject constructor(
    private val client: SupabaseClient,
    private val localCache: LocalCache,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    suspend fun listPayments(groupId: GroupId, period: YearMonth): OfflineReadResult<List<SettlementPayment>> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("settlement_payments")
                    .select {
                        filter {
                            eq("group_id", groupId.value)
                            eq("period_year", period.year)
                            eq("period_month", period.monthValue)
                        }
                    }
                    .decodeList<SettlementPaymentDto>()
                    .map { it.toDomain() }
                    .sortedBy { it.createdAt }
            },
            readCache = {
                if (localCache.hasPaymentsSnapshot(groupId, period)) {
                    localCache.listPayments(groupId, period)
                } else {
                    null
                }
            },
            writeCache = { localCache.replacePayments(groupId, period, it) },
        )
    }

    suspend fun createPayment(
        groupId: GroupId,
        fromUserId: UserId,
        toUserId: UserId,
        amount: Money,
        period: YearMonth,
        createdBy: UserId,
    ): SettlementPayment {
        val inserted = client.from("settlement_payments")
            .insert(
                SettlementPaymentInsertDto(
                    groupId = groupId.value,
                    fromUserId = fromUserId.value,
                    toUserId = toUserId.value,
                    amountMinor = amount.amountMinor,
                    currency = amount.currency.code,
                    periodYear = period.year,
                    periodMonth = period.monthValue,
                    createdBy = createdBy.value,
                ),
            ) {
                select()
            }
            .decodeSingle<SettlementPaymentDto>()
        return inserted.toDomain()
    }

    suspend fun deletePayment(paymentId: SettlementPaymentId) {
        client.from("settlement_payments").delete {
            filter { eq("id", paymentId.value) }
        }
    }
}
