package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.ui.theme.LocalAuraColors
import java.util.Locale

data class StorageSegment(
    val label: String,
    val bytes: Long,
    val color: Color
)

/**
 * Renders a rounded multi-color proportional storage breakdown bar with MB/GB legend pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBreakdownBar(
    segments: List<StorageSegment>,
    modifier: Modifier = Modifier
) {
    val auraColors = LocalAuraColors.current
    val totalBytes = segments.sumOf { it.bytes.coerceAtLeast(0L) }
    val nonZeroSegments = segments.filter { it.bytes > 0L }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(auraColors.surfaceCard)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Storage",
                color = auraColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatStorageBytes(totalBytes),
                color = auraColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val trackColor = if (auraColors.isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(trackColor),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (totalBytes <= 0L || nonZeroSegments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(trackColor)
                )
            } else {
                nonZeroSegments.forEach { segment ->
                    val rawWeight = (segment.bytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.03f)
                    Box(
                        modifier = Modifier
                            .weight(rawWeight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(segment.color)
                    )
                }
            }
        }

        if (segments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                segments.forEach { segment ->
                    StorageLegendPill(
                        segment = segment,
                        pillBg = auraColors.pillInactiveBg,
                        primaryText = auraColors.textPrimary,
                        secondaryText = auraColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegendPill(
    segment: StorageSegment,
    pillBg: Color,
    primaryText: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(pillBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(segment.color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = segment.label,
            color = primaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = formatStorageBytes(segment.bytes),
            color = secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

private fun formatStorageBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes == 0L) return "0 MB"
    val mb = safeBytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else if (mb >= 10.0) {
        String.format(Locale.US, "%.0f MB", mb)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
