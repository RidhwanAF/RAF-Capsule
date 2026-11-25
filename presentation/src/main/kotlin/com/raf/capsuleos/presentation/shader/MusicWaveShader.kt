package com.raf.capsuleos.presentation.shader

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language

/**
 * AGSL GPU shader rendering layered organic waves rising from the bottom of the
 * expanded media capsule, tinted with sampled album art colors.
 */
object MusicWaveShader {

    @Language("AGSL")
    private const val SHADER_SOURCE = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uPlaying;
        uniform float4 uPrimaryColor;
        uniform float4 uSecondaryColor;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
            float yFromBottom = 1.0 - uv.y;

            float amp = mix(0.012, 0.085, uPlaying);
            float speed = 1.0;

            float wave1 = 0.24 + amp * sin(uv.x * 7.5 + uTime * speed) * cos(uv.x * 3.1 - uTime * 0.9);
            float wave2 = 0.17 + amp * 0.85 * sin(uv.x * 10.5 - uTime * speed * 1.25 + 1.4);
            float wave3 = 0.11 + amp * 0.65 * cos(uv.x * 13.0 + uTime * speed * 1.6 + 2.2);

            float layer1 = smoothstep(wave1 + 0.06, wave1 - 0.04, yFromBottom);
            float layer2 = smoothstep(wave2 + 0.05, wave2 - 0.03, yFromBottom);
            float layer3 = smoothstep(wave3 + 0.04, wave3 - 0.02, yFromBottom);

            float3 col1 = uPrimaryColor.rgb * 0.72;
            float3 col2 = mix(uPrimaryColor.rgb, uSecondaryColor.rgb, 0.55) * 0.88;
            float3 col3 = uSecondaryColor.rgb;

            float3 color = col1 * layer1 * 0.42 + col2 * layer2 * 0.45 + col3 * layer3 * 0.52;
            float alpha = clamp(layer1 * 0.34 + layer2 * 0.38 + layer3 * 0.44, 0.0, 0.82);

            return half4(color * alpha, alpha);
        }
    """

    /** Creates a new [RuntimeShader] instance for the bottom music wave backdrop. */
    fun create(): RuntimeShader = RuntimeShader(SHADER_SOURCE)

    /** Updates uniform values for the music wave shader. */
    fun updateUniforms(
        shader: RuntimeShader,
        widthPx: Float,
        heightPx: Float,
        timeSeconds: Float,
        waveActivity: Float,
        primaryColor: Color,
        secondaryColor: Color,
    ) {
        shader.setFloatUniform("uResolution", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform("uPlaying", waveActivity.coerceIn(0f, 1.5f))
        shader.setFloatUniform(
            "uPrimaryColor",
            primaryColor.red,
            primaryColor.green,
            primaryColor.blue,
            primaryColor.alpha,
        )
        shader.setFloatUniform(
            "uSecondaryColor",
            secondaryColor.red,
            secondaryColor.green,
            secondaryColor.blue,
            secondaryColor.alpha,
        )
    }
}
