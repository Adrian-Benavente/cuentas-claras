package com.cuentasclaras.app.presentation.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.auth.AuthRepository
import com.cuentasclaras.app.data.expense.ExpenseRepository
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.data.period.PeriodRepository
import com.cuentasclaras.app.util.MoneyFormatter
import com.cuentasclaras.app.util.OfflineMessages
import com.cuentasclaras.app.util.UserFacingError
import com.cuentasclaras.domain.finance.InstallmentPlanner
import com.cuentasclaras.domain.finance.PeriodGate
import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.GroupThemeId
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class ExpenseEditorUiState(
    val description: String = "",
    val amountInput: String = "",
    val paidBy: UserId? = null,
    val date: LocalDate = LocalDate.now(),
    val members: List<GroupMember> = emptyList(),
    val currency: Currency = Currency.ARS,
    val themeId: GroupThemeId = GroupThemeId.FOREST,
    val closedPeriods: Set<YearMonth> = emptySet(),
    val isInstallment: Boolean = false,
    val installmentCountInput: String = "3",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
    val existing: Expense? = null,
    val fromCache: Boolean = false,
) {
    val isSelectedDateClosed: Boolean
        get() = !PeriodGate.canMutateExpense(date, closedPeriods)

    val isExistingPeriodClosed: Boolean
        get() = existing?.let { !PeriodGate.canMutateExpense(it.date, closedPeriods) } == true

    val parsedInstallmentCount: Int?
        get() = installmentCountInput.trim().toIntOrNull()

    val installmentPreview: String?
        get() {
            if (!isInstallment || existing != null) return null
            val total = MoneyFormatter.parseToMinor(amountInput, currency) ?: return null
            val count = parsedInstallmentCount ?: return null
            if (total <= 0L || count !in InstallmentPlanner.MIN_COUNT..InstallmentPlanner.MAX_COUNT) {
                return null
            }
            return runCatching {
                val slices = InstallmentPlanner.plan(total, count, date)
                val first = slices.first().amountMinor
                val lastDate = slices.last().date
                val monthLabel = lastDate.month
                    .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR"))
                    .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-AR")) }
                "Cada cuota: ${MoneyFormatter.format(Money(first, currency))} · " +
                    "$count gastos · hasta $monthLabel ${lastDate.year}"
            }.getOrNull()
        }

    val isMutationBlocked: Boolean
        get() {
            if (isExistingPeriodClosed) return true
            if (!isInstallment || existing != null) return isSelectedDateClosed
            val total = MoneyFormatter.parseToMinor(amountInput, currency) ?: return isSelectedDateClosed
            val count = parsedInstallmentCount ?: return isSelectedDateClosed
            if (total <= 0L || count !in InstallmentPlanner.MIN_COUNT..InstallmentPlanner.MAX_COUNT) {
                return isSelectedDateClosed
            }
            return runCatching {
                InstallmentPlanner.plan(total, count, date).any {
                    !PeriodGate.canMutateExpense(it.date, closedPeriods)
                }
            }.getOrDefault(isSelectedDateClosed)
        }
}

