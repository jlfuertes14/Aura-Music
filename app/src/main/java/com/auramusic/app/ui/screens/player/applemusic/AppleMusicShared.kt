package com.auramusic.app.ui.screens.player.applemusic

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Which sub-view is currently active in the Apple Music Now Playing sheet. */
enum class AppleMusicView {
    MAIN,
    LYRICS,
    QUEUE,
}

internal val AppleMusicTextSecondary = Color.White.copy(alpha = 0.72f)
internal val AppleMusicSecondaryText = Color.White.copy(alpha = 0.55f)
internal val AppleMusicPillInactive = Color.White.copy(alpha = 0.24f)
internal val AppleMusicTrackInactive = Color.White.copy(alpha = 0.26f)
internal val AppleMusicTrackActive = Color.White.copy(alpha = 0.92f)
internal val AppleMusicQualityLabelColor = Color.White.copy(alpha = 0.55f)

private const val FADE_EDGE_STEPS = 24
private const val BURST_DURATION_MS = 750
private const val PARTICLES_PER_BURST = 16

/**
 * Typography tokens for the Apple Music Now Playing views, mapped onto [AuraPoppinsFontFamily].
 */
@Immutable
data class AppleMusicTypography(
    val mainTitle: TextStyle,
    val mainArtist: TextStyle,
    val compactTitle: TextStyle,
    val compactArtist: TextStyle,
    val queueSectionHeader: TextStyle,
    val queueSectionSubtitle: TextStyle,
    val times: TextStyle,
    val footer: TextStyle,
    val lyricStrip: TextStyle,
)

/**
 * Creates and remembers [AppleMusicTypography] matching SimpMusic's Apple Music proportions
 * with Aura's [AuraPoppinsFontFamily].
 */
@Composable
fun rememberAppleMusicTypography(): AppleMusicTypography = remember {
    AppleMusicTypography(
        mainTitle = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Color.White,
        ),
        mainArtist = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = AppleMusicSecondaryText,
        ),
        compactTitle = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
        ),
        compactArtist = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = AppleMusicSecondaryText,
        ),
        queueSectionHeader = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Color.White,
        ),
        queueSectionSubtitle = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = AppleMusicSecondaryText,
        ),
        times = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = AppleMusicSecondaryText,
            fontFeatureSettings = "tnum",
        ),
        footer = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = AppleMusicSecondaryText,
        ),
        lyricStrip = TextStyle(
            fontFamily = AuraPoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
        ),
    )
}

/**
 * Computes the page backdrop tint at vertical [fraction] (0f..1f) by darkening [seedColor]
 * progressively toward black (5% at top, 32% at mid-page, 78% at bottom).
 */
fun appleMusicGradientColorAt(
    seedColor: Color,
    fraction: Float,
): Color {
    val top = lerp(seedColor, Color.Black, 0.05f)
    val mid = lerp(seedColor, Color.Black, 0.32f)
    val bottom = lerp(seedColor, Color.Black, 0.78f)
    return if (fraction <= 0.48f) {
        lerp(top, mid, (fraction / 0.48f).coerceIn(0f, 1f))
    } else {
        lerp(mid, bottom, ((fraction - 0.48f) / 0.52f).coerceIn(0f, 1f))
    }
}

/**
 * 24-step smoothstep vertical gradient brush eliminating hard linear edge creases.
 */
fun appleMusicSmoothScrimBrush(
    from: Color,
    to: Color,
    startFraction: Float = 0f,
    endFraction: Float = 1f,
    steps: Int = FADE_EDGE_STEPS,
): Brush {
    val stops = Array(steps + 1) { index ->
        val t = index / steps.toFloat()
        val pos = startFraction + (endFraction - startFraction) * t
        val eased = smoothstep(t)
        pos to lerp(from, to, eased)
    }
    return Brush.verticalGradient(colorStops = stops)
}

/**
 * True alpha dissolve at the top and bottom edges of a composable using an offscreen
 * [BlendMode.DstIn] layer with a 24-step smoothstep curve.
 */
