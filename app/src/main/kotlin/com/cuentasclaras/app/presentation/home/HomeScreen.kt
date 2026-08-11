package com.cuentasclaras.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.presentation.components.FullScreenLoading
import com.cuentasclaras.app.presentation.components.FullScreenMessage
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.domain.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis grupos") },
                actions = {
                    IconButton(
                        onClick = onJoinGroup,
                        modifier = Modifier.semantics { contentDescription = "Unirme a un grupo" },
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null)
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.semantics { contentDescription = "Cerrar sesión" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroup,
                modifier = Modifier.semantics { contentDescription = "Crear grupo" },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { padding ->
        when (val ui = state) {
            UiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            UiState.Empty -> FullScreenMessage(
                title = "Todavía no tenés grupos",
                body = "Creá uno o unite con un código de invitación.",
                actionLabel = "Crear grupo",
                onAction = onCreateGroup,
                modifier = Modifier.padding(padding),
            )
            is UiState.Error -> FullScreenMessage(
                title = "No pudimos cargar tus grupos",
                body = ui.message,
                actionLabel = "Reintentar",
                onAction = { viewModel.refresh(showLoading = true) },
                isError = true,
                modifier = Modifier.padding(padding),
            )
            is UiState.Content -> GroupList(
                modifier = Modifier.padding(padding),
                groups = ui.data,
                onOpenGroup = onOpenGroup,
            )
        }
    }
}

@Composable
private fun GroupList(
    modifier: Modifier,
    groups: List<Group>,
    onOpenGroup: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(groups, key = { it.id.value }) { group ->
            ListItem(
                headlineContent = { Text(group.name) },
                supportingContent = { Text("Moneda: ${group.currency.code}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGroup(group.id.value) }
                    .semantics { contentDescription = "Abrir grupo ${group.name}" },
            )
        }
    }
}
