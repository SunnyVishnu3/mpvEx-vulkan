/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.content.Context
import android.util.LruCache
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AmbientRenderContext(
  val scaleX: Double,
  val scaleY: Double,
)

data class AmbientSharedShaderConfig(
  val bezelDepth: Float,
  val vignetteStrength: Float,
  val opacity: Float,
  val edgeBlend: Float = 0f,
)

data class AmbientGlowShaderSpec(
  val style: AmbientStyle = AmbientStyle.Glow,
  val context: AmbientRenderContext,
  val shared: AmbientSharedShaderConfig,
  val blurSamples: Int,
  val maxRadius: Float,
  val glowIntensity: Float,
  val satBoost: Float,
  val warmth: Float,
  val fadeCurve: Float,
)

data class AmbientGlowPreset(
  val blurSamples: Int,
  val maxRadius: Float,
  val glowIntensity: Float,
  val satBoost: Float,
  val vignetteStrength: Float,
  val warmth: Float,
  val fadeCurve: Float,
  val opacity: Float,
)

object AmbientShaderPresets {
  val glowFast = AmbientGlowPreset(8, 0.15f, 1.2f, 1.0f, 0.3f, 0.0f, 1.2f, 0.8f)
  val glowBalanced = AmbientGlowPreset(18, 0.28f, 1.45f, 1.25f, 0.55f, 0.0f, 1.7f, 1.0f)
  val glowHighQuality = AmbientGlowPreset(24, 0.35f, 1.5f, 1.3f, 0.7f, 0.0f, 1.8f, 1.0f)
}

fun matchesGlowPreset(
  preset: AmbientGlowPreset,
  blurSamples: Int,
  maxRadius: Float,
  glowIntensity: Float,
  satBoost: Float,
  vignetteStrength: Float,
  warmth: Float,
  fadeCurve: Float,
  opacity: Float,
): Boolean =
  blurSamples == preset.blurSamples &&
    closeTo(maxRadius, preset.maxRadius) &&
    closeTo(glowIntensity, preset.glowIntensity) &&
    closeTo(satBoost, preset.satBoost) &&
    closeTo(vignetteStrength, preset.vignetteStrength) &&
    closeTo(warmth, preset.warmth) &&
    closeTo(fadeCurve, preset.fadeCurve) &&
    closeTo(opacity, preset.opacity)

private fun closeTo(left: Float, right: Float, tolerance: Float = 0.01f): Boolean = abs(left - right) <= tolerance

private const val GOLDEN_ANGLE = 2.399963229728653

private fun glslFloat(value: Double): String {
  val normalized = if (abs(value) < 0.0000005) 0.0 else value
  val formatted =
    String.format(Locale.US, "%.8f", normalized)
      .trimEnd('0')
      .trimEnd('.')
  return if (formatted.contains('.')) formatted else "$formatted.0"
}

private fun spiralRadiusNorm(
  index: Int,
  count: Int,
): Double = sqrt((index.toDouble() + 0.5) / count.toDouble())

private data class TapCacheKey(
  val samples: Int,
  val maxRadius: Float,
  val fadeCurve: Float,
)

/** In-memory cache for compiled GLSL tap tables to avoid repeated CPU trig and string formatting. */
private val tapTableCache = LruCache<TapCacheKey, String>(16)

private fun buildSpiralTapTable(
  name: String,
  spec: AmbientGlowShaderSpec,
): String {
  val key = TapCacheKey(spec.blurSamples, spec.maxRadius, spec.fadeCurve)
  tapTableCache.get(key)?.let { return it }

  val count = spec.blurSamples.coerceAtLeast(1)
  val thirdComponents = glowTapWeights(spec)
  val maxRadius = spec.maxRadius.toDouble()

  val taps =
    (0 until count).joinToString(",\n") { index ->
      // Pre-scale by maxRadius on CPU so the GPU inner loop avoids tap.xy * MAX_RADIUS
      val radiusScaled = spiralRadiusNorm(index, count) * maxRadius
      val theta = (index.toDouble() + 0.5) * GOLDEN_ANGLE
      val x = cos(theta) * radiusScaled
      val y = sin(theta) * radiusScaled
      "    vec3(${glslFloat(x)}, ${glslFloat(y)}, ${glslFloat(thirdComponents[index])})"
    }
  val table = "const vec3 $name[$count] = vec3[$count](\n$taps\n);"
  tapTableCache.put(key, table)
  return table
}

/**
 * Glow distance falloff, precomputed per tap on the CPU.
 */
