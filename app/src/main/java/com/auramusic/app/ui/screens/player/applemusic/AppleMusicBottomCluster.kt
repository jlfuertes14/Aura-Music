package com.auramusic.app.ui.screens.player.applemusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.auramusic.app.core.media.AudioOutputRouter
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.ui.components.AuraOutputSheet
import com.auramusic.app.ui.components.AuraSpeedPitchSheet
import com.auramusic.app.ui.components.AuraTrackInfoSheet
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalGlassBackdrop
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.liquidGlass
import java.util.Locale
import kotlin.math.roundToInt

private val DOCK_BUTTON_SIZE = 44.dp
private val CAPSULE_SEGMENT_WIDTH = 64.dp

/**
 * Track-only Apple Music scrubber/volume slider: 8.4.dp bar swelling smoothly to 16.8.dp
 * on press/drag inside an 18.dp touch box, with [AppleMusicTrackActive] (`White @ 0.92f`)
 * over [AppleMusicTrackInactive] (`White @ 0.26f`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleMusicThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = AppleMusicTrackActive,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val trackHeight by animateDpAsState(
        targetValue = if (pressed || dragged) 16.8.dp else 8.4.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "appleMusicSliderInflate",
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
            onValueChangeFinished = onValueChangeFinished,
            modifier = modifier,
            interactionSource = interactionSource,
            track = {
                val fraction = value.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(AppleMusicTrackInactive),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(activeColor),
                    )
                }
            },
            thumb = {
                Spacer(Modifier.size(0.dp))
            },
        )
    }
}

/**
 * Displays elapsed time on the left, a centered audio quality pill (`"${track.bitrateKbps} kbps · ${track.codec}"`
 * or `"Buffering"` shimmer when [isBuffering]), and `-remaining` time on the right.
 */
