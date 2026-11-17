package com.raf.capsuleos.presentation.shader

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language

/**
 * Unified GPU shader powering all capsule border, battery ring, and Gemini neural wash states:
 * - **State 1 (`uRingMix = 1`)**: Unified circular battery progress ring around the camera cutout
 *   with smooth animated color morphing when shrinking from expanded cards.
 * - **State 2 (`uRingMix = 0, uNeuralMode = 0`)**: Clean subtle perimeter border aura only (no dots).
 * - **State 3 (`uRingMix = 0, uNeuralMode = 1`)**: Cohesive Gemini blue/cyan/indigo perimeter wave
 *   ebbing smoothly inward around the rounded card edge (leaving the center blur clear, with no rainbow
 *   fill), paired on initial expand with fine neural stipple dots clipped strictly inside the wave.
 */
object NeuralBorderShader {

    @Language("AGSL")
    private const val SHADER_SOURCE = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uCornerRadius;
        uniform float uStrokeWidth;
        uniform float uIntensity;
        uniform float uDotIntensity;
        uniform float uNeuralMode;
        uniform float uRingMix;
        uniform float uRingProgress;
        uniform float uRingGlow;
        uniform float4 uColorA;
        uniform float4 uColorB;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        float3 sampleGeminiGlow(float phase01, float3 dynA, float3 dynB) {
            float3 primaryLight = min(dynA * 1.35 + float3(0.14), float3(1.0));
            float3 primaryDeep = dynA * 0.72;
            float t1 = 0.5 + 0.5 * sin(phase01 * 6.2831853);
            float t2 = 0.5 + 0.5 * cos(phase01 * 6.2831853 + 1.8);
            float3 shimmer = mix(primaryDeep, primaryLight, t1);
            return mix(shimmer, dynA, 0.48 + 0.25 * t2);
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = uResolution * 0.5;
            float2 p = fragCoord - halfSize;
            float radius = min(uCornerRadius, min(halfSize.x, halfSize.y));
            float dist = sdRoundedBox(p, halfSize - uStrokeWidth, max(radius - uStrokeWidth, 1.0));

            float2 normP = p / max(halfSize, float2(1.0));
            float angle = atan(normP.y, normP.x);

            // Unified circular battery progress arc starting at 12 o'clock top with optional 1s bloom glow
            float topAngle01 = fract((angle + 1.5707963) / 6.2831853);
            float arcMask = uRingProgress >= 0.995
                ? 1.0
                : smoothstep(uRingProgress + 0.016, uRingProgress - 0.012, topAngle01);
            float ringStroke = exp(-abs(dist) * 0.76);
            float glowHalo = exp(-abs(dist) * 0.24) * 0.68 * uRingGlow;
            float arcWeight = 0.24 + 0.76 * arcMask;
            float ringLum = min(ringStroke * arcWeight * (1.0 + 0.32 * uRingGlow) + glowHalo * (0.45 + 0.55 * arcMask), 1.0);
            float3 ringBloomRgb = mix(uColorA.rgb, float3(1.0), 0.28 * uRingGlow);
            float3 ringRgb = mix(uColorA.rgb, ringBloomRgb, smoothstep(0.0, 1.0, glowHalo * 1.4));

            // State 2: Calm compact border aura only (no neural dots)
            float compactMix = 0.5 + 0.5 * sin(angle * 2.0 + uTime * 0.65);
            float3 compactRgb = mix(uColorA.rgb, uColorB.rgb, compactMix);
            float state2Border = exp(-abs(dist) * 0.72) * 0.56;

            // State 3: Organic Gemini border wave undulating around perimeter while keeping interior crystal clear
            float inwardDist = max(-dist, 0.0);
            float borderCrest = exp(-abs(dist) * 0.18);
            float waveModA = 0.5 + 0.5 * sin(angle * 2.0 - uTime * 2.45);
            float waveModB = 0.5 + 0.5 * cos(angle * 3.0 + uTime * 1.75);
            float waveReachPx = 18.0 + 22.0 * waveModA + 14.0 * waveModB;
            float inwardDecay = exp(-pow(inwardDist / max(waveReachPx, 1.0), 1.55) * 2.15)
                * smoothstep(68.0, 14.0, inwardDist);
            float travelSurge = 0.28 + 0.72 * (0.5 + 0.5 * sin(angle - uTime * 2.25 + 0.65 * sin(angle * 2.0 + uTime * 1.15)));
            float innerMask = step(dist, 1.2);
            float waveBody = (innerMask * inwardDecay * 0.74 + borderCrest * 0.28) * travelSurge;
            float3 geminiGrad = sampleGeminiGlow(
                (angle / 6.2831853) - uTime * 0.32 + waveModA * 0.12,
                uColorA.rgb,
                uColorB.rgb
            );

            float3 activeCapsuleRgb = mix(compactRgb, geminiGrad, uNeuralMode);
            float capsuleLum = mix(state2Border, waveBody, uNeuralMode) * uIntensity;

            // Fine scattered neural stipple dots strictly clipped inside the perimeter wave
            float dotsLum = 0.0;
            float clippedWaveMask = smoothstep(0.10, 0.45, waveBody);
            if (uDotIntensity > 0.004 && uNeuralMode > 0.05 && dist <= 1.0 && clippedWaveMask > 0.01) {
                float spacing = 9.5;
                float2 gridUv = fragCoord / spacing;
                float rowShift = mod(floor(gridUv.y), 2.0) * 0.5;
                float2 shiftedUv = float2(gridUv.x + rowShift, gridUv.y);
                float2 cellId = floor(shiftedUv);
                float2 cellLocal = (fract(shiftedUv) - 0.5) * spacing;
                float dToCenter = length(cellLocal);

                float hash = fract(sin(dot(cellId, float2(12.9898, 78.233))) * 43758.5453);
                float densityMask = step(0.18, hash);
                float twinkle = pow(0.5 + 0.5 * sin(uTime * 6.5 + hash * 6.2831), 2.0);
                float dotRadius = 1.65;
                float dotMask = smoothstep(dotRadius, dotRadius * 0.2, dToCenter);

                dotsLum = dotMask * densityMask * clippedWaveMask * (0.35 + 0.65 * twinkle) * uDotIntensity * uNeuralMode * 0.95;
            }

            float3 dotRgb = mix(activeCapsuleRgb, float3(0.96, 0.99, 1.0), 0.74);
            float capsuleAlpha = clamp(capsuleLum + dotsLum, 0.0, 1.0);
            float3 capsuleOutRgb = activeCapsuleRgb * capsuleLum + dotRgb * dotsLum;

            float baseAlpha = mix(capsuleAlpha, ringLum, uRingMix);
            float3 baseRgb = mix(capsuleOutRgb, ringRgb * ringLum, uRingMix);
            float collapseWaveOverlay = smoothstep(0.55, 1.0, uIntensity) * uNeuralMode * uRingMix;
            float finalAlpha = clamp(max(baseAlpha, capsuleAlpha * collapseWaveOverlay), 0.0, 1.0);
            float3 finalRgb = baseRgb + capsuleOutRgb * collapseWaveOverlay * (1.0 - ringStroke * 0.45);

            return half4(min(finalRgb, float3(finalAlpha)), finalAlpha);
        }
    """

    /** Creates a new [RuntimeShader] instance configured for neural capsule borders & ring progress. */
    fun create(): RuntimeShader = RuntimeShader(SHADER_SOURCE)

    /** Updates uniform parameters on [shader] before drawing. */
    fun updateUniforms(
        shader: RuntimeShader,
        widthPx: Float,
        heightPx: Float,
        timeSeconds: Float,
        cornerRadiusPx: Float,
        strokeWidthPx: Float,
        intensity: Float,
        dotIntensity: Float,
        neuralMode: Float,
        ringMix: Float,
        ringProgress: Float,
        ringGlow: Float,
        colorA: Color = Color(0xFF4285F4),
        colorB: Color = Color(0xFF9B72CB),
    ) {
        shader.setFloatUniform("uResolution", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform("uCornerRadius", cornerRadiusPx)
        shader.setFloatUniform("uStrokeWidth", strokeWidthPx)
        shader.setFloatUniform("uIntensity", intensity.coerceIn(0f, 1.5f))
        shader.setFloatUniform("uDotIntensity", dotIntensity.coerceIn(0f, 1.5f))
        shader.setFloatUniform("uNeuralMode", neuralMode.coerceIn(0f, 1f))
        shader.setFloatUniform("uRingMix", ringMix.coerceIn(0f, 1f))
        shader.setFloatUniform("uRingProgress", ringProgress.coerceIn(0f, 1f))
        shader.setFloatUniform("uRingGlow", ringGlow.coerceIn(0f, 1f))
        shader.setFloatUniform("uColorA", colorA.red, colorA.green, colorA.blue, colorA.alpha)
        shader.setFloatUniform("uColorB", colorB.red, colorB.green, colorB.blue, colorB.alpha)
    }
}
