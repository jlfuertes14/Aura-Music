package com.auramusic.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Shimmer modifier providing a sleek dark-obsidian shimmer pulse across UI elements.
 */
fun Modifier.auraShimmer(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "AuraShimmerTransition")
    val widthPx = size.width.toFloat().coerceAtLeast(400f)
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * widthPx,
        targetValue = 2 * widthPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuraShimmerX"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF161820),
                Color(0xFF262936),
                Color(0xFF161820)
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + widthPx, size.height.toFloat().coerceAtLeast(100f))
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

/**
 * Standard tracklist row item skeleton
 */
@Composable
fun TrackItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .auraShimmer()
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .auraShimmer()
            )
            Spacer(modifier = Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .auraShimmer()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .auraShimmer()
        )
    }
}

/**
 * Horizontal Top Artists carousel skeleton
 */
@Composable
fun ArtistCarouselSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(88.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .auraShimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(62.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .auraShimmer()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .auraShimmer()
                )
            }
        }
    }
}

/**
 * Horizontal Community / Chart Playlists carousel skeleton
 */
@Composable
fun PlaylistCarouselSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(3) {
            Column(modifier = Modifier.width(130.dp)) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .auraShimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .auraShimmer()
                )
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.50f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .auraShimmer()
                )
            }
        }
    }
}

/**
 * Horizontal Albums & Discography carousel skeleton
 */
@Composable
fun AlbumCarouselSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(3) {
            Column(modifier = Modifier.width(130.dp)) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .auraShimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .auraShimmer()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.40f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .auraShimmer()
                )
            }
        }
    }
}

/**
 * Skeleton for Home Charts section (Video charts / genres carousel + Top artists list)
 */
@Composable
fun ChartSectionSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Shelf 1: Video charts / playlists carousel
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .width(130.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .auraShimmer()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) {
                Column(modifier = Modifier.width(155.dp)) {
                    Box(
                        modifier = Modifier
                            .size(155.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .auraShimmer()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .auraShimmer()
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .auraShimmer()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shelf 2: Top artists header + 3 rows
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .width(110.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .auraShimmer()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .auraShimmer()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .auraShimmer()
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .auraShimmer()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .auraShimmer()
                        )
                    }
                }
            }
        }
    }
}


