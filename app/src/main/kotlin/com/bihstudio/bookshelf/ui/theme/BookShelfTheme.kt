package com.bihstudio.bookshelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HiTechDarkColors = darkColorScheme(
    primary = Color(0xFF00F2FF), // Neon Cyan
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF97F3F6),
    secondary = Color(0xFFFF00E5), // Neon Magenta
    onSecondary = Color(0xFF4B0043),
    secondaryContainer = Color(0xFF6B0061),
    onSecondaryContainer = Color(0xFFFFD7F3),
    tertiary = Color(0xFF7000FF), // Electric Purple
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF4B00B0),
    onTertiaryContainer = Color(0xFFEDDCFF),
    background = Color(0xFF050B18), // Deep Space Blue
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF0D1424),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF1B263B),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9199),
)

private val HiTechLightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FB),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF9C4092),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD7F3),
    onSecondaryContainer = Color(0xFF390035),
    tertiary = Color(0xFF6B4EA2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF260058),
    background = Color(0xFFF8FDFF),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF8FDFF),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797A),
)

@Composable
fun BookShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Forcing Hi-Tech Dark as the primary experience for "Hi-Tech" feel
    val colorScheme = if (darkTheme) HiTechDarkColors else HiTechDarkColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
