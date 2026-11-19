package com.raf.capsuleos.presentation.setup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.presentation.R

/**
 * Parent category [Card] whose clickable region and Material 3 ripple cover the exact header
 * item size (full card when collapsed), while strictly excluding the expanded sub-item body so
 * interacting inside expanded content never triggers accidental card collapse.
 */
@Composable
fun ExpandableCategoryCard(
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
    expanded: Boolean = true,
    onToggleExpand: () -> Unit = {},
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    val cardShape = RoundedCornerShape(22.dp)
    val isBodyVisible = !collapsible || expanded

    Card(
        shape = cardShape,
        colors = cardColors,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .then(
                        if (collapsible) {
                            Modifier.clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onToggleExpand()
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(16.dp),
            ) {
                header()
            }

            AnimatedVisibility(
                visible = isBodyVisible,
                enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = 0.82f, stiffness = 380f)),
                exit = fadeOut(tween(150)) + shrinkVertically(spring(dampingRatio = 0.86f, stiffness = 400f)),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * Distinct Material 3 Expressive sub-section box grouping related customization controls inside
 * an [ExpandableCategoryCard] with tonal elevation, subtle outline, and optional icon/title header.
 */
@Composable
fun SettingsSubSectionBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

/**
 * Nested sub-item container box placed beneath a master toggle or expandable row to provide clean
 * visual indentation and tonal hierarchy instead of raw left-padding.
 */
@Composable
fun SettingsNestedInsetBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
        border = BorderStroke(
            width = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            content = content,
        )
    }
}

/**
 * Responsive [FlowRow] wrapper for sub-items inside an expanded menu card. Dynamically splits
 * sub-items into 2..[maxColumns] clean columns whenever width allows (using a 1.dp sub-pixel
 * rounding guard so items never prematurely wrap to a single column) and collapses to a single
 * full-width column on narrow screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveSubItemFlowRow(
    modifier: Modifier = Modifier,
    minItemWidth: Dp = 200.dp,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 6.dp,
    maxColumns: Int = 2,
    content: @Composable (itemModifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = ((maxWidth + horizontalSpacing) / (minItemWidth + horizontalSpacing))
            .toInt()
            .coerceIn(1, maxColumns.coerceAtLeast(1))
        val itemModifier = if (columns > 1) {
            val totalGap = horizontalSpacing * (columns - 1)
            val safeColumnWidth = ((maxWidth - totalGap - 1.dp) / columns.toFloat()).coerceAtLeast(1.dp)
            Modifier.width(safeColumnWidth)
        } else {
            Modifier.fillMaxWidth()
        }
        FlowRow(
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            content(itemModifier)
        }
    }
}

/**
 * Reusable Material 3 Expressive category card header featuring a leading rounded icon badge,
 * title, subtitle summary, optional dedicated-screen launcher button, and a smoothly animated
 * expand/collapse chevron icon (`0° <-> 180°` spring rotation).
 */
@Composable
fun ExpandableCategoryHeader(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    expanded: Boolean = true,
    collapsible: Boolean = true,
    onToggleExpand: () -> Unit = {},
    onOpenDedicatedScreen: (() -> Unit)? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "categoryChevronRotation",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }

        trailingBadge?.invoke()

        if (onOpenDedicatedScreen != null) {
            FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onOpenDedicatedScreen()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.desc_open_category_screen),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (collapsible) {
            FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onToggleExpand()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.desc_expand_collapse_category),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }
    }
}
