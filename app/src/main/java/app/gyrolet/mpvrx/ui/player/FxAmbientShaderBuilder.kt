/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.util.LruCache
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates custom MPV user shaders for the ambient light modes
 * implemented directly as in fx_ambient (Cinema, Echo, Mirror, Ambilight).
 *
 * Each mode is self-contained with fixed, authentic parameters from fx_ambient.frag,
 * lighting the black bars around the video without altering the picture:
 *
 * - Ambilight (FX Glow): Edge-sampled soft emitter curve with bright core relaxing into quiet tail.
 * - Cinema: Enlarged, dissolved backdrop wash behind the picture (YouTube style).
 * - Echo: Outward-propagating light diffusion along the edges.
 * - Mirror: Polished glass reflection continuing the frame into the bars.
 */
object FxAmbientShaderBuilder {

  private const val GOLDEN_ANGLE = 2.399963229728653

  private fun glslFloat(value: Double): String {
    val normalized = if (abs(value) < 0.0000005) 0.0 else value
    val formatted =
      String.format(Locale.US, "%.8f", normalized)
        .trimEnd('0')
        .trimEnd('.')
    return if (formatted.contains('.')) formatted else "$formatted.0"
  }

  private data class TapCacheKey(
    val samples: Int,
    val maxRadius: Float,
  )

  private val tapCache = LruCache<TapCacheKey, String>(16)

  private fun buildSpiralTapTable(samples: Int, maxRadius: Float): String {
    val key = TapCacheKey(samples, maxRadius)
    tapCache.get(key)?.let { return it }

    val count = samples.coerceAtLeast(1)
    val rMax = maxRadius.toDouble()

    val taps = (0 until count).joinToString(",\n") { index ->
      val radiusScaled = sqrt((index.toDouble() + 0.5) / count.toDouble()) * rMax
      val theta = (index.toDouble() + 0.5) * GOLDEN_ANGLE
      val x = cos(theta) * radiusScaled
      val y = sin(theta) * radiusScaled
      val weight = 1.0 / (1.0 + radiusScaled * 20.0)
      "    vec3(${glslFloat(x)}, ${glslFloat(y)}, ${glslFloat(weight)})"
    }

    val table = "const vec3 FX_TAPS[$count] = vec3[$count](\n$taps\n);"
    tapCache.put(key, table)
    return table
  }

  fun clearCache() {
    tapCache.evictAll()
  }

  /**
   * Common GLSL utilities adapted directly from fx_common.glsl:
   * - luma: standard Rec. 709 luminance
   * - vibrance: saturation adjustment around local luma
   * - rolloff: highlight roll-off curve preventing glare in the bars
   * - ign: Jimenez 2014 Interleaved Gradient Noise for screen-space dither
   * - ditherOut: dynamic amplitude dither eliminating banding on OLED displays
   * - barGeom: distance & bar thickness geometry calculation
   */
  private val GLSL_FX_COMMON =
    """
mediump float luma(mediump vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

mediump vec3 vibrance(mediump vec3 c, mediump float k) {
    mediump float l = luma(c);
    return max(vec3(l) + (c - vec3(l)) * k, vec3(0.0));
}

mediump vec3 rolloff(mediump vec3 c, mediump float k) {
    return c * (1.0 / (1.0 + k * luma(c)));
}

mediump float ign(highp vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

mediump vec3 ditherOut(mediump vec3 c, highp vec2 p) {
    mediump float amp = clamp(max(c.r, max(c.g, c.b)) * 255.0, 0.0, 1.0);
    return c + (ign(p) - 0.5) * (amp / 255.0);
}

void barGeom(highp vec2 p, highp vec4 rect, highp vec2 screen,
             out highp vec2 q, out highp float d, out highp float thick, out bool inTopBottom) {
    q = clamp(p, rect.xy, rect.zw);
    d = length(p - q);
    highp float tx = p.x < rect.x ? rect.x : (p.x > rect.z ? screen.x - rect.z : 0.0);
    highp float ty = p.y < rect.y ? rect.y : (p.y > rect.w ? screen.y - rect.w : 0.0);
    thick = max(max(tx, ty), 1.0);
    inTopBottom = ty >= tx;
}
    """.trimIndent()

