package com.auramusic.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.AppThemeMode
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.LyricsStyle
import com.auramusic.app.domain.model.ThemeColorSource
import com.auramusic.app.ui.components.SystemBarEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

// Encore Modern Design Tokens (Bespoke Obsidian + Electric Lime + Pastel Lilac)
val AuraNeonLime = Color(0xFFD4F644) // Electric Chartreuse / Neon Lime
val AuraLilacCard = Color(0xFFD6C7FF) // Pastel Lavender / Lilac Hero Card
val AuraLilacDarkText = Color(0xFF1D1438) // Deep Indigo text on Lilac card
val AuraLilacButton = Color(0xFF261947) // Deep Violet Play button on Lilac card
val AuraObsidianBg = Color(0xFF121316) // Deep warm Obsidian charcoal background
val AuraDarkSurface = Color(0xFF181A20) // Floating docks, surface containers
val AuraCardDark = Color(0xFF1E2026) // Card containers & list items
val AuraCardBorder = Color(0xFF2A2D36) // Subtle 1dp container borders
val AuraCircleButtonBg = Color(0xFF22242B) // Circular header and action buttons

// Semantic compatibility aliases
val SpotifyPitchBlack = AuraObsidianBg
val SpotifySurface = AuraDarkSurface
val SpotifySurfaceLight = Color(0xFF1F2128)
val SpotifyCard = AuraCardDark
val SpotifyCardElevated = Color(0xFF252731)
val SpotifyHeroCard = AuraLilacCard

val AuraSageGreen = Color(0xFFBCE354)
val AuraPlayGreen = AuraNeonLime // Primary brand accent across player and controls
val SpotifyGreen = AuraNeonLime
val AuraViolet = Color(0xFF8B5CF6)
val AuraMagenta = Color(0xFFEC4899)
val AuraCyan = Color(0xFF06B6D4)

val PillActiveBg = AuraNeonLime // Active capsule filter pill
val PillActiveText = Color(0xFF000000)
val PillInactiveBg = Color(0xFF1E1F25)
val PillInactiveText = Color(0xFF9E9EA7)
val AuraPillInactive = PillInactiveBg
val AuraPillInactiveText = PillInactiveText

val TextPrimary = Color(0xFFFFFFFF)
val TextCream = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF8E909A)
val TextMuted = Color(0xFF6B7280)
val BadgeLyricsBg = AuraNeonLime
val BadgeExplicitBg = Color(0xFF6B7280)

val AuraDarkScheme = darkColorScheme(
    primary = AuraNeonLime,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF283308),
    onPrimaryContainer = AuraNeonLime,
    secondary = AuraLilacCard,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2B2246),
    onSecondaryContainer = AuraLilacCard,
    tertiary = PillActiveBg,
    background = AuraObsidianBg,
    onBackground = TextPrimary,
    surface = AuraDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = AuraCardDark,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = AuraDarkSurface,
    surfaceContainer = AuraCardDark,
    surfaceContainerHigh = SpotifyCardElevated,
    surfaceContainerHighest = Color(0xFF2B2E3A),
    outline = AuraCardBorder
)

// Crisp Alabaster Light Theme Tokens
val AuraLightBg = Color(0xFFF8F8FA)
val AuraLightSurface = Color(0xFFFFFFFF)
val AuraLightSurfaceVariant = Color(0xFFF0F1F5)
val AuraLightCard = Color(0xFFFFFFFF)
val AuraLightCardBorder = Color(0xFFE4E6EE)
val AuraLightTextPrimary = Color(0xFF111216)
val AuraLightTextSecondary = Color(0xFF5A6070)
val AuraLightAccent = Color(0xFF4D6B00)

