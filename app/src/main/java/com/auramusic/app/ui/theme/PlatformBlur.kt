package com.auramusic.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * True Liquid Glass refraction (AGSL `RuntimeShader` lens distortion, chromatic aberration,
 * and shader vibrancy) requires Android 13 (API 33 / `VERSION_CODES.TIRAMISU`) or higher.
 */
fun isTrueLiquidGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Hardware frosted-glass blur (`RenderEffect.createBlurEffect` / Compose `Modifier.blur()`)
 * requires Android 12 (API 31 / `VERSION_CODES.S`) or higher.
 * On Android 12 (API 31–32), Liquid Glass surfaces fall back to this hardware frosted blur without
 * AGSL lens distortion; on Android 8–11 (API 26–30), they fall back to a static frosted-glass tint.
 */
fun isFrostedBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Compose `Modifier.blur()` uses `RenderEffect` (Android 12 / API 31+) and is a documented
 * no-op on earlier versions. Callers gate Apple Music blur-based lyrics on this check.
 */
fun isLyricsBlurSupported(): Boolean = isFrostedBlurSupported()

/**
 * Material You wallpaper dynamic color schemes (`dynamicDarkColorScheme` / `dynamicLightColorScheme`)
 * require Android 12 (API 31 / `VERSION_CODES.S`) or higher.
 */
fun isWallpaperDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Haze style for tinted floating/sticky bars (detail headers, top bars, lyrics headers).
 *
 * Uses Haze's default `blurEnabled` behavior (`RenderEffect` on supported Android versions and
 * solid `fallbackTint` below API 31) without forcing RenderScript blur, which can crash natively
 * on older devices during rapid screen transitions.
 */
fun barBlurStyle(
    tint: Color,
    tintAlpha: Float = 0.55f,
): HazeStyle =
    HazeStyle(
        backgroundColor = tint,
        tint = HazeTint(tint.copy(alpha = tintAlpha)),
        blurRadius = 24.dp,
        fallbackTint = HazeTint(tint.copy(alpha = 0.92f)),
    )

/**
 * [HazeMaterials.ultraThin] style for standard top bars with an opaque fallback tint below API 31.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ultraThinBarStyle(
    containerColor: Color = MaterialTheme.colorScheme.surface,
): HazeStyle =
    HazeMaterials.ultraThin(containerColor).copy(
        fallbackTint = HazeTint(containerColor),
    )

/**
 * Convenience wrapper around Haze's [Modifier.hazeEffect] for blurred chrome bars.
 */
fun Modifier.hazeBlurBar(
    state: HazeState,
    style: HazeStyle,
): Modifier = hazeEffect(state = state, style = style)
