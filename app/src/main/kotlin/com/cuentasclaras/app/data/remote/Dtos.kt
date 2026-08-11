package com.cuentasclaras.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val currency: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("theme_id") val themeId: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class GroupMemberDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
    val profiles: ProfileDto? = null,
)

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val description: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("expense_splits") val splits: List<ExpenseSplitDto> = emptyList(),
)

@Serializable
data class ExpenseSplitDto(
    val id: String? = null,
    @SerialName("expense_id") val expenseId: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("split_type") val splitType: String,
    @SerialName("share_amount_minor") val shareAmountMinor: Long,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val currency: String = "ARS",
)

@Serializable
data class JoinGroupRequest(
    @SerialName("p_invite_code") val inviteCode: String,
)

@Serializable
data class JoinGroupResponse(
    @SerialName("group_id") val groupId: String,
)

@Serializable
data class SettlementPaymentDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @SerialName("period_year") val periodYear: Int,
    @SerialName("period_month") val periodMonth: Int,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SettlementPaymentInsertDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @SerialName("period_year") val periodYear: Int,
    @SerialName("period_month") val periodMonth: Int,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class PeriodClosureDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("period_year") val periodYear: Int,
    @SerialName("period_month") val periodMonth: Int,
    @SerialName("closed_by") val closedBy: String,
    @SerialName("closed_at") val closedAt: String,
)
