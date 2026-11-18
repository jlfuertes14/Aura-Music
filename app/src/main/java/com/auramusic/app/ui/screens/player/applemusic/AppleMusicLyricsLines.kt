package com.auramusic.app.ui.screens.player.applemusic

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricWord
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily
import com.auramusic.app.ui.theme.isLyricsBlurSupported
import java.text.Bidi
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal val AppleMusicLyricFontSize = 28.sp
internal val AppleMusicLyricLineHeight = 34.sp
internal val AppleMusicLyricGap = 20.dp
internal val AppleMusicLyricPaddingX = 20.dp

internal val AppleMusicSubLineFontSize = 14.sp
internal val AppleMusicSubLineLineHeight = 21.sp
internal val AppleMusicMainToRomanizedGap = 10.dp
internal val AppleMusicWrappedLineSpacing = 2.dp

internal val AppleMusicLyricCornerRadius = 8.dp
internal val AppleMusicLyricPressedBackground = Color.White.copy(alpha = 0.067f)

internal val AppleMusicInactiveLineColor = Color(0xFF9B9B9B)
internal val AppleMusicPendingWordColor = Color(0xFF8E8E8E)
internal val AppleMusicRomanizedColor = Color.White.copy(alpha = 0.62f)
internal val AppleMusicActiveLineGlow = Shadow(color = Color.White, offset = Offset.Zero, blurRadius = 0f)

private const val BLUR_PER_LINE_EM = 0.095f
private const val BLUR_MAX_EM = 0.45f
private const val ACTIVE_LINE_ALPHA = 1f
private const val ALPHA_FALLOFF_PER_LINE = 0.25f
private const val MIN_LINE_ALPHA = 0.25f
private const val PRE_ROLL_LINE_ALPHA = 0.6f

private const val INTERLUDE_MIN_GAP_SEC = 3.0f
private const val INTERLUDE_DOT_COUNT = 3
private const val INTERLUDE_DOT = "•"

private const val PLAYHEAD_TICK_MS = 50L

// AMLL emphasis & travelling flare constants
private const val EMP_AMOUNT_REF_MS = 2000f
private const val EMP_BLUR_REF_MS = 3000f
private const val EMP_MIN_DURATION_MS = 1000f
private const val EMP_AMOUNT_GAIN = 0.6f
private const val EMP_BLUR_GAIN = 0.5f
private const val EMP_AMOUNT_CAP = 1.2f
private const val EMP_BLUR_CAP = 0.8f
private const val EMP_LAST_WORD_AMOUNT = 1.6f
private const val EMP_LAST_WORD_BLUR = 1.5f
private const val EMP_SCALE_EM = 0.1f
private const val EMP_RISE_EM = 0.025f
private const val EMP_GLOW_RADIUS_EM = 0.3f

private const val FLARE_REACH_CHARS = 3.5f
private const val FLARE_FADE_MS = 2_500
private const val FLARE_ATTACK_MS = 500
private const val FLARE_TAIL_CHARS = 2.5f

private const val CHAR_RISE_EM = 0.065f
private const val CHAR_RISE_MS = 1_000

private const val SUNG_BASE_GLOW_ALPHA = 0.95f
private const val SUNG_BASE_GLOW_EM = 0.26f

private val EmpBezIn = CubicBezierEasing(0.2f, 0.4f, 0.58f, 1f)
private val EmpBezOut = CubicBezierEasing(0.3f, 0f, 0.58f, 1f)

private fun empEasing(x: Float): Float =
    if (x < 0.5f) {
        EmpBezIn.transform((x / 0.5f).coerceIn(0f, 1f))
    } else {
        1f - EmpBezOut.transform(((x - 0.5f) / 0.5f).coerceIn(0f, 1f))
    }

@Immutable
data class AppleMusicDisplayLine(
    val line: LyricLine,
    val isInterlude: Boolean = false,
    val originalIndex: Int = -1,
)

