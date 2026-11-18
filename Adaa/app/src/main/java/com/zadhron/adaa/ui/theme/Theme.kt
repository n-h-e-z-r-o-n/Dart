package com.zadhron.adaa.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF35232F),
    onPrimary = Color(0xFFFFFBF6),
    primaryContainer = Color(0xFFF0E2E8),
    onPrimaryContainer = Color(0xFF35232F),
    secondary = Color(0xFF75636C),
    secondaryContainer = Color(0xFFEDE4DE),
    onSecondaryContainer = Color(0xFF35232F),
    tertiary = TertiaryBronze,
    tertiaryContainer = TertiaryContainerBronze,
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFDFC),
    onSurface = Color(0xFF30252C),
    onBackground = Color(0xFF30252C),
    surfaceVariant = Color(0xFFF0EBE7),
    onSurfaceVariant = Color(0xFF756B70),
    outline = Color(0xFFB0A2A8),
    outlineVariant = Color(0xFFE5DDDA)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8CFAB),
    onPrimary = Color(0xFF30212B),
    primaryContainer = PrimaryContainerDark,
    secondary = Color(0xFFD8C3CD),
    secondaryContainer = Color(0xFF48333F),
    onSecondaryContainer = Color(0xFFF7EFF2),
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurface = Color(0xFFF7EFF2),
    onBackground = Color(0xFFF7EFF2),
    onSurfaceVariant = Color(0xFFD0C1C8),
    outline = OutlineDark,
    outlineVariant = Color(0xFF4A3942)
)

@Composable
fun AdaaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
