package com.raf.capsuleos.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/**
 * Convenience Material 3 Expressive press-morphing [ButtonShapes] factory transitioning smoothly
 * between [restRadius] and [pressedRadius].
 */
@Composable
fun capsuleExpressiveButtonShapes(
    restRadius: Dp = 24.dp,
    pressedRadius: Dp = 10.dp,
): ButtonShapes = ButtonDefaults.shapes(
    shape = RoundedCornerShape(restRadius),
    pressedShape = RoundedCornerShape(pressedRadius),
)

/**
 * Convenience Material 3 Expressive press-morphing [IconButtonShapes] factory transitioning
 * between [restRadius] and [pressedRadius].
 */
@Composable
fun capsuleExpressiveIconButtonShapes(
    restRadius: Dp = 24.dp,
    pressedRadius: Dp = 10.dp,
): IconButtonShapes = IconButtonDefaults.shapes(
    shape = RoundedCornerShape(restRadius),
    pressedShape = RoundedCornerShape(pressedRadius),
)

/**
 * Lightweight M3 Expressive clickable surface for compact capsule tiles and pills that spring-morphs
 * corner geometry ([restCornerDp] -> [pressedCornerDp]) and scale on press without enforcing chunky
 * full-screen touch-target padding inside tight camera overlays.
 */
@Composable
fun CapsuleExpressiveSurface(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    restCornerDp: Float = 24f,
    pressedCornerDp: Float = 10f,
    pressedScale: Float = 0.95f,
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 920f),
        label = "capsuleExpressivePress",
    )
    val liveCornerDp = restCornerDp + (pressedCornerDp - restCornerDp) * pressProgress
    val liveShape = RoundedCornerShape(liveCornerDp.coerceAtLeast(2f).dp)
    val liveScale = 1f - (1f - pressedScale) * pressProgress

    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .graphicsLayer {
                scaleX = liveScale
                scaleY = liveScale
            }
            .clip(liveShape)
            .background(backgroundColor)
            .then(if (border != null) Modifier.border(border, liveShape) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

/**
 * M3 Expressive shape that smoothly morphs a 7-sided cookie ([MaterialShapes.Cookie7Sided]) toward
 * a circle ([MaterialShapes.Circle]) at the given [progress] (`0f..1f`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphingCookieShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    private val workPath = Path()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        workPath.rewind()
        morph.toPath(progress = progress.coerceIn(0f, 1f), path = workPath)
        val matrix = Matrix().apply { scale(x = size.width, y = size.height) }
        workPath.transform(matrix)
        workPath.translate(size.center - workPath.getBounds().center)
        return Outline.Generic(workPath)
    }
}

/**
 * Remembers an initialized [Morph] between [MaterialShapes.Cookie7Sided] and [MaterialShapes.Circle].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberCookieToCircleMorph(): Morph = remember {
    Morph(start = MaterialShapes.Cookie7Sided, end = MaterialShapes.Circle)
}
