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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.auraGlassSurface
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
    val isGlass = LocalLiquidGlassEnabled.current
    val isDark = auraColors.isDark
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val statsListState = rememberLazyListState()

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
        // Subtle ambient luminous bloom behind Stats panels when Liquid Glass theme is active
        if (isGlass) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColors.accent.copy(alpha = if (isDark) 0.14f else 0.13f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.90f, h * 0.12f),
                        radius = w * 0.85f
                    ),
                    radius = w * 0.85f,
                    center = Offset(w * 0.90f, h * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7C3AED).copy(alpha = if (isDark) 0.14f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.12f, h * 0.44f),
                        radius = w * 0.80f
                    ),
                    radius = w * 0.80f,
                    center = Offset(w * 0.12f, h * 0.44f)
                )
            }
        }

        // 1. Stats Screen (slides out to the left when Wrapped opens, slides back in from the left on close)
        AnimatedVisibility(
            visible = !showWrapped,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 280, easing = WrappedSlideEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = WrappedSlideEasing)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 260, easing = WrappedSlideEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = WrappedSlideEasing)),
            modifier = Modifier.fillMaxSize()
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .auraGlassSurface(
                                shape = CircleShape,
                                selected = false,
                                fallbackColor = auraColors.circleButtonBg,
                                glassAlpha = 0.12f,
                                borderTopAlpha = 0.34f,
                                borderBottomAlpha = 0.08f
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Stats",
                        color = auraColors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                }

                // Horizontal Range Pills (frosted Liquid Glass capsules when glass theme is selected)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    items(AnalyticsRange.entries) { range ->
                        val isSelected = range == selectedRange
                        Box(
                            modifier = Modifier
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .clip(CircleShape)
                                            .background(auraColors.accent)
                                            .then(
                                                if (isGlass) {
                                                    Modifier.border(
                                                        width = 0.8.dp,
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.White.copy(alpha = 0.55f),
                                                                Color.Transparent
                                                            )
                                                        ),
                                                        shape = CircleShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    } else {
                                        Modifier.auraGlassSurface(
                                            shape = CircleShape,
                                            selected = false,
                                            fallbackColor = auraColors.pillInactiveBg
                                        )
                                    }
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
                    state = statsListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp)
                ) {
                    // 1. Hero Stat Strip (borderless in Default; frosted Liquid Glass card in Liquid Glass)
                    item {
                        StatsGlassSection(isGlass = isGlass, isDark = isDark) {
                            EditorialHeroStatStrip(
                                snapshot = snapshot,
                                textPrimary = auraColors.textPrimary,
                                textSecondary = auraColors.textSecondary,
                                textMuted = auraColors.textMuted,
                                dividerColor = auraColors.divider,
                                showOuterDivider = !isGlass
                            )
                        }
                    }

                    // 2. Aura Wrapped Banner
                    item {
                        Spacer(modifier = Modifier.height(if (isGlass) 14.dp else 20.dp))
                        WrappedEditorialBanner(
                            rangeLabel = selectedRange.label,
                            accentColor = auraColors.accent,
                            onAccentColor = auraColors.onAccent,
                            isGlass = isGlass,
                            isDark = isDark,
                            onOpenWrapped = { showWrapped = true }
                        )
                        Spacer(modifier = Modifier.height(if (isGlass) 16.dp else 26.dp))
                    }

                    // 3. 24-Hour Listening Clock
                    item {
                        StatsGlassSection(isGlass = isGlass, isDark = isDark) {
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
                        }
                        if (isGlass) {
                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = auraColors.divider)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // 4. Top Songs
                    item {
                        StatsGlassSection(isGlass = isGlass, isDark = isDark) {
                            SectionHeader(
                                title = "Top Songs",
                                textPrimary = auraColors.textPrimary,
                                textSecondary = auraColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (snapshot.topTracks.isEmpty()) {
                                EmptyInsightNote(
                                    text = "Play a few songs to see your stats",
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
                        }

                        if (isGlass) {
                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = auraColors.divider)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // 5. Top Artists
                    item {
                        val maxArtistPlays = snapshot.topArtists.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                        StatsGlassSection(isGlass = isGlass, isDark = isDark) {
                            SectionHeader(
                                title = "Top Artists",
                                textPrimary = auraColors.textPrimary,
                                textSecondary = auraColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (snapshot.topArtists.isEmpty()) {
                                EmptyInsightNote(
                                    text = "Play a few songs to see your stats",
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
                        }

                        if (isGlass) {
                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = auraColors.divider)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // 6. Top Genres
                    item {
                        val maxGenrePlays = snapshot.topGenres.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                        StatsGlassSection(isGlass = isGlass, isDark = isDark) {
                            SectionHeader(
                                title = "Top Genres",
                                textPrimary = auraColors.textPrimary,
                                textSecondary = auraColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (snapshot.topGenres.isEmpty()) {
                                EmptyInsightNote(
                                    text = "Play a few songs to see your stats",
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
                        }

                        Spacer(modifier = Modifier.height(if (isGlass) 20.dp else 28.dp))
                    }

                    // 7. Thermal Listening Receipt
                    item {
                        SectionHeader(
                            title = "Receipt",
                            textPrimary = auraColors.textPrimary,
                            textSecondary = auraColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ThermalReceiptCard(
                            snapshot = snapshot,
                            accentColor = auraColors.accent,
                            onAccentColor = auraColors.onAccent,
                            isGlass = isGlass,
                            isSharing = isSharingReceipt,
                            onShareReceipt = shareReceipt
                        )
                    }
                }
            }
        }

        // 2. Aura Wrapped Full-Bleed Z-Index Overlay (slides in from right -> left; slides back out to right on close)
        AnimatedVisibility(
            visible = showWrapped,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 280, easing = WrappedSlideEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = WrappedSlideEasing)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 260, easing = WrappedSlideEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = WrappedSlideEasing)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(50f)
        ) {
            AuraWrappedOverlay(
                snapshot = snapshot,
                accentColor = auraColors.accent,
                onAccentColor = auraColors.onAccent,
                isGlass = isGlass,
                restoreDarkSystemBars = auraColors.isDark,
                onPlayTrack = onPlayTrack,
                onShareReceipt = shareReceipt,
                onDismiss = { showWrapped = false }
            )
        }
    }
}

@Composable
private fun StatsGlassSection(
    isGlass: Boolean,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isGlass) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
        return
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.86f),
                            Color(0xFFF2F4F9).copy(alpha = 0.70f)
                        )
                    }
                )
            )
            .border(
                width = 0.85.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.94f),
                            Color.White.copy(alpha = 0.48f),
                            Color.Black.copy(alpha = 0.07f)
                        )
                    }
                ),
                shape = shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.12f else 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(56f, 26f),
                        radius = 340f
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content
        )
    }
}

