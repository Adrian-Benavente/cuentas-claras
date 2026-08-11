package com.cuentasclaras.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cuentasclaras.app.navigation.CuentasClarasNavHost
import com.cuentasclaras.app.ui.theme.CuentasClarasTheme
import com.cuentasclaras.app.util.InviteShare
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingJoinCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingJoinCode = InviteShare.parseJoinCode(intent)
        enableEdgeToEdge()
        setContent {
            CuentasClarasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CuentasClarasNavHost(
                        pendingJoinCode = pendingJoinCode,
                        onPendingJoinCodeConsumed = { pendingJoinCode = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingJoinCode = InviteShare.parseJoinCode(intent)
    }
}
