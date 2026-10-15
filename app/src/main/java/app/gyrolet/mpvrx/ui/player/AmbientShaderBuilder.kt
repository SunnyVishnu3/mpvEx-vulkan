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
  val centerX: Double = 0.5,
  val centerY: Double = 0.5,
)

data class AmbientSharedShaderConfig(
  val bezelDepth: Float,
  val vignetteStrength: Float,
  val opacity: Float,
  val edgeBlend: Float = 0f,
)

data class AmbientGlowShaderSpec(
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
  val table = "const highp vec3 $name[$count] = vec3[$count](\n$taps\n);"
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
 * Common GLSL helpers using highp for smooth, sub-texel precision.
 * Uses Jorge Jimenez's Interleaved Gradient Noise (IGN) for cache-coherent,
 * zero-trig screen-space dither.
 */
private val GLSL_COMMON_HELPERS =
  """
highp float ign(highp vec2 p) {
    highp vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(magic.z * fract(dot(p, magic.xy)));
}

highp float luma(highp vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

highp vec3 adjust_saturation(highp vec3 rgb, highp float amount) {
    highp float l = luma(rgb);
    return max(vec3(l) + (rgb - vec3(l)) * amount, vec3(0.0));
}

// FIX 1: Multiplicative color-temperature adjustment.
// Additive offsets turned pitch-black (0,0,0) murky orange; multiplying by a
// per-channel gain preserves true black while still shifting white-point tone.
highp vec3 apply_warmth(highp vec3 rgb, highp float amount) {
    highp vec3 gain = vec3(1.0 + amount * 0.12, 1.0 + amount * 0.05, 1.0 - amount * 0.15);
    return clamp(rgb * gain, 0.0, 1.0);
}

// FIX 2: Highlight roll-off (from fx_ambient/fx_common.glsl).
// Bright scene edges (white walls, explosions) were multiplying through
// GLOW_INTENSITY uncapped, turning bars into a glaring white slab.
// Compression: c / (1 + k*luma) — dark/mid tones pass almost unchanged,
// highlights are softened so bars read as light, not overexposure.
highp vec3 rolloff(highp vec3 c, highp float k) {
    return c * (1.0 / (1.0 + k * luma(c)));
}

// FIX 3: Screen-space dither to dissolve 8-bit quantization banding.
// Dark gradients stair-stepped visibly on OLED/8-bit panels because the
// smooth edge_fade was truncated into 256 discrete output levels.
// IGN noise amplitude is scaled by peak channel so pure black gets no noise.
highp vec3 dither_out(highp vec3 c, highp vec2 p) {
    highp float amp = clamp(max(c.r, max(c.g, c.b)) * 255.0, 0.0, 1.0);
    return c + (ign(p) - 0.5) * (amp / 255.0);
}
  """.trimIndent()

/**
 * Start of hook(): remaps screen UV back to video UV and returns the untouched
 * video pixel outside the optional inner edge band.
 */
private val GLSL_VIDEO_PROLOGUE =
  """
    highp vec2 uv = HOOKED_pos;
    highp vec2 video_uv = (uv - vec2(CENTER_X, CENTER_Y)) * vec2(SCALE_X, SCALE_Y) + 0.5;

    // Stay half a texel inside the decoded frame when sampling the video edge.
    highp vec2 half_texel = vec2(0.5) / HOOKED_size;
    highp vec2 safe_min = half_texel;
    highp vec2 safe_max = vec2(1.0) - half_texel;

    bool inside_video = video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
              video_uv.y >= 0.0 && video_uv.y <= 1.0;
    highp float video_weight = 0.0;
    if (inside_video) {
      highp vec2 video_size = HOOKED_size / vec2(SCALE_X, SCALE_Y);
      highp float blend_width = float(EDGE_BLEND) * min(video_size.x, video_size.y);
      highp float inside_dist = blend_width;
      if (CENTER_X - 0.5 / SCALE_X > 0.0) {
        inside_dist = min(inside_dist, video_uv.x * video_size.x);
      }
      if (CENTER_X + 0.5 / SCALE_X < 1.0) {
        inside_dist = min(inside_dist, (1.0 - video_uv.x) * video_size.x);
      }
      if (CENTER_Y - 0.5 / SCALE_Y > 0.0) {
        inside_dist = min(inside_dist, video_uv.y * video_size.y);
      }
      if (CENTER_Y + 0.5 / SCALE_Y < 1.0) {
        inside_dist = min(inside_dist, (1.0 - video_uv.y) * video_size.y);
      }
      if (EDGE_BLEND <= 0.0 || inside_dist >= blend_width) {
        return HOOKED_tex(clamp(video_uv, safe_min, safe_max));
      }
      video_weight = smoothstep(0.0, blend_width, inside_dist);
    }

    highp vec2 edge_origin = clamp(video_uv, safe_min, safe_max);
    highp float dx = max(max(-video_uv.x, video_uv.x - 1.0), 0.0);
    highp float dy = max(max(-video_uv.y, video_uv.y - 1.0), 0.0);
    bool in_side_bar = (SCALE_X > SCALE_Y) && (dx > dy);
    highp float edge_dist = length(vec2(dx, dy));
    highp float aspect_x = HOOKED_size.y / HOOKED_size.x;
  """.trimIndent().prependIndent("    ")

/** End of hook(): vignette, opacity, and the optional edge and bezel blends. */
private val GLSL_AMBIENT_EPILOGUE =
  """
    highp float vig_r = length(uv - 0.5) * 2.0;
    ambient_rgb *= mix(1.0, smoothstep(1.3, 0.1, vig_r), VIGNETTE_STR);

    // FIX 7 (part a): dither before quantisation to the 8-bit swapchain so the
    // exponential fade into true black never shows stair-step contour lines.
    // Noise amplitude tracks peak channel so silence on pure black is guaranteed.
    ambient_rgb = dither_out(ambient_rgb, uv * HOOKED_size);

    highp vec4 ambient_out = vec4(ambient_rgb * OPACITY, 1.0);

    if (inside_video) {
      return mix(ambient_out, HOOKED_tex(clamp(video_uv, safe_min, safe_max)), video_weight);
    }

    // A zero bezel means a hard, gap-free handoff from video to ambience.
    if (BEZEL_DEPTH <= 0.0) {
      return ambient_out;
    }

    // FIX 7 (part b): Euclidean (L2) corner distance instead of Chebyshev (L∞).
    // max(dx,dy) produced sharp diamond-shaped seams at pillarbox/letterbox corners;
    // length() gives a smooth circular transition matching real-world bezel curvature.
    highp vec2 outside_dist = max(max(-video_uv, video_uv - vec2(1.0)), vec2(0.0));
    highp float dist_to_edge = length(outside_dist);
    highp float bezel_alpha = smoothstep(0.0, BEZEL_DEPTH, dist_to_edge);

    vec4 edge_pixel = HOOKED_tex(edge_origin);
    return mix(edge_pixel, ambient_out, bezel_alpha);
  """.trimIndent().prependIndent("    ")

object AmbientShaderBuilder {
  fun build(
    @Suppress("UNUSED_PARAMETER") context: Context,
    spec: AmbientGlowShaderSpec,
  ): String {
    val invRadiusScale = 1.0 / spec.maxRadius.toDouble().coerceAtLeast(0.001)

    return """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode (Glow)

#ifdef GL_ES
precision highp float;
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
#define FADE_CURVE       ${glslFloat(spec.fadeCurve.toDouble())}
#define OPACITY          ${glslFloat(spec.shared.opacity.toDouble())}
#define SCALE_X          ${glslFloat(spec.context.scaleX)}
#define SCALE_Y          ${glslFloat(spec.context.scaleY)}
#define CENTER_X         ${glslFloat(spec.context.centerX)}
#define CENTER_Y         ${glslFloat(spec.context.centerY)}

// tap.xy = pre-scaled offset by maxRadius; tap.z = precomputed distance falloff.
${buildSpiralTapTable("GLOW_TAPS", spec)}

$GLSL_COMMON_HELPERS

vec4 hook() {
$GLSL_VIDEO_PROLOGUE

    // Normalised reach distance: 0.0 at frame edge, >= 1.0 beyond glow reach.
    highp float dn = edge_dist * INV_RADIUS_SCALE;
    if (dn >= 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    // Anchor sample origin at the nearest video edge with a fixed 1.5% inward inset.
    highp vec2 sample_origin = mix(edge_origin, vec2(0.5), 0.015);

    // Physical dual-phase emitter curve (from fx_ambient):
    // luminous core relaxing into a smooth tail that cleanly lands on true black at dn = 1.0.
    highp float edge_fade = pow(1.0 - dn, FADE_CURVE * 1.4) * (0.6 + 0.4 * exp(-5.0 * dn));

    highp vec3 acc_color = vec3(0.0);
    highp float acc_weight = 0.0;

    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = GLOW_TAPS[i];

        highp vec2 tap_rot = vec2(tap.x * aspect_x, tap.y);
        // Compress perpendicular penetration to stay within the edge margin (≤ 0.02)
        // so internal moving objects (e.g. hands, faces, text) are never sampled as ghost reflections,
        // while allowing full wide diffuse glow along the bar.
        highp vec2 offset = in_side_bar
            ? vec2(tap_rot.x * 0.12, tap_rot.y)
            : vec2(tap_rot.x, tap_rot.y * 0.12);

        highp vec3 sample_rgb = HOOKED_tex(clamp(sample_origin + offset, safe_min, safe_max)).rgb;

        highp float weight = tap.z;

        acc_color += sample_rgb * weight;
        acc_weight += weight;
    }

    highp vec3 ambient_rgb = (acc_color / max(acc_weight, 1e-5)) * GLOW_INTENSITY;
    ambient_rgb = adjust_saturation(ambient_rgb, SAT_BOOST);
    ambient_rgb = apply_warmth(ambient_rgb, WARMTH);
    // FIX 6: apply highlight roll-off so bright-edge scenes don't produce a
    // glaring slab in the bars; dark/mid tones are nearly unaffected.
    ambient_rgb = rolloff(ambient_rgb, 0.55);
    ambient_rgb *= edge_fade;

$GLSL_AMBIENT_EPILOGUE
}
    """.trimIndent()
  }
}
