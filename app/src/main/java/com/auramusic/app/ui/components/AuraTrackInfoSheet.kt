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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Song Info bottom sheet displaying track metadata, audio format, engagement counts,
 * clickable chapters (when present), and YouTube URL actions.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    val hasValidVideoId = track.videoId.isNotBlank() && !track.videoId.startsWith("spotify_pending")
    val youtubeMusicUrl = "https://music.youtube.com/watch?v=${track.videoId}"
    val youtubeWebUrl = "https://www.youtube.com/watch?v=${track.videoId}"

    val timestamps = remember(description) { extractTimestamps(description) }
    val displayViews = rydData?.viewCount?.takeIf { it > 0L } ?: track.viewCount
    val formatSummary = listOfNotNull(
        track.codec.takeIf { it.isNotBlank() },
        track.bitrateKbps.takeIf { it > 0 }?.let { "$it kbps" }
    ).joinToString(" · ").ifBlank { "Unknown" }

    val infoRows = buildList {
        add("Title" to track.title)
        add("Artist" to track.artist)
        if (track.album.isNotBlank()) add("Album" to track.album)
        add("Format" to formatSummary)
        add("Loudness" to String.format(Locale.US, "%.1f dB", track.loudnessDb))
        displayViews?.let { add("Views" to formatCompactNumber(it)) }
        rydData?.let { add("Likes" to formatCompactNumber(it.likes)) }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(auraColors.circleButtonBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Song Info",
                        color = auraColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Song Info",
                        tint = auraColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(auraColors.surfaceCard)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                infoRows.forEachIndexed { index, (label, value) ->
                    InfoRow(
                        label = label,
                        value = value,
                        textPrimary = auraColors.textPrimary,
                        textSecondary = auraColors.textSecondary
                    )
                    if (index < infoRows.lastIndex) {
                        HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                    }
                }
            }

            if (timestamps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = auraColors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chapters",
                        color = auraColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
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
                                    color = auraColors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (ts.contextLabel.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ts.contextLabel,
                                        color = auraColors.textSecondary,
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(youtubeMusicUrl))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
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
                        text = "Copy link",
                        fontSize = 13.sp,
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
                            Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
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
                        text = "YouTube",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
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

private fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 10_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> NumberFormat.getIntegerInstance(Locale.US).format(value)
    }
}