val AuraLightScheme = lightColorScheme(
    primary = AuraLightAccent,
    onPrimary = Color.White,
    primaryContainer = AuraNeonLime,
    onPrimaryContainer = Color(0xFF141C00),
    secondary = Color(0xFF6750A4),
    onSecondary = Color.White,
    secondaryContainer = AuraLilacCard,
    onSecondaryContainer = AuraLilacDarkText,
    tertiary = Color(0xFF006874),
    onTertiary = Color.White,
    background = AuraLightBg,
    onBackground = AuraLightTextPrimary,
    surface = AuraLightSurface,
    onSurface = AuraLightTextPrimary,
    surfaceVariant = AuraLightSurfaceVariant,
    onSurfaceVariant = AuraLightTextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F5F8),
    surfaceContainer = Color(0xFFEEEFF4),
    surfaceContainerHigh = Color(0xFFE7E9F0),
    surfaceContainerHighest = Color(0xFFDFE2EB),
    outline = AuraLightCardBorder,
    outlineVariant = Color(0xFFD5D8E2)
)

/**
 * 10 curated accent swatches for [ThemeColorSource.CUSTOM] selection in Appearance settings.
 */
val AURA_CUSTOM_ACCENT_SWATCHES: List<Pair<String, Long>> = listOf(
    "Neon Lime" to 0xFFD4F644L,
    "Aura Violet" to 0xFF8B5CF6L,
    "Apple Rose" to 0xFFFA2D48L,
    "Emerald Sage" to 0xFF10B981L,
    "Ocean Cyan" to 0xFF06B6D4L,
    "Sunset Amber" to 0xFFF59E0BL,
    "Royal Indigo" to 0xFF6366F1L,
    "Magenta Pulse" to 0xFFEC4899L,
    "Warm Coral" to 0xFFFF6B4AL,
    "Teal Mint" to 0xFF14B8A6L,
)

val AuraPoppinsFontFamily = FontFamily(
    Font(com.auramusic.app.R.font.aura_poppins_regular, FontWeight.Normal),
    Font(com.auramusic.app.R.font.aura_poppins_medium, FontWeight.Medium),
    Font(com.auramusic.app.R.font.aura_poppins_medium, FontWeight.SemiBold),
    Font(com.auramusic.app.R.font.aura_poppins_medium, FontWeight.Bold),
    Font(com.auramusic.app.R.font.aura_poppins_bold, FontWeight.ExtraBold),
    Font(com.auramusic.app.R.font.aura_poppins_bold, FontWeight.Black)
)

private fun buildAuraTypography(isDark: Boolean): Typography {
    val primaryText = if (isDark) TextPrimary else AuraLightTextPrimary
    val secondaryText = if (isDark) TextSecondary else AuraLightTextSecondary
    val accentText = if (isDark) AuraSageGreen else AuraLightAccent
    return Typography(
        displayLarge = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
            color = primaryText
        ),
        displayMedium = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = primaryText
        ),
        displaySmall = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            color = primaryText
        ),
        headlineLarge = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
            color = primaryText
        ),
        headlineMedium = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.3).sp,
            color = primaryText
        ),
        headlineSmall = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = primaryText
        ),
        titleLarge = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.3).sp,
            color = primaryText
        ),
        titleMedium = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = primaryText
        ),
        titleSmall = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = primaryText
        ),
        bodyLarge = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = primaryText
        ),
        bodyMedium = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = secondaryText
        ),
        bodySmall = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = secondaryText
        ),
        labelLarge = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = primaryText
        ),
        labelMedium = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = secondaryText
        ),
        labelSmall = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            color = accentText
        )
    )
}

val AuraTypography = buildAuraTypography(isDark = true)
private val AuraLightTypography = buildAuraTypography(isDark = false)

val LocalLyricsStyle = staticCompositionLocalOf {
    if (isLyricsBlurSupported()) LyricsStyle.APPLE_MUSIC else LyricsStyle.CLASSIC
}

val LocalAuraThemeStyle = staticCompositionLocalOf {
    AppThemeStyle.LIQUID_GLASS
}

