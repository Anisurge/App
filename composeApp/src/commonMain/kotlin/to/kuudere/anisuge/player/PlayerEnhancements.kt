package to.kuudere.anisuge.player

import kotlinx.serialization.Serializable

private fun anime4kHqChain(
    first: String,
    second: String? = null,
    restore: Boolean = true,
): List<String> = buildList {
    add("Anime4K_Clamp_Highlights.glsl")
    add(first)
    if (restore) add("Anime4K_Upscale_CNN_x2_VL.glsl")
    second?.let(::add)
    add("Anime4K_AutoDownscalePre_x2.glsl")
    add("Anime4K_AutoDownscalePre_x4.glsl")
    add("Anime4K_Upscale_CNN_x2_M.glsl")
}

@Serializable
data class PlayerEnhancementSettings(
    val shaderPreset: String = ShaderPreset.OFF.id,
    val colorPreset: String = ColorPreset.NATURAL.id,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val gamma: Int = 0,
    val hue: Int = 0,
    val deband: Boolean = false,
    val interpolationMode: String = "off",
    val interpolationQuality: String = "oversample",
    val scale: String = "bilinear",
    val cscale: String = "bilinear",
    val dscale: String = "bilinear",
    val ditherDepth: String = "auto",
    val temporalDither: Boolean = false,
    val toneMapping: String = "auto",
    val videoSync: String = "audio",
    val decoder: String = "auto",
) {
    fun sanitized(): PlayerEnhancementSettings = copy(
        shaderPreset = ShaderPreset.fromId(shaderPreset).id,
        colorPreset = ColorPreset.fromId(colorPreset).id,
        brightness = brightness.coerceIn(-100, 100),
        contrast = contrast.coerceIn(-100, 100),
        saturation = saturation.coerceIn(-100, 100),
        gamma = gamma.coerceIn(-100, 100),
        hue = hue.coerceIn(-100, 100),
        scale = scale.takeIf { it in PlayerEnhancementOptions.scalers } ?: "bilinear",
        cscale = cscale.takeIf { it in PlayerEnhancementOptions.scalers } ?: "bilinear",
        dscale = dscale.takeIf { it in PlayerEnhancementOptions.downscalers } ?: "bilinear",
        ditherDepth = ditherDepth.takeIf { it in PlayerEnhancementOptions.ditherDepths } ?: "auto",
        toneMapping = toneMapping.takeIf { it in PlayerEnhancementOptions.toneMappings } ?: "auto",
        videoSync = videoSync.takeIf { it in PlayerEnhancementOptions.videoSyncModes } ?: "audio",
        decoder = decoder.takeIf { it in PlayerEnhancementOptions.decoders } ?: "auto",
        interpolationMode = interpolationMode.takeIf { it in PlayerEnhancementOptions.interpolationModes } ?: "off",
        interpolationQuality = interpolationQuality.takeIf { it in PlayerEnhancementOptions.interpolationQualities } ?: "oversample",
    )

    fun withColorPreset(preset: ColorPreset): PlayerEnhancementSettings = copy(
        colorPreset = preset.id,
        brightness = preset.brightness,
        contrast = preset.contrast,
        saturation = preset.saturation,
        gamma = preset.gamma,
        hue = preset.hue,
    )

    companion object {
        val DEFAULT = PlayerEnhancementSettings()
    }
}

enum class ShaderCost(val label: String) {
    NONE("Off"),
    FAST("Fast"),
    HEAVY("Heavy"),
    VERY_HEAVY("Very Heavy"),
}

