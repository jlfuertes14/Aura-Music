package com.auramusic.app.ui.screens.player.spotify

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.components.AudioPlayingIndicator
import com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Upcoming Queue Sheet matching Spotify flat minimalist UX:
 * - Real Mobile Bottom Sheet Modal anchored to the bottom with full dimming scrim
 * - Flat transparent list items with zero borders
 * - Drag to reorder via holding ANY tile OR dragging the handle icon
 * - Damped spring reorder animation with tactile haptic feedback
 * - Auto-scroll when dragging near viewport boundaries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotifyQueueSheet(
    currentTrack: AuraTrack,
    queue: List<AuraTrack>,
    isPlaying: Boolean = false,
    modalContainerColor: Color,
    onDismiss: () -> Unit,
    onPlayTrack: (AuraTrack) -> Unit,
    onReorderQueue: (List<AuraTrack>) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = modalContainerColor,
        contentColor = TextPrimary,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Spacer(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        ForceWhiteSystemBarsEffect()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
        ) {
            QueueSheetContent(
                currentTrack = currentTrack,
                queue = queue,
                isPlaying = isPlaying,
                modalContainerColor = modalContainerColor,
                onPlayTrack = {
                    onPlayTrack(it)
                    onDismiss()
                },
                onReorderQueue = onReorderQueue
            )
        }
    }
}

@Composable
internal fun QueueSheetContent(
    currentTrack: AuraTrack,
    queue: List<AuraTrack>,
    isPlaying: Boolean = false,
    modalContainerColor: Color,
    onPlayTrack: (AuraTrack) -> Unit,
    onReorderQueue: (List<AuraTrack>) -> Unit
) {
    val curIdx = queue.indexOfFirst { it.videoId == currentTrack.videoId }
    val upcomingTracks = remember(queue, currentTrack.videoId) {
        if (curIdx >= 0 && curIdx < queue.lastIndex) {
            queue.subList(curIdx + 1, queue.size)
        } else {
            queue.filterNot { it.videoId == currentTrack.videoId }
        }
    }

    var upcomingList by remember(upcomingTracks) { mutableStateOf(upcomingTracks) }

    // Decoupled UI & Data: List data remains frozen during drag so LazyColumn never re-layouts or scrolls unexpectedly
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetDropIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val tileHeightPx = with(density) { 56.dp.toPx() }

    fun startDrag(itemId: String) {
        val currentIdx = upcomingList.indexOfFirst { (it.videoId.ifBlank { it.id }) == itemId }
        if (currentIdx < 0) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        draggedIndex = currentIdx
        targetDropIndex = currentIdx
        dragOffsetY = 0f
    }

    fun handleDragDelta(deltaY: Float) {
        val current = draggedIndex ?: return
        dragOffsetY += deltaY

        val deltaSlots = (dragOffsetY / tileHeightPx).roundToInt()
        val newTarget = (current + deltaSlots).coerceIn(0, upcomingList.lastIndex)
        if (newTarget != targetDropIndex) {
            targetDropIndex = newTarget
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        // Auto-scroll when dragged near top or bottom of viewport
        val layoutInfo = lazyListState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        val itemInfo = visible.firstOrNull { it.index == current }
        if (itemInfo != null) {
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val topInViewport = itemInfo.offset + dragOffsetY
            val bottomInViewport = topInViewport + itemInfo.size
            if (topInViewport < 48f) {
                coroutineScope.launch {
                    lazyListState.scrollBy(-18f)
                }
            } else if (bottomInViewport > viewportHeight - 48f) {
                coroutineScope.launch {
                    lazyListState.scrollBy(18f)
                }
            }
        }
    }

    fun commitDrop() {
        val current = draggedIndex
        val target = targetDropIndex
        if (current != null && target != null && current != target &&
            current in upcomingList.indices && target in upcomingList.indices
        ) {
            val mutable = upcomingList.toMutableList()
            val moved = mutable.removeAt(current)
            mutable.add(target, moved)
            upcomingList = mutable
            onReorderQueue(mutable)
        }
        draggedIndex = null
        targetDropIndex = null
        dragOffsetY = 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AuraNeonLime,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Flat "Now Playing" Tile (Zero Border, Flat Transparent Design)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF202127)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = currentTrack.highResArtworkUrl,
                    contentDescription = currentTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AudioPlayingIndicator(
                            modifier = Modifier.fillMaxSize(0.72f),
                            color = AuraNeonLime
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.title,
                    color = AuraNeonLime,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentTrack.artist,
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AuraNeonLime.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = AuraNeonLime,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Playing",
                    color = AuraNeonLime,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            if (upcomingList.isNotEmpty()) {
                Text(
                    text = "${upcomingList.size} upcoming",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (upcomingList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "End of queue. Radio songs will load automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(
                    items = upcomingList,
                    key = { _, item -> item.videoId.ifBlank { item.id } }
                ) { index, item ->
                    val itemKey = item.videoId.ifBlank { item.id }
                    val isDraggingThis = draggedIndex == index
                    val currentDrag = draggedIndex
                    val currentTarget = targetDropIndex

                    // Physics engine spring displacement: surrounding items glide out of the way smoothly
                    val itemDisplacementY by animateFloatAsState(
                        targetValue = when {
                            currentDrag == null || currentTarget == null -> 0f
                            index == currentDrag -> 0f
                            // Dragging UP: items between currentTarget and currentDrag glide DOWN (+tileHeightPx)
                            currentDrag > currentTarget && index in currentTarget until currentDrag -> tileHeightPx
                            // Dragging DOWN: items between currentDrag and currentTarget glide UP (-tileHeightPx)
                            currentDrag < currentTarget && index in (currentDrag + 1)..currentTarget -> -tileHeightPx
                            else -> 0f
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "disp_$index"
                    )

                    val animatedElevation by animateDpAsState(
                        targetValue = if (isDraggingThis) 12.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "elev_$index"
                    )
                    val animatedScale by animateFloatAsState(
                        targetValue = if (isDraggingThis) 1.03f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "scale_$index"
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        // Dragged tile uses SOLID light gray color (not transparent)
                        color = if (isDraggingThis) Color(0xFF3A3D46) else Color.Transparent,
                        border = null,
                        shadowElevation = animatedElevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDraggingThis) 25f else 1f)
                            .graphicsLayer {
                                translationY = if (isDraggingThis) dragOffsetY else itemDisplacementY
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                            // Holding anywhere on the song tile activates drag mode
                            .pointerInput(itemKey) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startDrag(itemKey) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        handleDragDelta(dragAmount.y)
                                    },
                                    onDragEnd = { commitDrop() },
                                    onDragCancel = { commitDrop() }
                                )
                            }
                            .clickable {
                                if (draggedIndex == null) {
                                    onPlayTrack(item)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.highResArtworkUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDraggingThis) AuraNeonLime else Color.White,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.artist,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Drag Handle Icon on the right: Immediate touch & drag without waiting for long-press
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .pointerInput(itemKey) {
                                        detectDragGestures(
                                            onDragStart = { startDrag(itemKey) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                handleDragDelta(dragAmount.y)
                                            },
                                            onDragEnd = { commitDrop() },
                                            onDragCancel = { commitDrop() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDraggingThis) AuraNeonLime else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