@Immutable
data class AppleMusicDisplayLines(
    val items: List<AppleMusicDisplayLine>,
    val isUnsynced: Boolean,
)

/**
 * Builds the Apple Music display list from [lines], inserting 3-dot interlude entries for
 * `>= 3.0s` instrumental gaps between synced lines and falling back to unsynced lines when
 * timestamps cannot order the lyrics.
 */
fun buildAppleMusicDisplayLines(
    lines: List<LyricLine>,
    plainLyrics: String = "",
): AppleMusicDisplayLines {
    if (lines.isEmpty()) {
        val fallbackLines = plainLyrics
            .lines()
            .map { unescapeAppleLyricsHtml(it.trim()) }
            .filter { it.isNotEmpty() }
            .mapIndexed { idx, text ->
                AppleMusicDisplayLine(
                    line = LyricLine(timeSec = 0f, endTimeSec = 0f, text = text),
                    isInterlude = false,
                    originalIndex = idx,
                )
            }
        return AppleMusicDisplayLines(items = fallbackLines, isUnsynced = true)
    }

    val distinctTimes = lines.map { (it.timeSec * 1000f).roundToLong() }.distinct().size
    val isSingleZero = lines.size == 1 && lines[0].timeSec <= 0f && lines[0].words.isEmpty() && lines[0].endTimeSec <= 0f
    if ((lines.size > 1 && distinctTimes <= 1) || isSingleZero) {
        return AppleMusicDisplayLines(
            items = lines.mapIndexed { idx, line ->
                AppleMusicDisplayLine(line = line, isInterlude = false, originalIndex = idx)
            },
            isUnsynced = true,
        )
    }

    if (lines.size < 2) {
        return AppleMusicDisplayLines(
            items = lines.mapIndexed { idx, line ->
                AppleMusicDisplayLine(line = line, isInterlude = false, originalIndex = idx)
            },
            isUnsynced = false,
        )
    }

    val out = ArrayList<AppleMusicDisplayLine>(lines.size + 4)
    lines.forEachIndexed { index, line ->
        out += AppleMusicDisplayLine(line = line, isInterlude = false, originalIndex = index)
        val nextLine = lines.getOrNull(index + 1) ?: return@forEachIndexed
        val anchorSec =
            line.words.lastOrNull()?.startTimeSec
                ?: line.endTimeSec.takeIf { it > line.timeSec && it < nextLine.timeSec }
                ?: line.timeSec
        val dotsStartSec = anchorSec + INTERLUDE_MIN_GAP_SEC
        if (nextLine.timeSec <= dotsStartSec + 0.1f) return@forEachIndexed

        val stepSec = (nextLine.timeSec - dotsStartSec) / INTERLUDE_DOT_COUNT
        val dotWords = List(INTERLUDE_DOT_COUNT) { dot ->
            val wStart = dotsStartSec + stepSec * dot
            LyricWord(
                word = INTERLUDE_DOT,
                startTimeSec = wStart,
                endTimeSec = wStart + stepSec,
            )
        }
        out += AppleMusicDisplayLine(
            line = LyricLine(
                timeSec = dotsStartSec,
                endTimeSec = nextLine.timeSec,
                text = "$INTERLUDE_DOT $INTERLUDE_DOT $INTERLUDE_DOT",
                words = dotWords,
            ),
            isInterlude = true,
            originalIndex = -1,
        )
    }
    return AppleMusicDisplayLines(items = out, isUnsynced = false)
}

/**
 * Returns the active display index for [effectiveMs], or `-1` during pre-roll or when unsynced.
 */
internal fun AppleMusicDisplayLines.activeIndexAt(effectiveMs: Long): Int {
    if (isUnsynced || items.isEmpty()) return -1
    val firstStartMs = (items.first().line.timeSec * 1000f).roundToLong()
    if (effectiveMs < firstStartMs) return -1

    var lo = 0
    var hi = items.lastIndex
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val itemStartMs = (items[mid].line.timeSec * 1000f).roundToLong()
        if (itemStartMs <= effectiveMs) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}

