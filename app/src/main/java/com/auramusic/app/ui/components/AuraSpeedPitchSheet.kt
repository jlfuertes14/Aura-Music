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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Modal bottom sheet for real-time playback speed (0.25x..2.0x) and pitch (-12..+12 semitones).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuraSpeedPitchSheet(
    speed: Float,
    pitchSemitones: Int,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clampedSpeed = speed.coerceIn(0.25f, 2.0f)
    val clampedPitch = pitchSemitones.coerceIn(-12, 12)
    val isModified = abs(clampedSpeed - 1.0f) >= 0.01f || clampedPitch != 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = auraGlassSheetColor(Color(0xFF14161E)),
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
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = AuraNeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Speed & Pitch",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Fine-tune tempo and key independently",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Speed & Pitch",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Speed header + value badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PLAYBACK SPEED",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Surface(
                    shape = CircleShape,
                    color = AuraNeonLime.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2fx", clampedSpeed),
                        color = AuraNeonLime,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SPEED_PRESETS.forEach { preset ->
                    val isSelected = abs(clampedSpeed - preset) < 0.02f
                    val label = if (preset == 1.0f || preset == 2.0f || preset == 0.5f || preset == 1.5f) {
                        String.format(Locale.US, "%.1fx", preset)
                    } else {
                        String.format(Locale.US, "%.2fx", preset)
                    }
                    Surface(
                        onClick = { onSpeedChange(preset) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AuraNeonLime else Color.White.copy(alpha = 0.07f),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = if (isSelected) AuraNeonLime else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = clampedSpeed,
                onValueChange = { raw ->
                    val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.25f, 2.0f)
                    onSpeedChange(snapped)
                },
                valueRange = 0.25f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = AuraNeonLime,
                    activeTrackColor = AuraNeonLime,
                    inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.25x", color = TextSecondary, fontSize = 11.sp)
                Text("1.0x", color = TextSecondary, fontSize = 11.sp)
                Text("2.0x", color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Pitch Shift section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = AuraNeonLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PITCH SHIFT",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                val pitchLabel = when {
                    clampedPitch > 0 -> "+$clampedPitch st"
                    clampedPitch < 0 -> "$clampedPitch st"
                    else -> "0 st (Original)"
                }
                Text(
                    text = pitchLabel,
                    color = if (clampedPitch != 0) AuraNeonLime else Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { onPitchChange((clampedPitch - 1).coerceAtLeast(-12)) },
                    enabled = clampedPitch > -12,
                    modifier = Modifier
                        .size(38.dp)
                        .auraGlassSurface(shape = CircleShape, fallbackColor = Color(0xFF262A36))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease pitch by 1 semitone",
                        tint = if (clampedPitch > -12) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = clampedPitch.toFloat(),
                    onValueChange = { onPitchChange(it.roundToInt().coerceIn(-12, 12)) },
                    valueRange = -12f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = AuraNeonLime,
                        activeTrackColor = AuraNeonLime,
                        inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { onPitchChange((clampedPitch + 1).coerceAtMost(12)) },
                    enabled = clampedPitch < 12,
                    modifier = Modifier
                        .size(38.dp)
                        .auraGlassSurface(shape = CircleShape, fallbackColor = Color(0xFF262A36))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase pitch by 1 semitone",
                        tint = if (clampedPitch < 12) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-12 st", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Start)
                Text("0", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("+12 st", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.End)
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedButton(
                onClick = onReset,
                enabled = isModified,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = if (isModified) AuraNeonLime else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset to 1.0x & 0 st",
                    color = if (isModified) Color.White else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}
