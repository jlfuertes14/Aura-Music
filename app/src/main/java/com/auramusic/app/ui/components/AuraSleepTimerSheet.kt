package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor

private val SLEEP_TIMER_PRESETS = listOf(5, 10, 15, 30, 45, 60)

/**
 * Formats an active sleep timer summary for badges and settings subtitles.
 */
fun formatSleepTimerStatus(
    remainingMinutes: Int?,
    endOfTrackEnabled: Boolean
): String? = when {
    remainingMinutes != null && remainingMinutes > 0 && endOfTrackEnabled ->
        "${remainingMinutes}m left · End of track"
    remainingMinutes != null && remainingMinutes > 0 ->
        "${remainingMinutes}m left"
    endOfTrackEnabled ->
        "End of track"
    else -> null
}

/**
 * Modal bottom sheet for configuring Aura's sleep timer (presets, custom stepper,
 * end-of-track toggle, active countdown status, and turn off action).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuraSleepTimerSheet(
    remainingMinutes: Int?,
    endOfTrackEnabled: Boolean,
    onSelectMinutes: (Int?) -> Unit,
    onToggleEndOfTrack: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    containerColor: Color = auraGlassSheetColor(LocalAuraColors.current.sheetBackground)
) {
    val auraColors = LocalAuraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customMinutes by remember(remainingMinutes) {
        mutableIntStateOf((remainingMinutes ?: 30).coerceIn(5, 240))
    }
    val activeStatus = formatSleepTimerStatus(remainingMinutes, endOfTrackEnabled)
    val isTimerActive = activeStatus != null
    val destructiveColor = if (auraColors.isDark) Color(0xFFFF6961) else Color(0xFFD92D20)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
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
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .padding(bottom = 30.dp)
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
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleMedium,
                            color = auraColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeStatus != null) {
                            Text(
                                text = activeStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = auraColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Sleep Timer",
                        tint = auraColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                        color = if (isSelected) auraColors.accent else auraColors.pillInactiveBg
                    ) {
                        Text(
                            text = "${presetMin}m",
                            color = if (isSelected) auraColors.onAccent else auraColors.pillInactiveText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Custom",
                color = auraColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(auraColors.surfaceCard)
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
                            .clip(CircleShape)
                            .background(auraColors.circleButtonBg)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease 5 minutes",
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "${customMinutes}m",
                        color = auraColors.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.width(56.dp)
                    )

                    IconButton(
                        onClick = { customMinutes = (customMinutes + 5).coerceAtMost(240) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(auraColors.circleButtonBg)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase 5 minutes",
                            tint = auraColors.textPrimary,
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
                        containerColor = auraColors.accent,
                        contentColor = auraColors.onAccent
                    ),
                    elevation = null,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Set",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleEndOfTrack(!endOfTrackEnabled) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = auraColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "End of track",
                        color = auraColors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AuraLiquidGlassSwitch(
                    checked = endOfTrackEnabled,
                    onCheckedChange = onToggleEndOfTrack
                )
            }

            if (isTimerActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        onSelectMinutes(null)
                        onToggleEndOfTrack(false)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = destructiveColor.copy(alpha = if (auraColors.isDark) 0.14f else 0.10f),
                        contentColor = destructiveColor
                    ),
                    elevation = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Turn off",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