private fun applyCustomAccent(
    baseScheme: ColorScheme,
    accentArgb: Int,
    isDark: Boolean
): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accentArgb, hsl)

    // Ensure readable accent saturation & lightness against the active surface polarity
    val adjustedPrimaryHsl = hsl.copyOf().apply {
        if (!isDark && this[2] > 0.52f) {
            // Deepen bright pastel/neon swatches in Light mode so icons & text maintain WCAG contrast
            this[2] = 0.36f
        } else if (isDark && this[2] < 0.35f) {
            this[2] = 0.55f
        }
    }
    val adjustedPrimaryArgb = ColorUtils.HSLToColor(adjustedPrimaryHsl)
    val primaryColor = Color(adjustedPrimaryArgb)
    val onPrimaryColor = if (ColorUtils.calculateLuminance(adjustedPrimaryArgb) > 0.18) {
        Color.Black
    } else {
        Color.White
    }

    val containerHsl = hsl.copyOf().apply {
        this[1] = (this[1] * 0.72f).coerceIn(0.15f, 0.75f)
        this[2] = if (isDark) 0.22f else 0.90f
    }
    val containerColor = Color(ColorUtils.HSLToColor(containerHsl))
    val onContainerColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111216)

    val secondaryHsl = hsl.copyOf().apply {
        this[0] = (this[0] + 28f) % 360f
        this[1] = (this[1] * 0.65f).coerceIn(0.20f, 0.75f)
        this[2] = if (isDark) 0.76f else 0.42f
    }
    val secondaryArgb = ColorUtils.HSLToColor(secondaryHsl)
    val secondaryColor = Color(secondaryArgb)
    val onSecondaryColor = if (ColorUtils.calculateLuminance(secondaryArgb) > 0.18) {
        Color.Black
    } else {
        Color.White
    }

    return baseScheme.copy(
        primary = primaryColor,
        onPrimary = onPrimaryColor,
        primaryContainer = containerColor,
        onPrimaryContainer = onContainerColor,
        secondary = secondaryColor,
        onSecondary = onSecondaryColor,
        tertiary = primaryColor
    )
}

@Composable
private fun resolveAuraColorScheme(
    isDark: Boolean,
    colorSource: ThemeColorSource,
    customAccentArgb: Int?
): ColorScheme {
    val baseScheme = if (isDark) AuraDarkScheme else AuraLightScheme
    return when (colorSource) {
        ThemeColorSource.WALLPAPER -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isWallpaperDynamicColorSupported()) {
                val context = LocalContext.current
                val dynamic = if (isDark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
                // Blend wallpaper Material You accents with Aura's crisp Dark/Light surface hierarchy
                baseScheme.copy(
                    primary = dynamic.primary,
                    onPrimary = dynamic.onPrimary,
                    primaryContainer = dynamic.primaryContainer,
                    onPrimaryContainer = dynamic.onPrimaryContainer,
                    secondary = dynamic.secondary,
                    onSecondary = dynamic.onSecondary,
                    secondaryContainer = dynamic.secondaryContainer,
                    onSecondaryContainer = dynamic.onSecondaryContainer,
                    tertiary = dynamic.tertiary,
                    onTertiary = dynamic.onTertiary,
                    tertiaryContainer = dynamic.tertiaryContainer,
                    onTertiaryContainer = dynamic.onTertiaryContainer
                )
            } else {
                baseScheme
            }
        }
        ThemeColorSource.CUSTOM -> {
            if (customAccentArgb != null) {
                applyCustomAccent(baseScheme, customAccentArgb, isDark)
            } else {
                baseScheme
            }
        }
        ThemeColorSource.DEFAULT -> baseScheme
    }
}

@Composable
fun AuraMusicTheme(
    liquidGlassEnabled: Boolean = true,
    lyricsStyle: LyricsStyle = if (isLyricsBlurSupported()) LyricsStyle.APPLE_MUSIC else LyricsStyle.CLASSIC,
    isDarkTheme: Boolean? = null,
    themeMode: AppThemeMode = when (isDarkTheme) {
        false -> AppThemeMode.LIGHT
        true, null -> AppThemeMode.DARK
    },
    colorSource: ThemeColorSource = ThemeColorSource.DEFAULT,
    customAccentArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val resolvedIsDark = when {
        isDarkTheme != null -> isDarkTheme
        themeMode == AppThemeMode.DARK -> true
        themeMode == AppThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = resolveAuraColorScheme(
        isDark = resolvedIsDark,
        colorSource = colorSource,
        customAccentArgb = customAccentArgb
    )
    val typography = if (resolvedIsDark) AuraTypography else AuraLightTypography
    val themeStyle = if (liquidGlassEnabled) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalLiquidGlassEnabled provides liquidGlassEnabled,
            LocalLyricsStyle provides lyricsStyle,
            LocalIsDarkTheme provides resolvedIsDark,
            LocalAuraThemeStyle provides themeStyle,
        ) {
            SystemBarEffect(isDarkTheme = resolvedIsDark)
            content()
        }
    }
}

