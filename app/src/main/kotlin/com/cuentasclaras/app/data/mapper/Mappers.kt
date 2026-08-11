package com.cuentasclaras.app.data.mapper

import com.cuentasclaras.app.data.remote.ExpenseDto
import com.cuentasclaras.app.data.remote.ExpenseSplitDto
import com.cuentasclaras.app.data.remote.GroupDto
import com.cuentasclaras.app.data.remote.GroupMemberDto
import com.cuentasclaras.app.data.remote.ProfileDto
import com.cuentasclaras.app.data.remote.SettlementPaymentDto
import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.ExpenseSplit
import com.cuentasclaras.domain.model.Group
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.SettlementPayment
import com.cuentasclaras.domain.model.SettlementPaymentId
import com.cuentasclaras.domain.model.SplitType
import com.cuentasclaras.domain.model.User
import com.cuentasclaras.domain.model.UserId
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

fun ProfileDto.toDomain(): User = User(
    id = UserId(id),
    name = displayName,
    email = email,
    avatarUrl = avatarUrl,
    createdAt = createdAt?.let { Instant.parse(it) } ?: Instant.EPOCH,
    updatedAt = updatedAt?.let { Instant.parse(it) } ?: Instant.EPOCH,
)

fun GroupDto.toDomain(): Group = Group(
    id = GroupId(id),
    name = name,
    currency = Currency(currency),
    inviteCode = inviteCode,
    createdBy = UserId(createdBy),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)

fun GroupMemberDto.toDomain(): GroupMember = GroupMember(
    groupId = GroupId(groupId),
    userId = UserId(userId),
    role = when (role.uppercase()) {
        "OWNER" -> MemberRole.OWNER
        else -> MemberRole.MEMBER
    },
    joinedAt = Instant.parse(joinedAt),
    displayName = profiles?.displayName.orEmpty(),
)

fun ExpenseSplitDto.toDomain(currency: Currency): ExpenseSplit = ExpenseSplit(
    userId = UserId(userId),
    splitType = when (splitType.uppercase()) {
        "PERCENTAGE" -> SplitType.PERCENTAGE
        "FIXED_AMOUNT" -> SplitType.FIXED_AMOUNT
        else -> SplitType.EQUAL
    },
    share = Money(shareAmountMinor, currency),
)

fun ExpenseDto.toDomain(): Expense {
    val currency = Currency(currency)
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
        splits = splits.map { it.toDomain(currency) },
    )
}

fun SettlementPaymentDto.toDomain(): SettlementPayment = SettlementPayment(
    id = SettlementPaymentId(id),
    groupId = GroupId(groupId),
    fromUserId = UserId(fromUserId),
    toUserId = UserId(toUserId),
    amount = Money(amountMinor, Currency(currency)),
    period = YearMonth.of(periodYear, periodMonth),
    createdBy = UserId(createdBy),
    createdAt = Instant.parse(createdAt),
)
