package com.cuentasclaras.app.presentation.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear grupo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize()) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nombre del grupo") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Nombre del grupo" },
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(20.dp))
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = viewModel::create,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Crear grupo" },
                ) {
                    Text("Crear")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    onJoined: (String) -> Unit,
    onBack: () -> Unit,
    initialCode: String? = null,
    viewModel: JoinGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(initialCode) {
        if (!initialCode.isNullOrBlank()) {
            viewModel.prefillCode(initialCode)
        }
    }

    LaunchedEffect(state.joinedGroupId) {
        state.joinedGroupId?.let(onJoined)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unirme a un grupo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize()) {
            Text(
                "El código lo compartió quien creó el grupo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Código del grupo") },
                singleLine = true,
                supportingText = { Text("Sin espacios ni guiones") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Código de invitación" },
            )
            OutlinedButton(
                onClick = {
                    val pasted = clipboard.getText()?.text.orEmpty()
                    if (pasted.isNotBlank()) {
                        viewModel.prefillCode(pasted)
                    } else {
                        viewModel.onPasteEmpty()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { contentDescription = "Pegar código" },
            ) {
                Text("Pegar desde el portapapeles")
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(20.dp))
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = viewModel::join,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Unirme al grupo" },
                ) {
                    Text("Unirme")
                }
            }
        }
    }
}
