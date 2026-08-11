package com.cuentasclaras.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary = Color(0xFF0F6E56)
private val GreenSecondary = Color(0xFF3D8B74)
private val GreenTertiary = Color(0xFFB7E4C7)
private val ErrorRed = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    secondary = GreenSecondary,
    tertiary = GreenTertiary,
    error = ErrorRed,
    background = Color(0xFFF7FBF9),
    surface = Color(0xFFF7FBF9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3B5),
    onPrimary = Color(0xFF003828),
    secondary = Color(0xFF9AD4C0),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CuentasClarasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