  /**
   * Video sampling prologue:
   * Maps screen UV to decoded video coordinate space, ensures video pixels remain 100% untouched,
   * and computes bounding rectangles for the video and bars.
   */
  private fun buildPrologue(): String =
    """
    highp vec2 uv = HOOKED_pos;
    highp vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;

    highp vec2 half_texel = vec2(0.5) / HOOKED_size;
    highp vec2 safe_min = half_texel;
    highp vec2 safe_max = vec2(1.0) - half_texel;

    bool inside_video = video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
                        video_uv.y >= 0.0 && video_uv.y <= 1.0;
    if (inside_video) {
        return HOOKED_tex(clamp(video_uv, safe_min, safe_max));
    }

    highp vec2 inv_scale = vec2(1.0 / SCALE_X, 1.0 / SCALE_Y);
    highp vec2 screen = HOOKED_size;
    highp vec2 p = uv * screen;
    highp vec2 uv_min = 0.5 - 0.5 * inv_scale;
    highp vec2 uv_max = 0.5 + 0.5 * inv_scale;
    highp vec4 rect = vec4(uv_min * screen, uv_max * screen);
    highp vec2 vid_size = max(rect.zw - rect.xy, vec2(1.0));
    highp vec2 vid_center = (rect.xy + rect.zw) * 0.5;

    highp vec2 q;
    highp float d;
    highp float thick;
    bool inTopBottom;
    barGeom(p, rect, screen, q, d, thick, inTopBottom);
    """.trimIndent().prependIndent("    ")

  /**
   * Ambient epilogue:
   * Dithers and returns the bar light.
   */
  private fun buildEpilogue(): String =
    """
    return vec4(ditherOut(ambient_rgb, p), 1.0);
    """.trimIndent().prependIndent("    ")

  /**
   * Cinema Mode (Mode 2 from fx_ambient.frag):
   * Enlarges the whole frame and dissolves it into a soft, calm wash behind the picture.
   */
  fun buildCinema(spec: AmbientGlowShaderSpec): String {
    val tapsTable = buildSpiralTapTable(18, 0.28f)

    return """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode (Cinema)

#ifdef GL_ES
precision mediump float;
precision highp int;
#else
#define mediump
#define highp
#define lowp
#endif

#define BLUR_SAMPLES     18
#define SCALE_X          ${glslFloat(spec.context.scaleX)}
#define SCALE_Y          ${glslFloat(spec.context.scaleY)}

$tapsTable

$GLSL_FX_COMMON

vec4 hook() {
${buildPrologue()}

    // Reach from fx_ambient: mix(0.7, 1.6, 0.5) = 1.15
    highp float dn = d / (thick * 1.15);
    if (dn >= 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    // Backdrop projection: enlarged frame centered behind picture (1.12 + 0.30 * 0.5 = 1.27)
    highp vec2 proj_uv = (p - vid_center) / (vid_size * 1.27) + 0.5;

    // Soft wash blur radius expands with distance dn
    mediump float blur_rad = mix(0.03, 0.16, smoothstep(0.0, 1.0, dn));
    mediump float jitter = ign(uv * screen) * 6.2831853;
    highp float tap_scale = blur_rad / 0.28;
    highp mat2 rot = mat2(cos(jitter), sin(jitter), -sin(jitter), cos(jitter)) * tap_scale;

    mediump vec3 acc_c = vec3(0.0);
    mediump float acc_w = 0.0;

    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = FX_TAPS[i];
        highp vec2 tap_off = rot * tap.xy;

        highp vec2 sample_vid_uv = clamp(proj_uv + tap_off, 0.0, 1.0);
        highp vec2 sample_hooked_uv = (sample_vid_uv - 0.5) * inv_scale + 0.5;
        mediump vec3 rgb = HOOKED_tex(clamp(sample_hooked_uv, safe_min, safe_max)).rgb;

        mediump float wt = tap.z;
        acc_c += rgb * wt;
        acc_w += wt;
    }

    mediump vec3 c = acc_c / max(acc_w, 1e-5);
    c = rolloff(vibrance(c, 1.08), 0.55);

    // Smooth subtle falloff - calm backdrop, never brighter than a whisper
    mediump float f = pow(1.0 - smoothstep(0.0, 1.0, dn), 1.15);
    mediump vec3 ambient_rgb = c * (f * 0.55);

${buildEpilogue()}
}
    """.trimIndent()
  }