@Composable
private fun EditorialHeroStatStrip(
    snapshot: AnalyticsSnapshot,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    dividerColor: Color,
    showOuterDivider: Boolean = true
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

        if (showOuterDivider) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        }
    }
}

@Composable
private fun WrappedEditorialBanner(
    rangeLabel: String,
    accentColor: Color,
    onAccentColor: Color,
    isGlass: Boolean,
    isDark: Boolean,
    onOpenWrapped: () -> Unit
) {
    val bannerTop = Color(0xFF231842)
    val bannerBottom = Color(0xFF151926)
    val bannerInk = bannerTop.contrastInk()
    val bannerShape = RoundedCornerShape(if (isGlass) 22.dp else 16.dp)

    val containerModifier = if (isGlass) {
        Modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(if (isDark) Color(0xFF161326).copy(alpha = 0.72f) else Color(0xFF231842).copy(alpha = 0.86f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF5B35C9).copy(alpha = if (isDark) 0.54f else 0.62f),
                        Color(0xFF241749).copy(alpha = if (isDark) 0.44f else 0.58f),
                        Color(0xFF12182B).copy(alpha = if (isDark) 0.60f else 0.68f)
                    )
                )
            )
            .border(
                width = 0.85.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.46f),
                        Color(0xFF9F7AEA).copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                shape = bannerShape
            )
            .clickable { onOpenWrapped() }
    } else {
        Modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(Brush.linearGradient(colors = listOf(bannerTop, bannerBottom)))
            .clickable { onOpenWrapped() }
    }

    Box(modifier = containerModifier) {
        if (isGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            center = Offset(52f, 24f),
                            radius = 300f
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wrapped",
                    color = bannerInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$rangeLabel summary",
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
                    text = "Listening Clock",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Peak: ${peakHourLabel.ifBlank { "—" }}",
                    color = textSecondary,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = textSecondary,
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
    textPrimary: Color,
    textSecondary: Color,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 13.sp
            )
        }
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
    isGlass: Boolean,
    isSharing: Boolean,
    onShareReceipt: () -> Unit
) {
    val paperBg = if (isGlass) Color(0xFFF9F8F4).copy(alpha = 0.92f) else Color(0xFFF9F8F4)
    val inkDark = Color(0xFF111216)
    val inkMuted = Color(0xFF5A5D66)
    val dateStamp = remember {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date())
    }
    val receiptRows = remember(snapshot.topTracks) { snapshot.topTracks.take(10) }
    val receiptShape = RoundedCornerShape(if (isGlass) 22.dp else 16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(receiptShape)
            .background(paperBg)
            .then(
                if (isGlass) {
                    Modifier.border(
                        width = 0.9.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.35f)
                            )
                        ),
                        shape = receiptShape
                    )
                } else {
                    Modifier
                }
            )
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

        val buttonInk = accentColor.contrastInk()
        Button(
            onClick = onShareReceipt,
            enabled = !isSharing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = buttonInk,
                disabledContainerColor = accentColor.copy(alpha = 0.6f),
                disabledContentColor = buttonInk.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            if (isSharing) {
                CircularProgressIndicator(
                    color = buttonInk,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rendering PNG…",
                    color = buttonInk,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    tint = buttonInk,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share Receipt",
                    color = buttonInk,
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
    isGlass: Boolean,
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
    val blockingInteraction = remember { MutableInteractionSource() }

    val slideGradients = remember {
        listOf(
            listOf(Color(0xFF190C3B), Color(0xFF0A0B14)),
            listOf(Color(0xFF082630), Color(0xFF070D14)),
            listOf(Color(0xFF3A0B2B), Color(0xFF0C0913)),
            listOf(Color(0xFF0B291B), Color(0xFF080E10)),
            listOf(Color(0xFF1F1240), Color(0xFF0A0B13))
        )
    }

    val currentPage = pagerState.currentPage.coerceIn(0, slideGradients.lastIndex)
    val currentGradient = slideGradients[currentPage]
    val chromeInk = Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Brush.verticalGradient(currentGradient))
            // Consume all taps so nothing can ever click through to Stats behind Wrapped
            .clickable(
                interactionSource = blockingInteraction,
                indication = null,
                onClick = {}
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val gradient = slideGradients[page.coerceIn(0, slideGradients.lastIndex)]
            when (page) {
                0 -> WrappedSummarySlide(
                    snapshot = snapshot,
                    gradient = gradient,
                    accentColor = accentColor,
                    onAccentColor = accentColor.contrastInk()
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
                    onAccentColor = accentColor.contrastInk(),
                    onShareReceipt = onShareReceipt,
                    onDone = onDismiss
                )
            }
        }

        // Top Story Progress Bars + Close Button (floating cleanly over full-bleed artwork)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
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
                            if (active) accentColor else chromeInk.copy(alpha = 0.28f)
                        )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (isGlass) 0.24f else 0.34f))
                    .then(
                        if (isGlass) {
                            Modifier.border(
                                width = 0.8.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.45f),
                                        Color.White.copy(alpha = 0.10f)
                                    )
                                ),
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Wrapped",
                    tint = chromeInk,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun WrappedGraphicBackdrop(
    pageIndex: Int,
    gradient: List<Color>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val vibrantAccent = if (accentColor.luminance() > 0.35f) accentColor else AuraNeonLime
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
    ) {
        val w = size.width
        val h = size.height
        when (pageIndex) {
            0 -> {
                // Summary: Bold sweeping Spotify Wrapped ribbons + radial burst + vinyl arcs
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w * 0.88f, h * 0.18f),
                        radius = w * 0.78f
                    ),
                    radius = w * 0.78f,
                    center = Offset(w * 0.88f, h * 0.18f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(vibrantAccent.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(w * 0.12f, h * 0.82f),
                        radius = w * 0.72f
                    ),
                    radius = w * 0.72f,
                    center = Offset(w * 0.12f, h * 0.82f)
                )
                val ribbon = Path().apply {
                    moveTo(-w * 0.15f, h * 0.68f)
                    cubicTo(w * 0.25f, h * 0.48f, w * 0.65f, h * 0.86f, w * 1.15f, h * 0.56f)
                    lineTo(w * 1.15f, h * 0.78f)
                    cubicTo(w * 0.65f, h * 1.04f, w * 0.25f, h * 0.68f, -w * 0.15f, h * 0.86f)
                    close()
                }
                drawPath(
                    path = ribbon,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            vibrantAccent.copy(alpha = 0.22f),
                            Color(0xFFEC4899).copy(alpha = 0.26f)
                        )
                    )
                )
                for (i in 1..4) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = w * (0.24f + i * 0.14f),
                        center = Offset(w * 0.92f, h * 0.24f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            1 -> {
                // Chronotype / Peak Time: Concentric clock rings + electric wave arcs
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.28f),
                        radius = w * 0.85f
                    ),
                    radius = w * 0.85f,
                    center = Offset(w * 0.5f, h * 0.28f)
                )
                for (i in 1..6) {
                    drawCircle(
                        color = if (i % 2 == 0) vibrantAccent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f),
                        radius = w * (0.16f + i * 0.12f),
                        center = Offset(w * 0.82f, h * 0.30f),
                        style = Stroke(width = (if (i == 3) 4.dp else 1.5.dp).toPx())
                    )
                }
                val wave = Path().apply {
                    moveTo(-w * 0.1f, h * 0.76f)
                    cubicTo(w * 0.28f, h * 0.62f, w * 0.68f, h * 0.92f, w * 1.1f, h * 0.72f)
                }
                drawPath(
                    path = wave,
                    color = vibrantAccent.copy(alpha = 0.36f),
                    style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            2 -> {
                // #1 Artist: Dramatic magenta/coral radial glow + sweeping diagonal graphic bands
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFEC4899).copy(alpha = 0.52f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.34f),
                        radius = w * 0.78f
                    ),
                    radius = w * 0.78f,
                    center = Offset(w * 0.5f, h * 0.34f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF6B4A).copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.78f),
                        radius = w * 0.62f
                    ),
                    radius = w * 0.62f,
                    center = Offset(w * 0.15f, h * 0.78f)
                )
                for (i in 0..3) {
                    val yStart = h * (0.08f + i * 0.22f)
                    val band = Path().apply {
                        moveTo(-w * 0.1f, yStart + h * 0.12f)
                        lineTo(w * 1.1f, yStart - h * 0.08f)
                        lineTo(w * 1.1f, yStart - h * 0.02f)
                        lineTo(-w * 0.1f, yStart + h * 0.18f)
                        close()
                    }
                    drawPath(
                        path = band,
                        color = Color.White.copy(alpha = if (i % 2 == 0) 0.055f else 0.03f)
                    )
                }
            }
            3 -> {
                // Top 5 Songs: Asymmetric emerald/lime soundwave bands
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(vibrantAccent.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.16f),
                        radius = w * 0.75f
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * 0.85f, h * 0.16f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF10B981).copy(alpha = 0.36f), Color.Transparent),
                        center = Offset(w * 0.12f, h * 0.86f),
                        radius = w * 0.70f
                    ),
                    radius = w * 0.70f,
                    center = Offset(w * 0.12f, h * 0.86f)
                )
                for (i in 0..2) {
                    val offset = i * 28.dp.toPx()
                    val curve = Path().apply {
                        moveTo(-w * 0.1f, h * 0.18f + offset)
                        cubicTo(w * 0.35f, h * 0.06f + offset, w * 0.70f, h * 0.28f + offset, w * 1.1f, h * 0.14f + offset)
                    }
                    drawPath(
                        path = curve,
                        color = vibrantAccent.copy(alpha = 0.16f - i * 0.04f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            else -> {
                // Share Slide: Rich ultraviolet + lime kinetic wave backdrop
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.50f), Color.Transparent),
                        center = Offset(w * 0.2f, h * 0.22f),
                        radius = w * 0.82f
                    ),
                    radius = w * 0.82f,
                    center = Offset(w * 0.2f, h * 0.22f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(vibrantAccent.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.80f),
                        radius = w * 0.72f
                    ),
                    radius = w * 0.72f,
                    center = Offset(w * 0.85f, h * 0.80f)
                )
                val arc = Path().apply {
                    moveTo(-w * 0.1f, h * 0.52f)
                    cubicTo(w * 0.30f, h * 0.36f, w * 0.70f, h * 0.68f, w * 1.1f, h * 0.44f)
                    lineTo(w * 1.1f, h * 0.60f)
                    cubicTo(w * 0.70f, h * 0.84f, w * 0.30f, h * 0.52f, -w * 0.1f, h * 0.68f)
                    close()
                }
                drawPath(
                    path = arc,
                    color = Color.White.copy(alpha = 0.055f)
                )
            }
        }
    }
}