@Composable
fun AuraTheme(
    liquidGlassEnabled: Boolean = true,
    lyricsStyle: LyricsStyle = if (isLyricsBlurSupported()) LyricsStyle.APPLE_MUSIC else LyricsStyle.CLASSIC,
    isDarkTheme: Boolean? = null,
    themeMode: AppThemeMode = when (isDarkTheme) {
        false -> AppThemeMode.LIGHT
        true, null -> AppThemeMode.DARK
    },
    colorSource: ThemeColorSource = ThemeColorSource.DEFAULT,
    customAccentArgb: Int? = null,
    content: @Composable () -> Unit
) = AuraMusicTheme(
    liquidGlassEnabled = liquidGlassEnabled,
    lyricsStyle = lyricsStyle,
    isDarkTheme = isDarkTheme,
    themeMode = themeMode,
    colorSource = colorSource,
    customAccentArgb = customAccentArgb,
    content = content
)

/**
 * Automatically scans the cover art album and extracts dominant, vibrant, and ambient colors
 * using AndroidX Palette.
 */
@OptIn(ExperimentalCoilApi::class)
suspend fun extractAuraPaletteFromUrl(
    artworkUrl: String,
    context: Context? = null
): AuraPalette = withContext(Dispatchers.IO) {
    if (artworkUrl.isBlank()) return@withContext AuraPalette()
    try {
        // Use a 400x400 scaled URL for instant download & reliable palette extraction
        val scanUrl = if (artworkUrl.contains("googleusercontent.com") || artworkUrl.contains("ggpht.com")) {
            artworkUrl.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w400-h400-l90-rj")
        } else artworkUrl

        val localFileBitmap: Bitmap? = if (artworkUrl.startsWith("file://") || artworkUrl.startsWith("/")) {
            BitmapFactory.decodeFile(artworkUrl.removePrefix("file://"))
        } else null

        val cachedBitmap: Bitmap? = localFileBitmap ?: context?.let { ctx ->
            runCatching {
                val diskCache = Coil.imageLoader(ctx).diskCache
                val snap = diskCache?.openSnapshot(artworkUrl) ?: diskCache?.openSnapshot(scanUrl)
                snap?.use { BitmapFactory.decodeFile(it.data.toFile().absolutePath) }
            }.getOrNull()
        }

        val bitmap: Bitmap = cachedBitmap ?: run {
            val req = Request.Builder().url(scanUrl).build()
            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext AuraPalette()
                val bytes = resp.body?.bytes() ?: return@withContext AuraPalette()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext AuraPalette()
            }
        }
        run {
            val palette = Palette.from(bitmap).maximumColorCount(32).generate()

            val swatches = palette.swatches
            val dominant = palette.dominantSwatch
            val muted = palette.mutedSwatch

            // 1. Spotify Encore / Default theme color picker:
            // Score swatches by true area dominance + gentle chroma & midtone preference
            // so small 1% accent pixels never hijack the background, while monochrome covers
            // stay sleek neutral charcoal/slate instead of picking an unrelated hue.
            val totalPopulation = swatches.sumOf { it.population }.coerceAtLeast(1)
            val maxPopulation = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1

            val scoredSwatches = swatches.filter { s ->
                val hsl = s.hsl
                s.population >= (maxPopulation * 0.08f) && hsl[2] in 0.10f..0.88f
            }.sortedByDescending { s ->
                val hsl = s.hsl
                val areaShare = s.population.toFloat() / totalPopulation
                val chromaBoost = 1f + (hsl[1].coerceIn(0f, 0.85f) * 1.6f)
                val midLightnessBoost = 1f - kotlin.math.abs(hsl[2] - 0.45f) * 0.55f
                areaShare * chromaBoost * midLightnessBoost
            }

            val bestSwatch = scoredSwatches.firstOrNull()
                ?: dominant
                ?: muted
            val rawSpotifyColorInt = bestSwatch?.rgb ?: 0xFF8B5CF6.toInt()

            val baseHsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(rawSpotifyColorInt, baseHsl)

            // Primary (Spotify lyrics card & default mini-player): rich deep tone with AAA contrast
            val primaryHsl = baseHsl.copyOf().apply {
                this[1] = this[1].coerceAtMost(0.72f)
                this[2] = when {
                    this[2] > 0.30f -> 0.24f
                    this[2] < 0.14f -> 0.18f
                    else -> this[2]
                }
            }
            val coverColorInt = androidx.core.graphics.ColorUtils.HSLToColor(primaryHsl)

            // Ambient gradient top & bottom: coherently derived from the same dominant hue
            val ambientTopHsl = baseHsl.copyOf().apply {
                this[1] = this[1].coerceAtMost(0.65f)
                this[2] = this[2].coerceIn(0.16f, 0.24f)
            }
            val ambientBottomHsl = baseHsl.copyOf().apply {
                this[1] = this[1].coerceAtMost(0.48f)
                this[2] = this[2].coerceIn(0.06f, 0.09f)
            }
            val ambientTopInt = androidx.core.graphics.ColorUtils.HSLToColor(ambientTopHsl)
            val ambientBottomInt = androidx.core.graphics.ColorUtils.HSLToColor(ambientBottomHsl)

            // 2. Separate Apple Music blurred backdrop color:
            // Direct area-weighted pixel sampling of the artwork (matching the live blurred cover)
            val appleMusicColorInt = extractAppleMusicBackdropColor(bitmap, coverColorInt)

            AuraPalette(
                primary = Color(coverColorInt),
                secondary = Color(muted?.rgb ?: coverColorInt),
                backgroundTop = Color(ambientTopInt),
                backgroundBottom = Color(ambientBottomInt),
                appleMusicBackdrop = Color(appleMusicColorInt)
            )
        }
    } catch (e: Exception) {
        AuraPalette()
    }
}

