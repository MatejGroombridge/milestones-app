package dev.matejgroombridge.milestones.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User-selectable light/dark mode. Persist this via DataStore if you expose
 *  a settings screen — by default we just follow the system. */
enum class ThemeMode { System, Light, Dark }

private val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val FallbackDarkScheme: ColorScheme = darkColorScheme()
private val FallbackLightScheme: ColorScheme = lightColorScheme()

/**
 * Root theme shared across every app in the family. Wallpaper-derived dynamic
 * colour on Android 12+, baseline Material 3 on older OS versions.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.System,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light  -> false
        ThemeMode.Dark   -> true
    }

    val context = LocalContext.current
    val baseScheme: ColorScheme = when {
        supportsDynamicColor && isDark  -> dynamicDarkColorScheme(context)
        supportsDynamicColor && !isDark -> dynamicLightColorScheme(context)
        isDark                          -> FallbackDarkScheme
        else                            -> FallbackLightScheme
    }

    // AMOLED: when in dark mode, force background / surface tones to true
    // black so OLED screens can switch those pixels off entirely. We keep
    // the dynamic accent / primary colours intact so the UI doesn't lose
    // its personality.
    val colorScheme: ColorScheme = if (amoled && isDark) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1C1C1C),
        )
    } else baseScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val barsAreLight = colorScheme.background.luminance() > 0.5f
            controller.isAppearanceLightStatusBars = barsAreLight
            controller.isAppearanceLightNavigationBars = barsAreLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
