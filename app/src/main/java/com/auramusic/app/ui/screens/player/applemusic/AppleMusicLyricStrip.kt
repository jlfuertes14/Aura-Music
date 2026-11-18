package com.auramusic.app.ui.screens.player.applemusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricWord
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

private val STRIP_UNSUNG_COLOR = Color.White.copy(alpha = 0.45f)
private val STRIP_ROMANIZED_COLOR = Color.White.copy(alpha = 0.68f)
private val WAITING_DOT_SIZE = 5.dp
private val WAITING_DOT_GAP = 5.dp

private const val LINE_CHANGE_MS = 340
private const val LAST_WORD_FALLBACK_MS = 600L

private const val SWEEP_CATCH_UP_MS = 150.0
private const val SWEEP_SNAP_MS = 500.0
private const val SWEEP_MAX_EXTRAPOLATION_MS = 250.0

private sealed interface StripContent {
    data class Sung(val line: LyricLine) : StripContent
    data object Waiting : StripContent
}

/**
 * Inline lyric strip for the Apple Music Now Playing `MAIN` tab.
 *
 * Transitions between active lines with a `340ms` vertical slide + fade, shows 3 pulsing
 * `5.dp` [WaitingDots] during pre-roll or `>= 3.0s` instrumental gaps, and sweeps RichSync
 * lines smoothly on the frame clock using `withInfiniteAnimationFrameNanos` + `clipRect`
 * inside a `basicMarquee` container, with an optional `12.sp` romanized subtitle.
 */
