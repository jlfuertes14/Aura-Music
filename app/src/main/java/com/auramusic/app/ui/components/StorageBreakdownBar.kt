package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.ui.theme.LocalIsDarkTheme
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.auraGlassSurface
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
    val isDark = LocalIsDarkTheme.current
    val isGlass = LocalLiquidGlassEnabled.current
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    val totalBytes = segments.sumOf { it.bytes.coerceAtLeast(0L) }
    val nonZeroSegments = segments.filter { it.bytes > 0L }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .auraGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackColor = if (isDark) Color(0xFF181A22) else MaterialTheme.colorScheme.surfaceVariant,
                glassAlpha = if (isDark) 0.08f else 0.55f
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Storage Footprint",
                color = primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${formatStorageBytes(totalBytes)} used",
                color = secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val trackColor = when {
            isGlass && isDark -> Color.White.copy(alpha = 0.08f)
            isDark -> Color(0xFF252834)
            else -> Color(0xFFE2E4EC)
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
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegendPill(
    segment: StorageSegment,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color
) {
    val pillBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.045f)
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(pillBg)
            .border(width = 0.75.dp, color = pillBorder, shape = CircleShape)
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
            fontWeight = FontWeight.SemiBold
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
