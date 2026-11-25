package com.auramusic.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect
import com.auramusic.app.ui.theme.AuraLilacCard
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalIsDarkTheme
import com.auramusic.app.ui.theme.auraGlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

/**
 * Full-screen Listening Analytics, 24-Hour Listening Clock, Thermal Receiptify Card, and Aura Wrapped Story Modal.
 */
@Composable
fun AnalyticsScreen(
    snapshot: AnalyticsSnapshot,
    selectedRange: AnalyticsRange,
    onSelectRange: (AnalyticsRange) -> Unit,
    onPlayTrack: (AuraTrack) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val bgColor = MaterialTheme.colorScheme.background
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryAccent = MaterialTheme.colorScheme.primary

    var showWrappedModal by remember { mutableStateOf(false) }
    var isSharingReceipt by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryText
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Listening Insights",
                        color = primaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "On-device playback telemetry",
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                onClick = { showWrappedModal = true },
                shape = CircleShape,
                color = primaryAccent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Wrapped",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Range Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            items(AnalyticsRange.entries) { range ->
                val isSelected = range == selectedRange
                Surface(
                    onClick = { onSelectRange(range) },
                    shape = CircleShape,
                    color = if (isSelected) primaryAccent else Color.Transparent,
                    modifier = if (isSelected) {
                        Modifier
                    } else {
                        Modifier.auraGlassSurface(
                            shape = CircleShape,
                            fallbackColor = if (isDark) Color(0xFF1E2028) else Color(0xFFE7E9F0)
                        )
                    }
                ) {
                    Text(
                        text = range.label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else primaryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Summary Metric Cards (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            icon = Icons.Rounded.Schedule,
                            label = "Total Minutes",
                            value = formatIntStat(snapshot.totalMinutes),
                            accent = primaryAccent,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            icon = Icons.Rounded.PlayArrow,
                            label = "Total Plays",
                            value = formatIntStat(snapshot.totalPlays),
                            accent = primaryAccent,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            icon = Icons.Rounded.Person,
                            label = "Unique Artists",
                            value = formatIntStat(snapshot.uniqueArtists),
                            accent = primaryAccent,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            icon = Icons.Rounded.Headphones,
                            label = "Peak Hour",
                            value = snapshot.peakHourLabel.ifBlank { "—" },
                            accent = primaryAccent,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Open Aura Wrapped Hero Action
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF281C4B),
                                    Color(0xFF1B2436)
                                )
                            )
                        )
                        .border(1.dp, AuraLilacCard.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                        .clickable { showWrappedModal = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AURA WRAPPED • ${selectedRange.label.uppercase(Locale.US)}",
                            color = AuraNeonLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your Listening Story",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Explore your chronotype, #1 artist & top 5 anthems",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { showWrappedModal = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuraNeonLime,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Open Aura Wrapped",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. 24-Hour Listening Clock Canvas Chart
            item {
                ListeningClockCard(
                    hourlyCounts = snapshot.hourlyCounts,
                    peakHourLabel = snapshot.peakHourLabel,
                    accentColor = primaryAccent,
                    isDark = isDark
                )
            }

            // 4. Top Tracks (playable on tap)
            item {
                RankedSectionCard(
                    title = "Top Tracks",
                    subtitle = if (snapshot.topTracks.isEmpty()) "Play songs to build your chart" else "Tap any track to play",
                    isDark = isDark
                ) {
                    if (snapshot.topTracks.isEmpty()) {
                        EmptyInsightNote("No tracks recorded in this range yet.", secondaryText)
                    } else {
                        snapshot.topTracks.take(10).forEachIndexed { index, (track, plays) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlayTrack(track) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (index < 3) primaryAccent else secondaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp),
                                    textAlign = TextAlign.Center
                                )
                                AsyncImage(
                                    model = track.highResArtworkUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = primaryText,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$plays ${if (plays == 1) "play" else "plays"}",
                                    color = secondaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 5. Top Artists
            item {
                val maxArtistPlays = snapshot.topArtists.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                RankedSectionCard(
                    title = "Top Artists",
                    subtitle = "${snapshot.topArtists.size} featured",
                    isDark = isDark
                ) {
                    if (snapshot.topArtists.isEmpty()) {
                        EmptyInsightNote("No artist plays recorded yet.", secondaryText)
                    } else {
                        snapshot.topArtists.take(8).forEachIndexed { index, (artist, plays) ->
                            RankedBarRow(
                                rank = index + 1,
                                label = artist,
                                countLabel = "$plays plays",
                                fraction = (plays.toFloat() / maxArtistPlays).coerceIn(0.06f, 1f),
                                accent = primaryAccent,
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            // 6. Top Genres
            item {
                val maxGenrePlays = snapshot.topGenres.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                RankedSectionCard(
                    title = "Top Genres",
                    subtitle = "Sound signature breakdown",
                    isDark = isDark
                ) {
                    if (snapshot.topGenres.isEmpty()) {
                        EmptyInsightNote("No genre data recorded yet.", secondaryText)
                    } else {
                        snapshot.topGenres.take(6).forEachIndexed { index, (genre, plays) ->
                            RankedBarRow(
                                rank = index + 1,
                                label = genre,
                                countLabel = "$plays plays",
                                fraction = (plays.toFloat() / maxGenrePlays).coerceIn(0.06f, 1f),
                                accent = MaterialTheme.colorScheme.secondary,
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            // 7. Thermal Listening Receipt ("Receiptify" style) + Share Receipt PNG
            item {
                ThermalReceiptCard(
                    snapshot = snapshot,
                    isSharing = isSharingReceipt,
                    onShareReceipt = shareReceipt
                )
            }
        }
    }

    if (showWrappedModal) {
        AuraWrappedStoryDialog(
            snapshot = snapshot,
            onPlayTrack = onPlayTrack,
            onShareReceipt = shareReceipt,
            onDismiss = { showWrappedModal = false }
        )
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .auraGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackColor = if (isDark) Color(0xFF181A22) else MaterialTheme.colorScheme.surface,
                glassAlpha = if (isDark) 0.08f else 0.7f
            )
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            color = primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = secondaryText,
            fontSize = 11.5.sp
        )
    }
}

@Composable
private fun ListeningClockCard(
    hourlyCounts: List<Int>,
    peakHourLabel: String,
    accentColor: Color,
    isDark: Boolean
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val normalized = remember(hourlyCounts) {
        List(24) { hour -> hourlyCounts.getOrElse(hour) { 0 }.coerceAtLeast(0) }
    }
    val maxCount = normalized.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peakHourIndex = normalized.indices.maxByOrNull { normalized[it] } ?: -1
    val inactiveBarColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val midBarColor = accentColor.copy(alpha = 0.52f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .auraGlassSurface(
                shape = RoundedCornerShape(18.dp),
                fallbackColor = if (isDark) Color(0xFF181A22) else MaterialTheme.colorScheme.surface,
                glassAlpha = if (isDark) 0.08f else 0.7f
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "24-Hour Listening Clock",
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Peak window: ${peakHourLabel.ifBlank { "No data" }}",
                    color = secondaryText,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val barCount = 24
            val gapPx = 4.dp.toPx()
            val barWidth = ((size.width - gapPx * (barCount - 1)) / barCount).coerceAtLeast(2f)
            val maxBarHeight = size.height - 6.dp.toPx()

            // Subtle baseline
            drawLine(
                color = inactiveBarColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )

            for (hour in 0 until barCount) {
                val count = normalized[hour]
                val ratio = count.toFloat() / maxCount.toFloat()
                val barHeight = if (count == 0) 4.dp.toPx() else (ratio * maxBarHeight).coerceAtLeast(8.dp.toPx())
                val x = hour * (barWidth + gapPx)
                val y = size.height - barHeight
                val barColor = when {
                    count == 0 -> inactiveBarColor
                    hour == peakHourIndex -> accentColor
                    else -> midBarColor
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
                    color = secondaryText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RankedSectionCard(
    title: String,
    subtitle: String,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .auraGlassSurface(
                shape = RoundedCornerShape(18.dp),
                fallbackColor = if (isDark) Color(0xFF181A22) else MaterialTheme.colorScheme.surface,
                glassAlpha = if (isDark) 0.08f else 0.7f
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = primaryText,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = secondaryText,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun RankedBarRow(
    rank: Int,
    label: String,
    countLabel: String,
    fraction: Float,
    accent: Color,
    isDark: Boolean
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBg = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.07f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank. $label",
                color = primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = countLabel,
                color = secondaryText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun EmptyInsightNote(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.5.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun ThermalReceiptCard(
    snapshot: AnalyticsSnapshot,
    isSharing: Boolean,
    onShareReceipt: () -> Unit
) {
    val paperBg = Color(0xFFF6F2E9)
    val inkDark = Color(0xFF17181C)
    val inkMuted = Color(0xFF5A5D66)
    val dateStamp = remember {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date())
    }
    val receiptRows = remember(snapshot.topTracks) { snapshot.topTracks.take(10) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(paperBg)
            .border(1.dp, Color(0xFFDAD3C3), RoundedCornerShape(18.dp))
            .padding(18.dp)
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
                Text(
                    text = "${snapshot.range.label.uppercase(Locale.US)} • $dateStamp",
                    color = inkMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }
            Icon(
                imageVector = Icons.Rounded.ReceiptLong,
                contentDescription = null,
                tint = inkDark,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = inkDark.copy(alpha = 0.25f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(6.dp))

        if (receiptRows.isEmpty()) {
            Text(
                text = "00  NO LISTENING ITEMS RECORDED YET",
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
                        .padding(vertical = 3.dp),
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${plays}x",
                        color = inkDark,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = inkDark.copy(alpha = 0.25f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ITEM COUNT: ${snapshot.totalPlays}",
                color = inkDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TOTAL: ${snapshot.totalMinutes} MIN",
                color = inkDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onShareReceipt,
            enabled = !isSharing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = inkDark,
                contentColor = paperBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            if (isSharing) {
                CircularProgressIndicator(
                    color = paperBg,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rendering PNG…",
                    fontSize = 13.sp,
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AuraWrappedStoryDialog(
    snapshot: AnalyticsSnapshot,
    onPlayTrack: (AuraTrack) -> Unit,
    onShareReceipt: () -> Unit,
    onDismiss: () -> Unit
) {
    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ForceWhiteSystemBarsEffect()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C11))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Segmented story progress bar + Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { index ->
                        val active = index <= pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) AuraNeonLime else Color.White.copy(alpha = 0.20f)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Aura Wrapped",
                            tint = Color.White
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> WrappedSummaryCard(snapshot)
                        1 -> WrappedChronotypeCard(snapshot)
                        2 -> WrappedTopArtistCard(snapshot)
                        3 -> WrappedTopTracksCard(
                            snapshot = snapshot,
                            onPlayTrack = { track ->
                                onPlayTrack(track)
                                onDismiss()
                            }
                        )
                        else -> WrappedShareCard(
                            snapshot = snapshot,
                            onShareReceipt = onShareReceipt,
                            onDone = onDismiss
                        )
                    }
                }

                // Bottom navigation bar for story cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous card",
                            tint = if (pagerState.currentPage > 0) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        text = "${pagerState.currentPage + 1} / $pageCount",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
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
                            .background(AuraNeonLime)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next card",
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedCardShell(
    tag: String,
    gradient: List<Color>,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = tag,
            color = AuraNeonLime,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        content()
        Text(
            text = "AURA MUSIC WRAPPED",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun WrappedSummaryCard(snapshot: AnalyticsSnapshot) {
    WrappedCardShell(
        tag = "YOUR ${snapshot.range.label.uppercase(Locale.US)} IN SOUND",
        gradient = listOf(Color(0xFF1E133B), Color(0xFF10131C))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "${formatIntStat(snapshot.totalMinutes)} minutes",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 40.sp
            )
            Text(
                text = "spent locked into your favorite tracks across ${snapshot.totalPlays} plays and ${snapshot.uniqueArtists} artists.",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 16.sp,
                lineHeight = 23.sp
            )
            val topGenre = snapshot.topGenres.firstOrNull()?.first
            if (!topGenre.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = AuraNeonLime.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "Top Genre: $topGenre",
                        color = AuraNeonLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedChronotypeCard(snapshot: AnalyticsSnapshot) {
    val peakHour = snapshot.hourlyCounts.indices.maxByOrNull { snapshot.hourlyCounts[it] } ?: 21
    val (chronotypeTitle, chronotypeDesc) = when (peakHour) {
        in 5..10 -> "The Dawn Listener" to "You start the day early with crisp morning soundtracks before the world wakes up."
        in 11..16 -> "The Sunlit Curator" to "Afternoon momentum fuels your deepest playlists and discovery sessions."
        in 17..20 -> "The Golden Hour Groover" to "Sunset transitions and evening commutes are when your speakers shine brightest."
        else -> "The Midnight Audiophile" to "Late-night quiet lets every bassline and vocal nuance hit harder."
    }

    WrappedCardShell(
        tag = "LISTENING CHRONOTYPE",
        gradient = listOf(Color(0xFF102A33), Color(0xFF0E121B))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = AuraNeonLime,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = chronotypeTitle,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 35.sp
            )
            Text(
                text = "Peak hour: ${snapshot.peakHourLabel.ifBlank { "Evening" }}",
                color = AuraNeonLime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = chronotypeDesc,
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun WrappedTopArtistCard(snapshot: AnalyticsSnapshot) {
    val topArtist = snapshot.topArtists.firstOrNull()
    WrappedCardShell(
        tag = "#1 ARTIST",
        gradient = listOf(Color(0xFF34142E), Color(0xFF11121A))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = AuraNeonLime,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = topArtist?.first ?: "Waiting for your #1",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 36.sp
            )
            Text(
                text = if (topArtist != null) {
                    "${topArtist.second} plays in ${snapshot.range.label} — an undisputed staple in your rotation."
                } else {
                    "Keep playing music to crown your top artist."
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.5.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun WrappedTopTracksCard(
    snapshot: AnalyticsSnapshot,
    onPlayTrack: (AuraTrack) -> Unit
) {
    val topFive = snapshot.topTracks.take(5)
    WrappedCardShell(
        tag = "TOP 5 TRACKS",
        gradient = listOf(Color(0xFF17271B), Color(0xFF0E1218))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Heavy Rotation",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (topFive.isEmpty()) {
                Text(
                    text = "Play a few tracks to unlock your top 5 countdown.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp
                )
            } else {
                topFive.forEachIndexed { idx, (track, plays) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${idx + 1}",
                            color = AuraNeonLime,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.width(28.dp)
                        )
                        AsyncImage(
                            model = track.highResArtworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • ${plays}x",
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedShareCard(
    snapshot: AnalyticsSnapshot,
    onShareReceipt: () -> Unit,
    onDone: () -> Unit
) {
    WrappedCardShell(
        tag = "SHARE YOUR STATS",
        gradient = listOf(Color(0xFF211A3D), Color(0xFF0E1017))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Take your sound receipt anywhere",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 33.sp
            )
            Text(
                text = "${snapshot.totalMinutes} min • ${snapshot.totalPlays} plays • Peak ${snapshot.peakHourLabel}",
                color = AuraNeonLime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onShareReceipt,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraNeonLime,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Receipt PNG", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            OutlinedButton(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
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
            // Verify FileProvider path configuration; fallback to updates/ if needed.
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
    canvas.drawColor(AndroidColor.parseColor("#F6F2E9"))

    val ink = AndroidColor.parseColor("#17181C")
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
        color = AndroidColor.parseColor("#C8C2B4")
        strokeWidth = 3f
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
    java.text.NumberFormat.getIntegerInstance(Locale.US).format(value.coerceAtLeast(0))
