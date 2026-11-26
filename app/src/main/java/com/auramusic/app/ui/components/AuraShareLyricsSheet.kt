package com.auramusic.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class LyricCardTheme(val label: String) {
    ARTWORK_GRADIENT("Artwork"),
    DEEP_OBSIDIAN("Dark"),
    WARM_CREAM("Cream"),
    FROSTED_ACCENT("Accent")
}

private const val MAX_SELECTED_LYRIC_LINES = 6
private const val CARD_WIDTH_PX = 1080
private const val CARD_HEIGHT_PX = 1350
private val CardEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Bottom sheet allowing users to pick up to 6 consecutive lyric lines, choose one of 4
 * visual card themes, preview the card, and export a 1080x1350 PNG via [FileProvider].
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
    var selectedTheme by remember { mutableStateOf(LyricCardTheme.ARTWORK_GRADIENT) }
    var isSharing by remember { mutableStateOf(false) }
    var artworkBitmap by remember(track.highResArtworkUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(track.highResArtworkUrl) {
        val url = track.highResArtworkUrl
        if (url.isNotBlank()) {
            artworkBitmap = loadBitmapForShareCard(context, url)
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

            LyricCardPreview(
                track = track,
                selectedLines = selectedLines,
                theme = selectedTheme,
                palette = palette
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4 compact theme dots (borderless selection ring)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
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
                                        .background(themeSwatchColor(theme, palette))
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
                        .height(140.dp),
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
                        .heightIn(min = 120.dp, max = 210.dp),
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
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(rowBg)
                                .clickable {
                                    selectedRange = toggleConsecutiveSelection(
                                        current = selectedRange,
                                        tappedIndex = idx,
                                        maxLines = MAX_SELECTED_LYRIC_LINES
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
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

            Button(
                onClick = {
                    if (selectedLines.isEmpty() || isSharing) return@Button
                    isSharing = true
                    scope.launch {
                        val shareResult = withContext(Dispatchers.IO) {
                            exportAndPrepareShareFile(
                                context = context,
                                track = track,
                                lines = selectedLines,
                                theme = selectedTheme,
                                palette = palette,
                                artworkBitmap = artworkBitmap
                            )
                        }
                        isSharing = false
                        shareResult.onSuccess { outFile ->
                            launchShareIntent(context, outFile, track)
                        }.onFailure { err ->
                            Toast.makeText(
                                context,
                                "Unable to share: ${err.message ?: "unknown error"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = selectedLines.isNotEmpty() && !isSharing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = auraColors.accent,
                    contentColor = auraColors.onAccent,
                    disabledContainerColor = auraColors.pillInactiveBg,
                    disabledContentColor = auraColors.textMuted
                ),
                elevation = null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        color = auraColors.onAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Sharing…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun LyricCardPreview(
    track: AuraTrack,
    selectedLines: List<String>,
    theme: LyricCardTheme,
    palette: AuraPalette
) {
    val bgBrush = remember(theme, palette) { previewBrush(theme, palette) }
    val textColor = if (theme == LyricCardTheme.WARM_CREAM) Color(0xFF1A1815) else Color.White
    val mutedColor = textColor.copy(alpha = 0.68f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgBrush)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.primary.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
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
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = mutedColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val displayText = if (selectedLines.isEmpty()) {
                "Tap lines below"
            } else {
                selectedLines.joinToString("\n")
            }
            Text(
                text = displayText,
                color = if (selectedLines.isEmpty()) mutedColor else textColor,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURA MUSIC",
                    color = mutedColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (theme == LyricCardTheme.WARM_CREAM) Color(0xFF4D6B00) else AuraNeonLime
                        )
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

private fun themeSwatchColor(theme: LyricCardTheme, palette: AuraPalette): Color = when (theme) {
    LyricCardTheme.ARTWORK_GRADIENT -> palette.primary
    LyricCardTheme.DEEP_OBSIDIAN -> Color(0xFF161820)
    LyricCardTheme.WARM_CREAM -> Color(0xFFF5EEDC)
    LyricCardTheme.FROSTED_ACCENT -> palette.secondary
}

private fun previewBrush(theme: LyricCardTheme, palette: AuraPalette): Brush = when (theme) {
    LyricCardTheme.ARTWORK_GRADIENT -> Brush.linearGradient(
        listOf(palette.backgroundTop, palette.primary.copy(alpha = 0.78f), palette.backgroundBottom)
    )
    LyricCardTheme.DEEP_OBSIDIAN -> Brush.linearGradient(
        listOf(Color(0xFF101218), Color(0xFF1B1E28))
    )
    LyricCardTheme.WARM_CREAM -> Brush.linearGradient(
        listOf(Color(0xFFF9F3E8), Color(0xFFECE2CE))
    )
    LyricCardTheme.FROSTED_ACCENT -> Brush.linearGradient(
        listOf(palette.primary.copy(alpha = 0.85f), palette.secondary.copy(alpha = 0.78f))
    )
}

private suspend fun loadBitmapForShareCard(context: Context, url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(256)
                .build()
            val result = loader.execute(request) as? SuccessResult ?: return@withContext null
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
    }

private fun exportAndPrepareShareFile(
    context: Context,
    track: AuraTrack,
    lines: List<String>,
    theme: LyricCardTheme,
    palette: AuraPalette,
    artworkBitmap: Bitmap?
): Result<File> = runCatching {
    val cardBitmap = renderHighResLyricCardBitmap(
        track = track,
        lines = lines,
        theme = theme,
        palette = palette,
        artworkBitmap = artworkBitmap
    )
    try {
        val dir = File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
        val outFile = File(dir, "aura_lyrics.png")
        FileOutputStream(outFile).use { stream ->
            cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }
        outFile
    } finally {
        cardBitmap.recycle()
    }
}

private fun launchShareIntent(context: Context, file: File, track: AuraTrack) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Lyrics").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Could not launch share sheet: ${e.message ?: "FileProvider error"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun renderHighResLyricCardBitmap(
    track: AuraTrack,
    lines: List<String>,
    theme: LyricCardTheme,
    palette: AuraPalette,
    artworkBitmap: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(CARD_WIDTH_PX, CARD_HEIGHT_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val w = CARD_WIDTH_PX.toFloat()
    val h = CARD_HEIGHT_PX.toFloat()

    val isCream = theme == LyricCardTheme.WARM_CREAM
    val primaryTextColor = if (isCream) 0xFF1A1815.toInt() else 0xFFFFFFFF.toInt()
    val secondaryTextColor = if (isCream) 0xB31A1815.toInt() else 0xB8FFFFFF.toInt()
    val accentColor = if (isCream) 0xFF4D6B00.toInt() else AuraNeonLime.toArgb()

    // 1. Background fill
    val (bgStart, bgMid, bgEnd) = when (theme) {
        LyricCardTheme.ARTWORK_GRADIENT -> Triple(
            palette.backgroundTop.toArgb(),
            palette.primary.toArgb(),
            palette.backgroundBottom.toArgb()
        )
        LyricCardTheme.DEEP_OBSIDIAN -> Triple(
            0xFF0E1016.toInt(),
            0xFF161922.toInt(),
            0xFF0B0C10.toInt()
        )
        LyricCardTheme.WARM_CREAM -> Triple(
            0xFFFAF4E8.toInt(),
            0xFFF3EAD8.toInt(),
            0xFFE9DEC7.toInt()
        )
        LyricCardTheme.FROSTED_ACCENT -> Triple(
            palette.primary.toArgb(),
            palette.secondary.toArgb(),
            palette.backgroundBottom.toArgb()
        )
    }

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(bgStart, bgMid, bgEnd),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w, h, bgPaint)

    // 2. Inner glass panel frame
    val outerPad = 56f
    val panelRect = RectF(outerPad, outerPad, w - outerPad, h - outerPad)
    val panelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            LyricCardTheme.WARM_CREAM -> 0x66FFFFFF
            LyricCardTheme.FROSTED_ACCENT -> 0x33FFFFFF
            else -> 0x4D0D0F15
        }
    }
    canvas.drawRoundRect(panelRect, 48f, 48f, panelFillPaint)

    // 3. Header: Artwork tile (or tinted placeholder) + Track Title & Artist
    val contentLeft = 112f
    val contentRight = w - 112f
    val artSize = 136f
    val artTop = 116f
    val artRect = RectF(contentLeft, artTop, contentLeft + artSize, artTop + artSize)

    val artClipPath = android.graphics.Path().apply {
        addRoundRect(artRect, 26f, 26f, android.graphics.Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(artClipPath)
    if (artworkBitmap != null && !artworkBitmap.isRecycled) {
        canvas.drawBitmap(artworkBitmap, null, artRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                artRect.left, artRect.top, artRect.right, artRect.bottom,
                palette.primary.toArgb(), palette.secondary.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(artRect, placeholderPaint)
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt()
            textSize = 62f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("♪", artRect.centerX(), artRect.centerY() + 20f, notePaint)
    }
    canvas.restore()

    val metaLeft = contentLeft + artSize + 34f
    val metaMaxWidth = (contentRight - metaLeft).toInt().coerceAtLeast(100)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val titleLayout = StaticLayout.Builder
        .obtain(track.title, 0, track.title.length, titlePaint, metaMaxWidth)
        .setMaxLines(2)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.translate(metaLeft, artTop + 14f)
    titleLayout.draw(canvas)
    canvas.translate(0f, titleLayout.height.toFloat() + 10f)
    val artistLayout = StaticLayout.Builder
        .obtain(track.artist, 0, track.artist.length, artistPaint, metaMaxWidth)
        .setMaxLines(1)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()
    artistLayout.draw(canvas)
    canvas.restore()

    // 4. Quoted Lyric Lines body
    val lyricTop = artTop + artSize + 84f
    val lyricBottomLimit = h - 220f
    val availableHeight = (lyricBottomLimit - lyricTop).coerceAtLeast(200f)
    val bodyText = lines.joinToString("\n\n")
    val bodyWidth = (contentRight - contentLeft).toInt()

    val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 96f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    canvas.drawText("“", contentLeft - 6f, lyricTop + 46f, quoteMarkPaint)

    val startTextSize = when {
        lines.size <= 2 && bodyText.length < 90 -> 60f
        lines.size <= 4 && bodyText.length < 180 -> 50f
        else -> 42f
    }
    var currentSize = startTextSize
    var bodyLayout: StaticLayout
    while (true) {
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextColor
            textSize = currentSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        bodyLayout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, lyricPaint, bodyWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.12f)
            .build()
        if (bodyLayout.height <= availableHeight - 70f || currentSize <= 28f) {
            break
        }
        currentSize -= 3f
    }

    canvas.save()
    canvas.translate(contentLeft, lyricTop + 72f)
    bodyLayout.draw(canvas)
    canvas.restore()

    // 5. Subtle "AURA MUSIC" Footer
    val footerY = h - 124f
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isCream) 0x26000000 else 0x2EFFFFFF
        strokeWidth = 2f
    }
    canvas.drawLine(contentLeft, footerY - 46f, contentRight, footerY - 46f, dividerPaint)

    val accentPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    canvas.drawRoundRect(
        RectF(contentLeft, footerY - 14f, contentLeft + 18f, footerY + 4f),
        9f, 9f, accentPillPaint
    )

    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 26f
        letterSpacing = 0.16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("AURA MUSIC", contentLeft + 34f, footerY + 4f, footerPaint)

    return bitmap
}
