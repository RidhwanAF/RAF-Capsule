package com.raf.capsuleos.presentation.appsettings

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AppThemeMode
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.setup.components.AdaptiveSubItemFlowRow

private val SETTINGS_MAIN_CARD_MAX_WIDTH = 1040.dp

private data class LanguageOption(
    val tag: String,
    val englishNameRes: Int,
    val nativeNameRes: Int,
)

private val ALPHABETICAL_LANGUAGE_OPTIONS = listOf(
    LanguageOption(
        tag = "ar",
        englishNameRes = R.string.label_language_arabic,
        nativeNameRes = R.string.label_language_native_arabic,
    ),
    LanguageOption(
        tag = "zh-CN",
        englishNameRes = R.string.label_language_chinese,
        nativeNameRes = R.string.label_language_native_chinese,
    ),
    LanguageOption(
        tag = "en",
        englishNameRes = R.string.label_language_english,
        nativeNameRes = R.string.label_language_native_english,
    ),
    LanguageOption(
        tag = "fr",
        englishNameRes = R.string.label_language_french,
        nativeNameRes = R.string.label_language_native_french,
    ),
    LanguageOption(
        tag = "de",
        englishNameRes = R.string.label_language_german,
        nativeNameRes = R.string.label_language_native_german,
    ),
    LanguageOption(
        tag = "hi",
        englishNameRes = R.string.label_language_hindi,
        nativeNameRes = R.string.label_language_native_hindi,
    ),
    LanguageOption(
        tag = "id",
        englishNameRes = R.string.label_language_indonesian,
        nativeNameRes = R.string.label_language_native_indonesian,
    ),
    LanguageOption(
        tag = "ja",
        englishNameRes = R.string.label_language_japanese,
        nativeNameRes = R.string.label_language_native_japanese,
    ),
    LanguageOption(
        tag = "ko",
        englishNameRes = R.string.label_language_korean,
        nativeNameRes = R.string.label_language_native_korean,
    ),
    LanguageOption(
        tag = "pt",
        englishNameRes = R.string.label_language_portuguese,
        nativeNameRes = R.string.label_language_native_portuguese,
    ),
    LanguageOption(
        tag = "ru",
        englishNameRes = R.string.label_language_russian,
        nativeNameRes = R.string.label_language_native_russian,
    ),
    LanguageOption(
        tag = "es",
        englishNameRes = R.string.label_language_spanish,
        nativeNameRes = R.string.label_language_native_spanish,
    ),
    LanguageOption(
        tag = "th",
        englishNameRes = R.string.label_language_thai,
        nativeNameRes = R.string.label_language_native_thai,
    ),
    LanguageOption(
        tag = "vi",
        englishNameRes = R.string.label_language_vietnamese,
        nativeNameRes = R.string.label_language_native_vietnamese,
    ),
)

/**
 * Stateless App Settings screen for Theme polarity, Dynamic Color / M3 Expressive Monochrome,
 * and searchable AppCompatDelegate per-app language selection synced with Android System Settings.
 * Uses edge-to-edge non-clipping Scaffold padding, max width per main card, and [AdaptiveSubItemFlowRow]
 * for language sub-items inside the Language card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsContent(
    modifier: Modifier = Modifier,
    appThemeMode: AppThemeMode,
    useDynamicColor: Boolean,
    activeLocaleTag: String,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectLanguageTag: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    var languageSearchQuery by rememberSaveable { mutableStateOf("") }
    val systemLabel = stringResource(R.string.label_language_system)
    val resolvedLanguages = ALPHABETICAL_LANGUAGE_OPTIONS.map { option ->
        Triple(
            option.tag,
            stringResource(option.englishNameRes),
            stringResource(option.nativeNameRes),
        )
    }.sortedBy { it.second.lowercase() }

    val trimmedQuery = languageSearchQuery.trim().lowercase()
    val showSystemOption = remember(trimmedQuery, systemLabel) {
        trimmedQuery.isEmpty() ||
            systemLabel.lowercase().contains(trimmedQuery) ||
            "system".contains(trimmedQuery) ||
            "default".contains(trimmedQuery)
    }
    val filteredLanguages = remember(trimmedQuery, resolvedLanguages) {
        if (trimmedQuery.isEmpty()) {
            resolvedLanguages
        } else {
            resolvedLanguages.filter { (tag, englishName, nativeName) ->
                englishName.lowercase().contains(trimmedQuery) ||
                    nativeName.lowercase().contains(trimmedQuery) ||
                    tag.lowercase().contains(trimmedQuery)
            }
        }
    }

    val sharedContainerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "nav_app_settings_container"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.desc_navigate_back),
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = stringResource(R.string.title_app_settings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            text = stringResource(R.string.msg_app_settings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                },
            )
        },
        modifier = modifier
            .fillMaxSize()
            .then(sharedContainerModifier),
    ) { innerPadding ->
        val mainCardModifier = Modifier
            .widthIn(max = SETTINGS_MAIN_CARD_MAX_WIDTH)
            .fillMaxWidth()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 20.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 20.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 20.dp,
                ),
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = mainCardModifier,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.title_theme_category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(),
                        )
                    }

                    Text(
                        text = stringResource(R.string.label_theme_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )

                    val modes = listOf(
                        AppThemeMode.SYSTEM to R.string.label_theme_system,
                        AppThemeMode.LIGHT to R.string.label_theme_light,
                        AppThemeMode.DARK to R.string.label_theme_dark,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, (mode, labelRes) ->
                            SegmentedButton(
                                selected = appThemeMode == mode,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelectThemeMode(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = modes.size,
                                ),
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(),
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.label_dynamic_color),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                            Text(
                                text = stringResource(R.string.msg_dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onToggleDynamicColor(checked)
                            },
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = mainCardModifier,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.title_language_category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(),
                        )
                    }

                    Text(
                        text = stringResource(R.string.msg_language_sync_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.hint_search_languages),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (languageSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        languageSearchQuery = ""
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.desc_clear_search),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (showSystemOption) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (activeLocaleTag.isEmpty()) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LanguageOptionRow(
                                primaryLabel = systemLabel,
                                secondaryLabel = null,
                                selected = activeLocaleTag.isEmpty(),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelectLanguageTag("")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (filteredLanguages.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AdaptiveSubItemFlowRow(
                                minItemWidth = 200.dp,
                                horizontalSpacing = 8.dp,
                                verticalSpacing = 2.dp,
                                modifier = Modifier.padding(4.dp),
                            ) { itemMod ->
                                filteredLanguages.forEach { (tag, englishName, nativeName) ->
                                    LanguageOptionRow(
                                        primaryLabel = englishName,
                                        secondaryLabel = nativeName.takeIf { it != englishName },
                                        selected = activeLocaleTag == tag,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            onSelectLanguageTag(tag)
                                        },
                                        modifier = itemMod,
                                    )
                                }
                            }
                        }
                    } else if (!showSystemOption) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.label_no_languages_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(
    modifier: Modifier = Modifier,
    primaryLabel: String,
    secondaryLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            if (!secondaryLabel.isNullOrBlank()) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }
    }
}
