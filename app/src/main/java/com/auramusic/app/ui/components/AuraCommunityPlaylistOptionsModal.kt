package com.auramusic.app.ui.components

import android.content.Intent
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor

/**
 * Universal Mobile 3-Dot Options Bottom Sheet Modal for Community & Search Playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCommunityPlaylistOptionsModal(
    playlist: YtmCommunityPlaylist,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    val context = LocalContext.current
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
                .padding(bottom = 36.dp)
        ) {
            // Header: Thumbnail, Title & Author/Track Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                AsyncImage(
                    model = playlist.highResArtworkUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(auraColors.surfaceCard)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        color = auraColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitle = buildString {
                        if (playlist.author.isNotBlank()) append(playlist.author)
                        if (playlist.trackCount.isNotBlank() && !playlist.trackCount.equals("Playlist", ignoreCase = true)) {
                            if (isNotEmpty()) append(" • ")
                            append(playlist.trackCount)
                        }
                    }
                    Text(
                        text = subtitle.ifBlank { "Playlist" },
                        color = auraColors.textSecondary,
                        fontSize = 12.5.sp,
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

            // 1. Like / Save to Library
            CommunityPlaylistOptionRow(
                icon = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isSaved) "Remove from Library" else "Save to Library",
                iconTint = if (isSaved) accentTint else auraColors.textPrimary,
                textColor = auraColors.textPrimary,
                onClick = {
                    onToggleSave()
                    onDismiss()
                }
            )

            // 2. Play Playlist
            CommunityPlaylistOptionRow(
                icon = Icons.Rounded.PlayArrow,
                label = "Play Playlist",
                iconTint = auraColors.textPrimary,
                textColor = auraColors.textPrimary,
                onClick = {
                    onPlayPlaylist()
                    onDismiss()
                }
            )

            // 3. View Playlist Details
            CommunityPlaylistOptionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "View Playlist Details",
                iconTint = auraColors.textPrimary,
                textColor = auraColors.textPrimary,
                onClick = {
                    onOpenDetails()
                    onDismiss()
                }
            )

            // 4. Share Playlist
            CommunityPlaylistOptionRow(
                icon = Icons.Rounded.Share,
                label = "Share Playlist",
                iconTint = auraColors.textPrimary,
                textColor = auraColors.textPrimary,
                onClick = {
                    val shareUrl = if (playlist.id.startsWith("http")) playlist.id else "https://music.youtube.com/playlist?list=${playlist.id.removePrefix("VL")}"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, "Check out '${playlist.title}' on Aura:\n$shareUrl")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Playlist"))
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun CommunityPlaylistOptionRow(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
