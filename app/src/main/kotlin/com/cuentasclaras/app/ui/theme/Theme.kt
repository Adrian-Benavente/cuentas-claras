package com.cuentasclaras.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cuentasclaras.domain.model.GroupThemeId

data class GroupThemePalette(
    val id: GroupThemeId,
    val label: String,
    val light: ColorScheme,
    val dark: ColorScheme,
) {
    fun accent(): Color = light.primary

    fun scheme(darkTheme: Boolean): ColorScheme = if (darkTheme) dark else light
}

object GroupThemes {
    val all: List<GroupThemePalette> = listOf(
        palette(
            id = GroupThemeId.FOREST,
            label = "Bosque",
            primary = Color(0xFF0F6E56),
            secondary = Color(0xFF3D8B74),
            tertiary = Color(0xFFB7E4C7),
            background = Color(0xFFF7FBF9),
            darkPrimary = Color(0xFF7DD3B5),
            darkOnPrimary = Color(0xFF003828),
            darkSecondary = Color(0xFF9AD4C0),
        ),
        palette(
            id = GroupThemeId.OCEAN,
            label = "Océano",
            primary = Color(0xFF0B6E99),
            secondary = Color(0xFF3A8BB0),
            tertiary = Color(0xFFB3E0F2),
            background = Color(0xFFF5FBFE),
            darkPrimary = Color(0xFF7EC8E8),
            darkOnPrimary = Color(0xFF00344A),
            darkSecondary = Color(0xFF9AD4E8),
        ),
        palette(
            id = GroupThemeId.SUNSET,
            label = "Atardecer",
            primary = Color(0xFFB85C38),
            secondary = Color(0xFFC97B5A),
            tertiary = Color(0xFFF2D0C0),
            background = Color(0xFFFFF8F5),
            darkPrimary = Color(0xFFE8A88A),
            darkOnPrimary = Color(0xFF4A1F0E),
            darkSecondary = Color(0xFFE0B09A),
        ),
        palette(
            id = GroupThemeId.SLATE,
            label = "Pizarra",
            primary = Color(0xFF44566B),
            secondary = Color(0xFF5F738A),
            tertiary = Color(0xFFCDD7E2),
            background = Color(0xFFF6F8FA),
            darkPrimary = Color(0xFFB0C0D2),
            darkOnPrimary = Color(0xFF1B2A3A),
            darkSecondary = Color(0xFFC0CDDB),
        ),
        palette(
            id = GroupThemeId.ORCHID,
            label = "Orquídea",
            primary = Color(0xFF8B4F7A),
            secondary = Color(0xFFA56B96),
            tertiary = Color(0xFFE8CDE0),
            background = Color(0xFFFCF7FB),
            darkPrimary = Color(0xFFDBAED0),
            darkOnPrimary = Color(0xFF3A1530),
            darkSecondary = Color(0xFFE0C0D6),
        ),
    )

    fun of(id: GroupThemeId): GroupThemePalette =
        all.firstOrNull { it.id == id } ?: all.first { it.id == GroupThemeId.FOREST }

    fun ofValue(raw: String?): GroupThemePalette = of(GroupThemeId.fromValue(raw))
}

private fun palette(
    id: GroupThemeId,
    label: String,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    darkPrimary: Color,
    darkOnPrimary: Color,
    darkSecondary: Color,
): GroupThemePalette {
    val error = Color(0xFFB3261E)
    return GroupThemePalette(
        id = id,
        label = label,
        light = lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = tertiary,
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = tertiary,
            onSecondaryContainer = primary,
            tertiary = tertiary,
            error = error,
            background = background,
            surface = background,
        ),
        dark = darkColorScheme(
            primary = darkPrimary,
            onPrimary = darkOnPrimary,
            secondary = darkSecondary,
            onSecondary = darkOnPrimary,
            secondaryContainer = darkPrimary,
            onSecondaryContainer = darkOnPrimary,
            error = Color(0xFFFFB4AB),
        ),
    )
}

@Composable
fun GroupThemed(
    themeId: GroupThemeId,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = GroupThemes.of(themeId)
    MaterialTheme(
        colorScheme = palette.scheme(darkTheme),
        content = content,
    )
}

@Composable
fun CuentasClarasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    GroupThemed(
        themeId = GroupThemeId.FOREST,
        darkTheme = darkTheme,
        content = content,
    )
}
