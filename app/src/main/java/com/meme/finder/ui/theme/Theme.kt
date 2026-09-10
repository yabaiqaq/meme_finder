package com.meme.finder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
)

private val DarkColors = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF1B0091),
    secondary = PurpleGrey80,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
)

val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun MemeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
