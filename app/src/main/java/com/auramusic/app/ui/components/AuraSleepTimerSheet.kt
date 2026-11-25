package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.TextSecondary
import com.auramusic.app.ui.theme.auraGlassSheetColor
import com.auramusic.app.ui.theme.auraGlassSurface

private val SLEEP_TIMER_PRESETS = listOf(5, 10, 15, 30, 45, 60)

/**
 * Formats an active sleep timer summary for badges and settings subtitles.
 */
fun formatSleepTimerStatus(
    remainingMinutes: Int?,
    endOfTrackEnabled: Boolean
): String? = when {
    remainingMinutes != null && remainingMinutes > 0 && endOfTrackEnabled ->
        "${remainingMinutes}m • End of track"
    remainingMinutes != null && remainingMinutes > 0 ->
        "${remainingMinutes}m remaining"
    endOfTrackEnabled ->
        "End of current track"
    else -> null
}

/**
 * Modal bottom sheet for configuring Aura's sleep timer (presets, custom stepper,
 * end-of-current-track toggle, active countdown banner, and cancel action).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuraSleepTimerSheet(
    remainingMinutes: Int?,
    endOfTrackEnabled: Boolean,
    onSelectMinutes: (Int?) -> Unit,
    onToggleEndOfTrack: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    containerColor: Color = auraGlassSheetColor(Color(0xFF14161E))
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customMinutes by remember(remainingMinutes) {
        mutableIntStateOf((remainingMinutes ?: 20).coerceIn(5, 240))
    }
    val isTimerActive = (remainingMinutes != null && remainingMinutes > 0) || endOfTrackEnabled

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.20f))
            )
        }
    ) {
        ForceWhiteSystemBarsEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .padding(bottom = 30.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AuraNeonLime.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = AuraNeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Fade out and pause playback automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Sleep Timer",
                        tint = TextSecondary
                    )
                }
            }

            if (isTimerActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AuraNeonLime.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = AuraNeonLime.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Timer Active",
                                color = AuraNeonLime,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val bannerDetail = when {
                                remainingMinutes != null && remainingMinutes > 0 && endOfTrackEnabled ->
                                    "Playback pauses in $remainingMinutes min or at end of current track"
                                remainingMinutes != null && remainingMinutes > 0 ->
                                    "Playback pauses in $remainingMinutes min"
                                else ->
                                    "Playback pauses when the current track finishes"
                            }
                            Text(
                                text = bannerDetail,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "QUICK PRESETS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SLEEP_TIMER_PRESETS.forEach { presetMin ->
                    val isSelected = remainingMinutes == presetMin
                    Surface(
                        onClick = {
                            onSelectMinutes(presetMin)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AuraNeonLime else Color.White.copy(alpha = 0.07f),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AuraNeonLime else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            text = "${presetMin}m",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "CUSTOM DURATION",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { customMinutes = (customMinutes - 5).coerceAtLeast(5) },
                        modifier = Modifier
                            .size(40.dp)
                            .auraGlassSurface(shape = CircleShape, fallbackColor = Color(0xFF262A36))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease 5 minutes",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "$customMinutes min",
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(74.dp)
                    )

                    IconButton(
                        onClick = { customMinutes = (customMinutes + 5).coerceAtMost(240) },
                        modifier = Modifier
                            .size(40.dp)
                            .auraGlassSurface(shape = CircleShape, fallbackColor = Color(0xFF262A36))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase 5 minutes",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        onSelectMinutes(customMinutes)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraNeonLime,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Set ${customMinutes}m",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleEndOfTrack(!endOfTrackEnabled) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (endOfTrackEnabled) AuraNeonLime else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "End of current track",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Stop playback when the playing song finishes",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                AuraLiquidGlassSwitch(
                    checked = endOfTrackEnabled,
                    onCheckedChange = onToggleEndOfTrack
                )
            }

            if (isTimerActive) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSelectMinutes(null)
                        onToggleEndOfTrack(false)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Turn Off Timer",
                        color = Color(0xFFFF7A7A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