private fun glowTapWeights(spec: AmbientGlowShaderSpec): DoubleArray {
  val count = spec.blurSamples.coerceAtLeast(1)
  val maxRadius = spec.maxRadius.toDouble()
  val fadeCurve = spec.fadeCurve.toDouble()
  return DoubleArray(count) { index ->
    val r = spiralRadiusNorm(index, count) * maxRadius
    (1.0 / (1.0 + r * 40.0)).pow(fadeCurve)
  }
}

/**
 * Common GLSL helpers using mediump (FP16) where safe on mobile GPUs.
 * Uses Jorge Jimenez's Interleaved Gradient Noise (IGN) for cache-coherent,
 * zero-trig screen-space dither.
 */
private val GLSL_COMMON_HELPERS =
  """
mediump float ign(highp vec2 p) {
    mediump vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(magic.z * fract(dot(p, magic.xy)));
}

mediump float luma(mediump vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

mediump vec3 adjust_saturation(mediump vec3 rgb, mediump float amount) {
    return mix(vec3(luma(rgb)), rgb, amount);
}

mediump vec3 apply_warmth(mediump vec3 rgb, mediump float amount) {
    rgb.r = clamp(rgb.r + amount * 0.060, 0.0, 1.0);
    rgb.g = clamp(rgb.g + amount * 0.025, 0.0, 1.0);
    rgb.b = clamp(rgb.b - amount * 0.080, 0.0, 1.0);
    return rgb;
}
  """.trimIndent()

/**
 * Start of hook(): remaps screen UV back to video UV and returns the untouched
 * video pixel outside the optional inner edge band.
 */
private val GLSL_VIDEO_PROLOGUE =
  """
    highp vec2 uv = HOOKED_pos;
    highp vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;

    // Stay half a texel inside the decoded frame when sampling the video edge.
    highp vec2 half_texel = vec2(0.5) / HOOKED_size;
    highp vec2 safe_min = half_texel;
    highp vec2 safe_max = vec2(1.0) - half_texel;

    bool inside_video = video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
              video_uv.y >= 0.0 && video_uv.y <= 1.0;
    mediump float video_weight = 0.0;
    if (inside_video) {
      highp vec2 video_size = HOOKED_size / vec2(SCALE_X, SCALE_Y);
      mediump float blend_width = EDGE_BLEND * min(video_size.x, video_size.y);
      mediump float inside_dist = blend_width;
      if (SCALE_X > 1.0) {
        inside_dist = min(inside_dist, min(video_uv.x, 1.0 - video_uv.x) * video_size.x);
      }
      if (SCALE_Y > 1.0) {
        inside_dist = min(inside_dist, min(video_uv.y, 1.0 - video_uv.y) * video_size.y);
      }
      if (EDGE_BLEND <= 0.0 || inside_dist >= blend_width) {
        return HOOKED_tex(clamp(video_uv, safe_min, safe_max));
      }
      video_weight = smoothstep(0.0, blend_width, inside_dist);
    }

    highp vec2 edge_origin = clamp(video_uv, safe_min, safe_max);
    mediump float edge_dist = length(video_uv - clamp(video_uv, 0.0, 1.0));

    // Interleaved Gradient Noise jitter: avoids rand() sin() overhead and improves GPU cache hits
    mediump float jitter = ign(uv * HOOKED_size) * 6.2831853;
    mediump float jitter_s = sin(jitter);
    mediump float jitter_c = cos(jitter);
    mediump float aspect_x = HOOKED_size.y / HOOKED_size.x;
    mediump float jitter_c_aspect = jitter_c * aspect_x;
    mediump float jitter_s_aspect = jitter_s * aspect_x;
  """.trimIndent().prependIndent("    ")

/** End of hook(): vignette, opacity, and the optional edge and bezel blends. */
private val GLSL_AMBIENT_EPILOGUE =
  """
    mediump float vig_r = length(uv - 0.5) * 2.0;
    ambient_rgb *= mix(1.0, smoothstep(1.3, 0.1, vig_r), VIGNETTE_STR);

    mediump vec4 ambient_out = vec4(ambient_rgb * OPACITY, 1.0);

    if (inside_video) {
      return mix(ambient_out, HOOKED_tex(clamp(video_uv, safe_min, safe_max)), video_weight);
    }

    // A zero bezel means a hard, gap-free handoff from video to ambience.
    if (BEZEL_DEPTH <= 0.0) {
      return ambient_out;
    }

    highp vec2 outside_dist = max(max(-video_uv, video_uv - vec2(1.0)), vec2(0.0));
    mediump float dist_to_edge = max(outside_dist.x, outside_dist.y);
    mediump float bezel_alpha = smoothstep(0.0, BEZEL_DEPTH, dist_to_edge);

    vec4 edge_pixel = HOOKED_tex(edge_origin);
    return mix(edge_pixel, ambient_out, bezel_alpha);
  """.trimIndent().prependIndent("    ")

