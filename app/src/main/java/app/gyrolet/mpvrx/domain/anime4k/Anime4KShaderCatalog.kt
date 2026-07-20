package app.gyrolet.mpvrx.domain.anime4k

/** Single source of truth for the Anime4K shaders bundled with the app. */
object Anime4KShaderCatalog {
  const val ASSET_DIRECTORY = "shaders/anime4k"
  const val INSTALL_DIRECTORY = "shaders/anime4k"

  val requiredFiles = listOf(
    "Anime4K_Clamp_Highlights.glsl",
    "Anime4K_AutoDownscalePre_x2.glsl",
    "Anime4K_Restore_CNN_S.glsl",
    "Anime4K_Restore_CNN_M.glsl",
    "Anime4K_Restore_CNN_L.glsl",
    "Anime4K_Restore_CNN_Soft_S.glsl",
    "Anime4K_Restore_CNN_Soft_M.glsl",
    "Anime4K_Restore_CNN_Soft_L.glsl",
    "Anime4K_Upscale_CNN_x2_S.glsl",
    "Anime4K_Upscale_CNN_x2_M.glsl",
    "Anime4K_Upscale_CNN_x2_L.glsl",
    "Anime4K_Upscale_Denoise_CNN_x2_S.glsl",
    "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
    "Anime4K_Upscale_Denoise_CNN_x2_L.glsl",
    "Anime4K_Darken_Fast.glsl",
    "Anime4K_Darken_HQ.glsl",
    "Anime4K_Darken_VeryFast.glsl",
    "Anime4K_Thin_Fast.glsl",
    "Anime4K_Thin_HQ.glsl",
    "Anime4K_Thin_VeryFast.glsl",
    "Anime4K_Deblur_DoG.glsl",
    "Anime4K_Deblur_Original.glsl",
    "Ani4Kv2_ArtCNN_C4F32_i2_CMP.glsl",
    // ── Anime4K Ultra shaders ──────────────────────────────────────────────
    // Each file is a multi-pass shader combining FSR (EASU+RCAS) with CNN/Thin
    // passes. The FP16 optimizer handles them correctly: passNeedsHighpFloat
    // detects floatBitsToUint/uintBitsToFloat in FSR passes and forces highp,
    // while the CNN/Thin convolution passes are optimized to mediump.
    "Anime4K-Ultra.glsl",
    "Anime4K-Ultra_DbH.glsl",
    "Anime4K-Ultra_DbH_Sharp.glsl",
    "Anime4K-Ultra_DbL.glsl",
    "Anime4K-Ultra_DbM.glsl",
    "Anime4K-Ultra_Sh.glsl",
    "Anime4K-Ultra_SSh.glsl",
  )

  val requiredFileSet: Set<String> = requiredFiles.toSet()
}
