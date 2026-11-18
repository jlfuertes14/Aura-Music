package com.auramusic.app.ui.screens.player.applemusic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.ui.components.AudioPlayingIndicator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val QUEUE_TOP_FADE = 24.dp
private val QUEUE_BOTTOM_FADE = 48.dp

/**
 * Apple Music Queue tab view:
 * - [AppleMusicCompactHeader] at top
 * - Action pills: Shuffle, Repeat/[PlaybackMode] cycling, Save/Add to Playlist
 * - Upcoming & active queue list in a [LazyColumn] with [appleMusicVerticalFadeEdges],
 *   active track highlight, tap-to-play, item removal, and optional drag-to-reorder
 * - Fixed [bottomCluster] anchored below
 */
@Composable
fun AppleMusicQueueView(
    currentTrack: AuraTrack,
    queue: List<AuraTrack>,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    playbackMode: PlaybackMode,
    seedColor: Color,
    onTapHeaderTrack: () -> Unit,
    onTapArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOptionsClick: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onPlayQueueTrack: (AuraTrack) -> Unit,
    modifier: Modifier = Modifier,
    typography: AppleMusicTypography = rememberAppleMusicTypography(),
    onRemoveQueueItem: ((Int) -> Unit)? = null,
    onMoveQueueItem: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    bottomCluster: @Composable () -> Unit = {},
) {
    val activePillContainer = remember(seedColor) { lerp(seedColor, Color.White, 0.75f) }
    val activePillContent = remember(seedColor) { lerp(seedColor, Color.Black, 0.6f) }

    val displayQueue = remember(queue, currentTrack) {
        if (queue.isEmpty()) listOf(currentTrack) else queue
    }
    val currentIndex = remember(displayQueue, currentTrack.videoId, currentTrack.id) {
        displayQueue.indexOfFirst {
            it.videoId == currentTrack.videoId && it.id == currentTrack.id
        }.takeIf { it >= 0 }
            ?: displayQueue.indexOfFirst { it.videoId == currentTrack.videoId }
    }

    val rowKeys = remember(displayQueue) {
        val seen = HashMap<String, Int>(displayQueue.size)
        displayQueue.map { item ->
            val base = item.videoId.ifBlank { item.id }
            val occurrence = seen.getOrElse(base) { 0 }
            seen[base] = occurrence + 1
            "$base#$occurrence"
        }
    }

    // Drag-to-reorder state (decoupled from queue emission during active gesture)
    var localQueue by remember(displayQueue) { mutableStateOf(displayQueue) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetDropIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val tileHeightPx = with(density) { 60.dp.toPx() }

    LaunchedEffect(Unit) {
        if (currentIndex > 0) {
            lazyListState.scrollToItem(currentIndex)
        }
    }

    fun startDrag(index: Int) {
        if (onMoveQueueItem == null || index !in localQueue.indices) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        draggedIndex = index
        targetDropIndex = index
        dragOffsetY = 0f
    }

    fun handleDragDelta(deltaY: Float) {
        val current = draggedIndex ?: return
        dragOffsetY += deltaY
        val deltaSlots = (dragOffsetY / tileHeightPx).roundToInt()
        val newTarget = (current + deltaSlots).coerceIn(0, localQueue.lastIndex)
        if (newTarget != targetDropIndex) {
            targetDropIndex = newTarget
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        val layoutInfo = lazyListState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
        if (itemInfo != null) {
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val topInViewport = itemInfo.offset + dragOffsetY
            val bottomInViewport = topInViewport + itemInfo.size
            if (topInViewport < 48f) {
                coroutineScope.launch { lazyListState.scrollBy(-18f) }
            } else if (bottomInViewport > viewportHeight - 48f) {
                coroutineScope.launch { lazyListState.scrollBy(18f) }
            }
        }
    }

    fun commitDrop() {
        val from = draggedIndex
        val to = targetDropIndex
        if (from != null && to != null && from != to && from in localQueue.indices && to in localQueue.indices) {
            val mutable = localQueue.toMutableList()
            val moved = mutable.removeAt(from)
            mutable.add(to, moved)
            localQueue = mutable
            onMoveQueueItem?.invoke(from, to)
        }
        draggedIndex = null
        targetDropIndex = null
        dragOffsetY = 0f
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Leave room for the floating grabber sitting above the Crossfade
        Spacer(
            modifier = Modifier
                .statusBarsPadding()
                .height(24.dp),
        )

        AppleMusicCompactHeader(
            track = currentTrack,
            onTapTrack = onTapHeaderTrack,
            onTapArtist = onTapArtist,
            typography = typography,
            trailingActions = {
                AppleMusicHeaderActions(
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOptionsClick = onOptionsClick,
                )
            },
        )

        AppleMusicQueuePillsRow(
            isShuffleEnabled = isShuffleEnabled,
            playbackMode = playbackMode,
            activePillContainer = activePillContainer,
            activePillContent = activePillContent,
            onToggleShuffle = onToggleShuffle,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onSaveToPlaylist = onSaveToPlaylist,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        // Section Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 4.dp),
        ) {
            val cleanAlbum = currentTrack.album.trim()
            val validSource = cleanAlbum.isNotBlank() &&
                cleanAlbum != currentTrack.title &&
                !cleanAlbum.matches(Regex("^\\d+:\\d{2}(:\\d{2})?$")) &&
                !cleanAlbum.endsWith("views", ignoreCase = true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (validSource) "Playing from" else "Continue Playing",
                        style = typography.queueSectionSubtitle,
                    )
                    Text(
                        text = if (validSource) cleanAlbum else "Upcoming",
                        style = typography.queueSectionHeader,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${localQueue.size} ${if (localQueue.size == 1) "track" else "tracks"}",
                    style = typography.queueSectionSubtitle,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .appleMusicVerticalFadeEdges(
                    topFadeHeight = QUEUE_TOP_FADE,
                    bottomFadeHeight = QUEUE_BOTTOM_FADE,
                ),
        ) {
            if (localQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queue is empty",
                        style = typography.mainArtist,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(top = QUEUE_TOP_FADE, bottom = QUEUE_BOTTOM_FADE),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = localQueue,
                        key = { index, _ -> rowKeys.getOrElse(index) { "q_$index" } },
                    ) { index, item ->
                        val isActive = index == currentIndex ||
                            (currentIndex < 0 && item.videoId == currentTrack.videoId)
                        val isDraggingThis = draggedIndex == index
                        val currentDrag = draggedIndex
                        val currentTarget = targetDropIndex

                        val itemDisplacementY by animateFloatAsState(
                            targetValue = when {
                                currentDrag == null || currentTarget == null -> 0f
                                index == currentDrag -> 0f
                                currentDrag > currentTarget && index in currentTarget until currentDrag -> tileHeightPx
                                currentDrag < currentTarget && index in (currentDrag + 1)..currentTarget -> -tileHeightPx
                                else -> 0f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "queueDisp_$index",
                        )

                        val animatedElevation by animateDpAsState(
                            targetValue = if (isDraggingThis) 12.dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "queueElev_$index",
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isDraggingThis) 1.03f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "queueScale_$index",
                        )

                        val rowContainerColor = when {
                            isDraggingThis -> Color(0xFF3A3D46)
                            isActive -> activePillContainer.copy(alpha = 0.16f)
                            else -> Color.Transparent
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = rowContainerColor,
                            shadowElevation = animatedElevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 1.dp)
                                .zIndex(if (isDraggingThis) 25f else 1f)
                                .graphicsLayer {
                                    translationY = if (isDraggingThis) dragOffsetY else itemDisplacementY
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                }
                                .then(
                                    if (onMoveQueueItem != null) {
                                        Modifier.pointerInput(index, localQueue.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { startDrag(index) },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    handleDragDelta(dragAmount.y)
                                                },
                                                onDragEnd = { commitDrop() },
                                                onDragCancel = { commitDrop() },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable {
                                    if (draggedIndex == null) {
                                        onPlayQueueTrack(item)
                                    }
                                },
                        ) {
                            AppleMusicQueueRowContent(
                                track = item,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                activeAccent = activePillContainer,
                                typography = typography,
                                showRemove = onRemoveQueueItem != null && !isActive,
                                onRemove = { onRemoveQueueItem?.invoke(index) },
                                showDragHandle = onMoveQueueItem != null,
                                onHandleDragStart = { startDrag(index) },
                                onHandleDragDelta = { handleDragDelta(it) },
                                onHandleDragEnd = { commitDrop() },
                            )
                        }
                    }
                }
            }
        }

        bottomCluster()
    }
}

@Composable
private fun AppleMusicQueueRowContent(
    track: AuraTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    activeAccent: Color,
    typography: AppleMusicTypography,
    showRemove: Boolean,
    onRemove: () -> Unit,
    showDragHandle: Boolean,
    onHandleDragStart: () -> Unit,
    onHandleDragDelta: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.highResArtworkUrl)
                    .crossfade(250)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AudioPlayingIndicator(
                        modifier = Modifier.fillMaxSize(0.70f),
                        color = activeAccent,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = typography.compactTitle.copy(
                    color = if (isActive) activeAccent else Color.White,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    ExplicitBadge(
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(
                    text = track.artist,
                    style = typography.compactArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (showRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove from queue",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (showDragHandle) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .pointerInput(track.videoId, track.id) {
                        detectDragGestures(
                            onDragStart = { onHandleDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onHandleDragDelta(dragAmount.y)
                            },
                            onDragEnd = onHandleDragEnd,
                            onDragCancel = onHandleDragEnd,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Reorder track",
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AppleMusicQueuePillsRow(
    isShuffleEnabled: Boolean,
    playbackMode: PlaybackMode,
    activePillContainer: Color,
    activePillContent: Color,
    onToggleShuffle: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isShuffleActive = isShuffleEnabled || playbackMode == PlaybackMode.SHUFFLE
    val isRepeatActive = playbackMode == PlaybackMode.REPEAT_ALL || playbackMode == PlaybackMode.REPEAT_ONE
    val repeatIcon = if (playbackMode == PlaybackMode.REPEAT_ONE) {
        Icons.Rounded.RepeatOne
    } else {
        Icons.Rounded.Repeat
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppleMusicQueuePill(
            icon = Icons.Rounded.Shuffle,
            label = "Shuffle",
            active = isShuffleActive,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
        AppleMusicQueuePill(
            icon = repeatIcon,
            label = when (playbackMode) {
                PlaybackMode.REPEAT_ONE -> "Repeat 1"
                PlaybackMode.REPEAT_ALL -> "Repeat"
                else -> "Repeat"
            },
            active = isRepeatActive,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = onCyclePlaybackMode,
            modifier = Modifier.weight(1f),
        )
        AppleMusicQueuePill(
            icon = Icons.Rounded.PlaylistAdd,
            label = "Save",
            active = false,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = onSaveToPlaylist,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppleMusicQueuePill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeContainer: Color,
    activeContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (active) activeContainer else AppleMusicPillInactive,
        animationSpec = tween(220),
        label = "queuePillBg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) activeContent else Color.White,
        animationSpec = tween(220),
        label = "queuePillFg",
    )
    Row(
        modifier = modifier
            .appleMusicPressInflate(pressedScale = 1.08f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
