package com.auramusic.app.core.media

import java.util.Locale

/**
 * Curated 10-band ISO AutoEq headphone and IEM compensation profiles
 * aligned to the Harman 2018 over-ear / 2019 in-ear target curves.
 *
 * Band centers (Hz): 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000.
 */
object AutoEqRepository {

    data class HeadphonePreset(
        val name: String,
        val brand: String,
        val bandsDb: List<Float>
    )

    val curatedPresets: List<HeadphonePreset> = listOf(
        HeadphonePreset(
            name = "AirPods Pro 2",
            brand = "Apple",
            bandsDb = listOf(1.2f, 0.8f, -0.6f, -1.0f, 0.2f, 0.5f, 1.4f, 0.8f, -1.2f, 1.5f)
        ),
        HeadphonePreset(
            name = "AirPods Max",
            brand = "Apple",
            bandsDb = listOf(1.8f, 0.4f, -1.5f, -0.8f, 0.6f, 0.2f, 2.2f, 3.0f, -0.5f, 1.8f)
        ),
        HeadphonePreset(
            name = "Apple EarPods",
            brand = "Apple",
            bandsDb = listOf(5.8f, 4.5f, 1.5f, -1.2f, -1.5f, 0.4f, 1.8f, 2.5f, -1.0f, 2.0f)
        ),
        HeadphonePreset(
            name = "Sony WH-1000XM4",
            brand = "Sony",
            bandsDb = listOf(1.5f, -2.5f, -4.8f, -3.5f, 0.5f, 1.2f, 3.2f, 2.8f, 1.0f, 1.6f)
        ),
        HeadphonePreset(
            name = "Sony WH-1000XM5",
            brand = "Sony",
            bandsDb = listOf(1.8f, -1.4f, -3.6f, -2.4f, 0.4f, 0.8f, 2.8f, 2.4f, 1.4f, 1.8f)
        ),
        HeadphonePreset(
            name = "Sony WF-1000XM5",
            brand = "Sony",
            bandsDb = listOf(0.8f, -0.6f, -2.2f, -1.5f, 0.2f, 0.5f, 2.4f, 3.2f, 0.6f, 1.5f)
        ),
        HeadphonePreset(
            name = "Sennheiser HD 600",
            brand = "Sennheiser",
            bandsDb = listOf(5.5f, 3.6f, 0.8f, -1.2f, -0.8f, 0.0f, -0.6f, -1.4f, 0.8f, 1.6f)
        ),
        HeadphonePreset(
            name = "Sennheiser HD 650",
            brand = "Sennheiser",
            bandsDb = listOf(5.2f, 3.2f, 0.2f, -1.8f, -1.0f, -0.2f, 0.6f, 0.4f, 1.2f, 2.0f)
        ),
        HeadphonePreset(
            name = "Sennheiser HD 660S2",
            brand = "Sennheiser",
            bandsDb = listOf(3.8f, 2.2f, 0.0f, -1.2f, -0.5f, 0.3f, 1.4f, 1.2f, -0.6f, 1.8f)
        ),
        HeadphonePreset(
            name = "Sennheiser Momentum 4",
            brand = "Sennheiser",
            bandsDb = listOf(0.5f, -1.8f, -3.0f, -1.6f, 0.4f, 0.8f, 2.1f, 1.5f, -0.8f, 1.2f)
        ),
        HeadphonePreset(
            name = "Bose QC45",
            brand = "Bose",
            bandsDb = listOf(2.4f, 0.6f, -1.2f, -0.8f, 0.4f, 0.2f, 1.6f, -1.2f, -3.2f, 1.0f)
        ),
        HeadphonePreset(
            name = "Bose QuietComfort Ultra",
            brand = "Bose",
            bandsDb = listOf(1.4f, -0.5f, -2.0f, -1.2f, 0.5f, 0.8f, 2.4f, 1.6f, -1.4f, 1.2f)
        ),
        HeadphonePreset(
            name = "Samsung Galaxy Buds2 Pro",
            brand = "Samsung",
            bandsDb = listOf(0.8f, 0.2f, -1.4f, -1.0f, 0.6f, 0.4f, 1.0f, -1.8f, -2.6f, 0.8f)
        ),
        HeadphonePreset(
            name = "Moondrop Chu II",
            brand = "Moondrop",
            bandsDb = listOf(0.4f, -0.4f, -1.0f, -0.6f, 0.4f, 0.8f, 0.2f, -1.2f, 1.4f, 0.6f)
        ),
        HeadphonePreset(
            name = "Moondrop Aria",
            brand = "Moondrop",
            bandsDb = listOf(0.8f, 0.2f, -0.8f, -0.6f, 0.5f, 0.6f, 0.4f, 0.8f, 1.6f, 1.2f)
        ),
        HeadphonePreset(
            name = "Truthear Hexa",
            brand = "Truthear",
            bandsDb = listOf(2.6f, 1.4f, 0.2f, -0.4f, 0.2f, 0.4f, 0.6f, -0.8f, -1.2f, 1.4f)
        ),
        HeadphonePreset(
            name = "Beyerdynamic DT 770 Pro",
            brand = "Beyerdynamic",
            bandsDb = listOf(2.2f, 0.8f, -0.6f, 1.8f, 0.6f, -0.4f, 1.8f, -1.5f, -4.6f, 0.8f)
        ),
        HeadphonePreset(
            name = "Beyerdynamic DT 990 Pro",
            brand = "Beyerdynamic",
            bandsDb = listOf(4.8f, 2.4f, -1.8f, -1.4f, 0.6f, 0.8f, 2.2f, -2.4f, -5.8f, -1.2f)
        ),
        HeadphonePreset(
            name = "Audio-Technica ATH-M50x",
            brand = "Audio-Technica",
            bandsDb = listOf(1.6f, -0.8f, -2.2f, -0.6f, 1.2f, 0.6f, 1.4f, -1.2f, -1.8f, 1.2f)
        ),
        HeadphonePreset(
            name = "Nothing Ear",
            brand = "Nothing",
            bandsDb = listOf(0.6f, -1.2f, -2.4f, -1.4f, 0.8f, 1.2f, 2.2f, 0.4f, -2.0f, 1.4f)
        ),
        HeadphonePreset(
            name = "Google Pixel Buds Pro",
            brand = "Google",
            bandsDb = listOf(1.0f, -0.8f, -2.0f, -1.2f, 0.6f, 1.0f, 2.8f, 2.0f, -0.8f, 1.6f)
        ),
        HeadphonePreset(
            name = "Beats Studio Pro",
            brand = "Beats",
            bandsDb = listOf(2.2f, 1.0f, -0.8f, -1.2f, 0.4f, 0.8f, 2.6f, 1.8f, -1.0f, 1.8f)
        ),
        HeadphonePreset(
            name = "Harman Over-Ear 2018",
            brand = "Harman",
            bandsDb = listOf(4.5f, 3.8f, 1.8f, 0.0f, -0.6f, 0.0f, 2.4f, 3.2f, 1.8f, 0.5f)
        ),
        HeadphonePreset(
            name = "Harman In-Ear 2019",
            brand = "Harman",
            bandsDb = listOf(5.8f, 4.4f, 1.6f, -0.4f, -0.8f, 0.2f, 3.4f, 4.2f, 2.0f, 0.8f)
        )
    )

    fun toProfileId(name: String): String {
        return name.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    fun toAutoEqProfile(preset: HeadphonePreset): AutoEqProfile {
        val maxBoost = preset.bandsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        return AutoEqProfile(
            id = toProfileId(preset.name),
            name = preset.name,
            source = "AutoEq (${preset.brand})",
            bandsDb = preset.bandsDb,
            preampDb = if (maxBoost > 0f) -maxBoost else 0f
        )
    }

    fun findByName(name: String): HeadphonePreset? {
        return curatedPresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Compatibility facade exposing [AutoEqProfile] entries directly for UI pickers.
 */
object AutoEqProfiles {
    val ALL_PROFILES: List<AutoEqProfile> = AutoEqRepository.curatedPresets.map {
        AutoEqRepository.toAutoEqProfile(it)
    }

    fun findById(id: String): AutoEqProfile? {
        return ALL_PROFILES.firstOrNull { it.id == id }
    }

    fun findByName(name: String): AutoEqProfile? {
        return ALL_PROFILES.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
