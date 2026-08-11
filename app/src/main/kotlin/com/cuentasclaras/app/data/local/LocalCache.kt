package com.cuentasclaras.app.data.local

import com.cuentasclaras.domain.model.CategoryIcon
import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseCategory
import com.cuentasclaras.domain.model.ExpenseCategoryId
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.ExpenseSplit
import com.cuentasclaras.domain.model.Group
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.GroupThemeId
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.SettlementPayment
import com.cuentasclaras.domain.model.SettlementPaymentId
import com.cuentasclaras.domain.model.SplitType
import com.cuentasclaras.domain.model.UserId
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCache @Inject constructor(
    private val dao: CacheDao,
) {
    suspend fun clearAll() = dao.clearAll()

    suspend fun replaceGroups(groups: List<Group>) {
        val entities = groups.map { it.toEntity() }
        if (entities.isEmpty()) {
            dao.deleteGroupsAll()
        } else {
            dao.upsertGroups(entities)
            dao.deleteGroupsNotIn(entities.map { it.id })
        }
        dao.upsertFlag(CacheFlagEntity(KEY_GROUPS_LIST, true))
    }

    suspend fun hasGroupsListSnapshot(): Boolean = dao.getFlag(KEY_GROUPS_LIST) == true

    suspend fun upsertGroup(group: Group) {
        dao.upsertGroups(listOf(group.toEntity()))
    }

    suspend fun listGroups(): List<Group> = dao.listGroups().map { it.toDomain() }

    suspend fun getGroup(groupId: GroupId): Group? = dao.getGroup(groupId.value)?.toDomain()

    suspend fun replaceMembers(groupId: GroupId, members: List<GroupMember>) {
        dao.deleteMembersForGroup(groupId.value)
        if (members.isNotEmpty()) {
            dao.upsertMembers(members.map { it.toEntity() })
        }
        dao.upsertFlag(CacheFlagEntity(membersFlag(groupId), true))
    }

    suspend fun hasMembersSnapshot(groupId: GroupId): Boolean =
        dao.getFlag(membersFlag(groupId)) == true

    suspend fun listMembers(groupId: GroupId): List<GroupMember> =
        dao.listMembers(groupId.value).map { it.toDomain() }

    suspend fun replaceExpenses(groupId: GroupId, expenses: List<Expense>) {
        dao.deleteSplitsForGroup(groupId.value)
        dao.deleteExpensesForGroup(groupId.value)
        if (expenses.isNotEmpty()) {
            dao.upsertExpenses(expenses.map { it.toEntity() })
            dao.upsertSplits(
                expenses.flatMap { expense ->
                    expense.splits.map { split ->
                        ExpenseSplitEntity(
                            expenseId = expense.id.value,
                            userId = split.userId.value,
                            splitType = split.splitType.name,
                            shareAmountMinor = split.share.amountMinor,
                        )
                    }
                },
            )
        }
        dao.upsertFlag(CacheFlagEntity(expensesFlag(groupId), true))
    }

    suspend fun hasExpensesSnapshot(groupId: GroupId): Boolean =
        dao.getFlag(expensesFlag(groupId)) == true

    suspend fun listExpenses(groupId: GroupId): List<Expense> {
        return dao.listExpenses(groupId.value).map { entity ->
            entity.toDomain(dao.listSplits(entity.id))
        }
    }

    suspend fun getExpense(groupId: GroupId, expenseId: ExpenseId): Expense? {
        val entity = dao.getExpense(groupId.value, expenseId.value) ?: return null
        return entity.toDomain(dao.listSplits(entity.id))
    }

    suspend fun replaceCategories(groupId: GroupId, categories: List<ExpenseCategory>) {
        dao.deleteCategoriesForGroup(groupId.value)
        if (categories.isNotEmpty()) {
            dao.upsertCategories(categories.map { it.toEntity() })
        }
        dao.upsertFlag(CacheFlagEntity(categoriesFlag(groupId), true))
    }

    suspend fun hasCategoriesSnapshot(groupId: GroupId): Boolean =
        dao.getFlag(categoriesFlag(groupId)) == true

    suspend fun listCategories(groupId: GroupId): List<ExpenseCategory> =
        dao.listCategories(groupId.value).map { it.toDomain() }

    suspend fun replacePayments(groupId: GroupId, period: YearMonth, payments: List<SettlementPayment>) {
        dao.deletePaymentsForPeriod(groupId.value, period.year, period.monthValue)
        if (payments.isNotEmpty()) {
            dao.upsertPayments(payments.map { it.toEntity() })
        }
        dao.upsertFlag(CacheFlagEntity(paymentsFlag(groupId, period), true))
    }

    suspend fun hasPaymentsSnapshot(groupId: GroupId, period: YearMonth): Boolean =
        dao.getFlag(paymentsFlag(groupId, period)) == true

    suspend fun listPayments(groupId: GroupId, period: YearMonth): List<SettlementPayment> =
        dao.listPayments(groupId.value, period.year, period.monthValue).map { it.toDomain() }

    suspend fun replaceClosures(groupId: GroupId, closedPeriods: Set<YearMonth>) {
        dao.deleteClosuresForGroup(groupId.value)
        if (closedPeriods.isNotEmpty()) {
            dao.upsertClosures(
                closedPeriods.map { period ->
                    PeriodClosureEntity(
                        groupId = groupId.value,
                        periodYear = period.year,
                        periodMonth = period.monthValue,
                        closedBy = "",
                        closedAt = Instant.EPOCH.toString(),
                    )
                },
            )
        }
        dao.upsertFlag(CacheFlagEntity(closuresFlag(groupId), true))
    }

    suspend fun hasClosuresSnapshot(groupId: GroupId): Boolean =
        dao.getFlag(closuresFlag(groupId)) == true

    suspend fun listClosedPeriods(groupId: GroupId): Set<YearMonth> =
        dao.listClosures(groupId.value)
            .map { YearMonth.of(it.periodYear, it.periodMonth) }
            .toSet()

    suspend fun isPeriodClosed(groupId: GroupId, period: YearMonth): Boolean =
        dao.getClosure(groupId.value, period.year, period.monthValue) != null

    suspend fun setPeriodClosed(groupId: GroupId, period: YearMonth, closed: Boolean) {
        if (closed) {
            dao.upsertClosures(
                listOf(
                    PeriodClosureEntity(
                        groupId = groupId.value,
                        periodYear = period.year,
                        periodMonth = period.monthValue,
                        closedBy = "",
                        closedAt = Instant.EPOCH.toString(),
                    ),
                ),
            )
        } else {
            dao.deleteClosure(groupId.value, period.year, period.monthValue)
        }
    }

    private companion object {
        const val KEY_GROUPS_LIST = "groups_list"

        fun membersFlag(groupId: GroupId) = "members:${groupId.value}"
        fun expensesFlag(groupId: GroupId) = "expenses:${groupId.value}"
        fun categoriesFlag(groupId: GroupId) = "categories:${groupId.value}"
        fun paymentsFlag(groupId: GroupId, period: YearMonth) =
            "payments:${groupId.value}:${period.year}-${period.monthValue}"
        fun closuresFlag(groupId: GroupId) = "closures:${groupId.value}"
    }
}

