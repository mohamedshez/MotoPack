package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

enum class HudThemeMode {
    AUTO,
    DAYLIGHT,
    NIGHT_HUD
}

private val NightHudColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightSurfaceVariant,
    onPrimaryContainer = NightPrimary,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    secondaryContainer = NightSurfaceVariant,
    onSecondaryContainer = NightSecondary,
    tertiary = NightTertiary,
    onTertiary = NightOnTertiary,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
    error = NightError
)

private val DaylightColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = DayOnPrimary,
    primaryContainer = DaySurfaceVariant,
    onPrimaryContainer = DayPrimary,
    secondary = DaySecondary,
    onSecondary = DayOnSecondary,
    secondaryContainer = DaySurfaceVariant,
    onSecondaryContainer = DaySecondary,
    tertiary = DayTertiary,
    onTertiary = DayOnTertiary,
    background = DayBackground,
    onBackground = DayOnBackground,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayOnSurfaceVariant,
    outline = DayOutline,
    error = DayError
)

val MotoPackShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MotoPackTheme(
    hudThemeMode: HudThemeMode = HudThemeMode.AUTO,
    isLowBattery: Boolean = false,
    isBatteryEfficiencyMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val useNightHud = when (hudThemeMode) {
        HudThemeMode.NIGHT_HUD -> true
        HudThemeMode.DAYLIGHT -> false
        HudThemeMode.AUTO -> isSystemInDarkTheme() || isLowBattery || isBatteryEfficiencyMode
    }

    MaterialTheme(
        colorScheme = if (useNightHud) NightHudColorScheme else DaylightColorScheme,
        typography = MotoPackTypography,
        shapes = MotoPackShapes,
        content = content
    )
}
