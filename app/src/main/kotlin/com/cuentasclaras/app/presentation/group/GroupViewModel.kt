package com.cuentasclaras.app.presentation.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.auth.AuthRepository
import com.cuentasclaras.app.data.expense.ExpenseRepository
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.domain.finance.PeriodSummaryCalculator
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.Group
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.PeriodSummary
import com.cuentasclaras.domain.model.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class GroupContent(
    val group: Group,
    val members: List<GroupMember>,
    val expenses: List<Expense>,
    val period: YearMonth,
    val summary: PeriodSummary,
    val isOwner: Boolean,
    val currentUserId: UserId?,
)

@HiltViewModel
class GroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val groupId = GroupId(checkNotNull(savedStateHandle["groupId"]))

    private val _state = MutableStateFlow<UiState<GroupContent>>(UiState.Loading)
    val state: StateFlow<UiState<GroupContent>> = _state.asStateFlow()

    private var selectedPeriod: YearMonth = YearMonth.now()

    /**
     * Reloads group data from the backend.
     * Use [showLoading] = false when returning to this screen so existing
     * content stays visible while expenses/summary update.
     */
    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _state.value !is UiState.Content) {
                _state.value = UiState.Loading
            }
            runCatching {
                val group = groupRepository.getGroup(groupId)
                val members = groupRepository.listMembers(groupId)
                val expenses = expenseRepository.listExpenses(groupId)
                val currentUserId = authRepository.currentUserId()
                val isOwner = members.any {
                    it.userId == currentUserId && it.role == MemberRole.OWNER
                }
                val summary = PeriodSummaryCalculator.summarize(
                    expenses = expenses,
                    memberIds = members.map { it.userId },
                    currency = group.currency,
                    period = selectedPeriod,
                )
                GroupContent(
                    group = group,
                    members = members,
                    expenses = expenses,
                    period = selectedPeriod,
                    summary = summary,
                    isOwner = isOwner,
                    currentUserId = currentUserId,
                )
            }.onSuccess { content ->
                _state.value = UiState.Content(content)
            }.onFailure {
                if (_state.value !is UiState.Content) {
                    _state.value = UiState.Error("No pudimos cargar el grupo. Intentá de nuevo.")
                }
            }
        }
    }

    fun previousPeriod() {
        selectedPeriod = selectedPeriod.minusMonths(1)
        recomputePeriod()
    }

    fun nextPeriod() {
        selectedPeriod = selectedPeriod.plusMonths(1)
        recomputePeriod()
    }

    fun rotateInviteCode() {
        viewModelScope.launch {
            runCatching { groupRepository.rotateInviteCode(groupId) }
                .onSuccess { refresh() }
        }
    }

    private fun recomputePeriod() {
        val current = (_state.value as? UiState.Content)?.data ?: return refresh()
        val summary = PeriodSummaryCalculator.summarize(
            expenses = current.expenses,
            memberIds = current.members.map { it.userId },
            currency = current.group.currency,
            period = selectedPeriod,
        )
        _state.update {
            UiState.Content(
                current.copy(period = selectedPeriod, summary = summary),
            )
        }
    }
}