/**
 * Extracts a dedicated ambient color for Apple Music's blurred artwork backdrop & collapse
 * transition by directly sampling a downscaled grid of the cover art with gentle chroma weighting.
 */
private fun extractAppleMusicBackdropColor(bitmap: Bitmap, fallbackRgb: Int): Int {
    val gridSize = 24
    val scaled = Bitmap.createScaledBitmap(bitmap, gridSize, gridSize, true)
    val pixels = IntArray(gridSize * gridSize)
    scaled.getPixels(pixels, 0, gridSize, 0, 0, gridSize, gridSize)
    if (scaled != bitmap) scaled.recycle()

    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var totalWeight = 0.0
    val hsl = FloatArray(3)

    for (y in 0 until gridSize) {
        for (x in 0 until gridSize) {
            val px = pixels[y * gridSize + x]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            androidx.core.graphics.ColorUtils.RGBToHSL(r, g, b, hsl)

            // Skip extreme near-black letterboxing and pure white borders
            if (hsl[2] < 0.05f || hsl[2] > 0.96f) continue

            // Center-weighted + mild chroma weight to reflect what the blurred cover visually looks like
            val dx = (x - gridSize / 2f) / (gridSize / 2f)
            val dy = (y - gridSize / 2f) / (gridSize / 2f)
            val centerWeight = (1.15f - 0.35f * (dx * dx + dy * dy)).coerceAtLeast(0.5f)
            val chromaWeight = 0.65f + hsl[1] * 1.15f
            val w = (centerWeight * chromaWeight).toDouble()

            sumR += r * w
            sumG += g * w
            sumB += b * w
            totalWeight += w
        }
    }

    if (totalWeight <= 0.0) return fallbackRgb

    val avgR = (sumR / totalWeight).toInt().coerceIn(0, 255)
    val avgG = (sumG / totalWeight).toInt().coerceIn(0, 255)
    val avgB = (sumB / totalWeight).toInt().coerceIn(0, 255)
    androidx.core.graphics.ColorUtils.RGBToHSL(avgR, avgG, avgB, hsl)
    hsl[1] = hsl[1].coerceAtMost(0.78f)
    hsl[2] = hsl[2].coerceIn(0.18f, 0.34f)
    return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
}
