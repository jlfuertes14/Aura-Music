package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.core.media.AudioOutputDevice
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor

/**
 * Modal bottom sheet for per-app audio output routing (Speaker, Bluetooth, Wired, USB/HDMI, or System Default).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraOutputSheet(
    devices: List<AudioOutputDevice>,
    selectedDeviceId: Int?,
    onSelectDevice: (AudioOutputDevice?) -> Unit,
    onDismiss: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                            imageVector = Icons.Rounded.Speaker,
                            contentDescription = null,
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Audio Output",
                        style = MaterialTheme.typography.titleMedium,
                        color = auraColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Audio Output",
                        tint = auraColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            OutputDeviceRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "System Default",
                typeLabel = null,
                isSelected = selectedDeviceId == null,
                onClick = {
                    onSelectDevice(null)
                    onDismiss()
                }
            )

            HorizontalDivider(
                color = auraColors.divider,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No other devices",
                        color = auraColors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        val isExplicitSelected = selectedDeviceId == device.id
                        val shortType = resolveShortTypeLabel(device)
                        OutputDeviceRow(
                            icon = resolveOutputIcon(device),
                            title = device.name.ifBlank { shortType },
                            typeLabel = shortType,
                            isSelected = isExplicitSelected,
                            onClick = {
                                onSelectDevice(device)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun resolveShortTypeLabel(device: AudioOutputDevice): String = when {
    device.isBluetooth -> "Bluetooth"
    device.isSpeaker -> "Speaker"
    device.typeLabel.contains("USB", ignoreCase = true) -> "USB"
    device.typeLabel.contains("HDMI", ignoreCase = true) -> "HDMI"
    device.isWired -> "Wired"
    else -> "Audio"
}

private fun resolveOutputIcon(device: AudioOutputDevice): ImageVector = when {
    device.isBluetooth -> Icons.Rounded.BluetoothAudio
    device.isWired -> Icons.Rounded.Headphones
    device.typeLabel.contains("USB", ignoreCase = true) ||
        device.typeLabel.contains("HDMI", ignoreCase = true) -> Icons.Rounded.Usb
    else -> Icons.Rounded.Speaker
}

@Composable
private fun OutputDeviceRow(
    icon: ImageVector,
    title: String,
    typeLabel: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val rowBg = if (isSelected) auraColors.surfaceCard else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = auraColors.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = auraColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!typeLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = typeLabel,
                        color = auraColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected route",
                tint = auraColors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