/**
 * Apple Music depth-of-field focus treatment for a single lyric row.
 *
 * Active line renders sharp (`0.dp` blur, `1f` alpha). Inactive lines recede via distance-based
 * alpha `(1f - distance * 0.25f).coerceAtLeast(0.25f)` over a `400ms` tween, and apply
 * `Modifier.blur(..., BlurredEdgeTreatment.Unbounded)` ONLY on Android 12+ (`Build.VERSION.SDK_INT >= 31`)
 * when not dragging and not unsynced. On API 26–30, `Modifier.blur` is skipped and smooth
 * distance-based alpha + scale depth cues maintain visual hierarchy.
 */
@Composable
fun Modifier.appleMusicLyricFocus(
    distanceFromCurrent: Int,
    isUserDragging: Boolean,
    hasActiveLine: Boolean = true,
    isUnsynced: Boolean = false,
): Modifier {
    val blurSupported = isLyricsBlurSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val distance =
        if (distanceFromCurrent < 0) abs(distanceFromCurrent) + 1 else distanceFromCurrent

    val fontSizeDp = with(LocalDensity.current) { AppleMusicLyricFontSize.toDp() }
    val targetBlur: Dp =
        if (!blurSupported || isUserDragging || isUnsynced || !hasActiveLine || distanceFromCurrent == 0) {
            0.dp
        } else {
            fontSizeDp * (distance * BLUR_PER_LINE_EM).coerceAtMost(BLUR_MAX_EM)
        }

    val targetAlpha =
        when {
            isUnsynced -> ACTIVE_LINE_ALPHA
            !hasActiveLine -> PRE_ROLL_LINE_ALPHA
            distanceFromCurrent == 0 -> ACTIVE_LINE_ALPHA
            else -> (1f - distance * ALPHA_FALLOFF_PER_LINE).coerceAtLeast(MIN_LINE_ALPHA)
        }

    // On API 26-30 where RenderEffect blur is unavailable, subtle scale recession reinforces depth.
    val targetScale =
        if (blurSupported || isUnsynced || !hasActiveLine || distanceFromCurrent == 0) {
            1f
        } else {
            (1f - distance * 0.045f).coerceAtLeast(0.86f)
        }

    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(400),
        label = "appleMusicLyricBlur",
    )
    val lineAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(400),
        label = "appleMusicLyricAlpha",
    )
    val lineScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(400),
        label = "appleMusicLyricScale",
    )

    return this
        .graphicsLayer {
            if (lineScale != 1f) {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        }
        .alpha(lineAlpha)
        .then(
            if (blurSupported && blurRadius > 0.dp) {
                Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            } else {
                Modifier
            },
        )
}

/**
 * Sequential row lag for Apple Music lyric scrolling (`50ms` per row stagger on a
 * `FloatSpringSpec(dampingRatio = 0.9f, stiffness = 180f)` curve).
 */
class LyricsStagger {
    private val spring =
        FloatSpringSpec(dampingRatio = 0.9f, stiffness = 180f, visibilityThreshold = 0.001f)
    private val springMs = spring.getDurationNanos(0f, 1f, 0f) / 1_000_000f

    private var lagDistance by mutableFloatStateOf(0f)
    internal var elapsedMs by mutableFloatStateOf(0f)

    private var firstOnPage = 0
    private var target = -1

    internal fun progress(ms: Float): Float =
        when {
            ms <= 0f -> 0f
            ms >= springMs -> 1f
            else -> spring.getValueFromNanos((ms * 1_000_000f).toLong(), 0f, 1f, 0f)
        }