private fun Group.toEntity() = GroupEntity(
    id = id.value,
    name = name,
    currency = currency.code,
    inviteCode = inviteCode,
    createdBy = createdBy.value,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    avatarUrl = avatarUrl,
    themeId = themeId.value,
)

private fun GroupEntity.toDomain() = Group(
    id = GroupId(id),
    name = name,
    currency = Currency(currency),
    inviteCode = inviteCode,
    createdBy = UserId(createdBy),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    avatarUrl = avatarUrl,
    themeId = GroupThemeId.fromValue(themeId),
)

private fun GroupMember.toEntity() = MemberEntity(
    groupId = groupId.value,
    userId = userId.value,
    role = role.name,
    joinedAt = joinedAt.toString(),
    displayName = displayName,
)

private fun MemberEntity.toDomain() = GroupMember(
    groupId = GroupId(groupId),
    userId = UserId(userId),
    role = when (role.uppercase()) {
        "OWNER" -> MemberRole.OWNER
        else -> MemberRole.MEMBER
    },
    joinedAt = Instant.parse(joinedAt),
    displayName = displayName,
)

private fun Expense.toEntity() = ExpenseEntity(
    id = id.value,
    groupId = groupId.value,
    description = description,
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    paidBy = paidBy.value,
    expenseDate = date.toString(),
    createdBy = createdBy.value,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    installmentSeriesId = installmentSeriesId,
    installmentIndex = installmentIndex,
    installmentCount = installmentCount,
    categoryId = categoryId?.value,
    categoryName = categoryName,
    categoryIconKey = categoryIcon?.value,
)

private fun ExpenseEntity.toDomain(splits: List<ExpenseSplitEntity>): Expense {
    val currency = Currency(currency)
    val resolvedCategoryId = categoryId?.takeIf { it.isNotBlank() }?.let(::ExpenseCategoryId)
    return Expense(
        id = ExpenseId(id),
        groupId = GroupId(groupId),
        description = description,
        amount = Money(amountMinor, currency),
        paidBy = UserId(paidBy),
        date = LocalDate.parse(expenseDate),
        createdBy = UserId(createdBy),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        splits = splits.map { split ->
            ExpenseSplit(
                userId = UserId(split.userId),
                splitType = when (split.splitType.uppercase()) {
                    "PERCENTAGE" -> SplitType.PERCENTAGE
                    "FIXED_AMOUNT" -> SplitType.FIXED_AMOUNT
                    else -> SplitType.EQUAL
                },
                share = Money(split.shareAmountMinor, currency),
            )
        },
        installmentSeriesId = installmentSeriesId,
        installmentIndex = installmentIndex,
        installmentCount = installmentCount,
        categoryId = resolvedCategoryId,
        categoryName = if (resolvedCategoryId != null) {
            categoryName?.takeIf { it.isNotBlank() } ?: "Categoría"
        } else {
            null
        },
        categoryIcon = if (resolvedCategoryId != null) {
            CategoryIcon.fromValue(categoryIconKey)
        } else {
            null
        },
    )
}

private fun ExpenseCategory.toEntity() = ExpenseCategoryEntity(
    id = id.value,
    groupId = groupId.value,
    name = name,
    iconKey = icon.value,
    createdBy = createdBy.value,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun ExpenseCategoryEntity.toDomain() = ExpenseCategory(
    id = ExpenseCategoryId(id),
    groupId = GroupId(groupId),
    name = name,
    icon = CategoryIcon.fromValue(iconKey),
    createdBy = UserId(createdBy),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)

private fun SettlementPayment.toEntity() = SettlementPaymentEntity(
    id = id.value,
    groupId = groupId.value,
    fromUserId = fromUserId.value,
    toUserId = toUserId.value,
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    periodYear = period.year,
    periodMonth = period.monthValue,
    createdBy = createdBy.value,
    createdAt = createdAt.toString(),
)

private fun SettlementPaymentEntity.toDomain() = SettlementPayment(
    id = SettlementPaymentId(id),
    groupId = GroupId(groupId),
    fromUserId = UserId(fromUserId),
    toUserId = UserId(toUserId),
    amount = Money(amountMinor, Currency(currency)),
    period = YearMonth.of(periodYear, periodMonth),
    createdBy = UserId(createdBy),
    createdAt = Instant.parse(createdAt),
)