@Composable
fun AppleMusicTimesRow(
    track: AuraTrack,
    positionSec: Float,
    durationSec: Float,
    isBuffering: Boolean,
    modifier: Modifier = Modifier,
    typography: AppleMusicTypography = rememberAppleMusicTypography(),
    onOpenTrackInfo: (() -> Unit)? = null,
) {
    val safeDuration = durationSec.coerceAtLeast(0f)
    val clampedElapsed = if (safeDuration > 0f) positionSec.coerceIn(0f, safeDuration) else positionSec.coerceAtLeast(0f)
    val remainingSec = if (safeDuration > 0f) (safeDuration - clampedElapsed).coerceAtLeast(0f).roundToInt() else -1

    val qualityText = remember(track.bitrateKbps, track.codec) {
        val codec = track.codec.ifBlank { "AAC" }
        if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps · $codec" else codec
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatAppleMusicDuration(clampedElapsed.roundToInt()),
                style = typography.times,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Left,
            )
            Text(
                text = if (remainingSec >= 0) "-" + formatAppleMusicDuration(remainingSec) else "--:--",
                style = typography.times,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Right,
            )
        }

        Box(contentAlignment = Alignment.Center) {
            val sweepTransition = rememberInfiniteTransition(label = "appleMusicBufferingSweep")
            val bufferingSweep by sweepTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "appleMusicBufferingSweepHead",
            )
            val bufferingAlpha by animateFloatAsState(
                targetValue = if (isBuffering) 1f else 0f,
                animationSpec = tween(220),
                label = "appleMusicBufferingAlpha",
            )
            val qualityAlpha by animateFloatAsState(
                targetValue = if (!isBuffering) 1f else 0f,
                animationSpec = tween(220),
                label = "appleMusicQualityAlpha",
            )

            Box(modifier = Modifier.alpha(bufferingAlpha)) {
                val shimmerSpan = 120f
                val shimmerHead = bufferingSweep * (shimmerSpan * 3f) - shimmerSpan
                val baseColor = typography.times.color
                Text(
                    text = "Buffering…",
                    style = typography.times.copy(
                        brush = Brush.horizontalGradient(
                            0f to baseColor.copy(alpha = 0.45f),
                            0.5f to Color.White,
                            1f to baseColor.copy(alpha = 0.45f),
                            startX = shimmerHead,
                            endX = shimmerHead + shimmerSpan,
                            tileMode = TileMode.Clamp,
                        ),
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier
                    .alpha(qualityAlpha)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .then(
                        if (onOpenTrackInfo != null) Modifier.clickable(onClick = onOpenTrackInfo) else Modifier
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = AppleMusicQualityLabelColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = qualityText,
                    style = typography.times.copy(color = AppleMusicQualityLabelColor),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Centered Apple Music transport cluster: `SkipPrevious` (46.dp), plain crisp white
 * `PlayArrow`/`Pause` (66.dp) with [appleMusicPressInflate], and `SkipNext` (46.dp) at 58.dp spacing.
 */
@Composable
fun AppleMusicTransportRow(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    isPreviousAvailable: Boolean = true,
    isNextAvailable: Boolean = true,
    showShuffleAndRepeat: Boolean = false,
    playbackMode: PlaybackMode = PlaybackMode.NORMAL,
    onToggleShuffle: (() -> Unit)? = null,
    onCyclePlaybackMode: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (showShuffleAndRepeat) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(58.dp, Alignment.CenterHorizontally)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showShuffleAndRepeat && onToggleShuffle != null) {
            AppleMusicGlyphButton(
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                onClick = onToggleShuffle,
                size = 40.dp,
                tint = Color.White.copy(alpha = if (playbackMode == PlaybackMode.SHUFFLE) 1f else 0.4f),
            )
        }
        IconButton(
            onClick = { if (isPreviousAvailable) onSkipPrevious() },
            modifier = Modifier
                .appleMusicPressInflate()
                .size(56.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                tint = Color.White.copy(alpha = if (isPreviousAvailable) 1f else 0.4f),
                modifier = Modifier.size(46.dp),
            )
        }

        Box(
            modifier = Modifier
                .appleMusicPressInflate()
                .size(76.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = isPlaying, label = "appleMusicPlayPauseIcon") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(66.dp),
                )
            }
        }

        IconButton(
            onClick = { if (isNextAvailable) onSkipNext() },
            modifier = Modifier
                .appleMusicPressInflate()
                .size(56.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                tint = Color.White.copy(alpha = if (isNextAvailable) 1f else 0.4f),
                modifier = Modifier.size(46.dp),
            )
        }

        if (showShuffleAndRepeat && onCyclePlaybackMode != null) {
            AppleMusicGlyphButton(
                icon = if (playbackMode == PlaybackMode.REPEAT_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat mode",
                onClick = onCyclePlaybackMode,
                size = 40.dp,
                tint = Color.White.copy(
                    alpha = if (playbackMode == PlaybackMode.REPEAT_ALL || playbackMode == PlaybackMode.REPEAT_ONE) 1f else 0.4f,
                ),
            )
        }
    }
}

/**
 * Hardware media-stream volume row wired directly to Android's [AudioManager.STREAM_MUSIC],
 * automatically reflecting external volume-key adjustments via `VOLUME_CHANGED_ACTION`.
 */
@Composable
fun AppleMusicVolumeRow(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember(audioManager) {
        (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    }
    var volumeFraction by remember(audioManager) {
        mutableFloatStateOf(
            audioManager?.let {
                it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            } ?: 0.65f,
        )
    }

    DisposableEffect(context, audioManager, maxVolume) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val mgr = audioManager ?: return
                volumeFraction = (mgr.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume).coerceIn(0f, 1f)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    fun applyVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        volumeFraction = clamped
        audioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (clamped * maxVolume).roundToInt().coerceIn(0, maxVolume),
            0,
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { applyVolume((volumeFraction - 1f / maxVolume).coerceAtLeast(0f)) },
            modifier = Modifier
                .appleMusicPressInflate(1.2f)
                .size(44.dp),
        ) {
            Icon(
                imageVector = if (volumeFraction <= 0.01f) {
                    Icons.AutoMirrored.Rounded.VolumeMute
                } else {
                    Icons.AutoMirrored.Rounded.VolumeDown
                },
                contentDescription = "Decrease volume",
                tint = AppleMusicTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppleMusicThinSlider(
                value = volumeFraction,
                activeColor = AppleMusicTrackActive,
                onValueChange = { applyVolume(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        IconButton(
            onClick = { applyVolume((volumeFraction + 1f / maxVolume).coerceAtMost(1f)) },
            modifier = Modifier
                .appleMusicPressInflate(1.2f)
                .size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Increase volume",
                tint = AppleMusicTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Circular 40.dp dock toggle button (Lyrics / Queue) highlighted with [activeColor] and
 * [activeContentColor] when active.
 */
@Composable
fun AppleMusicDockButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container by animateColorAsState(
        targetValue = if (active) activeColor else Color.Transparent,
        animationSpec = tween(220),
        label = "appleMusicDockContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            active -> activeContentColor
            else -> Color.White.copy(alpha = 0.85f)
        },
        animationSpec = tween(220),
        label = "appleMusicDockContent",
    )
    Box(
        modifier = modifier
            .appleMusicPressInflate()
            .size(DOCK_BUTTON_SIZE)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Bottom navigation dock:
 * `Lyrics toggle pill · centered [Comments / More] Liquid Glass or tinted capsule · Queue toggle pill`.
 * Active Lyrics/Queue pills highlight with `lerp(seedColor, White, 0.75f)` container and
 * `lerp(seedColor, Black, 0.6f)` icon tint.
 */
@Composable
fun AppleMusicActionRow(
    viewState: AppleMusicView,
    onSelectView: (AppleMusicView) -> Unit,
    seedColor: Color,
    lyricsAvailable: Boolean,
    onOpenComments: () -> Unit,
    onOpenOptions: () -> Unit,
    onShareTrack: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoMode: Boolean = false,
    onToggleVideoMode: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    onOpenSleepTimer: (() -> Unit)? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    val ignoredShare = onShareTrack
    @Suppress("UNUSED_PARAMETER")
    val ignoredOptions = onOpenOptions
    val activePillContainer = remember(seedColor) { lerp(seedColor, Color.White, 0.75f) }
    val activePillContent = remember(seedColor) { lerp(seedColor, Color.Black, 0.6f) }
    val glassBackdrop = LocalGlassBackdrop.current
    val useGlass = LocalLiquidGlassEnabled.current && glassBackdrop != null
    val segmentCount = if (onOpenSleepTimer != null) 3 else 2

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val dividerCount = segmentCount - 1
        val segmentWidth = ((maxWidth - DOCK_BUTTON_SIZE * 2 - dividerCount.dp - 16.dp) / segmentCount)
            .coerceIn(44.dp, if (segmentCount == 3) 56.dp else 64.dp)
        val contentWidth = DOCK_BUTTON_SIZE * 2 + segmentWidth * segmentCount + dividerCount.dp
        val edgeInset = ((maxWidth - contentWidth) / 4).coerceAtLeast(0.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edgeInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicDockButton(
                icon = Icons.Rounded.Lyrics,
                contentDescription = "Lyrics",
                active = viewState == AppleMusicView.LYRICS,
                activeColor = activePillContainer,
                activeContentColor = activePillContent,
                enabled = lyricsAvailable,
                onClick = {
                    onSelectView(if (viewState == AppleMusicView.LYRICS) AppleMusicView.MAIN else AppleMusicView.LYRICS)
                },
            )

            val capsuleModifier = if (useGlass && glassBackdrop != null) {
                Modifier
                    .height(DOCK_BUTTON_SIZE)
                    .liquidGlass(
                        backdrop = glassBackdrop,
                        shape = CircleShape,
                        interactive = false,
                        fallbackColor = Color.White.copy(alpha = 0.12f),
                        minScrim = 0.08f,
                        maxScrim = 0.28f,
                    )
            } else {
                Modifier
                    .height(DOCK_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            }

            Row(
                modifier = capsuleModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppleMusicCapsuleSegment(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    contentDescription = "Comments",
                    width = segmentWidth,
                    onClick = onOpenComments,
                )
                if (onOpenSleepTimer != null) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.20f)),
                    )
                    AppleMusicCapsuleSegment(
                        icon = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep Timer",
                        width = segmentWidth,
                        tint = if (isSleepTimerActive) AuraNeonLime else Color.White.copy(alpha = 0.85f),
                        showIndicatorDot = isSleepTimerActive,
                        onClick = onOpenSleepTimer,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.20f)),
                )
                AppleMusicCapsuleSegment(
                    icon = if (isVideoMode) Icons.Rounded.Audiotrack else Icons.Rounded.Videocam,
                    contentDescription = if (isVideoMode) "Switch to audio" else "Switch to video",
                    width = segmentWidth,
                    onClick = onToggleVideoMode,
                )
            }

            AppleMusicDockButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Queue",
                active = viewState == AppleMusicView.QUEUE,
                activeColor = activePillContainer,
                activeContentColor = activePillContent,
                onClick = {
                    onSelectView(if (viewState == AppleMusicView.QUEUE) AppleMusicView.MAIN else AppleMusicView.QUEUE)
                },
            )
        }
    }
}

@Composable
private fun AppleMusicCapsuleSegment(
    icon: ImageVector,
    contentDescription: String,
    width: Dp,
    tint: Color = Color.White.copy(alpha = 0.85f),
    showIndicatorDot: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glyphScale by animateFloatAsState(
        targetValue = if (pressed) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "appleMusicCapsuleGlyph",
    )
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = glyphScale
                    scaleY = glyphScale
                }
                .size(22.dp),
        )
        if (showIndicatorDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 10.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AuraNeonLime),
            )
        }
    }
}

