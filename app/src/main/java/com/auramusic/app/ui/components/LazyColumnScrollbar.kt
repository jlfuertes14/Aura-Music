package com.auramusic.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auramusic.app.ui.theme.AuraNeonLime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fast-scroll draggable vertical scrollbar for LazyColumn.
 * Features:
 * - Fluid touch & drag-to-scroll targeting across 100+ to 1,000+ items.
 * - Smooth expansion and AuraNeonLime highlight on drag.
 * - Auto-fade when idle to stay clean and distraction-free.
 * - Generous 28.dp touch target on screen edge for effortless thumb grab.
 */
@Composable
fun LazyColumnScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White.copy(alpha = 0.50f),
    activeThumbColor: Color = AuraNeonLime,
    thumbWidth: Dp = 4.dp,
    thumbWidthDragging: Dp = 7.dp,
    minThumbHeight: Dp = 44.dp,
    padding: PaddingValues = PaddingValues(end = 3.dp, top = 16.dp, bottom = 120.dp),
    onlyWhenTrackScrolling: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isUserScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            isUserScrolling = true
        } else {
            delay(1200)
            isUserScrolling = false
        }
    }

    val isTrackScrolling = if (onlyWhenTrackScrolling) state.firstVisibleItemIndex >= 1 else true
    val isActive = (isDragging || isUserScrolling) && isTrackScrolling
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbarAlpha"
    )
    val width by animateDpAsState(
        targetValue = if (isDragging) thumbWidthDragging else thumbWidth,
        animationSpec = tween(durationMillis = 150),
        label = "scrollbarWidth"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(padding)
    ) {
        val totalHeightPx = constraints.maxHeight.toFloat()
        val totalItems = state.layoutInfo.totalItemsCount
        val visibleItems = state.layoutInfo.visibleItemsInfo.size

        if (totalItems > 12 && totalItems > visibleItems && totalHeightPx > 0f) {
            val visibleRatio = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.06f, 0.70f)
            val minThumbPx = with(LocalDensity.current) { minThumbHeight.toPx() }
            val thumbHeightPx = (totalHeightPx * visibleRatio).coerceAtLeast(minThumbPx).coerceAtMost(totalHeightPx * 0.75f)
            val trackHeightPx = (totalHeightPx - thumbHeightPx).coerceAtLeast(1f)

            val firstVisibleIndex = state.firstVisibleItemIndex
            val firstVisibleOffset = state.firstVisibleItemScrollOffset
            val firstItemSize = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f
            val maxScrollableItems = (totalItems - visibleItems).coerceAtLeast(1)
            val itemProgress = ((firstVisibleIndex.toFloat() + (firstVisibleOffset.toFloat() / firstItemSize.coerceAtLeast(1f))) / maxScrollableItems.toFloat()).coerceIn(0f, 1f)

            val scrollOffsetPx = (itemProgress * trackHeightPx).coerceIn(0f, trackHeightPx)

            if (alpha > 0.01f) {
                // Draggable touch area
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(28.dp)
                        .fillMaxHeight()
                        .pointerInput(totalItems, trackHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val clampedY = (offset.y - (thumbHeightPx / 2f)).coerceIn(0f, trackHeightPx)
                                    val ratio = clampedY / trackHeightPx
                                    val targetIndex = (ratio * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch {
                                        state.scrollToItem(targetIndex)
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val clampedY = (change.position.y - (thumbHeightPx / 2f)).coerceIn(0f, trackHeightPx)
                                    val ratio = clampedY / trackHeightPx
                                    val targetIndex = (ratio * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch {
                                        state.scrollToItem(targetIndex)
                                    }
                                }
                            )
                        }
                ) {
                    // Scrollbar thumb indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                translationY = scrollOffsetPx
                                this.alpha = alpha
                            }
                            .size(
                                width = width,
                                height = with(LocalDensity.current) { thumbHeightPx.toDp() }
                            )
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (isDragging) activeThumbColor else thumbColor)
                    )
                }
            }
        }
    }
}
