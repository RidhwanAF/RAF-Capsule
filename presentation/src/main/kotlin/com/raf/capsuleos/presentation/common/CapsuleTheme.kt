package com.raf.capsuleos.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.raf.capsuleos.domain.model.AppThemeMode

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0A0A0C),
    primaryContainer = Color(0xFF2B2B30),
    onPrimaryContainer = Color(0xFFF4F4F6),
    secondary = Color(0xFFD0D0D6),
    onSecondary = Color(0xFF141418),
    secondaryContainer = Color(0xFF242429),
    onSecondaryContainer = Color(0xFFE6E6EA),
    tertiary = Color(0xFFA6A6AE),
    onTertiary = Color(0xFF141418),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF09090B),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1D1D22),
    onSurfaceVariant = Color(0xFFA9A9B2),
    surfaceContainerLowest = Color(0xFF050507),
    surfaceContainerLow = Color(0xFF121215),
    surfaceContainer = Color(0xFF18181C),
    surfaceContainerHigh = Color(0xFF222227),
    surfaceContainerHighest = Color(0xFF2C2C32),
    outline = Color(0xFF45454E),
    outlineVariant = Color(0xFF2D2D34),
)

private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color(0xFF111114),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E3E8),
    onPrimaryContainer = Color(0xFF111114),
    secondary = Color(0xFF3A3A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECECF0),
    onSecondaryContainer = Color(0xFF18181C),
    tertiary = Color(0xFF5A5A64),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFFBFBFD),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFEAEAEF),
    onSurfaceVariant = Color(0xFF55555F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F7),
    surfaceContainer = Color(0xFFEEEEF2),
    surfaceContainerHigh = Color(0xFFE4E4EA),
    surfaceContainerHighest = Color(0xFFDADAE1),
    outline = Color(0xFFA0A0AA),
    outlineVariant = Color(0xFFCFCFD6),
)

/**
 * Resolves [darkTheme] polarity from [AppThemeMode], synchronizes system status and navigation
 * bar icon contrast via [WindowCompat.getInsetsController], and wraps content inside
 * [MaterialExpressiveTheme] using Dynamic Color (default) or high-contrast M3 Monochrome palette.
 */
@Composable
fun CapsuleTheme(
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val darkTheme = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme: ColorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> MonochromeDarkColorScheme
        else -> MonochromeLightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        Surface(
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
