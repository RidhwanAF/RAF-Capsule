package com.raf.capsuleos.presentation.shader

import android.graphics.RuntimeShader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.MediaAnimationColorMode
import com.raf.capsuleos.presentation.capsule.ui.components.capsuleBatteryAccentColor
import kotlinx.coroutines.isActive

/**
 * AGSL shader rendering the Pixel / Gemini style outer phone-frame expansion aura and inward-flowing
 * neural light filaments when a card expands to State 3.
 *
 * - Originates from the **outer top, left, and right edges of the phone frame** and radiates inward
 *   toward the center expanded card (with transparent center).
 * - Extends taller than the expanded card and attenuates smoothly from top to a 100% transparent bottom
 *   gradient (no bottom border).
 * - Fades out automatically a few seconds after expansion (`isNeuralPulseActive`).
 */
object OuterExpansionAuraShader {

    private const val SHADER_SRC = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uCornerRadius;
        uniform float uSpreadPx;
        uniform float uIntensity;
        uniform float4 uColorA;
        uniform float4 uColorB;

        float2 hash22(float2 p) {
            float3 p3 = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.xx + p3.yz) * p3.zy);
        }

        half4 main(float2 fragCoord) {
            if (uIntensity <= 0.004 || uResolution.x <= 1.0 || uResolution.y <= 1.0) {
                return half4(0.0);
            }

            float w = uResolution.x;
            float h = uResolution.y;
            float u = clamp(fragCoord.x / w, 0.0, 1.0);
            float v = clamp(fragCoord.y / h, 0.0, 1.0);

            // Top-to-bottom vertical fade: crisp at top, 100% transparent before bottom edge
            float vTopBoost = 1.0 - smoothstep(0.0, 0.96, v);
            float verticalMask = pow(clamp(vTopBoost, 0.0, 1.0), 1.45);
            if (verticalMask <= 0.002) {
                return half4(0.0);
            }

            // Follow physical rounded phone frame contour using a 2D rounded-box SDF
            float rTop = clamp(uCornerRadius, 8.0, min(w, h) * 0.48);
            float rBot = min(rTop * 0.85, h * 0.35);
            float2 halfSize = float2(w * 0.5, h * 0.5);
            float2 pBox = fragCoord - halfSize;
            float rActive = (pBox.y < 0.0) ? rTop : rBot;
            float2 q = abs(pBox) - halfSize + float2(rActive, rActive);
            float sd = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rActive;

            // Clip pixels outside the rounded screen corner arc so the outer aura matches the rounded phone shape
            if (sd > 1.5) {
                return half4(0.0);
            }
            float outerCornerMask = smoothstep(1.5, -0.5, sd);
            float dOuterEdge = max(-sd, 0.0);

            float spread = max(uSpreadPx, 28.0);

            // Rich, thicker inward wave modulation flowing from rounded phone frame edges -> center transparent
            float sideSign = (u < 0.5) ? 1.0 : -1.0;
            float wave1 = sin(v * 8.5 - uTime * 3.2 + u * 4.0 * sideSign);
            float wave2 = cos(v * 13.5 + uTime * 2.5 - u * 6.0);
            float topWave = sin(u * 9.5 + uTime * 3.0) * cos(u * 15.0 - uTime * 2.2);
            float dynamicReach = spread * (0.86 + 0.24 * (0.55 * wave1 + 0.45 * wave2) + 0.14 * topWave);

            // Thicker inward gradient reaching toward center while keeping center transparent
            float inwardNorm = clamp(dOuterEdge / max(dynamicReach, 12.0), 0.0, 1.0);
            float auraBody = pow(1.0 - inwardNorm, 1.95) * 0.64;
            float bezelRim = exp(-dOuterEdge / max(spread * 0.14, 3.0)) * 0.72;
            float inwardWaveFrontPos = fract(uTime * 0.62 + v * 0.48) * (spread * 0.82);
            float inwardWaveRibbon = exp(-abs(dOuterEdge - inwardWaveFrontPos) / max(spread * 0.14, 3.5))
                * (1.0 - inwardNorm) * 0.45;
            float waveLum = auraBody + bezelRim + inwardWaveRibbon;

            // Crisp neural stipple dot effect strictly clipped inside the thicker rounded frame wave
            float dotsLum = 0.0;
            float clippedWaveMask = smoothstep(0.04, 0.30, waveLum)
                * smoothstep(dynamicReach, dynamicReach * 0.12, dOuterEdge);
            if (clippedWaveMask > 0.01) {
                float spacing = 9.0;
                float2 gridUv = fragCoord / spacing;
                float rowShift = mod(floor(gridUv.y), 2.0) * 0.5;
                float2 shiftedUv = float2(gridUv.x + rowShift, gridUv.y);
                float2 cellId = floor(shiftedUv);
                float2 cellLocal = (fract(shiftedUv) - 0.5) * spacing;
                float dToCenter = length(cellLocal);

                float hash = fract(sin(dot(cellId, float2(12.9898, 78.233))) * 43758.5453);
                float densityMask = step(0.14, hash);
                float twinkle = pow(0.5 + 0.5 * sin(uTime * 6.8 + hash * 6.2831), 2.0);
                float dotRadius = 1.65;
                float dotMask = smoothstep(dotRadius, dotRadius * 0.18, dToCenter);
                dotsLum = dotMask * densityMask * clippedWaveMask * (0.42 + 0.58 * twinkle) * 0.92;
            }

            // Dynamic 2-tone color mix between ColorA (Primary / Album Primary) and ColorB (Tertiary / Album Secondary)
            float colorMix = clamp(
                0.5 + 0.42 * sin(u * 4.2 + v * 3.5 + uTime * 2.3) + 0.18 * cos(v * 7.0 - uTime * 1.9),
                0.0,
                1.0
            );
            float3 baseCol = mix(uColorA.rgb, uColorB.rgb, colorMix);
            float3 dotCol = mix(baseCol, float3(0.97, 0.99, 1.0), 0.72);
            float envMask = verticalMask * outerCornerMask * uIntensity;
            float rawWaveAlpha = clamp(waveLum * envMask, 0.0, 0.80);
            float rawDotsAlpha = clamp(dotsLum * envMask, 0.0, 0.88);
            float combinedAlpha = clamp(rawWaveAlpha + rawDotsAlpha, 0.0, 0.88);
            float3 outRgb = baseCol * rawWaveAlpha + dotCol * rawDotsAlpha;

            return half4(half3(min(outRgb, float3(combinedAlpha))), half(combinedAlpha));
        }
    """

    fun create(): RuntimeShader = RuntimeShader(SHADER_SRC)

    fun updateUniforms(
        shader: RuntimeShader,
        widthPx: Float,
        heightPx: Float,
        timeSeconds: Float,
        cornerRadiusPx: Float,
        spreadPx: Float,
        intensity: Float,
        colorA: Color,
        colorB: Color,
    ) {
        shader.setFloatUniform("uResolution", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform("uCornerRadius", cornerRadiusPx.coerceAtLeast(0f))
        shader.setFloatUniform("uSpreadPx", spreadPx.coerceAtLeast(16f))
        shader.setFloatUniform("uIntensity", intensity.coerceIn(0f, 1f))
        shader.setFloatUniform("uColorA", colorA.red, colorA.green, colorA.blue, colorA.alpha)
        shader.setFloatUniform("uColorB", colorB.red, colorB.green, colorB.blue, colorB.alpha)
    }
}

/**
 * Resolves the primary/secondary outer aura colors for [state]:
 * - [CapsuleContent.Media]: Material 3 [primaryColor]/[tertiaryColor] when [MediaAnimationColorMode.PRIMARY_COLOR]
 *   is selected, or active session's album primary & secondary palette colors by default.
 * - [CapsuleContent.Charging]: battery-level accent color.
 * - All other expanded cards: Material 3 [primaryColor] and [tertiaryColor].
 */
fun resolveOuterExpansionAuraColors(
    state: CapsuleUiState,
    primaryColor: Color,
    tertiaryColor: Color,
): Pair<Color, Color> = when (val content = state.content) {
    is CapsuleContent.Media -> {
        if (state.preferences.mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR) {
            primaryColor to tertiaryColor
        } else {
            val colorSession = state.mediaSessions.firstOrNull { it.isPlaying }
                ?: state.mediaSessions.getOrNull(state.selectedMediaIndex)
                ?: content
            Color(colorSession.primaryColorArgb.toLong() and 0xFFFFFFFFL) to
                Color(colorSession.secondaryColorArgb.toLong() and 0xFFFFFFFFL)
        }
    }
    is CapsuleContent.Charging -> {
        val batteryCol = capsuleBatteryAccentColor(
            batteryPercent = content.batteryPercent,
            isCharging = true,
            primaryColor = primaryColor,
        )
        batteryCol to batteryCol.copy(alpha = 0.78f)
    }
    else -> primaryColor to tertiaryColor
}

/**
 * Determines whether the outer phone-frame expansion aura should currently render for [state]:
 * - Respects [com.raf.capsuleos.domain.model.CapsulePreferences.enableOuterNeuronGlow].
 * - Disabled for the compact square Flashlight ([CapsuleContent.Torch]) card.
 * - For expanded Media cards, checks [ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW] and stays lit
 *   while playing/open when [com.raf.capsuleos.domain.model.CapsulePreferences.keepExpandedMediaGlowAlways] is enabled.
 */
fun shouldShowOuterExpansionAura(state: CapsuleUiState): Boolean {
    val prefs = state.preferences
    if (!prefs.enableOuterNeuronGlow) return false
    if (state.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) return false
    if (state.content is CapsuleContent.Torch) return false
    if (state.content is CapsuleContent.Media) {
        return ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW in prefs.expandedMediaGlowEffects && (state.isNeuralPulseActive || prefs.keepOuterMediaGlowAlways)
    }
    return state.isNeuralPulseActive
}

/**
 * Renders the outer phone-frame expansion aura and inward-flowing neuron gradient outside the
 * expanded State 3 card, fading out smoothly after the expansion pulse window.
 */
@Composable
fun OuterScreenExpansionAura(
    modifier: Modifier = Modifier,
    visible: Boolean,
    colorA: Color,
    colorB: Color,
    screenCornerRadiusDp: Float = 36f,
    spreadDp: Float = 72f,
) {
    val animatedIntensity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 260 else 620),
        label = "outerAuraIntensity",
    )
    val animatedColorA by animateColorAsState(
        targetValue = colorA,
        animationSpec = tween(durationMillis = 360),
        label = "outerAuraColorA",
    )
    val animatedColorB by animateColorAsState(
        targetValue = colorB,
        animationSpec = tween(durationMillis = 360),
        label = "outerAuraColorB",
    )
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    val shouldAnimateClock = visible || animatedIntensity > 0.005f
    LaunchedEffect(shouldAnimateClock) {
        if (!shouldAnimateClock) return@LaunchedEffect
        var startMs = -1L
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameMs ->
                if (startMs < 0L) startMs = frameMs
                timeSeconds = (frameMs - startMs) / 1000f
            }
        }
    }

    val density = LocalDensity.current
    val cornerPx = with(density) { screenCornerRadiusDp.dp.toPx() }
    val spreadPx = with(density) { spreadDp.dp.toPx() }
    val auraShader = remember { OuterExpansionAuraShader.create() }

    if (animatedIntensity > 0.005f) {
        Box(
            modifier = modifier.drawWithCache {
                val brush = ShaderBrush(auraShader)
                onDrawBehind {
                    OuterExpansionAuraShader.updateUniforms(
                        shader = auraShader,
                        widthPx = size.width,
                        heightPx = size.height,
                        timeSeconds = timeSeconds,
                        cornerRadiusPx = cornerPx,
                        spreadPx = spreadPx,
                        intensity = animatedIntensity,
                        colorA = animatedColorA,
                        colorB = animatedColorB,
                    )
                    drawRect(brush = brush)
                }
            },
        )
    }
}
