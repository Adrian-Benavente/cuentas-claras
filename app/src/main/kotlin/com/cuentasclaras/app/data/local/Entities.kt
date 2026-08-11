package com.cuentasclaras.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val inviteCode: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val avatarUrl: String? = null,
    val themeId: String = "forest",
)

@Entity(
    tableName = "cached_members",
    primaryKeys = ["groupId", "userId"],
    indices = [Index("groupId")],
)
data class MemberEntity(
    val groupId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
    val displayName: String,
)

@Entity(
    tableName = "cached_expenses",
    indices = [Index("groupId")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val paidBy: String,
    val expenseDate: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val installmentSeriesId: String? = null,
    val installmentIndex: Int? = null,
    val installmentCount: Int? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryIconKey: String? = null,
)

@Entity(
    tableName = "cached_expense_categories",
    indices = [Index("groupId")],
)
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val iconKey: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "cached_expense_splits",
    primaryKeys = ["expenseId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class ExpenseSplitEntity(
    val expenseId: String,
    val userId: String,
    val splitType: String,
    val shareAmountMinor: Long,
)

@Entity(
    tableName = "cached_settlement_payments",
    indices = [Index("groupId"), Index(value = ["groupId", "periodYear", "periodMonth"])],
)
data class SettlementPaymentEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
    val currency: String,
    val periodYear: Int,
    val periodMonth: Int,
    val createdBy: String,
    val createdAt: String,
)

@Entity(
    tableName = "cached_period_closures",
    primaryKeys = ["groupId", "periodYear", "periodMonth"],
    indices = [Index("groupId")],
)
data class PeriodClosureEntity(
    val groupId: String,
    val periodYear: Int,
    val periodMonth: Int,
    val closedBy: String,
    val closedAt: String,
)

@Entity(tableName = "cache_flags")
data class CacheFlagEntity(
    @PrimaryKey val key: String,
    val value: Boolean,
)
