package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkStudioColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = StudioSlate950,
    primaryContainer = CyanMutedContainer,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = AmberWarm,
    onSecondary = StudioSlate950,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberLight,
    tertiary = EmeraldSafe,
    onTertiary = StudioSlate950,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFF6EE7B7),
    background = StudioSlate950,
    onBackground = StudioSlate50,
    surface = StudioSlate900,
    onSurface = StudioSlate50,
    surfaceVariant = StudioSlate850,
    onSurfaceVariant = StudioSlate400,
    surfaceContainerLowest = StudioSlate950,
    surfaceContainerLow = StudioSlate900,
    surfaceContainer = StudioSlate850,
    surfaceContainerHigh = StudioSlate800,
    surfaceContainerHighest = StudioSlate700,
    outline = StudioSlate700,
    outlineVariant = Color(0xFF233044),
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = Color(0xFFFDA4AF)
)

private val LightStudioColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF164E63),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = LightStudioBg,
    onBackground = LightStudioText,
    surface = LightStudioSurface,
    onSurface = LightStudioText,
    surfaceVariant = LightStudioSurfaceVariant,
    onSurfaceVariant = LightStudioTextMuted,
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White
)

val LokaFotoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LokaFotoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LokaFotoTypography,
        shapes = LokaFotoShapes,
        content = content
    )
}
