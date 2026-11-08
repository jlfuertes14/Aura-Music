package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.ui.theme.TextSecondary

/**
 * Universal Mobile 3-Dot Options Bottom Sheet Modal for Playlists.
 * Used on Playlist Tiles (long-press) and Playlist Detail top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraPlaylistOptionsModal(
    playlist: AuraPlaylist,
    onDismiss: () -> Unit,
    onDownloadAll: () -> Unit,
    onChangeCover: () -> Unit,
    onResetCover: (() -> Unit)? = null,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFindInPlaylist: (() -> Unit)? = null
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
            // Header: Playlist Thumbnail, Title & Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                AuraPlaylistCover(
                    coverUrl = playlist.coverUrl,
                    tracks = playlist.tracks,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${playlist.tracks.size} tracks",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 0. Find in Playlist (if enabled)
            if (onFindInPlaylist != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.Search,
                    label = "Find in playlist",
                    iconTint = Color.White,
                    onClick = {
                        onDismiss()
                        onFindInPlaylist()
                    }
                )
            }

            // 1. Download All Songs
            TrackOptionRow(
                icon = Icons.Rounded.Download,
                label = "Download All Songs (${playlist.tracks.size})",
                iconTint = Color.White,
                onClick = onDownloadAll
            )

            // 2. Change Cover Art
            TrackOptionRow(
                icon = Icons.Rounded.Image,
                label = "Change Cover Art",
                iconTint = Color.White,
                onClick = onChangeCover
            )

            // 3. Reset to Spotify Collage
            if (playlist.coverUrl.isNotBlank() && onResetCover != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.RestartAlt,
                    label = "Reset to Spotify Collage",
                    iconTint = Color.White,
                    onClick = onResetCover
                )
            }

            // 4. Rename Playlist
            TrackOptionRow(
                icon = Icons.Rounded.Edit,
                label = "Rename Playlist",
                iconTint = Color.White,
                onClick = onRename
            )

            // 5. Delete Playlist
            TrackOptionRow(
                icon = Icons.Rounded.DeleteOutline,
                label = "Delete Playlist",
                iconTint = Color.White,
                onClick = onDelete
            )
        }
    }
}
