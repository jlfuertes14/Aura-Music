package com.auramusic.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import com.auramusic.app.R
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LyricCardTheme(val label: String) {
    ALBUM_PRIMARY("Album"),
    ALBUM_SECONDARY("Accent"),
    SLATE_BLUE("Slate"),
    DEEP_OBSIDIAN("Dark"),
    WARM_CREAM("Light")
}

private const val MAX_SELECTED_LYRIC_LINES = 6
private const val CARD_WIDTH_PX = 1080
private const val CARD_MIN_HEIGHT_PX = 1080
private const val CARD_MAX_HEIGHT_PX = 1350
private val CardEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Spotify-style Lyrics Share Sheet:
 * Pick up to 6 consecutive lyric lines, pick a clean solid color swatch (auto-contrast ink),
 * preview the rounded Spotify-inspired card with Aura Music's app logo, and Share or Save PNG.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraShareLyricsSheet(
    track: AuraTrack,
    lyrics: LyricsData,
    palette: AuraPalette,
    onDismiss: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectableLines = remember(lyrics) {
        if (lyrics.syncedLines.isNotEmpty()) {
            lyrics.syncedLines.map { it.text.trim() }.filter { it.isNotEmpty() }
        } else {
            lyrics.plainLyrics.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    var selectedRange by remember(selectableLines) {
        val initialEnd = minOf(3, selectableLines.lastIndex)
        mutableStateOf<IntRange?>(if (selectableLines.isNotEmpty()) 0..initialEnd else null)
    }
    var selectedTheme by remember { mutableStateOf(LyricCardTheme.ALBUM_PRIMARY) }
    var isSharing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var artworkBitmap by remember(track.highResArtworkUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(track.highResArtworkUrl) {
        val url = track.highResArtworkUrl
        if (url.isNotBlank()) {
            artworkBitmap = AuraImageShareHelper.loadArtworkBitmap(context, url, sizePx = 320)
        }
    }

    val selectedLines = remember(selectableLines, selectedRange) {
        selectedRange?.mapNotNull { idx -> selectableLines.getOrNull(idx) }.orEmpty()
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
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
                            imageVector = Icons.Rounded.FormatQuote,
                            contentDescription = null,
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Share Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            color = auraColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedLines.size} / $MAX_SELECTED_LYRIC_LINES",
                            style = MaterialTheme.typography.bodySmall.merge(
                                TextStyle(fontFeatureSettings = "tnum")
                            ),
                            color = auraColors.textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Share Lyrics",
                        tint = auraColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SpotifyStyleLyricCardPreview(
                track = track,
                selectedLines = selectedLines,
                theme = selectedTheme,
                palette = palette
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5 clean Spotify-style color swatch dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricCardTheme.entries.forEach { theme ->
                    val isSelected = theme == selectedTheme
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { selectedTheme = theme }
                            .semantics { contentDescription = theme.label },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) auraColors.textPrimary else Color.Transparent
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) auraColors.sheetBackground else Color.Transparent
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(resolveCardBackgroundColor(theme, palette))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectableLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No lyrics available",
                        color = auraColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 196.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(selectableLines) { idx, lineText ->
                        val isLineSelected = selectedRange?.contains(idx) == true
                        val rowBg by animateColorAsState(
                            targetValue = if (isLineSelected) auraColors.surfaceCard else Color.Transparent,
                            animationSpec = tween(durationMillis = 180, easing = CardEase),
                            label = "lyricLineBg"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(rowBg)
                                .clickable {
                                    selectedRange = toggleConsecutiveSelection(
                                        current = selectedRange,
                                        tappedIndex = idx,
                                        maxLines = MAX_SELECTED_LYRIC_LINES
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lineText,
                                color = if (isLineSelected) auraColors.textPrimary else auraColors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isLineSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isLineSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected line",
                                    tint = auraColors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save Image + Share Image action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedLines.isEmpty() || isSaving || isSharing) return@Button
                        isSaving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val bmp = renderSpotifyLyricCardBitmap(
                                    context = context,
                                    track = track,
                                    lines = selectedLines,
                                    theme = selectedTheme,
                                    palette = palette,
                                    artworkBitmap = artworkBitmap
                                )
                                try {
                                    AuraImageShareHelper.saveBitmapToGallery(
                                        context = context,
                                        bitmap = bmp,
                                        filePrefix = "aura_lyrics"
                                    )
                                } finally {
                                    bmp.recycle()
                                }
                            }
                            isSaving = false
                            result.onSuccess { folder ->
                                Toast.makeText(context, "Saved to $folder", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "Unable to save: ${err.message ?: "unknown error"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = selectedLines.isNotEmpty() && !isSaving && !isSharing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = auraColors.surfaceCard,
                        contentColor = auraColors.textPrimary,
                        disabledContainerColor = auraColors.pillInactiveBg,
                        disabledContentColor = auraColors.textMuted
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = auraColors.textPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving…", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                    }
                }

                Button(
                    onClick = {
                        if (selectedLines.isEmpty() || isSharing || isSaving) return@Button
                        isSharing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val bmp = renderSpotifyLyricCardBitmap(
                                    context = context,
                                    track = track,
                                    lines = selectedLines,
                                    theme = selectedTheme,
                                    palette = palette,
                                    artworkBitmap = artworkBitmap
                                )
                                try {
                                    AuraImageShareHelper.shareBitmapAsPng(
                                        context = context,
                                        bitmap = bmp,
                                        cacheSubDir = "shared_lyrics",
                                        fileName = "aura_lyrics.png",
                                        chooserTitle = "Share Lyrics"
                                    )
                                } finally {
                                    bmp.recycle()
                                }
                            }
                            isSharing = false
                            result.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "Unable to share: ${err.message ?: "unknown error"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = selectedLines.isNotEmpty() && !isSharing && !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = auraColors.accent,
                        contentColor = auraColors.onAccent,
                        disabledContainerColor = auraColors.pillInactiveBg,
                        disabledContentColor = auraColors.textMuted
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(50.dp)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            color = auraColors.onAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sharing…", color = auraColors.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = auraColors.onAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = auraColors.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyStyleLyricCardPreview(
    track: AuraTrack,
    selectedLines: List<String>,
    theme: LyricCardTheme,
    palette: AuraPalette
) {
    val bgColor by animateColorAsState(
        targetValue = resolveCardBackgroundColor(theme, palette),
        animationSpec = tween(durationMillis = 220, easing = CardEase),
        label = "lyricCardBg"
    )
    val inkColor = remember(bgColor) { resolveCardInkColor(bgColor) }
    val subInkColor = inkColor.copy(alpha = 0.84f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Header: Rounded square artwork + Bold title & "Song · Artist"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(inkColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = inkColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    if (track.highResArtworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.highResArtworkUrl,
                            contentDescription = "${track.title} artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = inkColor,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "Song · ${track.artist}",
                        color = subInkColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bold Spotify-style editorial lyric block (flush-left, tight leading, zero clutter)
            val displayText = if (selectedLines.isEmpty()) {
                "Tap lines below"
            } else {
                selectedLines.joinToString("\n")
            }
            Text(
                text = displayText,
                color = if (selectedLines.isEmpty()) subInkColor else inkColor,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Footer: Aura App Logo + "Aura Music" wordmark (exact Spotify footer placement)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.aura_app_icon_round),
                    contentDescription = "Aura Music",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aura Music",
                    color = inkColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
            }
        }
    }
}

private fun toggleConsecutiveSelection(
    current: IntRange?,
    tappedIndex: Int,
    maxLines: Int
): IntRange {
    if (current == null) return tappedIndex..tappedIndex
    val first = current.first
    val last = current.last
    return when {
        tappedIndex in current -> {
            when {
                first == last -> tappedIndex..tappedIndex
                tappedIndex == first -> (first + 1)..last
                tappedIndex == last -> first..(last - 1)
                else -> tappedIndex..tappedIndex
            }
        }
        tappedIndex < first && (last - tappedIndex + 1) <= maxLines -> tappedIndex..last
        tappedIndex > last && (tappedIndex - first + 1) <= maxLines -> first..tappedIndex
        else -> tappedIndex..tappedIndex
    }
}

private fun resolveCardBackgroundColor(theme: LyricCardTheme, palette: AuraPalette): Color = when (theme) {
    LyricCardTheme.ALBUM_PRIMARY -> refineSpotifyCardTone(palette.primary.toArgb())
    LyricCardTheme.ALBUM_SECONDARY -> refineSpotifyCardTone(palette.secondary.toArgb())
    LyricCardTheme.SLATE_BLUE -> Color(0xFF5D7FA3)
    LyricCardTheme.DEEP_OBSIDIAN -> Color(0xFF1A1C22)
    LyricCardTheme.WARM_CREAM -> Color(0xFFF3EFE6)
}

/**
 * Normalizes an extracted album palette color into a rich, saturated Spotify lyric-card tone
 * (never washed-out gray, never neon-blinding, always crisp with white or black ink).
 */
private fun refineSpotifyCardTone(rawArgb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rawArgb, hsl)
    hsl[1] = hsl[1].coerceIn(0.28f, 0.72f)
    hsl[2] = hsl[2].coerceIn(0.24f, 0.54f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun resolveCardInkColor(bgColor: Color): Color {
    val bgArgb = bgColor.toArgb()
    val whiteContrast = ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), bgArgb)
    val darkContrast = ColorUtils.calculateContrast(0xFF111216.toInt(), bgArgb)
    return if (whiteContrast >= darkContrast) Color.White else Color(0xFF111216)
}

/**
 * Renders a high-resolution Spotify-style Lyric Card PNG (1080px wide, clean rounded card,
 * top-left artwork + title + "Song · Artist", bold tight-tracked lyric block, and bottom-left
 * Aura Music circular app logo + "Aura Music" wordmark).
 */
private fun renderSpotifyLyricCardBitmap(
    context: Context,
    track: AuraTrack,
    lines: List<String>,
    theme: LyricCardTheme,
    palette: AuraPalette,
    artworkBitmap: Bitmap?
): Bitmap {
    val bgColor = resolveCardBackgroundColor(theme, palette)
    val bgArgb = bgColor.toArgb()
    val inkColor = resolveCardInkColor(bgColor)
    val inkArgb = inkColor.toArgb()
    val subInkArgb = ColorUtils.setAlphaComponent(inkArgb, (255 * 0.84f).toInt())

    val w = CARD_WIDTH_PX.toFloat()
    val padX = 76f
    val padTop = 76f
    val padBottom = 76f
    val contentWidth = (w - padX * 2f).toInt()
    val artSize = 114f
    val headerToLyricsGap = 78f
    val lyricsToFooterGap = 86f
    val logoSize = 68f

    val bodyText = lines.joinToString("\n")
    val maxLyricHeight = CARD_MAX_HEIGHT_PX - (padTop + artSize + headerToLyricsGap + lyricsToFooterGap + logoSize + padBottom)

    var lyricSize = when {
        lines.size <= 2 && bodyText.length < 70 -> 78f
        lines.size <= 4 && bodyText.length < 150 -> 68f
        else -> 58f
    }
    var bodyLayout: StaticLayout
    while (true) {
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkArgb
            textSize = lyricSize
            letterSpacing = -0.025f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        bodyLayout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, lyricPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.12f)
            .build()
        if (bodyLayout.height <= maxLyricHeight || lyricSize <= 38f) {
            break
        }
        lyricSize -= 3f
    }

    val naturalHeight = (padTop + artSize + headerToLyricsGap + bodyLayout.height + lyricsToFooterGap + logoSize + padBottom).toInt()
    val totalHeight = naturalHeight.coerceIn(CARD_MIN_HEIGHT_PX, CARD_MAX_HEIGHT_PX)
    val h = totalHeight.toFloat()

    val bitmap = Bitmap.createBitmap(CARD_WIDTH_PX, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Clean Spotify-style rounded card background
    val cardRadius = 60f
    val cardRect = RectF(0f, 0f, w, h)
    val cardPath = Path().apply {
        addRoundRect(cardRect, cardRadius, cardRadius, Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(cardPath)
    canvas.drawColor(bgArgb)

    // 2. Top Header: Rounded square artwork + Track Title + "Song · Artist"
    val artRect = RectF(padX, padTop, padX + artSize, padTop + artSize)
    val artPath = Path().apply {
        addRoundRect(artRect, 20f, 20f, Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(artPath)
    if (artworkBitmap != null && !artworkBitmap.isRecycled) {
        val srcW = artworkBitmap.width.toFloat().coerceAtLeast(1f)
        val srcH = artworkBitmap.height.toFloat().coerceAtLeast(1f)
        val scale = maxOf(artSize / srcW, artSize / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val dst = RectF(
            artRect.left + (artSize - scaledW) / 2f,
            artRect.top + (artSize - scaledH) / 2f,
            artRect.left + (artSize + scaledW) / 2f,
            artRect.top + (artSize + scaledH) / 2f
        )
        canvas.drawBitmap(artworkBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    } else {
        canvas.drawRect(artRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(inkArgb, 38)
        })
    }
    canvas.restore()

    val metaLeft = padX + artSize + 32f
    val metaWidth = (w - padX - metaLeft).toInt().coerceAtLeast(120)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkArgb
        textSize = 40f
        letterSpacing = -0.015f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subInkArgb
        textSize = 33f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val titleLayout = StaticLayout.Builder
        .obtain(track.title, 0, track.title.length, titlePaint, metaWidth)
        .setMaxLines(1)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()
    val artistText = "Song · ${track.artist}"
    val artistLayout = StaticLayout.Builder
        .obtain(artistText, 0, artistText.length, artistPaint, metaWidth)
        .setMaxLines(1)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()
    val totalMetaH = titleLayout.height + 8f + artistLayout.height
    val metaTop = padTop + (artSize - totalMetaH) / 2f
    canvas.save()
    canvas.translate(metaLeft, metaTop)
    titleLayout.draw(canvas)
    canvas.translate(0f, titleLayout.height + 8f)
    artistLayout.draw(canvas)
    canvas.restore()

    // 3. Bold Spotify-style Lyrics Body (vertically balanced between header and footer)
    val bodyAreaTop = padTop + artSize + headerToLyricsGap
    val footerTop = h - padBottom - logoSize
    val bodyAreaBottom = footerTop - lyricsToFooterGap
    val extraSpace = (bodyAreaBottom - bodyAreaTop - bodyLayout.height).coerceAtLeast(0f)
    val bodyDrawY = bodyAreaTop + extraSpace * 0.36f

    canvas.save()
    canvas.translate(padX, bodyDrawY)
    bodyLayout.draw(canvas)
    canvas.restore()

    // 4. Bottom-left Footer: Real Aura Music circular app logo + "Aura Music" wordmark
    val logoRect = RectF(padX, footerTop, padX + logoSize, footerTop + logoSize)
    val logoBitmap = AuraImageShareHelper.decodeAuraRoundLogo(context)
    if (logoBitmap != null && !logoBitmap.isRecycled) {
        val logoClip = Path().apply {
            addOval(logoRect, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(logoClip)
        canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        logoBitmap.recycle()
    }

    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkArgb
        textSize = 40f
        letterSpacing = -0.02f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val brandFm = brandPaint.fontMetrics
    val brandBaselineY = logoRect.centerY() - (brandFm.ascent + brandFm.descent) / 2f
    canvas.drawText("Aura Music", logoRect.right + 20f, brandBaselineY, brandPaint)

    canvas.restore()
    return bitmap
}
