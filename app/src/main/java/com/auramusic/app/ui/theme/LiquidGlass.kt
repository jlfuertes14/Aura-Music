package com.auramusic.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * Whether Liquid Glass surfaces render real backdrop blur/refraction or fall back to flat tinted fills.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

/**
 * True when the active theme surface is dark. Used to select dark vs light glass scrims.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Optional root [LayerBackdrop] provided by the host shell so descendant chrome can sample it.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Creates a [LayerBackdrop] pre-filled with [color] behind its source content.
 */
@Composable
fun rememberGlassBackdrop(color: Color = AuraObsidianBg): LayerBackdrop =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

/**
 * Marks a composable as the source layer sampled by sibling glass surfaces.
 * Never place glass surfaces inside the source itself, to avoid a RuntimeShader feedback loop.
 */
fun Modifier.glassBackdropSource(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

/**
 * Frosted glass card/panel modifier for in-content surfaces across screens when Liquid Glass is active.
 * Uses translucent fill + vertical specular hairline highlight so it never creates RuntimeShader
 * feedback loops when placed inside a [glassBackdropSource].
 */
@Composable
fun Modifier.auraGlassSurface(
    shape: Shape = RoundedCornerShape(18.dp),
    selected: Boolean = false,
    fallbackColor: Color = Color(0xFF1A1D26),
    glassAlpha: Float = if (selected) 0.16f else 0.085f,
    borderTopAlpha: Float = if (selected) 0.34f else 0.22f,
    borderBottomAlpha: Float = 0.06f,
    borderWidth: Dp = 0.75.dp,
): Modifier {
    if (!LocalLiquidGlassEnabled.current) {
        return this
            .clip(shape)
            .background(fallbackColor)
    }
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = glassAlpha))
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = borderTopAlpha),
                    Color.White.copy(alpha = borderBottomAlpha),
                ),
            ),
            shape = shape,
        )
}

/**
 * Returns a translucent glass-tinted modal sheet container color when Liquid Glass is active,
 * or [defaultColor] under the Default theme.
 */
@Composable
fun auraGlassSheetColor(defaultColor: Color = Color(0xFF14161E)): Color =
    if (LocalLiquidGlassEnabled.current) Color(0xFF151822).copy(alpha = 0.90f) else defaultColor

/**
 * Applies the Liquid Glass effect (vibrancy, color controls, blur, AGSL lens refraction on API 33+,
 * and spring-animated pointer-tracking glow) when [LocalLiquidGlassEnabled] is true, or a clean
 * tinted flat surface when disabled.
 *
 * On Android 8–11 (API 26–30), `RenderEffect` blur and AGSL shaders are unsupported; the surface
 * automatically raises its scrim floor for crisp legibility.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.50f,
): Modifier {
    if (!LocalLiquidGlassEnabled.current) {
        return this
            .clip(shape)
            .background(fallbackColor)
    }
    val isDark = LocalIsDarkTheme.current
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = null,
        luminance = { 0.5f },
        shape = shape,
        interaction = if (interactive) interaction else null,
        highlight = highlight,
        minScrim = minScrim,
        maxScrim = maxScrim,
    )
}

/**
 * Luminance-aware overload of [liquidGlass] for dynamic surfaces (such as the MiniPlayer or
 * floating navigation capsule) that sample their own [layer] into [luminance].
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    layer: GraphicsLayer,
    luminance: State<Float>,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.50f,
    pressedScale: Float = 1.04f,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
): Modifier {
    if (!LocalLiquidGlassEnabled.current) {
        return this
            .clip(shape)
            .background(fallbackColor)
    }
    val isDark = LocalIsDarkTheme.current
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = layer,
        luminance = { luminance.value },
        shape = shape,
        interaction = if (interactive) interaction else null,
        pressedScale = pressedScale,
        highlight = highlight,
        blurScale = blurScale,
        minScrim = minScrim,
        maxScrim = maxScrim,
    )
}

/**
 * A Liquid Glass container wrapping arbitrary [content].
 */
