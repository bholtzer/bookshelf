package com.bihstudio.bookshelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightLibraryColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6F3F28),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCC7),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF2C160C),
    secondary = androidx.compose.ui.graphics.Color(0xFF51664A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD5E8C9),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF10200E),
    tertiary = androidx.compose.ui.graphics.Color(0xFF7A5634),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDDB7),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF2B1703),
    background = androidx.compose.ui.graphics.Color(0xFFFFF8EF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF231A14),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBF6),
    onSurface = androidx.compose.ui.graphics.Color(0xFF231A14),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEBD8C8),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF554438),
    outline = androidx.compose.ui.graphics.Color(0xFF887568),
)

private val DarkLibraryColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFFB690),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF47210F),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF643620),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCC7),
    secondary = androidx.compose.ui.graphics.Color(0xFFB9CCAE),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF243421),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF3A4D35),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD5E8C9),
    tertiary = androidx.compose.ui.graphics.Color(0xFFECC192),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF452A0D),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF60401F),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDDB7),
    background = androidx.compose.ui.graphics.Color(0xFF18110D),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF0DFD3),
    surface = androidx.compose.ui.graphics.Color(0xFF211813),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF0DFD3),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF554438),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD8C2B3),
    outline = androidx.compose.ui.graphics.Color(0xFFA08D80),
)

@Composable
fun BookShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkLibraryColors else LightLibraryColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
