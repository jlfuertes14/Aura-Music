package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor

/**
 * Returns a legible accent tint when drawn as text or small icon directly over a light surface.
 */
internal fun auraReadableAccent(accent: Color, isDark: Boolean): Color =
    if (!isDark && accent.luminance() > 0.45f) lerp(accent, Color(0xFF1C1C1E), 0.56f) else accent

/**
 * Universal 3-Dot Track Options Bottom Sheet Modal
 * Used consistently across My Playlist Screen, Search Screen, Artist Screen, and Playlist Detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraTrackOptionsModal(
    track: AuraTrack,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onGoToArtist: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    onDownloadTrack: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
    onWatchVideo: (() -> Unit)? = null
) {
    val auraColors = LocalAuraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)

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
                .padding(bottom = 36.dp)
        ) {
            // Track Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
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

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = auraColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = auraColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = auraColors.divider,
                thickness = 0.5.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Keep the primary library actions together, like the SimpMusic track sheet.
            TrackOptionRow(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Remove from Liked Songs" else "Save to Liked Songs",
                iconTint = if (isFavorite) accentTint else auraColors.textPrimary,
                onClick = onToggleFavorite
            )

            TrackOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Playlist",
                iconTint = auraColors.textPrimary,
                onClick = onAddToPlaylist
            )

            if (isDownloaded && onRemoveDownload != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Downloads",
                    iconTint = auraColors.textPrimary,
                    onClick = onRemoveDownload
                )
            } else if (onDownloadTrack != null) {
                TrackOptionRow(
                    icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
                    label = if (isDownloaded) "Remove from Downloads" else "Download Song",
                    iconTint = auraColors.textPrimary,
                    onClick = {
                        if (isDownloaded && onRemoveDownload != null) {
                            onRemoveDownload()
                        } else {
                            onDownloadTrack()
                        }
                    }
                )
            }

            TrackOptionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Play Next",
                iconTint = auraColors.textPrimary,
                onClick = onPlayNext
            )

            TrackOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Queue",
                iconTint = auraColors.textPrimary,
                onClick = onAddToQueue
            )

            // Remove from playlist is contextual, so keep it below the general queue actions.
            if (onRemoveFromPlaylist != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Playlist",
                    iconTint = auraColors.textPrimary,
                    onClick = onRemoveFromPlaylist
                )
            }

            if (onRemoveFromRecent != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Recent",
                    iconTint = auraColors.textPrimary,
                    onClick = onRemoveFromRecent
                )
            }

            if (onWatchVideo != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.Videocam,
                    label = "Watch Music Video",
                    iconTint = auraColors.textPrimary,
                    onClick = onWatchVideo
                )
            }

            if (onGoToArtist != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.Person,
                    label = "View Artist",
                    iconTint = auraColors.textPrimary,
                    onClick = onGoToArtist
                )
            }
        }
    }
}

@Composable
fun TrackOptionRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = LocalAuraColors.current.textPrimary,
    onClick: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = auraColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