enum class ShaderPreset(
    val id: String,
    val label: String,
    val cost: ShaderCost,
    val files: List<String>,
) {
    OFF("off", "Off", ShaderCost.NONE, emptyList()),
    MODE_A_FAST(
        "mode_a_fast", "Anime4K: Mode A (Fast)", ShaderCost.FAST,
        listOf("Anime4K_Clamp_Highlights.glsl", "Anime4K_Restore_CNN_M.glsl", "Anime4K_Upscale_CNN_x2_M.glsl"),
    ),
    MODE_B_FAST(
        "mode_b_fast", "Anime4K: Mode B (Fast)", ShaderCost.FAST,
        listOf("Anime4K_Clamp_Highlights.glsl", "Anime4K_Restore_CNN_Soft_M.glsl", "Anime4K_Upscale_CNN_x2_M.glsl"),
    ),
    MODE_C_FAST(
        "mode_c_fast", "Anime4K: Mode C (Fast)", ShaderCost.FAST,
        listOf("Anime4K_Clamp_Highlights.glsl", "Anime4K_Upscale_Denoise_CNN_x2_M.glsl"),
    ),
    MODE_AA_FAST(
        "mode_aa_fast", "Anime4K: Mode A+A (Fast)", ShaderCost.HEAVY,
        listOf(
            "Anime4K_Clamp_Highlights.glsl", "Anime4K_Restore_CNN_VL.glsl",
            "Anime4K_Upscale_CNN_x2_VL.glsl", "Anime4K_Restore_CNN_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    MODE_BB_FAST(
        "mode_bb_fast", "Anime4K: Mode B+B (Fast)", ShaderCost.HEAVY,
        listOf(
            "Anime4K_Clamp_Highlights.glsl", "Anime4K_Restore_CNN_Soft_VL.glsl",
            "Anime4K_Upscale_CNN_x2_VL.glsl", "Anime4K_Restore_CNN_Soft_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    MODE_CA_FAST(
        "mode_ca_fast", "Anime4K: Mode C+A (Fast)", ShaderCost.HEAVY,
        listOf(
            "Anime4K_Clamp_Highlights.glsl", "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
            "Anime4K_Restore_CNN_M.glsl", "Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    MODE_A_HQ(
        "mode_a_hq", "Anime4K: Mode A (HQ)", ShaderCost.HEAVY,
        anime4kHqChain("Anime4K_Restore_CNN_VL.glsl"),
    ),
    MODE_B_HQ(
        "mode_b_hq", "Anime4K: Mode B (HQ)", ShaderCost.HEAVY,
        anime4kHqChain("Anime4K_Restore_CNN_Soft_VL.glsl"),
    ),
    MODE_C_HQ(
        "mode_c_hq", "Anime4K: Mode C (HQ)", ShaderCost.HEAVY,
        anime4kHqChain("Anime4K_Upscale_Denoise_CNN_x2_VL.glsl", restore = false),
    ),
    MODE_AA_HQ(
        "mode_aa_hq", "Anime4K: Mode A+A (HQ)", ShaderCost.VERY_HEAVY,
        anime4kHqChain("Anime4K_Restore_CNN_VL.glsl", "Anime4K_Restore_CNN_M.glsl"),
    ),
    MODE_BB_HQ(
        "mode_bb_hq", "Anime4K: Mode B+B (HQ)", ShaderCost.VERY_HEAVY,
        anime4kHqChain("Anime4K_Restore_CNN_Soft_VL.glsl", "Anime4K_Restore_CNN_Soft_M.glsl"),
    ),
    MODE_CA_HQ(
        "mode_ca_hq", "Anime4K: Mode C+A (HQ)", ShaderCost.VERY_HEAVY,
        anime4kHqChain(
            "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
            "Anime4K_Restore_CNN_M.glsl",
            restore = false,
        ),
    );

    companion object {
        fun fromId(id: String): ShaderPreset = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class ColorPreset(
    val id: String,
    val label: String,
    val brightness: Int,
    val contrast: Int,
    val saturation: Int,
    val gamma: Int,
    val hue: Int,
) {
    NATURAL("natural", "Natural", 0, 0, 0, 0, 0),
    ANIME("anime", "Anime", 2, 10, 12, 0, 0),
    CINEMA("cinema", "Cinema", -2, 12, 6, 4, 0),
    VIVID("vivid", "Vivid", 2, 14, 18, 0, 0),
    DARK("dark", "Dark room", -8, 8, -2, 8, 0),
    WARM("warm", "Warm", 1, 5, 6, 1, 5),
    COOL("cool", "Cool", 1, 5, 5, 1, -5),
    GRAYSCALE("grayscale", "Grayscale", 0, 8, -100, 2, 0),
    CUSTOM("custom", "Custom", 0, 0, 0, 0, 0);

    companion object {
        fun fromId(id: String): ColorPreset = entries.firstOrNull { it.id == id } ?: NATURAL
    }
}

object PlayerEnhancementOptions {
    val scalers = listOf("bilinear", "bicubic_fast", "lanczos", "ewa_lanczossharp")
    val downscalers = listOf("bilinear", "bicubic", "mitchell", "lanczos")
    val ditherDepths = listOf("auto", "8", "10")
    val toneMappings = listOf("auto", "clip", "mobius", "reinhard", "hable")
    val videoSyncModes = listOf("audio", "display-resample", "display-vdrop")
    val decoders = listOf("auto", "no")
    val interpolationModes = listOf("off", "auto", "60", "90", "120")
    val interpolationQualities = listOf("oversample", "mitchell", "spline36", "ginseng")
}

fun PlayerEnhancementSettings.mpvProperties(): Map<String, String> = buildMap {
    put("brightness", brightness.toString())
    put("contrast", contrast.toString())
    put("saturation", saturation.toString())
    put("gamma", gamma.toString())
    put("hue", hue.toString())
    put("deband", if (deband) "yes" else "no")
    put("deband-iterations", if (deband) "2" else "1")
    put("deband-threshold", if (deband) "48" else "64")
    put("scale", scale)
    put("cscale", cscale)
    put("dscale", dscale)
    put("dither-depth", ditherDepth)
    put("temporal-dither", if (temporalDither) "yes" else "no")
    put("tone-mapping", toneMapping)
    put("hwdec", decoder)

    when (interpolationMode) {
        "off" -> {
            put("interpolation", "no")
            put("video-sync", videoSync)
        }
        "auto" -> {
            put("interpolation", "yes")
            put("video-sync", "display-resample")
            put("tscale", interpolationQuality)
        }
        else -> {
            put("interpolation", "yes")
            put("video-sync", "display-resample")
            put("tscale", interpolationQuality)
            put("display-fps-override", interpolationMode)
        }
    }
}