fun Modifier.appleMusicVerticalFadeEdges(
    topFadeHeight: Dp = 28.dp,
    bottomFadeHeight: Dp = 18.dp,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = topFadeHeight.toPx().coerceAtMost(size.height / 2f)
            val bottomPx = bottomFadeHeight.toPx().coerceAtMost(size.height / 2f)
            val topStop = if (size.height > 0f) topPx / size.height else 0f
            val bottomStop = if (size.height > 0f) 1f - bottomPx / size.height else 1f
            drawRect(
                brush = Brush.verticalGradient(colorStops = fadeEdgeStops(topStop, bottomStop)),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * Smoothstep alpha dissolve placed between [transparentAt] and [opaqueAt] in vertical space.
 */
fun Modifier.appleMusicFadeBetween(
    transparentAt: Dp,
    opaqueAt: Dp,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = fadeEdgeStops(topStop = 1f, bottomStop = 1f),
                    startY = transparentAt.toPx(),
                    endY = opaqueAt.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun fadeEdgeStops(
    topStop: Float,
    bottomStop: Float,
): Array<Pair<Float, Color>> =
    buildList {
        if (topStop > 0f) {
            for (i in 0..FADE_EDGE_STEPS) {
                val t = i / FADE_EDGE_STEPS.toFloat()
                add(topStop * t to Color.Black.copy(alpha = smoothstep(t)))
            }
        } else {
            add(0f to Color.Black)
        }
        if (bottomStop < 1f) {
            for (i in 0..FADE_EDGE_STEPS) {
                val t = i / FADE_EDGE_STEPS.toFloat()
                add(bottomStop + (1f - bottomStop) * t to Color.Black.copy(alpha = 1f - smoothstep(t)))
            }
        } else {
            add(1f to Color.Black)
        }
    }.toTypedArray()

/**
 * Spring-inflates the element while touched (without consuming pointer events so nested
 * clickables and sliders continue working normally).
 */
@Composable
fun Modifier.appleMusicPressInflate(pressedScale: Float = 1.35f): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "appleMusicPressInflate",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/**
 * Compact 24dp–40dp circular glyph button with [appleMusicPressInflate] tactile spring response.
 */
@Composable
fun AppleMusicGlyphButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .appleMusicPressInflate()
            .size(size.coerceAtLeast(44.dp))
            .clip(CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * Explicit content badge (`E`) shown alongside explicit track titles or artist lines.
 */
@Composable
fun ExplicitBadge(
    modifier: Modifier = Modifier,
    tint: Color = Color.LightGray,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Explicit,
            contentDescription = "Explicit",
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Compact header shared by the Apple Music Lyrics and Queue tabs: 55.dp rounded thumbnail,
 * title, explicit badge, clickable artist/track info, and trailing actions.
 */
@Composable
fun AppleMusicCompactHeader(
    track: AuraTrack,
    artworkContent: (@Composable () -> Unit)? = null,
    onTapTrack: () -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    typography: AppleMusicTypography = rememberAppleMusicTypography(),
    onTapArtist: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(55.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onTapTrack),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkContent != null) {
                artworkContent()
            } else if (track.highResArtworkUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.highResArtworkUrl)
                        .crossfade(300)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onTapTrack)
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = track.title,
                style = typography.compactTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    ExplicitBadge(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(
                    text = track.artist,
                    style = typography.compactArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onTapArtist != null) {
                        Modifier.clickable(onClick = onTapArtist)
                    } else {
                        Modifier
                    },
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = trailingActions,
        )
    }
}

/**
 * Shared trailing actions trio: optional Add-to-Playlist, Favorite with celebratory star/glitter
 * burst, and More Options.
 */
@Composable
fun AppleMusicHeaderActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (onAddToPlaylist != null) {
            Box(
                modifier = Modifier
                    .appleMusicPressInflate()
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAddToPlaylist),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddCircleOutline,
                    contentDescription = "Add to playlist",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        val likeBurst = rememberAppleMusicHeartBurstState()
        Box(
            modifier = Modifier
                .appleMusicPressInflate()
                .size(44.dp)
                .appleMusicHeartBurst(likeBurst)
                .clip(CircleShape)
                .clickable {
                    if (!isFavorite) likeBurst.fire()
                    onToggleFavorite()
                },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = isFavorite, label = "appleMusicFavorite") { liked ->
                Icon(
                    imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (liked) "Remove from liked songs" else "Like song",
                    tint = if (liked) AuraNeonLime else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        AppleMusicGlyphButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = "More options",
            onClick = onOptionsClick,
            size = 44.dp,
        )
    }
}

/**
 * State holder for [appleMusicHeartBurst]. Only call [fire] on an explicit user like tap.
 */
@Stable
class AppleMusicHeartBurstState internal constructor(
    private val scope: CoroutineScope,
) {
    internal val bursts = mutableStateListOf<HeartBurst>()
    internal var colors: List<Color> = DefaultHeartBurstColors

    fun fire() {
        val burst = HeartBurst(List(PARTICLES_PER_BURST) { randomSpark(colors) })
        bursts += burst
        scope.launch {
            burst.progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(BURST_DURATION_MS, easing = LinearEasing),
            )
            bursts.remove(burst)
        }
    }
}

@Composable
fun rememberAppleMusicHeartBurstState(): AppleMusicHeartBurstState {
    val scope = rememberCoroutineScope()
    return remember { AppleMusicHeartBurstState(scope) }
}

private val DefaultHeartBurstColors = listOf(
    AuraNeonLime,
    Color(0xFFE6FF80),
    Color(0xFFB2FF59),
    Color.White,
)

/**
 * Draws celebratory stars and glitter shards shooting upward when [state.fire] is invoked.
 * Apply before `.clip(...)` so particles can escape the icon bounds.
 */
@Composable
fun Modifier.appleMusicHeartBurst(
    state: AppleMusicHeartBurstState,
    colors: List<Color> = DefaultHeartBurstColors,
): Modifier {
    state.colors = colors
    val starPath = remember { buildUnitStarPath() }
    return this.drawWithContent {
        drawContent()
        if (state.bursts.isEmpty()) return@drawWithContent
        val origin = Offset(size.width / 2f, size.height * 0.3f)
        val reach = size.height * 2.2f
        state.bursts.forEach { burst ->
            val progress = burst.progress.value
            if (progress <= 0f) return@forEach
            val flight = 1f - (1f - progress) * (1f - progress)
            val fall = progress * progress * reach * 0.35f
            val alpha = (1f - progress).coerceIn(0f, 1f)
            burst.sparks.forEach { spark ->
                val distance = spark.speed * flight * reach
                val x = origin.x + cos(spark.angleRad) * distance
                val y = origin.y + sin(spark.angleRad) * distance + fall
                val sparkSize = size.height * spark.relativeSize
                withTransform({
                    translate(x, y)
                    rotate(degrees = spark.spin * progress * 360f, pivot = Offset.Zero)
                }) {
                    when (spark.shape) {
                        HeartSparkShape.STAR ->
                            withTransform({ scale(sparkSize, sparkSize, Offset.Zero) }) {
                                drawPath(starPath, color = spark.color, alpha = alpha)
                            }
                        HeartSparkShape.GLITTER ->
                            drawRect(
                                color = spark.color,
                                alpha = alpha,
                                topLeft = Offset(-sparkSize * 0.18f, -sparkSize * 0.55f),
                                size = Size(sparkSize * 0.36f, sparkSize * 1.1f),
                            )
                    }
                }
            }
        }
    }
}

internal enum class HeartSparkShape { STAR, GLITTER }

internal class HeartSpark(
    val angleRad: Float,
    val speed: Float,
    val relativeSize: Float,
    val spin: Float,
    val shape: HeartSparkShape,
    val color: Color,
)

internal class HeartBurst(
    val sparks: List<HeartSpark>,
) {
    val progress = Animatable(0f)
}

private fun randomSpark(colors: List<Color>): HeartSpark {
    val angle = (-PI * 5f / 6f + Random.nextFloat() * (PI * 4f / 6f)).toFloat()
    return HeartSpark(
        angleRad = angle,
        speed = 0.55f + Random.nextFloat() * 0.45f,
        relativeSize = 0.12f + Random.nextFloat() * 0.10f,
        spin = -2f + Random.nextFloat() * 4f,
        shape = if (Random.nextFloat() < 0.45f) HeartSparkShape.STAR else HeartSparkShape.GLITTER,
        color = colors[Random.nextInt(colors.size)],
    )
}

private fun buildUnitStarPath(): Path =
    Path().apply {
        val innerRadius = 0.42f
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) 1f else innerRadius
            val angle = (-PI / 2 + i * PI / 5).toFloat()
            val x = cos(angle) * radius
            val y = sin(angle) * radius
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** Formats non-negative seconds as `m:ss` (or `h:mm:ss` for >= 1 hour). */
fun formatAppleMusicDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, mins, secs)
    } else {
        "%d:%02d".format(mins, secs)
    }
}
