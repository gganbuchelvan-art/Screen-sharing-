package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = OnCyanDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanPulse,
    secondary = CobaltBeam,
    onSecondary = Color.White,
    secondaryContainer = CobaltContainerDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = AmberBroadcast,
    onTertiary = Color.Black,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberBroadcast,
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = CoralStop,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralStop
)

private val LightColorScheme = lightColorScheme(
    primary = CyanLightPrimary,
    onPrimary = Color.White,
    primaryContainer = CyanLightContainer,
    onPrimaryContainer = CyanLightPrimary,
    secondary = CobaltLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = CobaltLightContainer,
    onSecondaryContainer = CobaltLightSecondary,
    tertiary = AmberBroadcast,
    onTertiary = Color.Black,
    background = DaylightCanvas,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = DaylightOutline,
    error = CoralStop,
    onError = Color.White
)

val MirraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MirraCastTheme(
    darkTheme: Boolean = true, // Studio dark broadcast aesthetic by default; obeys dynamicColor option
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = darkTheme || systemDark
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MirraTypography,
        shapes = MirraShapes,
        content = content
    )
}