@Composable
private fun WrappedSlideLayout(
    pageIndex: Int,
    tag: String,
    gradient: List<Color>,
    accentColor: Color,
    content: @Composable (ink: Color, accentOnSlide: Color) -> Unit
) {
    val ink = Color.White
    val accentOnSlide = if (accentColor.luminance() > 0.35f) accentColor else AuraNeonLime

    Box(modifier = Modifier.fillMaxSize()) {
        WrappedGraphicBackdrop(
            pageIndex = pageIndex,
            gradient = gradient,
            accentColor = accentColor
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 68.dp, bottom = 28.dp, start = 28.dp, end = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = tag,
                color = accentOnSlide,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            content(ink, accentOnSlide)

            Text(
                text = "AURA WRAPPED",
                color = ink.copy(alpha = 0.48f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
        }
    }
}

@Composable
private fun WrappedSummarySlide(
    snapshot: AnalyticsSnapshot,
    gradient: List<Color>,
    accentColor: Color,
    onAccentColor: Color
) {
    val heroArtUrl = snapshot.topTracks.firstOrNull()?.first?.highResArtworkUrl
    WrappedSlideLayout(
        pageIndex = 0,
        tag = "WRAPPED • ${snapshot.range.label.uppercase(Locale.US)}",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, _ ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!heroArtUrl.isNullOrBlank()) {
                AsyncImage(
                    model = heroArtUrl,
                    contentDescription = "Top song artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ink.copy(alpha = 0.12f))
                )
            }
            Text(
                text = "${formatIntStat(snapshot.totalMinutes)} min",
                color = ink,
                style = TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 52.sp,
                    letterSpacing = (-1.2).sp,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = "${formatIntStat(snapshot.totalPlays)} plays · ${formatIntStat(snapshot.uniqueArtists)} artists",
                color = ink.copy(alpha = 0.84f),
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
                        text = topGenre,
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
    val chronotypeTitle = when (peakHour) {
        in 5..10 -> "Early Bird"
        in 11..16 -> "Daytime Listener"
        in 17..20 -> "Evening Listener"
        else -> "Night Owl"
    }

    WrappedSlideLayout(
        pageIndex = 1,
        tag = "PEAK TIME",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, _ ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = chronotypeTitle,
                color = ink,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 44.sp,
                letterSpacing = (-0.8).sp
            )
            Text(
                text = "Peak hour: ${snapshot.peakHourLabel.ifBlank { "Evening" }}",
                color = ink.copy(alpha = 0.84f),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                )
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
    val artistCoverUrl = remember(snapshot, topArtist) {
        val artistName = topArtist?.first
        val matchingTrack = if (!artistName.isNullOrBlank()) {
            snapshot.topTracks.firstOrNull { (track, _) ->
                track.artist.contains(artistName, ignoreCase = true)
            }?.first
        } else {
            null
        }
        (matchingTrack ?: snapshot.topTracks.firstOrNull()?.first)?.highResArtworkUrl
    }

    WrappedSlideLayout(
        pageIndex = 2,
        tag = "#1 ARTIST",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, accentOnSlide ->
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!artistCoverUrl.isNullOrBlank()) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(204.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accentOnSlide.copy(alpha = 0.36f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    AsyncImage(
                        model = artistCoverUrl,
                        contentDescription = topArtist?.first ?: "Top artist cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(182.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.12f))
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(44.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = topArtist?.first ?: "No top artist",
                    color = ink,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.7).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (topArtist != null) {
                        "${topArtist.second} plays"
                    } else {
                        "Play a few songs to see your stats"
                    },
                    color = ink.copy(alpha = 0.82f),
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
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
        pageIndex = 3,
        tag = "TOP SONGS",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, accentOnSlide ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Top 5",
                color = ink,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (topFive.isEmpty()) {
                Text(
                    text = "Play a few songs to see your stats",
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
                                fontSize = 16.sp,
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ink.copy(alpha = 0.12f))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = ink,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • ${plays}x",
                                color = ink.copy(alpha = 0.74f),
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
                            modifier = Modifier.padding(start = 86.dp),
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
    val topTrack = snapshot.topTracks.firstOrNull()?.first
    val shareInk = accentColor.contrastInk()

    WrappedSlideLayout(
        pageIndex = 4,
        tag = "RECEIPT",
        gradient = gradient,
        accentColor = accentColor
    ) { ink, _ ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (topTrack != null && topTrack.highResArtworkUrl.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AsyncImage(
                        model = topTrack.highResArtworkUrl,
                        contentDescription = topTrack.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ink.copy(alpha = 0.14f))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = topTrack.title,
                            color = ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = topTrack.artist,
                            color = ink.copy(alpha = 0.74f),
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = "Share your stats",
                color = ink,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 40.sp,
                letterSpacing = (-0.6).sp
            )
            Text(
                text = "${formatIntStat(snapshot.totalMinutes)} min · ${formatIntStat(snapshot.totalPlays)} plays",
                color = ink.copy(alpha = 0.82f),
                style = TextStyle(
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onShareReceipt,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = shareInk
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    tint = shareInk,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share Receipt",
                    color = shareInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ink.copy(alpha = 0.14f),
                    contentColor = ink
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Done",
                    color = ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                )
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
