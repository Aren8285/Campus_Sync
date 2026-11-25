package com.campussync.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Ink,
    onPrimaryContainer = Paper,
    inversePrimary = Paper,
    secondary = Graphite,
    onSecondary = Paper,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = Slate,
    onTertiary = Paper,
    tertiaryContainer = Mist,
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Slate,
    surfaceTint = Ink,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    error = Ink,
    onError = Paper,
    errorContainer = Mist,
    onErrorContainer = Ink,
    outline = Color(0xFF8A8A8A),
    outlineVariant = Hairline,
    scrim = Ink,
    surfaceBright = Paper,
    surfaceDim = Color(0xFFE4E4E4),
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper
)

private val DarkColorScheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Paper,
    onPrimaryContainer = Ink,
    inversePrimary = Ink,
    secondary = Silver,
    onSecondary = Ink,
    secondaryContainer = NightRaised,
    onSecondaryContainer = Paper,
    tertiary = Silver,
    onTertiary = Ink,
    tertiaryContainer = NightRaised,
    onTertiaryContainer = Paper,
    background = Night,
    onBackground = Paper,
    surface = NightSurface,
    onSurface = Paper,
    surfaceVariant = NightRaised,
    onSurfaceVariant = Silver,
    surfaceTint = Paper,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    error = Paper,
    onError = Ink,
    errorContainer = NightRaised,
    onErrorContainer = Paper,
    outline = Color(0xFF6B6B6B),
    outlineVariant = NightLine,
    scrim = Ink,
    surfaceBright = Color(0xFF262626),
    surfaceDim = Night,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightSurface,
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = Color(0xFF1F1F1F)
)

private val CampusSyncShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CampusSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = CampusSyncShapes,
        content = content
    )
}