@Composable
fun LiquidGlassContainer(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            backdrop = backdrop,
            shape = shape,
            interactive = interactive,
            highlight = highlight,
            fallbackColor = fallbackColor,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Convenience single-icon Liquid Glass button (e.g., floating back, search, or options controls).
 */
@Composable
fun LiquidGlassIconButton(
    backdrop: LayerBackdrop,
    imageVector: ImageVector,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    contentDescription: String? = null,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Plain,
    onClick: () -> Unit,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        highlight = highlight,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

/**
 * Observe-only press/drag state holder that drives the pointer-following radial glow and
 * spring scale response without intercepting child click handlers.
 */
class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring(
        dampingRatio = 0.5f,
        stiffness = 300f,
        visibilityThreshold = 0.001f,
    )
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0f at rest, animating to 1f while pressed. */
    val pressProgress: Float get() = pressAnimation.value

    /** Local-space pointer position used as the radial highlight center. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            ) { change, _ ->
                touchPosition = change.position
            }
        }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * Core modifier applying 3-tier adaptive glass across Android versions:
 * - **Android 13+ (API 33+)**: True Liquid Glass with AGSL `lens` refraction, `vibrancy`, `colorControls`, and `RenderEffect` blur.
 * - **Android 12 / 12L (API 31–32)**: Frosted Glass Blur using `RenderEffect` `blur` + `colorControls` + frosted sheen (no AGSL shaders).
 * - **Android 8.0–11 (API 26–30)**: Static Frosted Glass look using translucent frosted layers and specular borders without `drawBackdrop`.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: LayerBackdrop,
    layer: GraphicsLayer?,
    luminance: () -> Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.50f,
): Modifier {
    // Tier 3: Android 8.0–11 (API 26–30) — Static Frosted Glass fallback (no RenderEffect or AGSL)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val baseColor = if (isDark) {
            Color(0xFF1C202B).copy(alpha = 0.90f)
        } else {
            Color(0xFFF3F5FA).copy(alpha = 0.90f)
        }
        return this
            .clip(shape)
            .background(baseColor, shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.35f),
                        Color.White.copy(alpha = if (isDark) 0.05f else 0.12f),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
                shape = shape,
            )
            .then(
                if (interaction != null) {
                    Modifier.pointerInput(interaction) { interaction.detectPress(this) }
                } else {
                    Modifier
                },
            )
    }

    // Tier 1 (Android 13+ / API 33+: AGSL Liquid Glass) & Tier 2 (Android 12 / API 31–32: Frosted Blur Glass)
    val supportsAgslRefraction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { highlight },
            effects = {
                val l = (luminance() * 2f - 1f).let { sign(it) * it * it }
                val press = interaction?.pressProgress ?: 0f
                if (supportsAgslRefraction) {
                    vibrancy()
                }
                colorControls(
                    brightness = if (supportsAgslRefraction) 0.05f else 0.03f,
                    contrast = 1f,
                    saturation = if (supportsAgslRefraction) 1.5f else 1.25f,
                )
                val effectiveBlurScale = if (supportsAgslRefraction) blurScale else (blurScale * 1.25f)
                blur(
                    (
                        if (l > 0f) {
                            lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                        } else {
                            lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                        }
                    ) * effectiveBlurScale + 2f.dp.toPx() * press,
                )
                if (supportsAgslRefraction &&
                    shape is CornerBasedShape &&
                    size.minDimension > 0f
                ) {
                    lens(
                        refractionHeight = size.minDimension / 4f + 2f.dp.toPx() * press,
                        refractionAmount = size.minDimension / 2f,
                        depthEffect = false,
                    )
                }
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
                layer?.record { drawBackdrop() }
            },
            onDrawSurface = {
                val baseDarken = lerp(minScrim, maxScrim, ((luminance() - 0.3f) / 0.5f).coerceIn(0f, 1f))
                val darken = if (!supportsAgslRefraction) {
                    // Android 12 frosted blur look: slightly stronger frosted scrim + soft white frost veil
                    maxOf(baseDarken, 0.24f)
                } else {
                    baseDarken
                }
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                if (!supportsAgslRefraction) {
                    drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.16f))
                }
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * press),
                                Color.Transparent,
                            ),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension.coerceAtLeast(1f) * 1.2f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val scale = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
        ).then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )
}

internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent =
            drag(
                pointerId = down.id,
                onDrag = { onDrag(it, it.positionChange()) },
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