object AmbientShaderBuilder {
  private val shaderCache = LruCache<AmbientGlowShaderSpec, String>(24)

  fun build(spec: AmbientGlowShaderSpec): String = build(null, spec)

  fun build(
    @Suppress("UNUSED_PARAMETER") context: Context?,
    spec: AmbientGlowShaderSpec,
  ): String {
    shaderCache.get(spec)?.let { return it }

    val shader =
      when (spec.style) {
        AmbientStyle.Ambilight -> FxAmbientShaderBuilder.buildAmbilight(spec)
        AmbientStyle.Cinema -> FxAmbientShaderBuilder.buildCinema(spec)
        AmbientStyle.Echo -> FxAmbientShaderBuilder.buildEcho(spec)
        AmbientStyle.Mirror -> FxAmbientShaderBuilder.buildMirror(spec)
        AmbientStyle.Glow, AmbientStyle.YouTube -> buildLegacy(spec)
      }

    shaderCache.put(spec, shader)
    return shader
  }

  fun clearCache() {
    shaderCache.evictAll()
    tapTableCache.evictAll()
    FxAmbientShaderBuilder.clearCache()
  }

  fun buildLegacy(spec: AmbientGlowShaderSpec): String {
    val invRadiusScale = 3.0 / spec.maxRadius.toDouble().coerceAtLeast(0.001)

    return """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode (Glow)

#ifdef GL_ES
precision mediump float;
precision highp int;
#else
#define mediump
#define highp
#define lowp
#endif

#define BLUR_SAMPLES     ${spec.blurSamples}
#define MAX_RADIUS       ${glslFloat(spec.maxRadius.toDouble())}
#define INV_RADIUS_SCALE ${glslFloat(invRadiusScale)}
#define GLOW_INTENSITY   ${glslFloat(spec.glowIntensity.toDouble())}
#define SAT_BOOST        ${glslFloat(spec.satBoost.toDouble())}
#define BEZEL_DEPTH      ${glslFloat(spec.shared.bezelDepth.toDouble())}
#define EDGE_BLEND       ${glslFloat(spec.shared.edgeBlend.toDouble())}
#define VIGNETTE_STR     ${glslFloat(spec.shared.vignetteStrength.toDouble())}
#define WARMTH           ${glslFloat(spec.warmth.toDouble())}
#define OPACITY          ${glslFloat(spec.shared.opacity.toDouble())}
#define SCALE_X          ${glslFloat(spec.context.scaleX)}
#define SCALE_Y          ${glslFloat(spec.context.scaleY)}

// tap.xy = pre-scaled offset by maxRadius; tap.z = precomputed distance falloff.
${buildSpiralTapTable("GLOW_TAPS", spec)}

$GLSL_COMMON_HELPERS

vec4 hook() {
$GLSL_VIDEO_PROLOGUE

    // Rational falloff curve: replaces expensive exp() with cheap ALU operations
    mediump float d = edge_dist * INV_RADIUS_SCALE;
    mediump float edge_fade = 1.0 / (1.0 + d * (1.0 + d * 0.5));

    mediump vec3 acc_color = vec3(0.0);
    mediump float acc_weight = 0.0;

    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = GLOW_TAPS[i];

        // tap.xy is already pre-scaled by maxRadius; aspect_x is folded into jitter_aspect
        highp vec2 offset = vec2(
            tap.x * jitter_c_aspect - tap.y * jitter_s_aspect,
            tap.x * jitter_s + tap.y * jitter_c
        );
        mediump vec3 sample_rgb = HOOKED_tex(clamp(edge_origin + offset, safe_min, safe_max)).rgb;

        mediump float weight = tap.z * (1.0 + luma(sample_rgb) * 2.0);

        acc_color += sample_rgb * weight;
        acc_weight += weight;
    }

    mediump vec3 ambient_rgb = (acc_color / max(acc_weight, 1e-5)) * GLOW_INTENSITY;
    ambient_rgb = adjust_saturation(ambient_rgb, SAT_BOOST);
    ambient_rgb = apply_warmth(ambient_rgb, WARMTH);
    ambient_rgb *= edge_fade;

$GLSL_AMBIENT_EPILOGUE
}
    """.trimIndent()
  }
}