  /**
   * Echo Mode (Mode 3 from fx_ambient.frag):
   * The picture's light travelling outward into the dark along the edges.
   */
  fun buildEcho(spec: AmbientGlowShaderSpec): String {
    return """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode (Echo)

#ifdef GL_ES
precision mediump float;
precision highp int;
#else
#define mediump
#define highp
#define lowp
#endif

#define SCALE_X          ${glslFloat(spec.context.scaleX)}
#define SCALE_Y          ${glslFloat(spec.context.scaleY)}

$GLSL_FX_COMMON

vec4 hook() {
${buildPrologue()}

    // Reach from fx_ambient: mix(0.55, 1.3, 0.5) = 0.925
    highp float dn = d / (thick * 0.925);
    if (dn >= 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    highp vec2 norm_uv = clamp((p - rect.xy) / vid_size, 0.0, 1.0);
    highp float dv = max(rect.y - p.y, p.y - rect.w);
    highp float dh = max(rect.x - p.x, p.x - rect.z);

    // Edge diffusion width grows with distance d
    highp float sw_x = (14.0 + 0.7 * d) / vid_size.x;
    highp float sw_y = (14.0 + 0.7 * d) / vid_size.y;

    highp vec2 edge_uv_h = vec2(norm_uv.x, p.y < rect.y ? 0.015 : 0.985);
    highp vec2 edge_uv_v = vec2(p.x < rect.x ? 0.015 : 0.985, norm_uv.y);

    const mediump float W0 = 1.0;
    const mediump float W1 = 0.8824969;
    const mediump float W2 = 0.6065307;
    const mediump float INV_TOTAL_W = 1.0 / (1.0 + 2.0 * W1 + 2.0 * W2);

    highp vec2 off_h1 = vec2(0.5 * sw_x, 0.0);
    highp vec2 off_h2 = vec2(1.0 * sw_x, 0.0);
    highp vec2 off_v1 = vec2(0.0, 0.5 * sw_y);
    highp vec2 off_v2 = vec2(0.0, 1.0 * sw_y);

    mediump vec3 c_h = HOOKED_tex(clamp((edge_uv_h - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb * W0;
    c_h += (HOOKED_tex(clamp((edge_uv_h + off_h1 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb +
            HOOKED_tex(clamp((edge_uv_h - off_h1 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb) * W1;
    c_h += (HOOKED_tex(clamp((edge_uv_h + off_h2 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb +
            HOOKED_tex(clamp((edge_uv_h - off_h2 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb) * W2;
    c_h *= INV_TOTAL_W;

    mediump vec3 c_v = HOOKED_tex(clamp((edge_uv_v - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb * W0;
    c_v += (HOOKED_tex(clamp((edge_uv_v + off_v1 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb +
            HOOKED_tex(clamp((edge_uv_v - off_v1 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb) * W1;
    c_v += (HOOKED_tex(clamp((edge_uv_v + off_v2 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb +
            HOOKED_tex(clamp((edge_uv_v - off_v2 - 0.5) * inv_scale + 0.5, safe_min, safe_max)).rgb) * W2;
    c_v *= INV_TOTAL_W;

    mediump vec3 c;
    if (dh <= 0.0) {
        c = c_h;
    } else if (dv <= 0.0) {
        c = c_v;
    } else {
        // Corner diagonal smooth blend
        float wv = dv / (dv + dh);
        c = mix(c_h, c_v, wv);
    }

    c = rolloff(vibrance(c, 1.15), 0.55);

    mediump float f = pow(1.0 - dn, 1.8);
    mediump vec3 ambient_rgb = c * (f * 0.66);

${buildEpilogue()}
}
    """.trimIndent()
  }