    internal fun begin(
        layoutInfo: LazyListLayoutInfo,
        index: Int,
        distance: Float,
        wasVisible: Boolean,
    ): Float {
        val staggered = wasVisible && index == target + 1
        target = index
        val pageTop = layoutInfo.viewportStartOffset + distance
        firstOnPage =
            layoutInfo.visibleItemsInfo
                .firstOrNull { it.offset + it.size > pageTop }
                ?.index ?: index
        elapsedMs = 0f
        lagDistance = if (staggered) distance else 0f
        if (!staggered) return springMs
        val lastRow = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: index) + 2
        return springMs + delayMs(lastRow)
    }

    internal fun end() {
        lagDistance = 0f
        elapsedMs = 0f
    }

    /** Vertical trailing offset in pixels for row [index] during an active staggered scroll. */
    fun lagPx(index: Int): Float {
        val distance = lagDistance
        if (distance == 0f) return 0f
        val delay = delayMs(index)
        if (delay == 0f) return 0f
        val ms = elapsedMs
        return distance * (progress(ms) - progress(ms - delay))
    }

    private fun delayMs(index: Int): Float {
        var delay = 0f
        var step = 50f
        for (row in firstOnPage until index) {
            delay += step
            if (row >= target) step /= 1.05f
        }
        return delay
    }
}

/**
 * Anchors [index] near the top of the viewport (`extraOffsetPx` negative leaves ~1 previous row
 * visible above it) while driving [stagger] along the same spring clock.
 */
suspend fun LazyListState.animateScrollAndAnchorItemTop(
    index: Int,
    extraOffsetPx: Float = 0f,
    stagger: LyricsStagger,
) {
    if (index < 0) return
    val initiallyVisible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!initiallyVisible) {
        scrollToItem(index)
    }
    withFrameNanos { }
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val distance = (itemInfo.offset - layoutInfo.viewportStartOffset).toFloat() + extraOffsetPx
    val spanMs = stagger.begin(layoutInfo, index, distance, initiallyVisible)
    if (spanMs <= 0f) {
        stagger.end()
        return
    }
    try {
        var scrolled = 0f
        scroll {
            val clock = tween<Float>(spanMs.toInt().coerceAtLeast(1), easing = LinearEasing)
            animate(0f, spanMs, animationSpec = clock) { ms, _ ->
                stagger.elapsedMs = ms
                scrolled += scrollBy(distance * stagger.progress(ms) - scrolled)
            }
        }
    } finally {
        stagger.end()
    }
}

/**
 * Frame-interpolated playback position in milliseconds, capped at `50ms` ahead of the last [rawMs]
 * tick so rapid RichSync word boundaries never stutter or get skipped between player emissions.
 */
@Composable
fun rememberSmoothPlayhead(
    rawMs: Long,
    enabled: Boolean,
): State<Long> {
    val playhead = remember { mutableLongStateOf(rawMs) }
    LaunchedEffect(rawMs, enabled) {
        if (!enabled) {
            playhead.longValue = rawMs
            return@LaunchedEffect
        }
        var baseNanos = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                if (baseNanos < 0L) baseNanos = frameNanos
                val elapsedMs = (frameNanos - baseNanos) / 1_000_000L
                playhead.longValue = rawMs + elapsedMs.coerceIn(0L, PLAYHEAD_TICK_MS)
            }
        }
    }
    return playhead
}

@Composable
internal fun rememberAppleLyricLayoutDirection(text: String): LayoutDirection =
    remember(text) {
        if (Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
            LayoutDirection.Ltr
        } else {
            LayoutDirection.Rtl
        }
    }

@Composable
internal fun AppleLyricText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    alignmentText: String = text,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val alignmentDirection = rememberAppleLyricLayoutDirection(alignmentText)
    val readingDirection = rememberAppleLyricLayoutDirection(text)
    CompositionLocalProvider(LocalLayoutDirection provides readingDirection) {
        Text(
            text = text,
            style = style.copy(textDirection = TextDirection.ContentOrLtr),
            modifier = modifier,
            textAlign = if (alignmentDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
            color = color,
            maxLines = maxLines,
            softWrap = softWrap,
        )
    }
}

/**
 * Single lyric row wrapper handling tap-to-seek, subtle rounded `8.dp` press highlight
 * (`Color.White.copy(alpha = 0.067f)`), staggered vertical trailing, and depth-of-field focus.
 */
