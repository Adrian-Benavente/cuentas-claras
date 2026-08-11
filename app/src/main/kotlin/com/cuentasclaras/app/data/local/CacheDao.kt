package com.cuentasclaras.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CacheDao {
    @Query("DELETE FROM cached_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM cached_members")
    suspend fun deleteAllMembers()

    @Query("DELETE FROM cached_expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM cached_expense_splits")
    suspend fun deleteAllSplits()

    @Query("DELETE FROM cached_settlement_payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM cached_period_closures")
    suspend fun deleteAllClosures()

    @Query("DELETE FROM cached_expense_categories")
    suspend fun deleteAllCategories()

    @Transaction
    suspend fun clearAll() {
        deleteAllSplits()
        deleteAllExpenses()
        deleteAllCategories()
        deleteAllPayments()
        deleteAllClosures()
        deleteAllMembers()
        deleteAllGroups()
        deleteAllFlags()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<GroupEntity>)

    @Query("SELECT * FROM cached_groups ORDER BY name COLLATE NOCASE ASC")
    suspend fun listGroups(): List<GroupEntity>

    @Query("SELECT * FROM cached_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): GroupEntity?

    @Query("DELETE FROM cached_groups WHERE id NOT IN (:keepIds)")
    suspend fun deleteGroupsNotIn(keepIds: List<String>)

    @Query("DELETE FROM cached_groups")
    suspend fun deleteGroupsAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Query("DELETE FROM cached_members WHERE groupId = :groupId")
    suspend fun deleteMembersForGroup(groupId: String)

    @Query("SELECT * FROM cached_members WHERE groupId = :groupId ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun listMembers(groupId: String): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(expenses: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM cached_expense_splits WHERE expenseId IN (SELECT id FROM cached_expenses WHERE groupId = :groupId)")
    suspend fun deleteSplitsForGroup(groupId: String)

    @Query("DELETE FROM cached_expenses WHERE groupId = :groupId")
    suspend fun deleteExpensesForGroup(groupId: String)

    @Query("SELECT * FROM cached_expenses WHERE groupId = :groupId ORDER BY expenseDate DESC")
    suspend fun listExpenses(groupId: String): List<ExpenseEntity>

    @Query("SELECT * FROM cached_expenses WHERE groupId = :groupId AND id = :expenseId LIMIT 1")
    suspend fun getExpense(groupId: String, expenseId: String): ExpenseEntity?

    @Query("SELECT * FROM cached_expense_splits WHERE expenseId = :expenseId")
    suspend fun listSplits(expenseId: String): List<ExpenseSplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<ExpenseCategoryEntity>)

    @Query("DELETE FROM cached_expense_categories WHERE groupId = :groupId")
    suspend fun deleteCategoriesForGroup(groupId: String)

    @Query("SELECT * FROM cached_expense_categories WHERE groupId = :groupId ORDER BY name COLLATE NOCASE ASC")
    suspend fun listCategories(groupId: String): List<ExpenseCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<SettlementPaymentEntity>)

    @Query(
        "DELETE FROM cached_settlement_payments WHERE groupId = :groupId " +
            "AND periodYear = :year AND periodMonth = :month",
    )
    suspend fun deletePaymentsForPeriod(groupId: String, year: Int, month: Int)

    @Query(
        "SELECT * FROM cached_settlement_payments WHERE groupId = :groupId " +
            "AND periodYear = :year AND periodMonth = :month ORDER BY createdAt ASC",
    )
    suspend fun listPayments(groupId: String, year: Int, month: Int): List<SettlementPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClosures(closures: List<PeriodClosureEntity>)

    @Query("DELETE FROM cached_period_closures WHERE groupId = :groupId")
    suspend fun deleteClosuresForGroup(groupId: String)

    @Query("SELECT * FROM cached_period_closures WHERE groupId = :groupId")
    suspend fun listClosures(groupId: String): List<PeriodClosureEntity>

    @Query(
        "SELECT * FROM cached_period_closures WHERE groupId = :groupId " +
            "AND periodYear = :year AND periodMonth = :month LIMIT 1",
    )
    suspend fun getClosure(groupId: String, year: Int, month: Int): PeriodClosureEntity?

    @Query(
        "DELETE FROM cached_period_closures WHERE groupId = :groupId " +
            "AND periodYear = :year AND periodMonth = :month",
    )
    suspend fun deleteClosure(groupId: String, year: Int, month: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFlag(flag: CacheFlagEntity)

    @Query("SELECT value FROM cache_flags WHERE key = :key LIMIT 1")
    suspend fun getFlag(key: String): Boolean?

    @Query("DELETE FROM cache_flags")
    suspend fun deleteAllFlags()
}
