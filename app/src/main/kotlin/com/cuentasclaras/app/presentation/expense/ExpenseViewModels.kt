package com.cuentasclaras.app.presentation.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.auth.AuthRepository
import com.cuentasclaras.app.data.expense.ExpenseRepository
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.util.MoneyFormatter
import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExpenseEditorUiState(
    val description: String = "",
    val amountInput: String = "",
    val paidBy: UserId? = null,
    val date: LocalDate = LocalDate.now(),
    val members: List<GroupMember> = emptyList(),
    val currency: Currency = Currency.ARS,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
    val existing: Expense? = null,
)

@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val groupId = GroupId(checkNotNull(savedStateHandle["groupId"]))
    private val expenseId: ExpenseId? = savedStateHandle.get<String>("expenseId")?.let(::ExpenseId)

    private val _state = MutableStateFlow(ExpenseEditorUiState(isLoading = true))
    val state: StateFlow<ExpenseEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val group = groupRepository.getGroup(groupId)
                val members = groupRepository.listMembers(groupId)
                val currentUser = authRepository.currentUserId()
                val existing = expenseId?.let { expenseRepository.getExpense(groupId, it) }
                _state.value = ExpenseEditorUiState(
                    description = existing?.description.orEmpty(),
                    amountInput = existing?.let { MoneyFormatter.formatMajorInput(it.amount.amountMinor) }.orEmpty(),
                    paidBy = existing?.paidBy ?: currentUser ?: members.firstOrNull()?.userId,
                    date = existing?.date ?: LocalDate.now(),
                    members = members,
                    currency = group.currency,
                    isLoading = false,
                    existing = existing,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "No pudimos cargar el formulario.",
                )
            }
        }
    }

    fun onDescriptionChange(value: String) {
        _state.value = _state.value.copy(description = value, errorMessage = null)
    }

    fun onAmountChange(value: String) {
        _state.value = _state.value.copy(amountInput = value, errorMessage = null)
    }

    fun onPaidByChange(userId: UserId) {
        _state.value = _state.value.copy(paidBy = userId)
    }

    fun onDateChange(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
    }

    fun save() {
        val current = _state.value
        val description = current.description.trim()
        val amountMinor = MoneyFormatter.parseToMinor(current.amountInput, current.currency)
        val paidBy = current.paidBy
        val createdBy = authRepository.currentUserId()

        when {
            description.isBlank() -> {
                _state.value = current.copy(errorMessage = "Ingresá el concepto del gasto.")
                return
            }
            amountMinor == null || amountMinor <= 0L -> {
                _state.value = current.copy(errorMessage = "Ingresá un monto mayor a cero.")
                return
            }
            paidBy == null -> {
                _state.value = current.copy(errorMessage = "Elegí quién pagó.")
                return
            }
            createdBy == null -> {
                _state.value = current.copy(errorMessage = "Sesión no válida. Volvé a iniciar sesión.")
                return
            }
            current.members.isEmpty() -> {
                _state.value = current.copy(errorMessage = "El grupo no tiene miembros.")
                return
            }
            expenseId == null && current.members.size < 2 -> {
                _state.value = current.copy(
                    errorMessage = "Necesitás al menos otra persona en el grupo para cargar un gasto. " +
                        "Invitala con el código desde Configuración.",
                )
                return
            }
        }

        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, errorMessage = null)
            val amount = Money(amountMinor!!, current.currency)
            val participants = current.members.map { it.userId }
            runCatching {
                if (expenseId == null) {
                    expenseRepository.createExpense(
                        groupId = groupId,
                        description = description,
                        amount = amount,
                        paidBy = paidBy!!,
                        date = current.date,
                        createdBy = createdBy!!,
                        participantIds = participants,
                    )
                } else {
                    expenseRepository.updateExpense(
                        expenseId = expenseId,
                        description = description,
                        amount = amount,
                        paidBy = paidBy!!,
                        date = current.date,
                        participantIds = participants,
                    )
                }
            }.onSuccess {
                _state.value = _state.value.copy(isSaving = false, done = true)
            }.onFailure { error ->
                val message = error.message.orEmpty().lowercase()
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = when {
                        message.contains("group needs at least two members") ->
                            "Necesitás al menos otra persona en el grupo para cargar un gasto."
                        else ->
                            "No pudimos guardar el gasto. Intentá de nuevo."
                    },
                )
            }
        }
    }
}

data class ExpenseDetailUiState(
    val isLoading: Boolean = true,
    val expense: Expense? = null,
    val members: List<GroupMember> = emptyList(),
    val canEdit: Boolean = false,
    val errorMessage: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val groupId = GroupId(checkNotNull(savedStateHandle["groupId"]))
    private val expenseId = ExpenseId(checkNotNull(savedStateHandle["expenseId"]))

    private val _state = MutableStateFlow(ExpenseDetailUiState())
    val state: StateFlow<ExpenseDetailUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val keepContent = _state.value.expense != null
            if (!keepContent) {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                val expense = expenseRepository.getExpense(groupId, expenseId)
                val members = groupRepository.listMembers(groupId)
                val currentUser = authRepository.currentUserId()
                val isOwner = members.any {
                    it.userId == currentUser && it.role == com.cuentasclaras.domain.model.MemberRole.OWNER
                }
                _state.value = ExpenseDetailUiState(
                    isLoading = false,
                    expense = expense,
                    members = members,
                    canEdit = currentUser == expense.createdBy || isOwner,
                )
            }.onFailure {
                if (!keepContent) {
                    _state.value = ExpenseDetailUiState(
                        isLoading = false,
                        errorMessage = "No pudimos cargar el gasto.",
                    )
                }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { expenseRepository.deleteExpense(expenseId) }
                .onSuccess { _state.value = _state.value.copy(deleted = true) }
                .onFailure {
                    _state.value = _state.value.copy(errorMessage = "No pudimos eliminar el gasto.")
                }
        }
    }
}
