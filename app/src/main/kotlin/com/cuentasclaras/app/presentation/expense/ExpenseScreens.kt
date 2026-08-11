package com.cuentasclaras.app.presentation.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.presentation.components.ExpenseDateField
import com.cuentasclaras.app.util.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    groupId: String,
    expenseId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpenseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId == null) "Nuevo gasto" else "Editar gasto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Concepto") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Concepto del gasto" },
            )
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Monto (${state.currency.code})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Monto del gasto" },
            )

            var payerExpanded by remember { mutableStateOf(false) }
            val selectedPayer = state.members.find { it.userId == state.paidBy }
            ExposedDropdownMenuBox(
                expanded = payerExpanded,
                onExpandedChange = { payerExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedPayer?.displayName ?: "Elegí quién pagó",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Quién pagó") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .semantics { contentDescription = "Quién pagó" },
                )
                ExposedDropdownMenu(
                    expanded = payerExpanded,
                    onDismissRequest = { payerExpanded = false },
                ) {
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.displayName) },
                            onClick = {
                                viewModel.onPaidByChange(member.userId)
                                payerExpanded = false
                            },
                        )
                    }
                }
            }

            ExpenseDateField(
                date = state.date,
                onDateChange = viewModel::onDateChange,
            )

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            if (state.isSaving) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Guardar gasto" },
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    groupId: String,
    expenseId: String,
    flashMessage: String? = null,
    onFlashConsumed: () -> Unit = {},
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpenseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    LaunchedEffect(flashMessage) {
        val message = flashMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onFlashConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del gasto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val expense = state.expense
        if (state.isLoading) {
            Column(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (expense == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(state.errorMessage ?: "Gasto no encontrado.")
                TextButton(onClick = onBack) { Text("Volver") }
            }
            return@Scaffold
        }

        val payer = state.members.find { it.userId == expense.paidBy }?.displayName ?: "Alguien"
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(expense.description, style = MaterialTheme.typography.headlineSmall)
            Text(MoneyFormatter.format(expense.amount), style = MaterialTheme.typography.headlineMedium)
            Text("Pagó: $payer")
            Text("Fecha: ${expense.date}")
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            if (state.canEdit) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Editar gasto" },
                ) {
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Eliminar gasto" },
                ) {
                    Text("Eliminar")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar gasto") },
            text = { Text("¿Seguro que querés eliminar este gasto? Los saldos se recalcularán.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}
