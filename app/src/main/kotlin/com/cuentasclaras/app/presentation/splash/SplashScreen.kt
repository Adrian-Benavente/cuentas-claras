package com.cuentasclaras.app.presentation.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cuentasclaras.app.data.auth.SessionState
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    sessionState: SessionState,
    onAuthenticated: () -> Unit,
    onUnauthenticated: () -> Unit,
) {
    LaunchedEffect(sessionState) {
        when (sessionState) {
            SessionState.Loading -> Unit
            is SessionState.SignedIn -> {
                delay(300)
                onAuthenticated()
            }
            SessionState.SignedOut -> {
                delay(300)
                onUnauthenticated()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Cargando Cuentas Claras" },
        contentAlignment = Alignment.Center,
    ) {
        if (sessionState is SessionState.Loading) {
            CircularProgressIndicator()
        } else {
            Text("Cuentas Claras")
        }
    }
}