@Composable
fun AppleMusicLyricStrip(
    lines: List<LyricLine>,
    currentPositionSec: Float,
    lyricsOffsetMs: Long,
    isPlaying: Boolean,
    showRomanized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLines = remember(lines) { buildAppleMusicDisplayLines(lines) }
    val isSynced = !displayLines.isUnsynced && displayLines.items.isNotEmpty()
    val effectiveMs = (currentPositionSec * 1000f).roundToLong() + lyricsOffsetMs
    val activeIdx = if (isSynced) displayLines.activeIndexAt(effectiveMs) else -1
    val activeEntry = if (isSynced && activeIdx >= 0) displayLines.items.getOrNull(activeIdx) else null
    val activeLine = activeEntry?.takeIf { !it.isInterlude }?.line

    val playhead =
        rememberSweepPlayhead(
            rawMs = effectiveMs,
            running = activeLine != null && activeLine.words.isNotEmpty() && isPlaying,
        )

    val targetState: StripContent? =
        when {
            activeLine != null -> StripContent.Sung(activeLine)
            isSynced -> StripContent.Waiting
            else -> null
        }

    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val enter =
                fadeIn(tween(LINE_CHANGE_MS, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(LINE_CHANGE_MS, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() }
            val exit =
                fadeOut(tween(LINE_CHANGE_MS, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(LINE_CHANGE_MS, easing = FastOutSlowInEasing)) { -(it * 0.35f).toInt() }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "appleMusicLyricStrip",
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = lines.isNotEmpty(),
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Open synced lyrics",
                onClick = onClick,
            ),
    ) { shown ->
        val lineText = (shown as? StripContent.Sung)?.line?.let(::stripDisplayText).orEmpty()
        val lineDirection = rememberAppleLyricLayoutDirection(lineText)

        CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (shown) {
                    is StripContent.Sung -> {
                        val romanized =
                            if (showRomanized) {
                                shown.line.romanizedText?.let(::unescapeAppleLyricsHtml)?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (shown.line.words.isNotEmpty()) {
                                SweptStripLine(
                                    words = shown.line.words,
                                    lineEndSec = shown.line.endTimeSec,
                                    playhead = playhead,
                                )
                            } else {
                                StripText(text = stripDisplayText(shown.line))
                            }
                            if (romanized != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = romanized,
                                    style = TextStyle(
                                        fontFamily = AuraPoppinsFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = STRIP_ROMANIZED_COLOR,
                                        textDirection = TextDirection.ContentOrLtr,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    StripContent.Waiting -> {
                        // Keep a matching single-line height behind the dots so the slot stays stable.
                        StripText(text = "")
                        WaitingDots(isPlaying = isPlaying)
                    }

                    null -> {
                        StripText(text = "")
                    }
                }
            }
        }
    }
}

private val StripTextStyle = TextStyle(
    fontFamily = AuraPoppinsFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = Color.White,
)

@Composable
private fun StripText(text: String) {
    Text(
        text = text,
        style = StripTextStyle.copy(textDirection = TextDirection.ContentOrLtr),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            animationMode = MarqueeAnimationMode.Immediately,
        ),
    )
}

@Composable
private fun WaitingDots(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "appleMusicWaitingDots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waitingDotsPhase",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(WAITING_DOT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val wave = if (isPlaying) {
                val offset = ((phase - index * 0.22f + 1f) % 1f)
                (0.5f + 0.5f * sin(offset * 2f * PI.toFloat())).coerceIn(0f, 1f)
            } else {
                0.25f
            }
            val dotAlpha = 0.35f + 0.55f * wave
            val dotScale = 0.85f + 0.25f * wave
            Box(
                modifier = Modifier
                    .size(WAITING_DOT_SIZE)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                        alpha = dotAlpha
                    }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SweptStripLine(
    words: List<LyricWord>,
    lineEndSec: Float,
    playhead: State<Long>,
) {
    val cleanWords = remember(words) {
        words.map { w -> w.copy(word = unescapeAppleLyricsHtml(w.word)) }
    }
    val text = remember(cleanWords) { cleanWords.joinToString(" ") { it.word } }
    val ranges = remember(cleanWords) {
        var start = 0
        cleanWords.map { word ->
            val range = start until (start + word.word.length)
            start += word.word.length + 1
            range
        }
    }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier = Modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            animationMode = MarqueeAnimationMode.Immediately,
        ),
    ) {
        Text(
            text = text,
            style = StripTextStyle.copy(color = STRIP_UNSUNG_COLOR, textDirection = TextDirection.ContentOrLtr),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { layout = it },
        )
        Text(
            text = text,
            style = StripTextStyle.copy(color = Color.White, textDirection = TextDirection.ContentOrLtr),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.drawWithContent {
                val measured = layout ?: return@drawWithContent
                val sweepX = computeSweepX(cleanWords, ranges, lineEndSec, measured, playhead.value)
                    ?: return@drawWithContent
                if (measured.getParagraphDirection(0) == ResolvedTextDirection.Rtl) {
                    clipRect(left = sweepX) { this@drawWithContent.drawContent() }
                } else {
                    clipRect(right = sweepX) { this@drawWithContent.drawContent() }
                }
            },
        )
    }
}

private fun computeSweepX(
    words: List<LyricWord>,
    ranges: List<IntRange>,
    lineEndSec: Float,
    layout: TextLayoutResult,
    positionMs: Long,
): Float? {
    val current = words.indexOfLast { it.startTimeMs() <= positionMs }
    if (current < 0) return null
    val range = ranges[current]
    if (range.isEmpty()) return null
    val startMs = words[current].startTimeMs()
    val explicitEndMs = words[current].endTimeMs()
    val lineEndMs = (lineEndSec * 1000f).roundToLong()
    val endMs =
        words.getOrNull(current + 1)?.startTimeMs()
            ?: explicitEndMs.takeIf { it > startMs }
            ?: lineEndMs.takeIf { it > startMs }
            ?: (startMs + LAST_WORD_FALLBACK_MS)
    val progress = ((positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val lastIndex = layout.layoutInput.text.length.coerceAtLeast(1)
    val left = layout.getHorizontalPosition(range.first.coerceIn(0, lastIndex), usePrimaryDirection = true)
    val right = layout.getHorizontalPosition((range.last + 1).coerceIn(0, lastIndex), usePrimaryDirection = true)
    return left + (right - left) * progress
}

@Composable
private fun rememberSweepPlayhead(
    rawMs: Long,
    running: Boolean,
): State<Long> {
    val playhead = remember { mutableLongStateOf(rawMs) }
    val latestRaw by rememberUpdatedState(rawMs)
    LaunchedEffect(running) {
        if (!running) {
            snapshotFlow { latestRaw }.collect { playhead.longValue = it }
            return@LaunchedEffect
        }
        var reported = latestRaw
        var reportedAt = -1L
        var lastFrame = -1L
        var position = playhead.longValue.toDouble()
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (reportedAt < 0L || latestRaw != reported) {
                    reported = latestRaw
                    reportedAt = now
                }
                val target = reported + ((now - reportedAt) / 1_000_000.0).coerceAtMost(SWEEP_MAX_EXTRAPOLATION_MS)
                val step = if (lastFrame < 0L) 0.0 else (now - lastFrame) / 1_000_000.0
                lastFrame = now
                position =
                    if (abs(target - position) > SWEEP_SNAP_MS) {
                        target
                    } else {
                        val advanced = position + step
                        max(position, advanced + (target - advanced) * (step / SWEEP_CATCH_UP_MS).coerceAtMost(1.0))
                    }
                playhead.longValue = position.roundToLong()
            }
        }
    }
    return playhead
}

private fun stripDisplayText(line: LyricLine): String =
    if (line.text.isNotBlank()) {
        unescapeAppleLyricsHtml(line.text)
    } else {
        line.words.joinToString(" ") { unescapeAppleLyricsHtml(it.word) }
    }
