package com.raf.capsuleos.presentation.setup.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.raf.capsuleos.domain.model.CapsuleGestureAction
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CardGestureConfig
import com.raf.capsuleos.domain.model.GestureActionBinding
import com.raf.capsuleos.domain.model.GestureCardTarget
import com.raf.capsuleos.presentation.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

private sealed interface AppPickerRequest {
    data object GlobalDoubleTap : AppPickerRequest
    data object GlobalLongHold : AppPickerRequest
    data class CardDoubleTap(val target: GestureCardTarget) : AppPickerRequest
    data class CardLongHold(val target: GestureCardTarget) : AppPickerRequest
}

/**
 * Category card configuring customizable Global Double-Tap & Long-Hold gestures as well as
 * per-card overrides ([GestureCardTarget]) including an installed App Picker for launching custom apps.
 */
@Composable
fun GestureSettingsCard(
    modifier: Modifier = Modifier,
    preferences: CapsulePreferences,
    collapsible: Boolean = true,
    initiallyExpanded: Boolean = true,
    onOpenDedicatedScreen: (() -> Unit)? = null,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var appPickerRequest by remember { mutableStateOf<AppPickerRequest?>(null) }
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var perCardExpanded by rememberSaveable { mutableStateOf(!collapsible) }
    val isBodyVisible = !collapsible || expanded
    val perCardChevronRotation by animateFloatAsState(
        targetValue = if (perCardExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "perCardChevron",
    )

    val resolveAppLabel: (String?) -> String? = remember(context) {
        { pkg ->
            pkg?.takeIf { it.isNotBlank() }?.let { packageName ->
                runCatching {
                    val info = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(packageName)
            }
        }
    }

    ExpandableCategoryCard(
        collapsible = collapsible,
        expanded = isBodyVisible,
        onToggleExpand = { expanded = !expanded },
        header = {
            ExpandableCategoryHeader(
                icon = Icons.Rounded.TouchApp,
                title = stringResource(R.string.title_gestures_category),
                subtitle = stringResource(R.string.msg_gestures_subtitle),
                expanded = isBodyVisible,
                collapsible = collapsible,
                onToggleExpand = { expanded = !expanded },
                onOpenDedicatedScreen = onOpenDedicatedScreen,
            )
        },
        modifier = modifier,
    ) {

        SettingsSubSectionBox(
            title = stringResource(R.string.section_global_gestures),
            icon = Icons.Rounded.TouchApp,
        ) {
            AdaptiveSubItemFlowRow { itemMod ->
                GestureSelectorRow(
                    label = stringResource(R.string.label_global_double_tap),
                    selectedAction = preferences.globalDoubleTapAction,
                    customAppLabel = resolveAppLabel(preferences.globalDoubleTapAppPackage),
                    allowInherit = false,
                    onSelectAction = { action ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (action == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                            onUpdatePreferences { it.copy(globalDoubleTapAction = action) }
                            appPickerRequest = AppPickerRequest.GlobalDoubleTap
                        } else if (action != null) {
                            onUpdatePreferences { it.copy(globalDoubleTapAction = action) }
                        }
                    },
                    onPickApp = { appPickerRequest = AppPickerRequest.GlobalDoubleTap },
                    modifier = itemMod,
                )

                GestureSelectorRow(
                    label = stringResource(R.string.label_global_long_hold),
                    selectedAction = preferences.globalLongHoldAction,
                    customAppLabel = resolveAppLabel(preferences.globalLongHoldAppPackage),
                    allowInherit = false,
                    onSelectAction = { action ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (action == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                            onUpdatePreferences { it.copy(globalLongHoldAction = action) }
                            appPickerRequest = AppPickerRequest.GlobalLongHold
                        } else if (action != null) {
                            onUpdatePreferences { it.copy(globalLongHoldAction = action) }
                        }
                    },
                    onPickApp = { appPickerRequest = AppPickerRequest.GlobalLongHold },
                    modifier = itemMod,
                )
            }
        }

        SettingsSubSectionBox {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        perCardExpanded = !perCardExpanded
                    }
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_per_card_gestures),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(),
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.desc_expand_collapse_category),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = perCardChevronRotation },
                )
            }

            AnimatedVisibility(
                visible = perCardExpanded,
                enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.82f, stiffness = 380f)),
                exit = fadeOut(tween(140)) + shrinkVertically(spring(dampingRatio = 0.86f, stiffness = 400f)),
            ) {
                AdaptiveSubItemFlowRow(
                    verticalSpacing = 8.dp,
                    modifier = Modifier.padding(top = 6.dp),
                ) { itemMod ->
                    GestureCardTarget.entries.forEach { target ->
                        val cfg = preferences.cardGestures[target] ?: CardGestureConfig()
                        SettingsNestedInsetBox(modifier = itemMod.padding(top = 0.dp)) {
                            Text(
                                text = cardTargetLabel(target),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            GestureSelectorRow(
                                label = stringResource(R.string.label_double_tap),
                                selectedAction = cfg.doubleTap.action,
                                customAppLabel = resolveAppLabel(cfg.doubleTap.customAppPackage),
                                allowInherit = true,
                                onSelectAction = { action ->
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    updateCardConfig(preferences, target, onUpdatePreferences) { cardCfg ->
                                        cardCfg.copy(
                                            doubleTap = GestureActionBinding(
                                                action = action,
                                                customAppPackage = if (action == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                                                    cardCfg.doubleTap.customAppPackage
                                                } else {
                                                    null
                                                },
                                            ),
                                        )
                                    }
                                    if (action == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                                        appPickerRequest = AppPickerRequest.CardDoubleTap(target)
                                    }
                                },
                                onPickApp = { appPickerRequest = AppPickerRequest.CardDoubleTap(target) },
                            )
                            GestureSelectorRow(
                                label = stringResource(R.string.label_long_hold),
                                selectedAction = cfg.longHold.action,
                                customAppLabel = resolveAppLabel(cfg.longHold.customAppPackage),
                                allowInherit = true,
                                onSelectAction = { action ->
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    updateCardConfig(preferences, target, onUpdatePreferences) { cardCfg ->
                                        cardCfg.copy(
                                            longHold = GestureActionBinding(
                                                action = action,
                                                customAppPackage = if (action == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                                                    cardCfg.longHold.customAppPackage
                                                } else {
                                                    null
                                                },
                                            ),
                                        )
                                    }
                                    if (action == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                                        appPickerRequest = AppPickerRequest.CardLongHold(target)
                                    }
                                },
                                onPickApp = { appPickerRequest = AppPickerRequest.CardLongHold(target) },
                            )
                        }
                    }
                }
            }
        }
    }

    val activeRequest = appPickerRequest
    if (activeRequest != null) {
        InstalledAppPickerDialog(
            onDismiss = { appPickerRequest = null },
            onSelectPackage = { pkg ->
                when (activeRequest) {
                    AppPickerRequest.GlobalDoubleTap -> onUpdatePreferences {
                        it.copy(
                            globalDoubleTapAction = CapsuleGestureAction.LAUNCH_CUSTOM_APP,
                            globalDoubleTapAppPackage = pkg,
                        )
                    }
                    AppPickerRequest.GlobalLongHold -> onUpdatePreferences {
                        it.copy(
                            globalLongHoldAction = CapsuleGestureAction.LAUNCH_CUSTOM_APP,
                            globalLongHoldAppPackage = pkg,
                        )
                    }
                    is AppPickerRequest.CardDoubleTap -> updateCardConfig(preferences, activeRequest.target, onUpdatePreferences) { cfg ->
                        cfg.copy(
                            doubleTap = GestureActionBinding(
                                action = CapsuleGestureAction.LAUNCH_CUSTOM_APP,
                                customAppPackage = pkg,
                            ),
                        )
                    }
                    is AppPickerRequest.CardLongHold -> updateCardConfig(preferences, activeRequest.target, onUpdatePreferences) { cfg ->
                        cfg.copy(
                            longHold = GestureActionBinding(
                                action = CapsuleGestureAction.LAUNCH_CUSTOM_APP,
                                customAppPackage = pkg,
                            ),
                        )
                    }
                }
                appPickerRequest = null
            },
        )
    }
}

