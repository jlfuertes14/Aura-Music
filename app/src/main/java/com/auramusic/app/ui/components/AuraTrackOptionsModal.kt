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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.TextSecondary

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14161E),
        scrimColor = Color.Black.copy(alpha = 0.75f),
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
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Keep the primary library actions together, like the SimpMusic track sheet.
            TrackOptionRow(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Remove from Liked Songs" else "Save to Liked Songs",
                iconTint = Color.White,
                onClick = onToggleFavorite
            )

            TrackOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Playlist",
                iconTint = Color.White,
                onClick = onAddToPlaylist
            )

            if (isDownloaded && onRemoveDownload != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Downloads",
                    iconTint = Color.White,
                    onClick = onRemoveDownload
                )
            } else if (onDownloadTrack != null) {
                TrackOptionRow(
                    icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
                    label = if (isDownloaded) "Remove from Downloads" else "Download Song",
                    iconTint = Color.White,
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
                onClick = onPlayNext
            )

            TrackOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Queue",
                onClick = onAddToQueue
            )

            // Remove from playlist is contextual, so keep it below the general queue actions.
            if (onRemoveFromPlaylist != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Playlist",
                    iconTint = Color.White,
                    onClick = onRemoveFromPlaylist
                )
            }

            if (onRemoveFromRecent != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Recent",
                    iconTint = Color.White,
                    onClick = onRemoveFromRecent
                )
            }

            if (onWatchVideo != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.Videocam,
                    label = "Watch Music Video",
                    iconTint = Color.White,
                    onClick = onWatchVideo
                )
            }

            if (onGoToArtist != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.Person,
                    label = "View Artist",
                    iconTint = Color.White,
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
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
