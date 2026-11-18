package com.auramusic.app.ui.components

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalIsDarkTheme
import com.auramusic.app.ui.theme.drawInteractiveGlass
import com.auramusic.app.ui.theme.inspectDragGestures
import com.auramusic.app.ui.theme.rememberGlassInteraction
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class AuraNavTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
)

private val CapsuleShape = RoundedCornerShape(percent = 50)
private val MaxTabWidth = 94.dp
private val TabBarHeight = 62.dp
private val BlobHeight = 52.dp
private val BarInset = 5.dp

/**
 * SimpMusic / iOS 26-inspired Liquid Glass bottom navigation bar with a spring-damped
 * sliding frosted indicator pill across [tabs].
 */
@Composable
fun AuraLiquidGlassTabBar(
    tabs: List<AuraNavTabItem>,
    selectedTab: Int,
    backdrop: LayerBackdrop,
    layer: GraphicsLayer,
    luminance: State<Float>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    val density = LocalDensity.current
    val tabsCount = tabs.size
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val barInteraction = rememberGlassInteraction()
    val isDark = LocalIsDarkTheme.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth
        val tabWidth = ((availableWidth - BarInset * 2) / tabsCount).coerceAtMost(MaxTabWidth)
        val fullWidth = tabWidth * tabsCount + BarInset * 2
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val currentTabWidthPx by rememberUpdatedState(tabWidthPx)

        var currentIndex by remember { mutableIntStateOf(selectedTab.coerceIn(0, tabsCount - 1)) }
        val draggedFlag = remember { booleanArrayOf(false) }

        val dampedDrag = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTab.coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1f,
                onDragStarted = { draggedFlag[0] = false },
                onDragStopped = {
                    if (draggedFlag[0]) {
                        val target = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                        currentIndex = target
                        animateToValue(target.toFloat())
                    }
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount.x != 0f) draggedFlag[0] = true
                    updateValue(
                        (targetValue + dragAmount.x / currentTabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                },
            )
        }

        LaunchedEffect(selectedTab) {
            val clamped = selectedTab.coerceIn(0, tabsCount - 1)
            if (currentIndex != clamped) {
                draggedFlag[0] = false
                currentIndex = clamped
            }
        }

        LaunchedEffect(dampedDrag) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDrag.animateToValue(index.toFloat())
                    if (draggedFlag[0]) {
                        draggedFlag[0] = false
                        onTabSelected(index)
                    }
                }
        }

        Box(
            modifier = Modifier
                .height(TabBarHeight)
                .width(fullWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 1. Frosted glass capsule backdrop (no tap bulge/shrink)
            Box(
                Modifier
                    .matchParentSize()
                    .drawInteractiveGlass(
                        isDark = isDark,
                        backdrop = backdrop,
                        layer = layer,
                        luminance = { luminance.value },
                        shape = CapsuleShape,
                        interaction = null,
                        pressedScale = 1f,
                    ),
            )

            // 2. Sliding frosted glass indicator pill behind the icons & labels
            Box(
                Modifier
                    .wrapContentSize(Alignment.CenterStart, unbounded = true)
                    .size(fullWidth, TabBarHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                val indicatorModifier = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    // Android 8.0–11 (API 26–30): Static frosted-glass sliding pill without RenderEffect/AGSL
                    Modifier
                        .graphicsLayer {
                            translationX =
                                (if (isLtr) dampedDrag.value else (tabsCount - 1) - dampedDrag.value) * tabWidthPx +
                                BarInset.toPx()
                        }
                        .clip(CapsuleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.11f),
                                ),
                            ),
                            shape = CapsuleShape,
                        )
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.36f),
                                    Color.White.copy(alpha = 0.10f),
                                ),
                            ),
                            shape = CapsuleShape,
                        )
                } else {
                    val supportsAgslRefraction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    Modifier
                        .graphicsLayer {
                            translationX =
                                (if (isLtr) dampedDrag.value else (tabsCount - 1) - dampedDrag.value) * tabWidthPx +
                                BarInset.toPx()
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CapsuleShape },
                            effects = {
                                val l = (luminance.value * 2f - 1f).let { sign(it) * it * it }
                                val progress = dampedDrag.pressProgress
                                if (supportsAgslRefraction) {
                                    vibrancy()
                                }
                                colorControls(
                                    brightness = if (supportsAgslRefraction) 0.05f else 0.03f,
                                    contrast = 1f,
                                    saturation = if (supportsAgslRefraction) 1.5f else 1.25f,
                                )
                                blur(
                                    (if (l > 0f) lerp(8f.dp.toPx(), 16f.dp.toPx(), l) else lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)) +
                                        18f.dp.toPx(),
                                )
                                if (supportsAgslRefraction && size.minDimension > 0f) {
                                    lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                                }
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.6f) },
                            shadow = { Shadow(radius = 4f.dp, alpha = 0.4f) },
                            innerShadow = {
                                val progress = dampedDrag.pressProgress
                                InnerShadow(radius = 8f.dp * progress, alpha = progress)
                            },
                            layerBlock = {
                                scaleX = dampedDrag.scaleX
                                scaleY = dampedDrag.scaleY
                                val velocity = dampedDrag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val lumNorm = ((luminance.value - 0.3f) / 0.5f).coerceIn(0f, 1f)
                                val darken = if (isDark) lerp(0.16f, 0.40f, lumNorm) else lerp(0.06f, 0.14f, lumNorm)
                                drawRect(Color.Black.copy(alpha = darken))
                                drawRect(Color.White.copy(alpha = if (supportsAgslRefraction) 0.16f else 0.20f))
                            },
                        )
                }

                Box(
                    indicatorModifier
                        .width(tabWidth)
                        .height(BlobHeight),
                )

                // 3. Crisp tab icons & labels on top
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = BarInset)
                        .then(dampedDrag.modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { position, tab ->
                        val isSelected = currentIndex == position
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) AuraNeonLime else Color.White.copy(alpha = 0.82f),
                            animationSpec = tween(durationMillis = 180),
                            label = "liquidTabColor",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.10f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = 0.65f,
                                stiffness = 420f,
                            ),
                            label = "liquidTabIconScale",
                        )

                        Column(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(CapsuleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                ) {
                                    if (position == currentIndex) {
                                        onTabSelected(position)
                                    } else {
                                        draggedFlag[0] = false
                                        currentIndex = position
                                        onTabSelected(position)
                                    }
                                },
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    letterSpacing = 0.1.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spring-damped 1-D drag animation driving the sliding frosted glass pill.
 */
private class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(0.82f, 480f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos {}
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
        animationScope.launch {
            valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}
