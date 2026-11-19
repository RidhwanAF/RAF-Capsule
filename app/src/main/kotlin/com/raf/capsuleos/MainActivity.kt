package com.raf.capsuleos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.presentation.common.CapsuleTheme
import com.raf.capsuleos.presentation.navigation.CapsuleNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main AppCompat activity hosting [CapsuleNavHost], synced AppCompat per-app locales,
 * and reactive M3 Expressive / Dynamic Color theming with synchronized system bar appearance.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: CapsuleSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by settingsRepository.observePreferences()
                .collectAsStateWithLifecycle(initialValue = CapsulePreferences())

            CapsuleTheme(
                appThemeMode = preferences.appThemeMode,
                useDynamicColor = preferences.useDynamicColor,
            ) {
                CapsuleNavHost()
            }
        }
    }
}
