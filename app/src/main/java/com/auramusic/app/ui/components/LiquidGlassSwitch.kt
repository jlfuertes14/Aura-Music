package com.auramusic.app.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.glassBackdropSource
import com.auramusic.app.ui.theme.rememberGlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.roundToInt

/**
 * Adaptive toggle switch for Aura Settings:
 * - When [LocalLiquidGlassEnabled] is `true`: renders an iOS 26-inspired Liquid Glass pill toggle
 *   using a local sibling [rememberGlassBackdrop] over the glowing track so the sliding crystal
 *   thumb optically refracts the track underneath without any ancestor feedback loop:
 *     - Android 13+ (API 33+): real AGSL `lens` refraction + chromatic aberration + vibrancy + blur.
 *     - Android 12 (API 31–32): `RenderEffect` frosted blur + specular glass sheen (no AGSL shaders).
 *     - Android 8–11 (API 26–30): static frosted glass fill + specular rim highlight.
 * - When [LocalLiquidGlassEnabled] is `false`: renders the standard Material 3 [Switch].
 */
@Composable
fun AuraLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val auraColors = LocalAuraColors.current
    val isDark = auraColors.isDark

    if (!LocalLiquidGlassEnabled.current) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = auraColors.onAccent,
                checkedTrackColor = auraColors.accent,
                uncheckedThumbColor = if (isDark) Color.White.copy(alpha = 0.88f) else Color.White,
                uncheckedTrackColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFFD1D1D6),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val thumbHeight = 24.dp
    val horizontalPadding = 4.dp

    val thumbWidth by animateDpAsState(
        targetValue = if (isPressed) 32.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 460f),
        label = "liquidSwitchThumbWidth",
    )
    val thumbFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 440f),
        label = "liquidSwitchThumbFraction",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 420f),
        label = "liquidSwitchThumbScale",
    )
    val activeAccentEnd = lerp(auraColors.accent, Color(0xFF0E1014), if (isDark) 0.14f else 0.08f)
    val trackStartColor by animateColorAsState(
        targetValue = if (checked) {
            auraColors.accent.copy(alpha = if (isDark) 0.90f else 0.96f)
        } else if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color(0xFF787880).copy(alpha = 0.18f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "liquidSwitchTrackStart",
    )
    val trackEndColor by animateColorAsState(
        targetValue = if (checked) {
            activeAccentEnd.copy(alpha = if (isDark) 0.78f else 0.88f)
        } else if (isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color(0xFF787880).copy(alpha = 0.14f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "liquidSwitchTrackEnd",
    )
    val borderTopColor by animateColorAsState(
        targetValue = if (isDark) {
            if (checked) Color.White.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.34f)
        } else {
            Color.Black.copy(alpha = if (checked) 0.05f else 0.07f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "liquidSwitchBorderTop",
    )
    val borderBottomColor by animateColorAsState(
        targetValue = if (isDark) {
            if (checked) auraColors.accent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.08f)
        } else {
            Color.Black.copy(alpha = 0.04f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "liquidSwitchBorderBottom",
    )

    val supportsAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val switchBackdrop = rememberGlassBackdrop(Color.Transparent)

    // 44.dp touch target for mobile ergonomics
    Box(
        modifier = modifier
            .height(44.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(width = trackWidth, height = trackHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 1. Sibling glass backdrop source: luminous translucent capsule track
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (supportsHardwareBlur) {
                            Modifier.glassBackdropSource(switchBackdrop)
                        } else {
                            Modifier
                        },
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(trackStartColor, trackEndColor)),
                    )
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = if (checked) 0.24f else if (isDark) 0.14f else 0.30f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = if (isDark) 0.14f else 0.05f),
                        ),
                    )
                    .border(
                        width = if (isDark) 0.85.dp else 0.5.dp,
                        brush = Brush.verticalGradient(listOf(borderTopColor, borderBottomColor)),
                        shape = CircleShape,
                    ),
            )

            // 2. Sibling Liquid Glass thumb refracting the track underneath
            val maxTravel = (trackWidth - thumbWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val isGlassLensActive = isPressed || (thumbFraction > 0.04f && thumbFraction < 0.96f)
            val thumbBaseModifier = Modifier
                .padding(horizontal = horizontalPadding)
                .offset {
                    IntOffset(
                        x = (maxTravel.toPx() * thumbFraction).roundToInt(),
                        y = 0,
                    )
                }
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .width(thumbWidth)
                .height(thumbHeight)
                .shadow(
                    elevation = if (isPressed) 6.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.36f else 0.14f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.48f else 0.20f),
                )

            if (supportsHardwareBlur) {
                // Android 12+ (API 31-32 frosted blur) & Android 13+ (API 33+ real AGSL lens refraction)
                val restingWhiteAlpha = if (isGlassLensActive) {
                    if (isDark) 0.34f else 0.48f
                } else {
                    if (isDark) 0.78f else 0.94f
                }
                val thumbBorderColors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = if (isGlassLensActive) 0.30f else 0.48f),
                    )
                } else {
                    listOf(
                        Color.Black.copy(alpha = 0.04f),
                        Color.Black.copy(alpha = 0.09f),
                    )
                }
                Box(
                    modifier = thumbBaseModifier
                        .drawBackdrop(
                            backdrop = switchBackdrop,
                            shape = { CircleShape },
                            highlight = {
                                Highlight.Default.copy(
                                    alpha = if (isDark) {
                                        if (isGlassLensActive) 0.92f else 0.65f
                                    } else {
                                        if (isGlassLensActive) 0.55f else 0.30f
                                    },
                                )
                            },
                            effects = {
                                if (supportsAgsl) {
                                    vibrancy()
                                }
                                colorControls(
                                    brightness = if (isGlassLensActive) 0.10f else 0.06f,
                                    contrast = 1.05f,
                                    saturation = if (supportsAgsl) 1.55f else 1.25f,
                                )
                                blur((if (isGlassLensActive) 3.5f else 6f).dp.toPx())
                                if (supportsAgsl && size.minDimension > 0f) {
                                    val lensBoost = if (isGlassLensActive) 1.0f else 0.55f
                                    lens(
                                        refractionHeight = size.minDimension * 0.30f * lensBoost,
                                        refractionAmount = size.minDimension * 0.52f * lensBoost,
                                        chromaticAberration = isGlassLensActive,
                                    )
                                }
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = restingWhiteAlpha))
                            },
                        )
                        .border(
                            width = if (isDark) 0.85.dp else 0.5.dp,
                            brush = Brush.verticalGradient(thumbBorderColors),
                            shape = CircleShape,
                        ),
                )
            } else {
                // Android 8.0-11 static frosted crystal thumb
                val staticThumbColors = if (isDark) {
                    if (checked) {
                        listOf(Color(0xFFFFFFFF), Color(0xFFEAF3D4))
                    } else {
                        listOf(Color.White.copy(alpha = 0.95f), Color(0xFFD8DEE9).copy(alpha = 0.88f))
                    }
                } else {
                    listOf(Color(0xFFFFFFFF), Color(0xFFF6F6F8))
                }
                val staticThumbBorder = if (isDark) {
                    listOf(Color.White, Color.White.copy(alpha = 0.35f))
                } else {
                    listOf(Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.09f))
                }
                Box(
                    modifier = thumbBaseModifier
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(colors = staticThumbColors),
                        )
                        .border(
                            width = if (isDark) 0.85.dp else 0.5.dp,
                            brush = Brush.verticalGradient(staticThumbBorder),
                            shape = CircleShape,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.55f),
                                    0.48f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.06f),
                                ),
                            ),
                    )
                }
            }
        }
    }
}