@Composable
internal fun AppleMusicLyricRow(
    displayLine: AppleMusicDisplayLine,
    index: Int,
    distanceFromCurrent: Int,
    isCurrent: Boolean,
    isUserDragging: Boolean,
    hasActiveLine: Boolean,
    isUnsynced: Boolean,
    currentPositionMs: Long,
    isPlaying: Boolean,
    showRomanized: Boolean,
    stagger: LyricsStagger,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = displayLine.line
    val lineInteraction = remember { MutableInteractionSource() }
    val linePressed by lineInteraction.collectIsPressedAsState()
    val canSeek = !isUnsynced && !displayLine.isInterlude

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = stagger.lagPx(index) }
            .background(
                color = if (linePressed && canSeek) AppleMusicLyricPressedBackground else Color.Transparent,
                shape = RoundedCornerShape(AppleMusicLyricCornerRadius),
            )
            .clickable(
                interactionSource = lineInteraction,
                indication = null,
                enabled = canSeek,
                role = Role.Button,
                onClickLabel = "Seek to lyric line",
            ) {
                onLineClick(line.timeSec)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .appleMusicLyricFocus(
                    distanceFromCurrent = distanceFromCurrent,
                    isUserDragging = isUserDragging,
                    hasActiveLine = hasActiveLine,
                    isUnsynced = isUnsynced,
                ),
        ) {
            Box(modifier = Modifier.padding(horizontal = AppleMusicLyricPaddingX)) {
                if (line.words.isNotEmpty() && !isUnsynced) {
                    AnimatedRichSyncLine(
                        line = line,
                        currentPositionMs = currentPositionMs,
                        isCurrent = isCurrent,
                        showRomanized = showRomanized,
                        isInterlude = displayLine.isInterlude,
                        isPlaying = isPlaying,
                    )
                } else {
                    AppleMusicSyncedLineItem(
                        text = unescapeAppleLyricsHtml(line.text),
                        romanizedText = if (showRomanized) line.romanizedText?.let(::unescapeAppleLyricsHtml) else null,
                        isCurrent = isCurrent || isUnsynced,
                    )
                }
            }
        }
    }
}

/**
 * Line-synced and unsynced Apple Music lyric item (`28.sp`, `34.sp` lineHeight, `20.dp` vertical gap)
 * with optional stacked `14.sp` romanized reading (`10.dp` top spacing).
 */
@Composable
fun AppleMusicSyncedLineItem(
    text: String,
    romanizedText: String?,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val mainStyle = TextStyle(
        fontFamily = AuraPoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = AppleMusicLyricFontSize,
        lineHeight = AppleMusicLyricLineHeight,
    )
    val subStyle = TextStyle(
        fontFamily = AuraPoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = AppleMusicSubLineFontSize,
        lineHeight = AppleMusicSubLineLineHeight,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(AppleMusicLyricGap))
        AppleLyricText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = if (isCurrent) Color.White else AppleMusicInactiveLineColor,
            style = mainStyle,
        )
        if (!romanizedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(AppleMusicMainToRomanizedGap))
            AppleLyricText(
                text = romanizedText,
                alignmentText = text,
                modifier = Modifier.fillMaxWidth(),
                style = subStyle,
                color = if (isCurrent) AppleMusicRomanizedColor else AppleMusicInactiveLineColor,
            )
        }
        Spacer(modifier = Modifier.height(AppleMusicLyricGap))
    }
}

