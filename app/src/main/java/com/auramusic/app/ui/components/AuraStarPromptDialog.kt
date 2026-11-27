package com.auramusic.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.auramusic.app.R
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor

private const val AURA_GITHUB_REPO_URL = "https://github.com/jlfuertes14/Aura-Music"

/**
 * Clean SimpMusic-inspired GitHub star reminder dialog styled with Aura's
 * Obsidian Dark / Apple Light & Liquid Glass tokens.
 */
@Composable
fun AuraStarPromptDialog(
    onDismissLater: () -> Unit,
    onGiveStar: () -> Unit,
) {
    val auraColors = LocalAuraColors.current
    val context = LocalContext.current
    val containerBg = auraGlassSheetColor(auraColors.sheetBackground)

    // Keep "Give a star" legible on Apple Light surfaces even when Neon Lime accent is active.
    val confirmActionColor = if (!auraColors.isDark && auraColors.accent.luminance() > 0.38f) {
        auraColors.textPrimary
    } else {
        auraColors.accent
    }

    AlertDialog(
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        onDismissRequest = onDismissLater,
        shape = RoundedCornerShape(28.dp),
        containerColor = containerBg,
        icon = {
            Image(
                painter = painterResource(id = R.drawable.aura_app_icon_round),
                contentDescription = "Aura Music",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        },
        title = {
            Text(
                text = "Enjoying Aura Music?",
                color = auraColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "If you enjoy using Aura Music, consider giving it a star on GitHub to support the project.",
                    color = auraColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissLater) {
                Text(
                    text = "Later",
                    color = auraColors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onGiveStar()
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AURA_GITHUB_REPO_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
            ) {
                Text(
                    text = "Give a star",
                    color = confirmActionColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
