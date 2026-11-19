package com.raf.capsuleos.presentation.appsettings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.capsuleos.presentation.setup.SetupViewModel

/**
 * Stateful Navigation 3 App Settings destination managing Theme Polarity, Dynamic Color /
 * M3 Expressive Monochrome toggle, and Per-App Language synced directly with Android System Settings.
 */
@Composable
fun AppSettingsScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeLocaleTag by remember { mutableStateOf(resolveCurrentLocaleTag()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activeLocaleTag = resolveCurrentLocaleTag()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AppSettingsContent(
        appThemeMode = uiState.preferences.appThemeMode,
        useDynamicColor = uiState.preferences.useDynamicColor,
        activeLocaleTag = activeLocaleTag,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onSelectThemeMode = { mode ->
            viewModel.updatePreferences { it.copy(appThemeMode = mode) }
        },
        onToggleDynamicColor = { enabled ->
            viewModel.updatePreferences { it.copy(useDynamicColor = enabled) }
        },
        onSelectLanguageTag = { tag ->
            val localeList = if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
            activeLocaleTag = resolveCurrentLocaleTag()
        },
        modifier = modifier,
    )
}

private fun resolveCurrentLocaleTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return ""
    val rawLang = locales.get(0)?.language.orEmpty().lowercase()
    return when (rawLang) {
        "ar" -> "ar"
        "de" -> "de"
        "en" -> "en"
        "es" -> "es"
        "fr" -> "fr"
        "hi" -> "hi"
        "in", "id" -> "id"
        "ja" -> "ja"
        "ko" -> "ko"
        "pt" -> "pt"
        "ru" -> "ru"
        "th" -> "th"
        "vi" -> "vi"
        "zh" -> "zh-CN"
        else -> ""
    }
}
