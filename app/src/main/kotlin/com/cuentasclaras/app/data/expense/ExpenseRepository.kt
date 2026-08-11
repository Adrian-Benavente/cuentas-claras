package com.cuentasclaras.app.data.expense

import com.cuentasclaras.app.data.mapper.toDomain
import com.cuentasclaras.app.data.remote.ExpenseDto
import com.cuentasclaras.domain.finance.EqualSplitCalculator
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun listExpenses(groupId: GroupId): List<Expense> {
        return client.from("expenses")
            .select(Columns.raw("*, expense_splits(*)")) {
                filter { eq("group_id", groupId.value) }
            }
            .decodeList<ExpenseDto>()
            .map { it.toDomain() }
            .sortedByDescending { it.date }
    }

    suspend fun getExpense(groupId: GroupId, expenseId: ExpenseId): Expense {
        return client.from("expenses")
            .select(Columns.raw("*, expense_splits(*)")) {
                filter {
                    eq("group_id", groupId.value)
                    eq("id", expenseId.value)
                }
            }
            .decodeSingle<ExpenseDto>()
            .toDomain()
    }

    suspend fun createExpense(
        groupId: GroupId,
        description: String,
        amount: Money,
        paidBy: UserId,
        date: LocalDate,
        createdBy: UserId,
        participantIds: List<UserId>,
    ): Expense {
        val splits = EqualSplitCalculator.split(amount, participantIds)
        val payload = buildJsonObject {
            put("p_group_id", groupId.value)
            put("p_description", description.trim())
            put("p_amount_minor", amount.amountMinor)
            put("p_currency", amount.currency.code)
            put("p_paid_by", paidBy.value)
            put("p_expense_date", date.toString())
            putJsonArray("p_splits") {
                splits.forEach { split ->
                    addJsonObject {
                        put("user_id", split.userId.value)
                        put("split_type", split.splitType.name)
                        put("share_amount_minor", split.share.amountMinor)
                    }
                }
            }
        }
        // createdBy is enforced server-side via auth.uid()
        return client.postgrest.rpc("create_expense", payload).decodeAs<ExpenseDto>().toDomain()
    }

    suspend fun updateExpense(
        expenseId: ExpenseId,
        description: String,
        amount: Money,
        paidBy: UserId,
        date: LocalDate,
        participantIds: List<UserId>,
    ): Expense {
        val splits = EqualSplitCalculator.split(amount, participantIds)
        val payload = buildJsonObject {
            put("p_expense_id", expenseId.value)
            put("p_description", description.trim())
            put("p_amount_minor", amount.amountMinor)
            put("p_currency", amount.currency.code)
            put("p_paid_by", paidBy.value)
            put("p_expense_date", date.toString())
            putJsonArray("p_splits") {
                splits.forEach { split ->
                    addJsonObject {
                        put("user_id", split.userId.value)
                        put("split_type", split.splitType.name)
                        put("share_amount_minor", split.share.amountMinor)
                    }
                }
            }
        }
        return client.postgrest.rpc("update_expense", payload).decodeAs<ExpenseDto>().toDomain()
    }

    suspend fun deleteExpense(expenseId: ExpenseId) {
        client.from("expenses").delete {
            filter { eq("id", expenseId.value) }
        }
    }
}
