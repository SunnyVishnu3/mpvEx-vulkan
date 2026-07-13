package app.gyrolet.mpvrx.preferences

/**
 * How subtitles are drawn on screen.
 *
 * - [GPU]    : Standard libmpv behaviour — subtitles are rendered by libass/libplacebo on the
 *              GPU. Full ASS/SSA typesetting, positioning and karaoke are preserved.
 * - [NATIVE] : mpv's on-screen subtitle rendering is disabled (`sub-visibility=no`) but the
 *              track keeps decoding, and the current line is streamed from the `sub-text`
 *              property into a Jetpack Compose `Text` overlay (ExoPlayer-style). Works for every
 *              subtitle format (ASS/SSA text is delivered tag-stripped); complex typesetting is
 *              flattened to plain lines, but styling (size/colour/outline/shadow/position) is
 *              driven by the same subtitle-customisation properties as GPU mode.
 */
enum class SubtitleRenderMode {
  GPU,
  NATIVE,
}