private fun updateCardConfig(
    current: CapsulePreferences,
    target: GestureCardTarget,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    transform: (CardGestureConfig) -> CardGestureConfig,
) {
    onUpdatePreferences { prefs ->
        val mutable = prefs.cardGestures.toMutableMap()
        val updated = transform(mutable[target] ?: CardGestureConfig())
        if (updated.doubleTap.action == null && updated.longHold.action == null) {
            mutable.remove(target)
        } else {
            mutable[target] = updated
        }
        prefs.copy(cardGestures = mutable)
    }
}

@Composable
private fun GestureSelectorRow(
    modifier: Modifier = Modifier,
    label: String,
    selectedAction: CapsuleGestureAction?,
    customAppLabel: String?,
    allowInherit: Boolean,
    onSelectAction: (CapsuleGestureAction?) -> Unit,
    onPickApp: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .basicMarquee(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedAction == CapsuleGestureAction.LAUNCH_CUSTOM_APP) {
                FilledTonalButton(
                    onClick = onPickApp,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customAppLabel ?: stringResource(R.string.title_pick_app),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            Box {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Text(
                        text = gestureActionLabel(selectedAction),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (allowInherit) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.label_gesture_inherit)) },
                            onClick = {
                                menuExpanded = false
                                onSelectAction(null)
                            },
                        )
                    }
                    CapsuleGestureAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(text = gestureActionLabel(action)) },
                            onClick = {
                                menuExpanded = false
                                onSelectAction(action)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppPickerDialog(
    onDismiss: () -> Unit,
    onSelectPackage: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }

    LaunchedEffect(context) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcherIntent, 0)
                .distinctBy { it.activityInfo.packageName }
                .map { info ->
                    val pkg = info.activityInfo.packageName
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                    val bmp = runCatching {
                        info.loadIcon(pm)
                            .toBitmap(width = 64, height = 64, config = Bitmap.Config.ARGB_8888)
                            .asImageBitmap()
                    }.getOrNull()
                    InstalledAppItem(packageName = pkg, label = label, icon = bmp)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val filtered = remember(apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.title_pick_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(text = stringResource(R.string.hint_search_apps)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(items = filtered, key = { it.packageName }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectPackage(item.packageName) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            if (item.icon != null) {
                                Image(
                                    bitmap = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        },
    )
}

@Composable
private fun cardTargetLabel(target: GestureCardTarget): String = stringResource(
    when (target) {
        GestureCardTarget.IDLE_RING -> R.string.label_card_idle_ring
        GestureCardTarget.MEDIA -> R.string.label_card_media
        GestureCardTarget.FLASHLIGHT -> R.string.label_card_flashlight
        GestureCardTarget.NOTIFICATION -> R.string.label_card_notification
        GestureCardTarget.VOLUME -> R.string.label_card_volume
    },
)

@Composable
private fun gestureActionLabel(action: CapsuleGestureAction?): String = stringResource(
    when (action) {
        null -> R.string.label_gesture_inherit
        CapsuleGestureAction.NONE -> R.string.label_gesture_none
        CapsuleGestureAction.EXPAND_CARD -> R.string.label_gesture_expand
        CapsuleGestureAction.COLLAPSE_CARD -> R.string.label_gesture_collapse
        CapsuleGestureAction.LOCK_SCREEN -> R.string.label_gesture_lock_screen
        CapsuleGestureAction.TOGGLE_FLASHLIGHT -> R.string.label_gesture_toggle_torch
        CapsuleGestureAction.TOGGLE_MEDIA_PLAY_PAUSE -> R.string.label_gesture_toggle_media
        CapsuleGestureAction.PEEK_BATTERY -> R.string.label_gesture_peek_battery
        CapsuleGestureAction.LAUNCH_CUSTOM_APP -> R.string.label_gesture_launch_app
    },
)
