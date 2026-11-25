package com.auramusic.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.contrastInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AnalyticsRange(val label: String, val days: Int?) {
    DAYS_7("7D", 7),
    DAYS_30("30D", 30),
    DAYS_90("90D", 90),
    ALL_TIME("All Time", null)
}

data class AnalyticsSnapshot(
    val range: AnalyticsRange,
    val totalMinutes: Int,
    val totalPlays: Int,
    val uniqueArtists: Int,
    val peakHourLabel: String,
    val hourlyCounts: List<Int>,
    val topTracks: List<Pair<AuraTrack, Int>>,
    val topArtists: List<Pair<String, Int>>,
    val topGenres: List<Pair<String, Int>>
)

private val WrappedSlideEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Full-screen Listening Stats, 24-Hour Listening Clock, Thermal Receipt Card, and Aura Wrapped story flow.
 */
@Composable
fun AnalyticsScreen(
    snapshot: AnalyticsSnapshot,
    selectedRange: AnalyticsRange,
    onSelectRange: (AnalyticsRange) -> Unit,
    onPlayTrack: (AuraTrack) -> Unit,
    onBack: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showWrapped by remember { mutableStateOf(false) }
    var isSharingReceipt by remember { mutableStateOf(false) }

    BackHandler(enabled = !showWrapped) { onBack() }

    // Ensure accent text remains readable on light canvas when user selects a bright custom swatch (e.g. Neon Lime)
    val readableAccentText = if (!auraColors.isDark && auraColors.accent.luminance() > 0.38f) {
        auraColors.textPrimary
    } else {
        auraColors.accent
    }

    val shareReceipt: () -> Unit = {
        if (!isSharingReceipt) {
            isSharingReceipt = true
            coroutineScope.launch {
                val result = shareListeningReceiptPng(context, snapshot)
                isSharingReceipt = false
                result.onFailure { err ->
                    Toast.makeText(
                        context,
                        "Unable to share receipt: ${err.message ?: "unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(auraColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Editorial Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(auraColors.circleButtonBg)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Stats",
                            color = auraColors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        )
                        Text(
                            text = "On-device listening replay",
                            color = auraColors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(auraColors.accent)
                        .clickable { showWrapped = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = auraColors.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wrapped",
                        color = auraColors.onAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Borderless Horizontal Range Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                items(AnalyticsRange.entries) { range ->
                    val isSelected = range == selectedRange
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) auraColors.accent else auraColors.pillInactiveBg
                            )
                            .clickable { onSelectRange(range) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.label,
                            color = if (isSelected) auraColors.onAccent else auraColors.pillInactiveText,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp)
            ) {
                // 1. Open Editorial Hero Stat Strip (Zero boxed cards)
                item {
                    EditorialHeroStatStrip(
                        snapshot = snapshot,
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary,
                        textMuted = auraColors.textMuted,
                        dividerColor = auraColors.divider
                    )
                }

                // 2. Borderless Aura Wrapped Banner
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    WrappedEditorialBanner(
                        rangeLabel = selectedRange.label,
                        accentColor = auraColors.accent,
                        onAccentColor = auraColors.onAccent,
                        onOpenWrapped = { showWrapped = true }
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                }

                // 3. Borderless 24-Hour Listening Clock directly on canvas
                item {
                    ListeningClockSection(
                        hourlyCounts = snapshot.hourlyCounts,
                        peakHourLabel = snapshot.peakHourLabel,
                        accentColor = auraColors.accent,
                        readableAccentText = readableAccentText,
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary,
                        textMuted = auraColors.textMuted,
                        dividerColor = auraColors.divider
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = auraColors.divider)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 4. Borderless Top Tracks
                item {
                    SectionHeader(
                        title = "Top Tracks",
                        subtitle = if (snapshot.topTracks.isEmpty()) {
                            "Play songs to build your chart"
                        } else {
                            "Tap any track to play"
                        },
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (snapshot.topTracks.isEmpty()) {
                        EmptyInsightNote(
                            text = "No tracks recorded in this range yet.",
                            color = auraColors.textMuted
                        )
                    } else {
                        val tracks = snapshot.topTracks.take(10)
                        tracks.forEachIndexed { index, (track, plays) ->
                            EditorialTrackRow(
                                rank = index + 1,
                                track = track,
                                plays = plays,
                                accentText = readableAccentText,
                                placeholderColor = auraColors.surfaceCard,
                                textPrimary = auraColors.textPrimary,
                                textSecondary = auraColors.textSecondary,
                                textMuted = auraColors.textMuted,
                                onClick = { onPlayTrack(track) }
                            )
                            if (index < tracks.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 88.dp),
                                    thickness = 0.5.dp,
                                    color = auraColors.divider
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = auraColors.divider)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 5. Borderless Top Artists
                item {
                    val maxArtistPlays = snapshot.topArtists.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    SectionHeader(
                        title = "Top Artists",
                        subtitle = if (snapshot.topArtists.isEmpty()) {
                            "Your most played artists"
                        } else {
                            "${snapshot.topArtists.size} featured"
                        },
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (snapshot.topArtists.isEmpty()) {
                        EmptyInsightNote(
                            text = "No artist plays recorded yet.",
                            color = auraColors.textMuted
                        )
                    } else {
                        val artists = snapshot.topArtists.take(8)
                        artists.forEachIndexed { index, (artist, plays) ->
                            EditorialRankedBarRow(
                                rank = index + 1,
                                label = artist,
                                countLabel = "$plays ${if (plays == 1) "play" else "plays"}",
                                fraction = (plays.toFloat() / maxArtistPlays).coerceIn(0.06f, 1f),
                                barColor = auraColors.accent,
                                trackBg = auraColors.textMuted.copy(alpha = 0.16f),
                                rankColor = if (index < 3) readableAccentText else auraColors.textMuted,
                                textPrimary = auraColors.textPrimary,
                                textMuted = auraColors.textMuted
                            )
                            if (index < artists.lastIndex) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = auraColors.divider)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 6. Borderless Top Genres
                item {
                    val maxGenrePlays = snapshot.topGenres.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    SectionHeader(
                        title = "Top Genres",
                        subtitle = "Sound signature breakdown",
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (snapshot.topGenres.isEmpty()) {
                        EmptyInsightNote(
                            text = "No genre data recorded yet.",
                            color = auraColors.textMuted
                        )
                    } else {
                        val genres = snapshot.topGenres.take(6)
                        genres.forEachIndexed { index, (genre, plays) ->
                            EditorialRankedBarRow(
                                rank = index + 1,
                                label = genre,
                                countLabel = "$plays ${if (plays == 1) "play" else "plays"}",
                                fraction = (plays.toFloat() / maxGenrePlays).coerceIn(0.06f, 1f),
                                barColor = auraColors.accent.copy(alpha = 0.85f),
                                trackBg = auraColors.textMuted.copy(alpha = 0.16f),
                                rankColor = if (index < 3) readableAccentText else auraColors.textMuted,
                                textPrimary = auraColors.textPrimary,
                                textMuted = auraColors.textMuted
                            )
                            if (index < genres.lastIndex) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 7. Thermal Listening Receipt (Zero border strokes, primary Share button uses accent + onAccent)
                item {
                    SectionHeader(
                        title = "Listening Receipt",
                        subtitle = "Shareable thermal summary",
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ThermalReceiptCard(
                        snapshot = snapshot,
                        accentColor = auraColors.accent,
                        onAccentColor = auraColors.onAccent,
                        isSharing = isSharingReceipt,
                        onShareReceipt = shareReceipt
                    )
                }
            }
        }

        // Aura Wrapped Full-Bleed Story Overlay with Emil Kowalski Horizontal Slide Transition
        AnimatedVisibility(
            visible = showWrapped,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 270, easing = WrappedSlideEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 270, easing = WrappedSlideEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180)),
            modifier = Modifier.fillMaxSize()
        ) {
            AuraWrappedOverlay(
                snapshot = snapshot,
                accentColor = auraColors.accent,
                onAccentColor = auraColors.onAccent,
                restoreDarkSystemBars = auraColors.isDark,
                onPlayTrack = onPlayTrack,
                onShareReceipt = shareReceipt,
                onDismiss = { showWrapped = false }
            )
        }
    }
}

@Composable
private fun EditorialHeroStatStrip(
    snapshot: AnalyticsSnapshot,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    dividerColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MINUTES",
                    color = textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatIntStat(snapshot.totalMinutes),
                    color = textPrimary,
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.0).sp,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(0.5.dp)
                    .height(48.dp)
                    .background(dividerColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PLAYS",
                    color = textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatIntStat(snapshot.totalPlays),
                    color = textPrimary,
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.0).sp,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${formatIntStat(snapshot.uniqueArtists)} unique artists",
                    color = textPrimary,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Peak ${snapshot.peakHourLabel.ifBlank { "—" }}",
                    color = textPrimary,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
    }
}

@Composable
private fun WrappedEditorialBanner(
    rangeLabel: String,
    accentColor: Color,
    onAccentColor: Color,
    onOpenWrapped: () -> Unit
) {
    val bannerTop = Color(0xFF231842)
    val bannerBottom = Color(0xFF151926)
    val bannerInk = bannerTop.contrastInk()
    val bannerAccent = if (accentColor.luminance() > 0.35f) accentColor else AuraNeonLime

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = listOf(bannerTop, bannerBottom)))
            .clickable { onOpenWrapped() }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AURA WRAPPED • ${rangeLabel.uppercase(Locale.US)}",
                color = bannerAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your Listening Story",
                color = bannerInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Chronotype, #1 artist & top 5 anthems",
                color = bannerInk.copy(alpha = 0.72f),
                fontSize = 12.5.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(accentColor)
                .clickable { onOpenWrapped() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = onAccentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Open",
                color = onAccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ListeningClockSection(
    hourlyCounts: List<Int>,
    peakHourLabel: String,
    accentColor: Color,
    readableAccentText: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    dividerColor: Color
) {
    val normalized = remember(hourlyCounts) {
        List(24) { hour -> hourlyCounts.getOrElse(hour) { 0 }.coerceAtLeast(0) }
    }
    val hasActivity = normalized.any { it > 0 }
    val maxCount = normalized.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peakHourIndex = if (hasActivity) {
        normalized.indices.maxByOrNull { normalized[it] } ?: -1
    } else {
        -1
    }
    val mutedBarColor = textMuted.copy(alpha = 0.25f)
    val activeBarColor = accentColor.copy(alpha = 0.45f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "24-Hour Listening Clock",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Peak window: ${peakHourLabel.ifBlank { "No data yet" }}",
                    color = textSecondary,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
            Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = null,
                tint = readableAccentText,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            val barCount = 24
            val gapPx = 4.dp.toPx()
            val barWidth = ((size.width - gapPx * (barCount - 1)) / barCount).coerceAtLeast(2f)
            val maxBarHeight = size.height - 6.dp.toPx()

            drawLine(
                color = dividerColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 0.5.dp.toPx()
            )

            for (hour in 0 until barCount) {
                val count = normalized[hour]
                val ratio = count.toFloat() / maxCount.toFloat()
                val barHeight = if (count == 0) {
                    4.dp.toPx()
                } else {
                    (ratio * maxBarHeight).coerceAtLeast(8.dp.toPx())
                }
                val x = hour * (barWidth + gapPx)
                val y = size.height - barHeight
                val barColor = when {
                    count == 0 -> mutedBarColor
                    hour == peakHourIndex -> accentColor
                    else -> activeBarColor
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12a", "6a", "12p", "6p", "11p").forEach { marker ->
                Text(
                    text = marker,
                    color = textMuted,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = textSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EditorialTrackRow(
    rank: Int,
    track: AuraTrack,
    plays: Int,
    accentText: Color,
    placeholderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            color = if (rank <= 3) accentText else textMuted,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = track.highResArtworkUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$plays ${if (plays == 1) "play" else "plays"}",
            color = textMuted,
            style = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

@Composable
private fun EditorialRankedBarRow(
    rank: Int,
    label: String,
    countLabel: String,
    fraction: Float,
    barColor: Color,
    trackBg: Color,
    rankColor: Color,
    textPrimary: Color,
    textMuted: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$rank",
                    color = rankColor,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.width(26.dp)
                )
                Text(
                    text = label,
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = countLabel,
                color = textMuted,
                style = TextStyle(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                )
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(trackBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun EmptyInsightNote(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ThermalReceiptCard(
    snapshot: AnalyticsSnapshot,
    accentColor: Color,
    onAccentColor: Color,
    isSharing: Boolean,
    onShareReceipt: () -> Unit
) {
    val paperBg = Color(0xFFF9F8F4)
    val inkDark = Color(0xFF111216)
    val inkMuted = Color(0xFF5A5D66)
    val dateStamp = remember {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date())
    }
    val receiptRows = remember(snapshot.topTracks) { snapshot.topTracks.take(10) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(paperBg)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AURA RECEIPTIFY",
                    color = inkDark,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${snapshot.range.label.uppercase(Locale.US)} • $dateStamp",
                    color = inkMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = inkDark,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        DashedReceiptDivider(color = inkDark.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "QTY  ITEM",
                color = inkMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "PLAYS",
                color = inkMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (receiptRows.isEmpty()) {
            Text(
                text = "00   NO LISTENING ITEMS RECORDED YET",
                color = inkMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            receiptRows.forEachIndexed { idx, (track, plays) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%02d   %s - %s",
                            idx + 1,
                            track.title.uppercase(Locale.US),
                            track.artist.uppercase(Locale.US)
                        ),
                        color = inkDark,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${plays}x",
                        color = inkDark,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        DashedReceiptDivider(color = inkDark.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ITEM COUNT: ${snapshot.totalPlays}",
                color = inkDark,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = "TOTAL: ${snapshot.totalMinutes} MIN",
                color = inkDark,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subtle thermal barcode strip
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            val pattern = intArrayOf(4, 2, 7, 3, 3, 6, 2, 5, 9, 3, 4, 2, 7, 3, 5, 2, 8, 4)
            var bx = 0f
            var i = 0
            while (bx < size.width) {
                val w = (pattern[i % pattern.size] * 1.4f).dp.toPx()
                if (i % 2 == 0) {
                    drawRect(
                        color = inkDark.copy(alpha = 0.85f),
                        topLeft = Offset(bx, 0f),
                        size = Size(w.coerceAtMost(size.width - bx), size.height)
                    )
                }
                bx += w + 3.dp.toPx()
                i++
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onShareReceipt,
            enabled = !isSharing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = onAccentColor,
                disabledContainerColor = accentColor.copy(alpha = 0.6f),
                disabledContentColor = onAccentColor.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            if (isSharing) {
                CircularProgressIndicator(
                    color = onAccentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rendering PNG…",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share Receipt PNG",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DashedReceiptDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}

@Composable
private fun AuraWrappedOverlay(
    snapshot: AnalyticsSnapshot,
    accentColor: Color,
    onAccentColor: Color,
    restoreDarkSystemBars: Boolean,
    onPlayTrack: (AuraTrack) -> Unit,
    onShareReceipt: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val view = LocalView.current
    DisposableEffect(view, restoreDarkSystemBars) {
        val window = resolveActivityWindow(view)
        window?.let { setLightSystemBarIcons(it, isDarkCanvas = true) }
        onDispose {
            window?.let { setLightSystemBarIcons(it, isDarkCanvas = restoreDarkSystemBars) }
        }
    }

    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    val slideGradients = remember {
        listOf(
            listOf(Color(0xFF1F143D), Color(0xFF0E1018)),
            listOf(Color(0xFF0F2A33), Color(0xFF0B0E16)),
            listOf(Color(0xFF34142E), Color(0xFF0E1018)),
            listOf(Color(0xFF14281B), Color(0xFF0B0F14)),
            listOf(Color(0xFF211A3D), Color(0xFF0C0E15))
        )
    }

    val currentGradient = slideGradients[pagerState.currentPage.coerceIn(0, slideGradients.lastIndex)]
    val chromeInk = currentGradient.first().contrastInk()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(currentGradient))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean Story Progress Bars + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val active = index <= pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) accentColor else chromeInk.copy(alpha = 0.22f)
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(chromeInk.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Aura Wrapped",
                        tint = chromeInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val gradient = slideGradients[page.coerceIn(0, slideGradients.lastIndex)]
                when (page) {
                    0 -> WrappedSummarySlide(
                        snapshot = snapshot,
                        gradient = gradient,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor
                    )
                    1 -> WrappedChronotypeSlide(
                        snapshot = snapshot,
                        gradient = gradient,
                        accentColor = accentColor
                    )
                    2 -> WrappedTopArtistSlide(
                        snapshot = snapshot,
                        gradient = gradient,
                        accentColor = accentColor
                    )
                    3 -> WrappedTopTracksSlide(
                        snapshot = snapshot,
                        gradient = gradient,
                        accentColor = accentColor,
                        onPlayTrack = { track ->
                            onPlayTrack(track)
                            onDismiss()
                        }
                    )
                    else -> WrappedShareSlide(
                        snapshot = snapshot,
                        gradient = gradient,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        onShareReceipt = onShareReceipt,
                        onDone = onDismiss
                    )
                }
            }

            // Bottom Story Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(chromeInk.copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Previous slide",
                        tint = if (pagerState.currentPage > 0) chromeInk else chromeInk.copy(alpha = 0.30f)
                    )
                }

                Text(
                    text = "${pagerState.currentPage + 1} / $pageCount",
                    color = chromeInk.copy(alpha = 0.72f),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )

                IconButton(
                    onClick = {
                        if (pagerState.currentPage < pageCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = if (pagerState.currentPage < pageCount - 1) "Next slide" else "Finish",
                        tint = onAccentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedSlideLayout(
    tag: String,
    gradient: List<Color>,
    accentColor: Color,
    content: @Composable (ink: Color, accentOnSlide: Color) -> Unit
) {
    val ink = gradient.first().contrastInk()
    val accentOnSlide = if (accentColor.luminance() > 0.35f) accentColor else AuraNeonLime

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = tag,
            color = accentOnSlide,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )

        content(ink, accentOnSlide)

        Text(
            text = "AURA MUSIC WRAPPED",
            color = ink.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun WrappedSummarySlide(
    snapshot: AnalyticsSnapshot,
    gradient: List<Color>,
    accentColor: Color,
    onAccentColor: Color
) {
    WrappedSlideLayout(
        tag = "YOUR ${snapshot.range.label.uppercase(Locale.US)} IN SOUND",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, _ ->
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "${formatIntStat(snapshot.totalMinutes)} minutes",
                color = ink,
                style = TextStyle(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 46.sp,
                    letterSpacing = (-1.0).sp,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = "spent locked into your favorite tracks across ${formatIntStat(snapshot.totalPlays)} plays and ${formatIntStat(snapshot.uniqueArtists)} artists.",
                color = ink.copy(alpha = 0.80f),
                style = TextStyle(
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontFeatureSettings = "tnum"
                )
            )
            val topGenre = snapshot.topGenres.firstOrNull()?.first
            if (!topGenre.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Top Genre: $topGenre",
                        color = onAccentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedChronotypeSlide(
    snapshot: AnalyticsSnapshot,
    gradient: List<Color>,
    accentColor: Color
) {
    val peakHour = if (snapshot.hourlyCounts.any { it > 0 }) {
        snapshot.hourlyCounts.indices.maxByOrNull { snapshot.hourlyCounts[it] } ?: 21
    } else {
        21
    }
    val (chronotypeTitle, chronotypeDesc) = when (peakHour) {
        in 5..10 -> "The Dawn Listener" to "You start the day early with crisp morning soundtracks before the world wakes up."
        in 11..16 -> "The Sunlit Curator" to "Afternoon momentum fuels your deepest playlists and discovery sessions."
        in 17..20 -> "The Golden Hour Groover" to "Sunset transitions and evening commutes are when your speakers shine brightest."
        else -> "The Midnight Audiophile" to "Late-night quiet lets every bassline and vocal nuance hit harder."
    }

    WrappedSlideLayout(
        tag = "LISTENING CHRONOTYPE",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, accentOnSlide ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = accentOnSlide,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = chronotypeTitle,
                color = ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 38.sp,
                letterSpacing = (-0.6).sp
            )
            Text(
                text = "Peak hour: ${snapshot.peakHourLabel.ifBlank { "Evening" }}",
                color = accentOnSlide,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = chronotypeDesc,
                color = ink.copy(alpha = 0.80f),
                fontSize = 16.sp,
                lineHeight = 23.sp
            )
        }
    }
}

@Composable
private fun WrappedTopArtistSlide(
    snapshot: AnalyticsSnapshot,
    gradient: List<Color>,
    accentColor: Color
) {
    val topArtist = snapshot.topArtists.firstOrNull()
    WrappedSlideLayout(
        tag = "#1 ARTIST",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, accentOnSlide ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = null,
                tint = accentOnSlide,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = topArtist?.first ?: "Waiting for your #1",
                color = ink,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 40.sp,
                letterSpacing = (-0.6).sp
            )
            Text(
                text = if (topArtist != null) {
                    "${topArtist.second} plays in ${snapshot.range.label} — an undisputed staple in your rotation."
                } else {
                    "Keep playing music to crown your top artist."
                },
                color = ink.copy(alpha = 0.82f),
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

@Composable
private fun WrappedTopTracksSlide(
    snapshot: AnalyticsSnapshot,
    gradient: List<Color>,
    accentColor: Color,
    onPlayTrack: (AuraTrack) -> Unit
) {
    val topFive = snapshot.topTracks.take(5)
    WrappedSlideLayout(
        tag = "TOP 5 TRACKS",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, accentOnSlide ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Heavy Rotation",
                color = ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (topFive.isEmpty()) {
                Text(
                    text = "Play a few tracks to unlock your top 5 countdown.",
                    color = ink.copy(alpha = 0.72f),
                    fontSize = 15.sp
                )
            } else {
                topFive.forEachIndexed { idx, (track, plays) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(track) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = accentOnSlide,
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFeatureSettings = "tnum"
                            ),
                            modifier = Modifier.width(26.dp)
                        )
                        AsyncImage(
                            model = track.highResArtworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ink.copy(alpha = 0.12f))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • ${plays}x",
                                color = ink.copy(alpha = 0.70f),
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (idx < topFive.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp),
                            thickness = 0.5.dp,
                            color = ink.copy(alpha = 0.14f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedShareSlide(
    snapshot: AnalyticsSnapshot,
    gradient: List<Color>,
    accentColor: Color,
    onAccentColor: Color,
    onShareReceipt: () -> Unit,
    onDone: () -> Unit
) {
    WrappedSlideLayout(
        tag = "SHARE YOUR STATS",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, accentOnSlide ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Take your sound receipt anywhere",
                color = ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "${formatIntStat(snapshot.totalMinutes)} min • ${formatIntStat(snapshot.totalPlays)} plays • Peak ${snapshot.peakHourLabel.ifBlank { "—" }}",
                color = accentOnSlide,
                style = TextStyle(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onShareReceipt,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Receipt PNG", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ink.copy(alpha = 0.12f),
                    contentColor = ink
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
            }
        }
    }
}

private fun resolveActivityWindow(view: View): Window? {
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) return context.window
        context = context.baseContext
    }
    return null
}

private fun setLightSystemBarIcons(window: Window, isDarkCanvas: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !isDarkCanvas
        isAppearanceLightNavigationBars = !isDarkCanvas
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
}

private suspend fun shareListeningReceiptPng(
    context: Context,
    snapshot: AnalyticsSnapshot
): Result<Unit> = runCatching {
    val pngFile = withContext(Dispatchers.IO) {
        val bitmap = renderReceiptBitmap(snapshot)
        try {
            val preferredDir = File(context.cacheDir, "shared_receipts").apply { mkdirs() }
            val preferredFile = File(preferredDir, "aura_listening_receipt.png")
            FileOutputStream(preferredFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", preferredFile)
            }.getOrNull()
            if (uri != null) {
                preferredFile
            } else {
                val fallbackDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val fallbackFile = File(fallbackDir, "aura_listening_receipt.png")
                FileOutputStream(fallbackFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                fallbackFile
            }
        } finally {
            bitmap.recycle()
        }
    }

    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pngFile
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(
            Intent.EXTRA_TEXT,
            "My Aura Music ${snapshot.range.label} Listening Receipt (${snapshot.totalMinutes} min)"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Share Aura Receipt").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun renderReceiptBitmap(snapshot: AnalyticsSnapshot): Bitmap {
    val width = 1080
    val rows = snapshot.topTracks.take(10)
    val height = (980 + rows.size * 76).coerceAtLeast(1350)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    // Warm thermal paper background
    canvas.drawColor(AndroidColor.parseColor("#F9F8F4"))

    val ink = AndroidColor.parseColor("#111216")
    val muted = AndroidColor.parseColor("#5A5D66")

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 52f
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = 28f
    }
    val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = 30f
    }
    val rowBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 30f
        textAlign = Paint.Align.RIGHT
    }
    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#B8B6AE")
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    val left = 84f
    val right = width - 84f
    var y = 130f

    canvas.drawText("AURA RECEIPTIFY", left, y, titlePaint)
    y += 50f
    val stamp = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.US).format(Date())
    canvas.drawText("${snapshot.range.label.uppercase(Locale.US)}  •  $stamp", left, y, subPaint)
    y += 42f
    canvas.drawLine(left, y, right, y, rulePaint)
    y += 56f

    canvas.drawText("QTY   TRACK / ARTIST", left, y, subPaint)
    canvas.drawText("PLAYS", right, y, rowBoldPaint)
    y += 46f

    if (rows.isEmpty()) {
        canvas.drawText("00    NO TRACKS PLAYED YET", left, y, subPaint)
        y += 64f
    } else {
        rows.forEachIndexed { index, (track, plays) ->
            val rawLabel = String.format(
                Locale.US,
                "%02d    %s - %s",
                index + 1,
                track.title.uppercase(Locale.US),
                track.artist.uppercase(Locale.US)
            )
            val truncated = ellipsizeText(rawLabel, rowPaint, right - left - 120f)
            canvas.drawText(truncated, left, y, rowPaint)
            canvas.drawText("${plays}x", right, y, rowBoldPaint)
            y += 72f
        }
    }

    y += 16f
    canvas.drawLine(left, y, right, y, rulePaint)
    y += 56f

    canvas.drawText("TOTAL PLAYS: ${snapshot.totalPlays}", left, y, rowPaint)
    canvas.drawText("${snapshot.totalMinutes} MIN", right, y, rowBoldPaint)
    y += 46f
    canvas.drawText("PEAK HOUR: ${snapshot.peakHourLabel.uppercase(Locale.US)}", left, y, subPaint)
    y += 74f

    // Decorative thermal barcode footer
    val barPaint = Paint().apply { color = ink }
    var bx = left
    val barTop = y
    val barBottom = y + 90f
    val pattern = intArrayOf(6, 3, 10, 4, 4, 8, 3, 6, 12, 4, 5, 3, 9, 4, 6, 3, 10, 5)
    var i = 0
    while (bx < right - 16f) {
        val w = pattern[i % pattern.size].toFloat() * 2.2f
        if (i % 2 == 0) {
            canvas.drawRect(bx, barTop, (bx + w).coerceAtMost(right), barBottom, barPaint)
        }
        bx += w + 6f
        i++
    }
    y = barBottom + 56f
    canvas.drawText("THANK YOU FOR LISTENING ON AURA", left, y, subPaint)

    return bitmap
}

private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 3 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
        end--
    }
    return text.substring(0, end.coerceAtLeast(1)) + "…"
}

private fun formatIntStat(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value.coerceAtLeast(0))
