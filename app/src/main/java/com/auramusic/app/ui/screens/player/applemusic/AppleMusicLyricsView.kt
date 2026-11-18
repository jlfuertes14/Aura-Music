package com.auramusic.app.ui.screens.player.applemusic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily
import com.auramusic.app.ui.theme.LiquidGlassContainer
import com.auramusic.app.ui.theme.LiquidGlassIconButton
import com.auramusic.app.ui.theme.LocalGlassBackdrop
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

private const val FADE_EDGE_STEPS = 24
private const val FOOTER_ALPHA = 0.45f
private const val AUTO_SCROLL_RESUME_DELAY_MS = 3_500L
private const val CLUSTER_AUTO_HIDE_MS = 8_000L

private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

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
 * Smoothstep `BlendMode.DstIn` vertical edge dissolve so scrolling lyrics fade cleanly into
 * whatever backdrop sits behind them without painting a hard color scrim.
 */
fun Modifier.appleMusicVerticalFadeEdges(
    topFade: Dp = 28.dp,
    bottomFade: Dp = 18.dp,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = topFade.toPx().coerceAtMost(size.height / 2f)
            val bottomPx = bottomFade.toPx().coerceAtMost(size.height / 2f)
            val topStop = if (size.height > 0f) topPx / size.height else 0f
            val bottomStop = if (size.height > 0f) 1f - bottomPx / size.height else 1f
            drawRect(
                brush = Brush.verticalGradient(colorStops = fadeEdgeStops(topStop, bottomStop)),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * Reusable Apple Music depth-of-field synced lyrics list, used in both [AppleMusicLyricsTabView]
 * and `FullScreenSongLyricsView` when `LyricsStyle.APPLE_MUSIC` is active.
 */
@Composable
fun AppleMusicLyricsList(
    lyricsData: LyricsData?,
    currentPositionSec: Float,
    lyricsOffsetMs: Long,
    isPlaying: Boolean,
    showRomanized: Boolean,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    bottomExtraPadding: Dp = 0.dp,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val displayLines = remember(lyricsData?.syncedLines, lyricsData?.plainLyrics) {
        buildAppleMusicDisplayLines(
            lines = lyricsData?.syncedLines.orEmpty(),
            plainLyrics = lyricsData?.plainLyrics.orEmpty(),
        )
    }

    val effectiveMs = (currentPositionSec * 1000f).roundToLong() + lyricsOffsetMs
    val currentDisplayIndex = remember(displayLines, effectiveMs) {
        displayLines.activeIndexAt(effectiveMs)
    }

    val initialAnchorIndex = remember(lyricsData?.videoId) {
        (currentDisplayIndex - 1).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialAnchorIndex)
    val isDragging by listState.interactionSource.collectIsDraggedAsState()

    var isUserScrollingPaused by remember(lyricsData?.videoId) { mutableStateOf(false) }
    var userScrollTick by remember { mutableIntStateOf(0) }

    val userScrollTracker = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0.5f) {
                    isUserScrollingPaused = true
                    userScrollTick++
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            isUserScrollingPaused = true
            userScrollTick++
        }
    }

    LaunchedEffect(isUserScrollingPaused, isDragging, userScrollTick) {
        if (isUserScrollingPaused && !isDragging) {
            delay(AUTO_SCROLL_RESUME_DELAY_MS)
            isUserScrollingPaused = false
        }
    }

    val exposedRowPx = with(LocalDensity.current) {
        AppleMusicLyricLineHeight.toPx() + AppleMusicLyricGap.toPx()
    }
    val stagger = remember(displayLines) { LyricsStagger() }

    LaunchedEffect(currentDisplayIndex, displayLines.isUnsynced, isUserScrollingPaused, stagger) {
        if (currentDisplayIndex >= 0 && !displayLines.isUnsynced && !isUserScrollingPaused) {
            listState.animateScrollAndAnchorItemTop(
                index = currentDisplayIndex,
                extraOffsetPx = -exposedRowPx,
                stagger = stagger,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .appleMusicVerticalFadeEdges(topFade = 28.dp, bottomFade = 18.dp),
    ) {
        val tailPadding = maxHeight * 0.72f + bottomExtraPadding

        if (isLoading && displayLines.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Loading lyrics…",
                        style = TextStyle(
                            fontFamily = AuraPoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.72f),
                        ),
                    )
                }
            }
        } else if (displayLines.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppleMusicLyricPaddingX),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No lyrics available for this track.",
                    style = TextStyle(
                        fontFamily = AuraPoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        color = Color.White.copy(alpha = 0.65f),
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(userScrollTracker),
                contentPadding = PaddingValues(bottom = tailPadding),
            ) {
                items(
                    count = displayLines.items.size,
                    key = { idx ->
                        val item = displayLines.items[idx]
                        if (item.isInterlude) {
                            "interlude_${idx}_${item.line.timeSec}"
                        } else {
                            "line_${idx}_${item.line.timeSec}_${item.line.text.hashCode()}"
                        }
                    },
                ) { index ->
                    val entry = displayLines.items[index]
                    val distanceFromCurrent =
                        if (currentDisplayIndex < 0) 0 else index - currentDisplayIndex

                    AppleMusicLyricRow(
                        displayLine = entry,
                        index = index,
                        distanceFromCurrent = distanceFromCurrent,
                        isCurrent = index == currentDisplayIndex,
                        isUserDragging = isDragging || isUserScrollingPaused,
                        hasActiveLine = currentDisplayIndex >= 0,
                        isUnsynced = displayLines.isUnsynced,
                        currentPositionMs = effectiveMs,
                        isPlaying = isPlaying,
                        showRomanized = showRomanized,
                        stagger = stagger,
                        onLineClick = { targetSec ->
                            isUserScrollingPaused = false
                            onLineClick(targetSec)
                        },
                    )
                }

                item(key = "apple_music_lyrics_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = stagger.lagPx(displayLines.items.size) }
                            .padding(horizontal = AppleMusicLyricPaddingX)
                            .alpha(FOOTER_ALPHA),
                    ) {
                        if (footerContent != null) {
                            footerContent()
                        } else {
                            DefaultLyricsAttributionFooter(
                                lyricsData = lyricsData,
                                isUnsynced = displayLines.isUnsynced,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultLyricsAttributionFooter(
    lyricsData: LyricsData?,
    isUnsynced: Boolean,
) {
    if (lyricsData == null) return
    val syncLabel =
        when {
            isUnsynced || lyricsData.syncedLines.isEmpty() -> "Unsynced"
            lyricsData.source.contains("RichSync", ignoreCase = true) ||
                lyricsData.syncedLines.any { it.words.isNotEmpty() } -> "Word by word"
            else -> "Line synced"
        }
    val footerStyle = TextStyle(
        fontFamily = AuraPoppinsFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = Color.White.copy(alpha = 0.72f),
    )

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = syncLabel,
            style = footerStyle,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        if (lyricsData.source.isNotBlank() && lyricsData.source != "None") {
            Text(
                text = "Lyrics provided by ${lyricsData.source}",
                style = footerStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Full Apple Music `LYRICS` tab layout combining [compactHeader], [AppleMusicLyricsList],
 * floating `38.dp` Liquid Glass / translucent action pills (Offset `±0.1s` / `±0.5s`,
 * Romanization toggle when `hasRomanization`, and Fullscreen expand), and an `8_000L` ms
 * auto-hiding [bottomCluster] woken on tap or scroll.
 */
@Composable
fun AppleMusicLyricsTabView(
    track: AuraTrack,
    lyricsData: LyricsData?,
    isLoading: Boolean,
    currentPositionSec: Float,
    isPlaying: Boolean,
    lyricsOffsetMs: Long,
    showRomanized: Boolean,
    onSeekToSec: (Float) -> Unit,
    onAdjustOffsetMs: (Int) -> Unit,
    onToggleRomanized: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onBackToMain: () -> Unit,
    compactHeader: @Composable () -> Unit,
    bottomCluster: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val backdrop = LocalGlassBackdrop.current

    var showCluster by rememberSaveable { mutableStateOf(true) }
    var showOffsetControl by rememberSaveable(track.videoId) { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(showCluster, interactionTick) {
        if (showCluster) {
            delay(CLUSTER_AUTO_HIDE_MS)
            showCluster = false
            showOffsetControl = false
        }
    }

    val scrollWakesControls = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    showCluster = true
                    interactionTick++
                }
                return Offset.Zero
            }
        }
    }

    val hasTimedLyrics = remember(lyricsData?.syncedLines) {
        val lines = lyricsData?.syncedLines.orEmpty()
        lines.size >= 2 && lines.map { (it.timeSec * 1000f).roundToLong() }.distinct().size > 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Lyrics for ${track.title} by ${track.artist}"
            },
    ) {
        Spacer(modifier = Modifier.height(statusTopDp + 20.dp))
        compactHeader()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(scrollWakesControls)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    showCluster = !showCluster
                    if (!showCluster) showOffsetControl = false
                    interactionTick++
                },
        ) {
            AppleMusicLyricsList(
                lyricsData = lyricsData,
                currentPositionSec = currentPositionSec,
                lyricsOffsetMs = lyricsOffsetMs,
                isPlaying = isPlaying,
                showRomanized = showRomanized,
                isLoading = isLoading,
                onLineClick = { targetSec ->
                    interactionTick++
                    onSeekToSec(targetSec)
                },
            )

            if (lyricsData == null && !isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 96.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(role = Role.Button, onClick = onBackToMain)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Back to player",
                        style = TextStyle(
                            fontFamily = AuraPoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White,
                        ),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showCluster && !isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Column(
                    modifier = Modifier.padding(end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (hasTimedLyrics) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnimatedVisibility(visible = showOffsetControl) {
                                LyricsOffsetInlinePill(
                                    offsetMs = lyricsOffsetMs.toInt(),
                                    backdrop = backdrop,
                                    onAdjustOffsetMs = { delta ->
                                        interactionTick++
                                        onAdjustOffsetMs(delta)
                                    },
                                )
                            }
                            FloatingLyricsPillButton(
                                icon = Icons.Rounded.Timer,
                                contentDescription = "Toggle lyrics timing offset controls",
                                active = showOffsetControl || lyricsOffsetMs != 0L,
                                onClick = {
                                    interactionTick++
                                    showOffsetControl = !showOffsetControl
                                },
                            )
                        }
                    }

                    if (lyricsData?.hasRomanization == true) {
                        FloatingLyricsPillButton(
                            icon = Icons.Rounded.Translate,
                            contentDescription = if (showRomanized) {
                                "Hide romanized lyrics"
                            } else {
                                "Show romanized lyrics"
                            },
                            active = showRomanized,
                            onClick = {
                                interactionTick++
                                onToggleRomanized()
                            },
                        )
                    }

                    FloatingLyricsPillButton(
                        icon = Icons.Rounded.OpenInFull,
                        contentDescription = "Open fullscreen lyrics",
                        active = false,
                        onClick = {
                            interactionTick++
                            onOpenFullscreen()
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showCluster,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            bottomCluster()
        }
    }
}

@Composable
private fun FloatingLyricsPillButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val tint = if (active) AuraNeonLime else Color.White

    if (backdrop != null) {
        LiquidGlassIconButton(
            backdrop = backdrop,
            imageVector = icon,
            modifier = Modifier
                .pressInflate()
                .size(38.dp),
            shape = CircleShape,
            tint = tint,
            contentDescription = contentDescription,
            onClick = onClick,
        )
    } else {
        Box(
            modifier = Modifier
                .pressInflate()
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.24f),
                )
                .clickable(
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LyricsOffsetInlinePill(
    offsetMs: Int,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onAdjustOffsetMs: (Int) -> Unit,
) {
    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OffsetStepChip(
                    label = "-0.5s",
                    contentDescription = "Delay lyrics by 0.5 seconds",
                    onClick = { onAdjustOffsetMs(-500) },
                )
                OffsetStepChip(
                    label = "-0.1s",
                    contentDescription = "Delay lyrics by 0.1 seconds",
                    onClick = { onAdjustOffsetMs(-100) },
                )
                Text(
                    text = formatOffsetSeconds(offsetMs),
                    style = TextStyle(
                        fontFamily = AuraPoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (offsetMs != 0) AuraNeonLime else Color.White,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 42.dp),
                )
                OffsetStepChip(
                    label = "+0.1s",
                    contentDescription = "Advance lyrics by 0.1 seconds",
                    onClick = { onAdjustOffsetMs(100) },
                )
                OffsetStepChip(
                    label = "+0.5s",
                    contentDescription = "Advance lyrics by 0.5 seconds",
                    onClick = { onAdjustOffsetMs(500) },
                )
                AnimatedVisibility(visible = offsetMs != 0) {
                    IconButton(
                        onClick = { onAdjustOffsetMs(-offsetMs) },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset lyrics timing offset",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }

    if (backdrop != null) {
        LiquidGlassContainer(
            backdrop = backdrop,
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(19.dp),
            interactive = false,
        ) {
            content()
        }
    } else {
        Box(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun OffsetStepChip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = AuraPoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.White,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Modifier.pressInflate(pressedScale: Float = 1.24f): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "floatingPillInflate",
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

private fun formatOffsetSeconds(offsetMs: Int): String {
    if (offsetMs == 0) return "0.0s"
    val sign = if (offsetMs > 0) "+" else "-"
    val absMs = abs(offsetMs)
    val sec = absMs / 1000
    val tenths = (absMs % 1000) / 100
    return "$sign$sec.${tenths}s"
}
