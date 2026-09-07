package com.bihstudio.madafim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BookIconDarkColors = darkColorScheme(
    primary = Color(0xFF42A5F5),
    onPrimary = Color(0xFF06284A),
    primaryContainer = Color(0xFF174F87),
    onPrimaryContainer = Color(0xFFD7ECFF),
    secondary = Color(0xFFFFB51B),
    onSecondary = Color(0xFF3D2900),
    secondaryContainer = Color(0xFF704B00),
    onSecondaryContainer = Color(0xFFFFE2A6),
    tertiary = Color(0xFF78C943),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF356B18),
    onTertiaryContainer = Color(0xFFD9FFC2),
    background = Color(0xFF07182E),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF102A49),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF1A3B60),
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
    val colorScheme = if (darkTheme) BookIconDarkColors else BookIconDarkColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