/**
 * RichSync word-by-word lyric renderer with frame-interpolated playhead (`50ms` cap),
 * char-by-char `#8E8E8E` -> `Color.White` wipe, upward char rise (`0.065em`), travelling
 * glow flare (`Shadow` blur `0.26em`), held-word emphasis rise/scale/glow, and stacked
 * `14.sp` romanization (`10.dp` top spacing).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimatedRichSyncLine(
    line: LyricLine,
    currentPositionMs: Long,
    isCurrent: Boolean,
    showRomanized: Boolean,
    modifier: Modifier = Modifier,
    isInterlude: Boolean = false,
    isPlaying: Boolean = true,
) {
    val playhead = rememberSmoothPlayhead(currentPositionMs, enabled = isCurrent && isPlaying)
    val originalText = remember(line.words, line.text) {
        if (line.words.isNotEmpty()) {
            line.words.joinToString(" ") { unescapeAppleLyricsHtml(it.word) }
        } else {
            unescapeAppleLyricsHtml(line.text)
        }
    }
    val lineDirection = rememberAppleLyricLayoutDirection(originalText)

    val currentWordIndex by remember(line.words, isCurrent) {
        derivedStateOf {
            if (!isCurrent) return@derivedStateOf -1
            line.words.indexOfLast { (it.startTimeMs()) <= playhead.value }
        }
    }

    val subStyle = TextStyle(
        fontFamily = AuraPoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = AppleMusicSubLineFontSize,
        lineHeight = AppleMusicSubLineLineHeight,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(AppleMusicLyricGap))

        CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isInterlude) 10.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(AppleMusicWrappedLineSpacing),
            ) {
                line.words.forEachIndexed { index, wordTiming ->
                    val wordStartMs = wordTiming.startTimeMs()
                    val wordEndMs =
                        if (index < line.words.lastIndex) {
                            line.words[index + 1].startTimeMs().coerceAtLeast(wordStartMs + 1L)
                        } else {
                            val explicitEndMs = wordTiming.endTimeMs()
                            val lineEndMs = (line.endTimeSec * 1000f).roundToLong()
                            when {
                                explicitEndMs > wordStartMs -> explicitEndMs
                                lineEndMs > wordStartMs -> lineEndMs
                                index > 0 && line.words[index - 1].startTimeMs() < wordStartMs ->
                                    wordStartMs + (wordStartMs - line.words[index - 1].startTimeMs())
                                else -> wordStartMs + 500L
                            }
                        }
                    AnimatedRichSyncWord(
                        word = unescapeAppleLyricsHtml(wordTiming.word),
                        lineDirection = lineDirection,
                        wordStartTimeMs = wordStartMs,
                        wordEndTimeMs = wordEndMs,
                        currentTimeMs = currentPositionMs,
                        playheadMs = playhead,
                        isActive = isCurrent && index == currentWordIndex,
                        isPast = isCurrent && index < currentWordIndex,
                        isCurrent = isCurrent,
                        isLastWord = index == line.words.lastIndex && !isInterlude,
                    )
                }
            }
        }

        val romanized = line.romanizedText?.let(::unescapeAppleLyricsHtml)
        if (showRomanized && !romanized.isNullOrBlank() && !isInterlude) {
            Spacer(modifier = Modifier.height(AppleMusicMainToRomanizedGap))
            AppleLyricText(
                text = romanized,
                alignmentText = originalText,
                modifier = Modifier.fillMaxWidth(),
                style = subStyle,
                color = if (isCurrent) AppleMusicRomanizedColor else AppleMusicInactiveLineColor,
            )
        }

        Spacer(modifier = Modifier.height(AppleMusicLyricGap))
    }
}

@Composable
private fun AnimatedRichSyncWord(
    word: String,
    lineDirection: LayoutDirection,
    wordStartTimeMs: Long,
    wordEndTimeMs: Long,
    currentTimeMs: Long,
    playheadMs: State<Long>,
    isActive: Boolean,
    isPast: Boolean,
    isCurrent: Boolean,
    isLastWord: Boolean,
) {
    val wordBidi =
        remember(word, lineDirection) {
            val fallback =
                if (lineDirection == LayoutDirection.Rtl) {
                    Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT
                } else {
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                }
            Bidi(word, fallback)
        }
    val wordDirection = if (wordBidi.baseIsLeftToRight()) TextDirection.Ltr else TextDirection.Rtl
    val style = TextStyle(
        fontFamily = AuraPoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = AppleMusicLyricFontSize,
        lineHeight = AppleMusicLyricLineHeight,
        textDirection = wordDirection,
    )

    if (!isCurrent) {
        Text(text = word, style = style, color = AppleMusicInactiveLineColor)
        return
    }

    val wordDurationMs = (wordEndTimeMs - wordStartTimeMs).coerceAtLeast(1L)
    val anim =
        remember(wordStartTimeMs, wordEndTimeMs) {
            val initial = ((currentTimeMs - wordStartTimeMs).toFloat() / wordDurationMs.toFloat()).coerceIn(0f, 1f)
            Animatable(initial)
        }

    LaunchedEffect(wordStartTimeMs, wordEndTimeMs, isActive, isPast) {
        when {
            isPast -> anim.snapTo(1f)
            isActive -> {
                val now = playheadMs.value
                val current = ((now - wordStartTimeMs).toFloat() / wordDurationMs.toFloat()).coerceIn(0f, 1f)
                anim.snapTo(current)
                val remainingMs = (wordEndTimeMs - now).coerceAtLeast(0L).toInt().coerceAtLeast(1)
                anim.animateTo(1f, tween(remainingMs, easing = LinearEasing))
            }
        }
    }

    val wordProgress =
        when {
            isPast -> 1f
            isActive -> anim.value
            else -> 0f
        }

    val emphasisDurationMs = maxOf(EMP_MIN_DURATION_MS, wordDurationMs.toFloat())
    val amount = run {
        val raw = emphasisDurationMs / EMP_AMOUNT_REF_MS
        val shaped = if (raw > 1f) sqrt(raw) else raw * raw * raw
        min(EMP_AMOUNT_CAP, shaped * EMP_AMOUNT_GAIN * if (isLastWord) EMP_LAST_WORD_AMOUNT else 1f)
    }
    val blurAmount = run {
        val raw = emphasisDurationMs / EMP_BLUR_REF_MS
        val shaped = if (raw > 1f) sqrt(raw) else raw * raw * raw
        min(EMP_BLUR_CAP, shaped * EMP_BLUR_GAIN * if (isLastWord) EMP_LAST_WORD_BLUR else 1f)
    }

    val flareGate by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isActive) FLARE_ATTACK_MS else FLARE_FADE_MS,
            easing = LinearEasing,
        ),
        label = "flareGate",
    )

    val eased = if (amount <= 0f && blurAmount <= 0f) 0f else empEasing(wordProgress)
    val fontPx = with(LocalDensity.current) { style.fontSize.toPx() }
    val heldGlow =
        if (eased * blurAmount <= 0.01f) {
            null
        } else {
            AppleMusicActiveLineGlow.copy(
                color = AppleMusicActiveLineGlow.color.copy(alpha = (eased * blurAmount).coerceIn(0f, 1f)),
                blurRadius = min(EMP_GLOW_RADIUS_EM, blurAmount * EMP_GLOW_RADIUS_EM) * fontPx,
            )
        }

    Box(
        modifier = Modifier.graphicsLayer {
            val scale = 1f + eased * EMP_SCALE_EM * amount
            scaleX = scale
            scaleY = scale
            translationY = -eased * EMP_RISE_EM * amount * fontPx
        },
    ) {
        if (!wordBidi.isLeftToRight) {
            ShapedRichSyncWord(
                word = word,
                style = style,
                progress = wordProgress,
                isRtl = wordDirection == TextDirection.Rtl,
                restingColor = AppleMusicPendingWordColor,
                glow = heldGlow ?: AppleMusicActiveLineGlow.copy(blurRadius = SUNG_BASE_GLOW_EM * fontPx),
                flare = flareGate,
                fontPx = fontPx,
            )
        } else {
            val chars = word.toCharArray()
            val charCount = chars.size.coerceAtLeast(1)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row {
                    chars.forEachIndexed { charIndex, ch ->
                        val charFrom = charIndex.toFloat() / charCount
                        val charTo = (charIndex + 1).toFloat() / charCount
                        val charProgress = ((wordProgress - charFrom) / (charTo - charFrom)).coerceIn(0f, 1f)
                        val charPast = wordProgress >= charTo
                        val charActive = isActive && wordProgress >= charFrom && wordProgress < charTo
                        val charCenter = (charFrom + charTo) / 2f
                        val reach = (FLARE_REACH_CHARS / charCount).coerceAtLeast(0.0001f)
                        val charFlare =
                            if (flareGate <= 0f) {
                                0f
                            } else {
                                val delta = wordProgress - charCenter
                                val shape =
                                    if (delta > 0f) {
                                        exp(-delta / (FLARE_TAIL_CHARS / charCount))
                                    } else {
                                        (1f - abs(delta) / reach).coerceIn(0f, 1f)
                                    }
                                shape * flareGate
                            }
                        val charRise by animateFloatAsState(
                            targetValue = if (charProgress > 0f) 1f else 0f,
                            animationSpec = tween(CHAR_RISE_MS, easing = FastOutSlowInEasing),
                            label = "charRise",
                        )
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationY = -charRise * CHAR_RISE_EM * fontPx
                            },
                        ) {
                            val glowShadow = heldGlow ?: AppleMusicActiveLineGlow.copy(blurRadius = SUNG_BASE_GLOW_EM * fontPx)
                            Text(
                                text = ch.toString(),
                                modifier = Modifier.clearAndSetSemantics {},
                                style = style.copy(
                                    shadow = glowShadow.copy(
                                        color = glowShadow.color.copy(alpha = SUNG_BASE_GLOW_ALPHA * charFlare),
                                    ),
                                ),
                                color = Color.Transparent,
                            )
                            Text(
                                text = ch.toString(),
                                style = style,
                                color =
                                    when {
                                        charPast -> Color.White
                                        charActive -> lerp(AppleMusicPendingWordColor, Color.White, charProgress)
                                        else -> AppleMusicPendingWordColor
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapedRichSyncWord(
    word: String,
    style: TextStyle,
    progress: Float,
    isRtl: Boolean,
    restingColor: Color,
    glow: Shadow,
    flare: Float,
    fontPx: Float,
) {
    val rise by animateFloatAsState(
        targetValue = if (progress > 0f) 1f else 0f,
        animationSpec = tween(CHAR_RISE_MS, easing = FastOutSlowInEasing),
        label = "shapedWordRise",
    )
    Box(Modifier.graphicsLayer { translationY = -rise * CHAR_RISE_EM * fontPx }) {
        Text(text = word, style = style, color = restingColor)
        Text(
            text = word,
            modifier = Modifier.clearAndSetSemantics {},
            style = style.copy(shadow = glow.copy(color = glow.color.copy(alpha = SUNG_BASE_GLOW_ALPHA * flare))),
            color = Color.Transparent,
        )
        Text(
            text = word,
            style = style,
            color = Color.White,
            modifier = Modifier
                .clearAndSetSemantics {}
                .drawWithContent {
                    if (progress >= 1f) {
                        drawContent()
                    } else if (progress > 0f) {
                        clipRect(
                            left = if (isRtl) size.width * (1f - progress) else 0f,
                            right = if (isRtl) size.width else size.width * progress,
                        ) { this@drawWithContent.drawContent() }
                    }
                },
        )
    }
}

internal fun LyricWord.startTimeMs(): Long = (startTimeSec * 1000f).roundToLong()

internal fun LyricWord.endTimeMs(): Long = (endTimeSec * 1000f).roundToLong()

internal fun unescapeAppleLyricsHtml(text: String): String {
    if (!text.contains("&")) return text
    var res = text
    res = Regex("&#x([0-9a-fA-F]+);").replace(res) { match ->
        val code = match.groupValues[1].toIntOrNull(16)
        if (code != null) code.toChar().toString() else match.value
    }
    res = Regex("&#([0-9]+);").replace(res) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null) code.toChar().toString() else match.value
    }
    return res
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&hellip;", "…")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&rdquo;", "”")
        .replace("&ldquo;", "“")
}