/**
 * Fixed bottom control cluster shared by Apple Music `MAIN`, `LYRICS`, and `QUEUE` views:
 * scrub bar, timestamps & audio quality pill, transport controls, hardware volume bar,
 * dock action row, and active audio output caption.
 */
@Composable
fun AppleMusicBottomCluster(
    track: AuraTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionSec: Float,
    durationSec: Float,
    viewState: AppleMusicView,
    onSelectView: (AppleMusicView) -> Unit,
    seedColor: Color,
    lyricsAvailable: Boolean,
    onSeekTo: (Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenOptions: () -> Unit,
    onShareTrack: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoMode: Boolean = false,
    onToggleVideoMode: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    onOpenSleepTimer: (() -> Unit)? = null,
    typography: AppleMusicTypography = rememberAppleMusicTypography(),
    topPadding: Dp = 8.dp,
    outputDeviceLabel: String? = null,
) {
    val safeDuration = durationSec.coerceAtLeast(1f)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val playbackFraction = (positionSec / safeDuration).coerceIn(0f, 1f)
    val displayFraction = if (isScrubbing) scrubFraction else playbackFraction
    val displayPositionSec = if (isScrubbing) scrubFraction * safeDuration else positionSec

    // Reset scrubbing state if track changes mid-drag
    LaunchedEffect(track.videoId) {
        isScrubbing = false
    }

    val context = LocalContext.current
    val playbackSpeed by AuraPlayerManager.playbackSpeed.collectAsState()
    val playbackPitchSemitones by AuraPlayerManager.playbackPitchSemitones.collectAsState()
    val selectedOutputId by AuraPlayerManager.selectedAudioOutputId.collectAsState()
    var showOutputSheet by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showTrackInfoSheet by remember { mutableStateOf(false) }
    val outputDevices = remember(context, showOutputSheet, selectedOutputId) {
        AudioOutputRouter.listOutputDevices(context)
    }
    val resolvedOutputLabel = outputDeviceLabel
        ?: outputDevices.firstOrNull { it.id == selectedOutputId || it.isActive }?.name
        ?: rememberActiveAudioOutputName(context)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = topPadding)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppleMusicThinSlider(
                value = displayFraction,
                activeColor = AppleMusicTrackActive,
                onValueChange = { fraction ->
                    isScrubbing = true
                    scrubFraction = fraction
                },
                onValueChangeFinished = {
                    if (isScrubbing) {
                        onSeekTo((scrubFraction * safeDuration).coerceIn(0f, safeDuration))
                        isScrubbing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AppleMusicTimesRow(
            track = track,
            positionSec = displayPositionSec,
            durationSec = durationSec,
            isBuffering = isBuffering,
            typography = typography,
            onOpenTrackInfo = { showTrackInfoSheet = true },
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(22.dp))

        AppleMusicTransportRow(
            isPlaying = isPlaying,
            onTogglePlayPause = onTogglePlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
        )

        Spacer(modifier = Modifier.height(18.dp))

        AppleMusicVolumeRow()

        Spacer(modifier = Modifier.height(24.dp))

        AppleMusicActionRow(
            viewState = viewState,
            onSelectView = onSelectView,
            seedColor = seedColor,
            lyricsAvailable = lyricsAvailable,
            onOpenComments = onOpenComments,
            onOpenOptions = onOpenOptions,
            onShareTrack = onShareTrack,
            isVideoMode = isVideoMode,
            onToggleVideoMode = onToggleVideoMode,
            isSleepTimerActive = isSleepTimerActive,
            onOpenSleepTimer = onOpenSleepTimer,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(
                targetState = resolvedOutputLabel.orEmpty(),
                animationSpec = tween(300),
                label = "appleMusicOutputCaption",
            ) { name ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showOutputSheet = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SpeakerGroup,
                        contentDescription = "Audio Output Device",
                        tint = typography.footer.color,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = name.ifBlank { "Audio Output" },
                        style = typography.footer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            val isSpeedPitchModified = playbackSpeed != 1.0f || playbackPitchSemitones != 0
            val speedChipLabel = if (isSpeedPitchModified) {
                buildString {
                    append(String.format(Locale.US, "%.2gx", playbackSpeed))
                    if (playbackPitchSemitones != 0) {
                        append(" ")
                        append(if (playbackPitchSemitones > 0) "+$playbackPitchSemitones" else "$playbackPitchSemitones")
                        append("st")
                    }
                }
            } else {
                "1x"
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSpeedPitchModified) AuraNeonLime.copy(alpha = 0.20f)
                        else Color.White.copy(alpha = 0.09f)
                    )
                    .clickable { showSpeedPitchSheet = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = "Speed & Pitch",
                    tint = if (isSpeedPitchModified) AuraNeonLime else typography.footer.color,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = speedChipLabel,
                    style = typography.footer.copy(
                        color = if (isSpeedPitchModified) AuraNeonLime else typography.footer.color
                    ),
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }

    if (showOutputSheet) {
        AuraOutputSheet(
            devices = outputDevices,
            selectedDeviceId = selectedOutputId,
            onSelectDevice = { dev ->
                AuraPlayerManager.selectAudioOutputDevice(dev)
                showOutputSheet = false
            },
            onDismiss = { showOutputSheet = false },
        )
    }

    if (showSpeedPitchSheet) {
        AuraSpeedPitchSheet(
            speed = playbackSpeed,
            pitchSemitones = playbackPitchSemitones,
            onSpeedChange = { AuraPlayerManager.setPlaybackSpeed(it) },
            onPitchChange = { AuraPlayerManager.setPlaybackPitchSemitones(it) },
            onReset = { AuraPlayerManager.resetSpeedAndPitch() },
            onDismiss = { showSpeedPitchSheet = false },
        )
    }

    if (showTrackInfoSheet) {
        AuraTrackInfoSheet(
            track = track,
            rydData = null,
            description = "",
            onSeekToSec = { sec ->
                onSeekTo(sec.toFloat())
                showTrackInfoSheet = false
            },
            onDismiss = { showTrackInfoSheet = false },
        )
    }
}

@Composable
private fun rememberActiveAudioOutputName(context: Context): String? {
    return remember(context) {
        runCatching {
            val mgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@runCatching null
            val devices = mgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val external = devices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
            val label = external?.productName?.toString()?.trim()?.takeIf { it.isNotBlank() }
            label ?: when {
                devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } -> "Bluetooth Audio"
                devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "Headphones"
                else -> "${Build.MODEL} Speaker"
            }
        }.getOrNull()
    }
}
