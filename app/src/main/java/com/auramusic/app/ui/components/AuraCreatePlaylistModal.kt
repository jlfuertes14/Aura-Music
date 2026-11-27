package com.auramusic.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor

/**
 * Mobile Sheet Modal for choosing between "Create a playlist" and "Import Playlist from Spotify".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCreateOrImportModal(
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportSpotify: () -> Unit
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
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "New Playlist",
                color = auraColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            HorizontalDivider(
                color = auraColors.divider,
                thickness = 0.5.dp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            TrackOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Create a playlist",
                iconTint = auraColors.textPrimary,
                onClick = onCreatePlaylist
            )

            TrackOptionRow(
                icon = Icons.Rounded.Link,
                label = "Import Playlist from Spotify",
                iconTint = auraColors.textPrimary,
                onClick = onImportSpotify
            )
        }
    }
}

/**
 * Mobile Sheet Modal for creating a brand new playlist with Cover Image, Title, and Description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCreatePlaylistModal(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, coverUri: String) -> Unit
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pickedCoverUri by remember { mutableStateOf<String?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedCoverUri = uri.toString()
        }
    }

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
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create Playlist",
                    color = auraColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = auraColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Row: Left = Cover Image Box, Right = Stacked Title & Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left: Cover Image Box with Tap to Pick
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(auraColors.surfaceCard)
                        .clickable { mediaPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (pickedCoverUri != null) {
                        AsyncImage(
                            model = pickedCoverUri,
                            contentDescription = "Cover Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Pick Cover",
                                tint = auraColors.textSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Add Cover",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = auraColors.textSecondary
                            )
                        }
                    }
                }

                // Right: Stacked Title & Description Fields (Borderless)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = {
                            Text(
                                text = "Playlist Title",
                                color = auraColors.textMuted,
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = auraColors.surfaceCard,
                            unfocusedContainerColor = auraColors.pillInactiveBg,
                            focusedTextColor = auraColors.textPrimary,
                            unfocusedTextColor = auraColors.textPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = accentTint
                        )
                    )

                    TextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = {
                            Text(
                                text = "Description",
                                color = auraColors.textMuted,
                                fontSize = 13.sp
                            )
                        },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = auraColors.surfaceCard,
                            unfocusedContainerColor = auraColors.pillInactiveBg,
                            focusedTextColor = auraColors.textPrimary,
                            unfocusedTextColor = auraColors.textPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = accentTint
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Action: Create Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title.trim(), description.trim(), pickedCoverUri ?: "")
                    }
                },
                enabled = title.isNotBlank(),
                elevation = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = auraColors.accent,
                    contentColor = auraColors.onAccent,
                    disabledContainerColor = auraColors.pillInactiveBg,
                    disabledContentColor = auraColors.textMuted
                )
            ) {
                Text(
                    text = "Create Playlist",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