  /**
   * Mirror Mode (Mode 4 from fx_ambient.frag):
   * The frame standing on polished glass: continues as its own reflection, sharp at the
   * seam and dissolving with distance.
   */
  fun buildMirror(spec: AmbientGlowShaderSpec): String {
    val tapsTable = buildSpiralTapTable(16, 0.28f)

    return """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode (Mirror)

#ifdef GL_ES
precision mediump float;
precision highp int;
#else
#define mediump
#define highp
#define lowp
#endif

#define BLUR_SAMPLES     16
#define SCALE_X          ${glslFloat(spec.context.scaleX)}
#define SCALE_Y          ${glslFloat(spec.context.scaleY)}

$tapsTable

$GLSL_FX_COMMON

vec4 hook() {
${buildPrologue()}

    // Reach from fx_ambient: mix(0.5, 1.25, 0.5) = 0.875
    highp float dn = d / (thick * 0.875);
    if (dn >= 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    // Fold coordinate across whichever edge p is beyond to create reflection
    highp vec2 ref_uv = video_uv;
    ref_uv = mix(ref_uv, -ref_uv, step(ref_uv, vec2(0.0)));
    ref_uv = mix(ref_uv, 2.0 - ref_uv, step(vec2(1.0), ref_uv));
    ref_uv = clamp(ref_uv, 0.0, 1.0);

    // Reflection blur dissolves with distance from seam
    mediump float blur_rad = pow(clamp(dn, 0.0, 1.0), 0.7) * 0.08 * 0.28;
    mediump float jitter = ign(uv * screen) * 6.2831853;
    highp float tap_scale = blur_rad / 0.28;
    highp mat2 rot = mat2(cos(jitter), sin(jitter), -sin(jitter), cos(jitter)) * tap_scale;

    mediump vec3 acc_c = vec3(0.0);
    mediump float acc_w = 0.0;

    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = FX_TAPS[i];
        highp vec2 tap_off = rot * tap.xy;

        highp vec2 s_uv = clamp(ref_uv + tap_off, 0.0, 1.0);
        highp vec2 hooked_uv = (s_uv - 0.5) * inv_scale + 0.5;
        mediump vec3 rgb = HOOKED_tex(clamp(hooked_uv, safe_min, safe_max)).rgb;

        mediump float wt = tap.z;
        acc_c += rgb * wt;
        acc_w += wt;
    }

    mediump vec3 c = acc_c / max(acc_w, 1e-5);
    c = rolloff(vibrance(c, 0.92), 0.30);

    mediump float f = pow(1.0 - dn, 2.0) * (0.55 + 0.45 * exp(-6.0 * dn));
    // A floor reflects more than a ceiling
    if (p.y < rect.y) f *= 0.7;

    mediump vec3 ambient_rgb = c * (f * 0.45);

${buildEpilogue()}
}
    """.trimIndent()
  }

  /**
   * Ambilight Mode (Mode 1 from fx_ambient.frag):
   * Edge-sampled soft emitter curve with bright core relaxing into a long quiet tail,
   * pulling light deeper into the picture as distance increases.
   */
  fun buildAmbilight(spec: AmbientGlowShaderSpec): String {
    val tapsTable = buildSpiralTapTable(18, 0.28f)

    return """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode (Ambilight)

#ifdef GL_ES
precision mediump float;
precision highp int;
#else
#define mediump
#define highp
#define lowp
#endif

#define BLUR_SAMPLES     18
#define SCALE_X          ${glslFloat(spec.context.scaleX)}
#define SCALE_Y          ${glslFloat(spec.context.scaleY)}

$tapsTable

$GLSL_FX_COMMON

vec4 hook() {
${buildPrologue()}

    // Reach from fx_ambient: mix(0.45, 1.3, 0.5) = 0.875
    highp float dn = d / (thick * 0.875);
    if (dn >= 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    // Edge UV with depth pull: light from further out comes deeper from inside the video
    highp vec2 edge_uv = (q - rect.xy) / vid_size;
    edge_uv = mix(edge_uv, vec2(0.5), 0.03 + 0.10 * dn);

    // Blur grows with distance from the edge
    mediump float blur_rad = (18.0 + 0.85 * d) / max(min(vid_size.x, vid_size.y), 1.0) * 0.35;
    mediump float jitter = ign(uv * screen) * 6.2831853;
    highp float tap_scale = blur_rad / 0.28;
    highp mat2 rot = mat2(cos(jitter), sin(jitter), -sin(jitter), cos(jitter)) * tap_scale;

    mediump vec3 acc_c = vec3(0.0);
    mediump float acc_w = 0.0;

    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = FX_TAPS[i];
        highp vec2 tap_off = rot * tap.xy;

        highp vec2 sample_vid_uv = clamp(edge_uv + tap_off, 0.0, 1.0);
        highp vec2 sample_hooked_uv = (sample_vid_uv - 0.5) * inv_scale + 0.5;
        mediump vec3 rgb = HOOKED_tex(clamp(sample_hooked_uv, safe_min, safe_max)).rgb;

        mediump float wt = tap.z;
        acc_c += rgb * wt;
        acc_w += wt;
    }

    mediump vec3 c = acc_c / max(acc_w, 1e-5);
    c = rolloff(vibrance(c, 1.18), 0.55);

    // Soft emitter curve: bright core relaxing into a long, quiet tail
    mediump float f = pow(1.0 - dn, 2.4) * (0.6 + 0.4 * exp(-5.0 * dn));
    mediump vec3 ambient_rgb = c * (f * 0.775);

${buildEpilogue()}
}
    """.trimIndent()
  }

  fun build(spec: AmbientGlowShaderSpec): String = AmbientShaderBuilder.build(spec)
}
