package com.pgoorts.tripplanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Teal300,
    onPrimary = Navy950,
    primaryContainer = Teal400,
    onPrimaryContainer = Navy950,
    secondary = Teal200,
    onSecondary = Navy950,
    secondaryContainer = Navy700,
    onSecondaryContainer = Teal100,
    tertiary = Teal200,
    onTertiary = Navy950,
    background = Navy900,
    onBackground = Grey100,
    surface = Navy800,
    onSurface = Grey100,
    surfaceVariant = Navy700,
    onSurfaceVariant = Grey300,
    outline = Grey700,
    outlineVariant = Navy600,
    error = ErrorRed,
    onError = White,
    inverseSurface = Grey100,
    inverseOnSurface = Navy900,
    inversePrimary = Teal400
)

private val LightColorScheme = lightColorScheme(
    primary = Teal400,
    onPrimary = White,
    primaryContainer = Teal100,
    onPrimaryContainer = Navy900,
    secondary = Teal300,
    onSecondary = White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Navy800,
    background = Grey100,
    onBackground = Navy900,
    surface = White,
    onSurface = Navy900,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey500,
    outline = Grey500,
    error = ErrorRed,
    onError = White
)

@Composable
fun TripPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TripPlannerTypography,
        content = content
    )
}
