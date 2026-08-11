package com.cuentasclaras.app.data.expense

import com.cuentasclaras.app.data.local.LocalCache
import com.cuentasclaras.app.data.mapper.toDomain
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.data.offline.OfflineRead
import com.cuentasclaras.app.data.offline.OfflineReadResult
import com.cuentasclaras.app.data.remote.ExpenseCategoryDto
import com.cuentasclaras.domain.model.CategoryIcon
import com.cuentasclaras.domain.model.ExpenseCategory
import com.cuentasclaras.domain.model.ExpenseCategoryId
import com.cuentasclaras.domain.model.GroupId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val client: SupabaseClient,
    private val localCache: LocalCache,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    suspend fun listCategories(groupId: GroupId): OfflineReadResult<List<ExpenseCategory>> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("expense_categories")
                    .select {
                        filter { eq("group_id", groupId.value) }
                        order("name", Order.ASCENDING)
                    }
                    .decodeList<ExpenseCategoryDto>()
                    .map { it.toDomain() }
            },
            readCache = {
                if (localCache.hasCategoriesSnapshot(groupId)) {
                    localCache.listCategories(groupId)
                } else {
                    null
                }
            },
            writeCache = { localCache.replaceCategories(groupId, it) },
        )
    }

    suspend fun createCategory(
        groupId: GroupId,
        name: String,
        icon: CategoryIcon,
    ): ExpenseCategory {
        val payload = buildJsonObject {
            put("p_group_id", groupId.value)
            put("p_name", name.trim())
            put("p_icon_key", icon.value)
        }
        val created = client.postgrest.rpc("create_expense_category", payload)
            .decodeAs<ExpenseCategoryDto>()
            .toDomain()
        if (localCache.hasCategoriesSnapshot(groupId)) {
            val merged = (localCache.listCategories(groupId) + created)
                .distinctBy { it.id.value }
                .sortedBy { it.name.lowercase() }
            localCache.replaceCategories(groupId, merged)
        }
        return created
    }

    suspend fun updateCategory(
        categoryId: ExpenseCategoryId,
        groupId: GroupId,
        name: String,
        icon: CategoryIcon,
    ): ExpenseCategory {
        val payload = buildJsonObject {
            put("p_category_id", categoryId.value)
            put("p_name", name.trim())
            put("p_icon_key", icon.value)
        }
        val updated = client.postgrest.rpc("update_expense_category", payload)
            .decodeAs<ExpenseCategoryDto>()
            .toDomain()
        if (localCache.hasCategoriesSnapshot(groupId)) {
            val merged = localCache.listCategories(groupId)
                .map { if (it.id == categoryId) updated else it }
                .sortedBy { it.name.lowercase() }
            localCache.replaceCategories(groupId, merged)
        }
        return updated
    }

    suspend fun deleteCategory(categoryId: ExpenseCategoryId, groupId: GroupId) {
        val payload = buildJsonObject {
            put("p_category_id", categoryId.value)
        }
        client.postgrest.rpc("delete_expense_category", payload)
        if (localCache.hasCategoriesSnapshot(groupId)) {
            val remaining = localCache.listCategories(groupId)
                .filterNot { it.id == categoryId }
            localCache.replaceCategories(groupId, remaining)
        }
    }
}
