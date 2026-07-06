package com.bihstudio.bookshelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightLibraryColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF147D83),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFC8F1F0),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF003739),
    secondary = androidx.compose.ui.graphics.Color(0xFF9C5571),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD9E5),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF3C1025),
    tertiary = androidx.compose.ui.graphics.Color(0xFF536FAD),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE5FF),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF14254D),
    background = androidx.compose.ui.graphics.Color(0xFFF7FAFF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF182027),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF182027),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8F0F5),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF47545D),
    outline = androidx.compose.ui.graphics.Color(0xFF788991),
)

private val DarkLibraryColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF72D8D7),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003738),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF07565A),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFA7F1EF),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFAEC9),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF5C1834),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF73344F),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD9E5),
    tertiary = androidx.compose.ui.graphics.Color(0xFFB7C5FF),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF203566),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF394B7D),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE5FF),
    background = androidx.compose.ui.graphics.Color(0xFF111A27),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE4EAF5),
    surface = androidx.compose.ui.graphics.Color(0xFF182331),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE4EAF5),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3E4857),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC3CAD5),
    outline = androidx.compose.ui.graphics.Color(0xFF8D96A4),
)

@Composable
fun BookShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkLibraryColors else LightLibraryColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
