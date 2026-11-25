package com.auramusic.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor
import java.text.NumberFormat
import java.util.Locale

private val TIMESTAMP_REGEX = Regex("""\b(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)\b""")

private data class DescriptionTimestamp(
    val label: String,
    val contextLabel: String,
    val seconds: Int
)

/**
 * "Stats for Nerds" & Track Info bottom sheet displaying audio stream telemetry, RYD votes,
 * YouTube URL actions, and clickable timestamp chips extracted from the description.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuraTrackInfoSheet(
    track: AuraTrack,
    rydData: ReturnYouTubeDislikeData?,
    description: String,
    onSeekToSec: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    val hasValidVideoId = track.videoId.isNotBlank() && !track.videoId.startsWith("spotify_pending")
    val youtubeMusicUrl = "https://music.youtube.com/watch?v=${track.videoId}"
    val youtubeWebUrl = "https://www.youtube.com/watch?v=${track.videoId}"

    val timestamps = remember(description) { extractTimestamps(description) }
    val displayViews = rydData?.viewCount?.takeIf { it > 0L } ?: track.viewCount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = auraGlassSheetColor(auraColors.sheetBackground),
        scrimColor = auraColors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(auraColors.dragHandle)
            )
        }
    ) {
        ForceWhiteSystemBarsEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AsyncImage(
                        model = track.highResArtworkUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(auraColors.surfaceCard)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = auraColors.textPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = auraColors.textSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (track.album.isNotBlank()) {
                            Text(
                                text = track.album,
                                color = auraColors.textMuted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Track Info",
                        tint = auraColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Copy / Open YouTube URL buttons (borderless)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(youtubeMusicUrl))
                        Toast.makeText(context, "Copied YouTube Music URL", Toast.LENGTH_SHORT).show()
                    },
                    enabled = hasValidVideoId,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = auraColors.circleButtonBg,
                        contentColor = auraColors.textPrimary,
                        disabledContainerColor = auraColors.pillInactiveBg.copy(alpha = 0.55f),
                        disabledContentColor = auraColors.textMuted
                    ),
                    elevation = null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy URL",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeWebUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "Could not open YouTube link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = hasValidVideoId,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = auraColors.accent,
                        contentColor = auraColors.onAccent,
                        disabledContainerColor = auraColors.pillInactiveBg.copy(alpha = 0.55f),
                        disabledContentColor = auraColors.textMuted
                    ),
                    elevation = null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open YouTube",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats for Nerds telemetry list (borderless grouped list)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = accentTint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STATS FOR NERDS",
                    color = auraColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(auraColors.surfaceCard)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                StatRow("Video ID", track.videoId.ifBlank { "Unavailable" }, monospace = true, textPrimary = auraColors.textPrimary, textSecondary = auraColors.textSecondary)
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                StatRow("Codec", track.codec.ifBlank { "Unknown" }, textPrimary = auraColors.textPrimary, textSecondary = auraColors.textSecondary)
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                StatRow("Bitrate", "${track.bitrateKbps} kbps", textPrimary = auraColors.textPrimary, textSecondary = auraColors.textSecondary)
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                StatRow("Loudness", String.format(Locale.US, "%.1f dB", track.loudnessDb), textPrimary = auraColors.textPrimary, textSecondary = auraColors.textSecondary)
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                StatRow("Source", track.source.replaceFirstChar { it.uppercase() }, textPrimary = auraColors.textPrimary, textSecondary = auraColors.textSecondary)
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                StatRow("Duration", formatSecondsLabel(track.durationSeconds), textPrimary = auraColors.textPrimary, textSecondary = auraColors.textSecondary)
            }

            if (displayViews != null || rydData != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayViews?.let { views ->
                        EngagementPill(
                            icon = Icons.Rounded.Visibility,
                            label = "Views",
                            value = formatCompactNumber(views),
                            accentTint = accentTint,
                            surfaceColor = auraColors.surfaceCard,
                            textPrimary = auraColors.textPrimary,
                            textSecondary = auraColors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rydData != null) {
                        EngagementPill(
                            icon = Icons.Rounded.ThumbUp,
                            label = "Likes",
                            value = formatCompactNumber(rydData.likes),
                            accentTint = accentTint,
                            surfaceColor = auraColors.surfaceCard,
                            textPrimary = auraColors.textPrimary,
                            textSecondary = auraColors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        EngagementPill(
                            icon = Icons.Rounded.ThumbDown,
                            label = "Dislikes",
                            value = formatCompactNumber(rydData.dislikes),
                            accentTint = accentTint,
                            surfaceColor = auraColors.surfaceCard,
                            textPrimary = auraColors.textPrimary,
                            textSecondary = auraColors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (timestamps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = accentTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TIMESTAMPS (TAP TO SEEK)",
                        color = auraColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timestamps.forEach { ts ->
                        Surface(
                            onClick = { onSeekToSec(ts.seconds) },
                            shape = RoundedCornerShape(10.dp),
                            color = auraColors.pillInactiveBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ts.label,
                                    color = accentTint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (ts.contextLabel.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ts.contextLabel,
                                        color = auraColors.textPrimary,
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

            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "DESCRIPTION",
                    color = auraColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description.trim(),
                    color = auraColors.textPrimary.copy(alpha = 0.86f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textSecondary,
            fontSize = 12.5.sp
        )
        Text(
            text = value,
            color = textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun EngagementPill(
    icon: ImageVector,
    label: String,
    value: String,
    accentTint: Color,
    surfaceColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentTint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = textSecondary,
                fontSize = 10.5.sp
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun extractTimestamps(description: String): List<DescriptionTimestamp> {
    if (description.isBlank()) return emptyList()
    val results = mutableListOf<DescriptionTimestamp>()
    val seen = mutableSetOf<Int>()
    description.lines().forEach { line ->
        TIMESTAMP_REGEX.findAll(line).forEach { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            val seconds = match.groupValues[3].toIntOrNull() ?: 0
            val totalSec = hours * 3600 + minutes * 60 + seconds
            if (seen.add(totalSec)) {
                val remainder = line
                    .replace(match.value, "")
                    .trim()
                    .trimStart('-', '•', '|', ':')
                    .trim()
                    .take(28)
                results.add(
                    DescriptionTimestamp(
                        label = match.value,
                        contextLabel = remainder,
                        seconds = totalSec
                    )
                )
            }
        }
    }
    return results
}

private fun formatSecondsLabel(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 10_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> NumberFormat.getIntegerInstance(Locale.US).format(value)
    }
}