@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val periodRepository: PeriodRepository,
    private val authRepository: AuthRepository,
    private val connectivityMonitor: ConnectivityMonitor,
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
                val closedPeriods = periodRepository.listClosedPeriods(groupId)
                val currentUser = authRepository.currentUserId()
                val existing = expenseId?.let { expenseRepository.getExpense(groupId, it) }
                _state.value = ExpenseEditorUiState(
                    description = existing?.data?.description.orEmpty(),
                    amountInput = existing?.data?.let {
                        MoneyFormatter.formatMajorInput(it.amount.amountMinor)
                    }.orEmpty(),
                    paidBy = existing?.data?.paidBy ?: currentUser ?: members.data.firstOrNull()?.userId,
                    date = existing?.data?.date ?: LocalDate.now(),
                    members = members.data,
                    currency = group.data.currency,
                    themeId = group.data.themeId,
                    closedPeriods = closedPeriods.data,
                    isLoading = false,
                    existing = existing?.data,
                    fromCache = group.fromCache ||
                        members.fromCache ||
                        closedPeriods.fromCache ||
                        (existing?.fromCache == true),
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingError.from(error, UserFacingError.Context.LoadGroup),
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
        _state.value = _state.value.copy(date = date, errorMessage = null)
    }

    fun onInstallmentEnabledChange(enabled: Boolean) {
        if (_state.value.existing != null) return
        _state.value = _state.value.copy(isInstallment = enabled, errorMessage = null)
    }

    fun onInstallmentCountChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(2)
        _state.value = _state.value.copy(installmentCountInput = filtered, errorMessage = null)
    }

    fun save() {
        val current = _state.value
        val description = current.description.trim()
        val amountMinor = MoneyFormatter.parseToMinor(current.amountInput, current.currency)
        val paidBy = current.paidBy
        val createdBy = authRepository.currentUserId()
        val installmentCount = current.parsedInstallmentCount

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
            current.isInstallment && expenseId == null &&
                (installmentCount == null ||
                    installmentCount !in InstallmentPlanner.MIN_COUNT..InstallmentPlanner.MAX_COUNT) -> {
                _state.value = current.copy(
                    errorMessage = "Ingresá entre ${InstallmentPlanner.MIN_COUNT} y " +
                        "${InstallmentPlanner.MAX_COUNT} cuotas.",
                )
                return
            }
            current.isMutationBlocked -> {
                _state.value = current.copy(
                    errorMessage = if (current.isInstallment && expenseId == null) {
                        "Algún mes de la serie está cerrado. Reabrilo o elegí otra fecha de inicio."
                    } else {
                        "Este período está cerrado. Reabrilo para hacer cambios."
                    },
                )
                return
            }
            !connectivityMonitor.currentlyOnline() -> {
                _state.value = current.copy(errorMessage = OfflineMessages.NEED_CONNECTION)
                return
            }
        }

        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, errorMessage = null)
            val amount = Money(amountMinor!!, current.currency)
            val participants = current.members.map { it.userId }
            runCatching {
                if (expenseId == null && current.isInstallment) {
                    expenseRepository.createInstallments(
                        groupId = groupId,
                        description = description,
                        totalAmount = amount,
                        paidBy = paidBy!!,
                        startDate = current.date,
                        installmentCount = installmentCount!!,
                        participantIds = participants,
                    )
                } else if (expenseId == null) {
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
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = UserFacingError.from(error, UserFacingError.Context.SaveExpense),
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
    val isPeriodClosed: Boolean = false,
    val themeId: GroupThemeId = GroupThemeId.FOREST,
    val fromCache: Boolean = false,
    val errorMessage: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val periodRepository: PeriodRepository,
    private val authRepository: AuthRepository,
    private val connectivityMonitor: ConnectivityMonitor,
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
                val group = groupRepository.getGroup(groupId)
                val closedPeriods = periodRepository.listClosedPeriods(groupId)
                val currentUser = authRepository.currentUserId()
                val isOwner = members.data.any {
                    it.userId == currentUser && it.role == com.cuentasclaras.domain.model.MemberRole.OWNER
                }
                val periodClosed = !PeriodGate.canMutateExpense(expense.data.date, closedPeriods.data)
                _state.value = ExpenseDetailUiState(
                    isLoading = false,
                    expense = expense.data,
                    members = members.data,
                    canEdit = (currentUser == expense.data.createdBy || isOwner) && !periodClosed,
                    isPeriodClosed = periodClosed,
                    themeId = group.data.themeId,
                    fromCache = expense.fromCache || members.fromCache ||
                        closedPeriods.fromCache || group.fromCache,
                )
            }.onFailure { error ->
                if (!keepContent) {
                    _state.value = ExpenseDetailUiState(
                        isLoading = false,
                        errorMessage = UserFacingError.from(error, UserFacingError.Context.LoadGroup),
                    )
                }
            }
        }
    }

    fun delete() {
        delete(series = false)
    }

    fun deleteSeries() {
        delete(series = true)
    }

    private fun delete(series: Boolean) {
        if (_state.value.isPeriodClosed) {
            _state.value = _state.value.copy(
                errorMessage = "Este período está cerrado. Reabrilo para hacer cambios.",
            )
            return
        }
        if (!connectivityMonitor.currentlyOnline()) {
            _state.value = _state.value.copy(errorMessage = OfflineMessages.NEED_CONNECTION)
            return
        }
        val seriesId = _state.value.expense?.installmentSeriesId
        if (series && seriesId.isNullOrBlank()) {
            _state.value = _state.value.copy(errorMessage = "Este gasto no forma parte de una serie.")
            return
        }
        viewModelScope.launch {
            runCatching {
                if (series) {
                    expenseRepository.deleteInstallmentSeries(seriesId!!)
                } else {
                    expenseRepository.deleteExpense(expenseId)
                }
            }
                .onSuccess { _state.value = _state.value.copy(deleted = true) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingError.from(error, UserFacingError.Context.DeleteExpense),
                    )
                }
        }
    }
}
