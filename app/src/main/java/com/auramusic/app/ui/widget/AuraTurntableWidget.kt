package com.auramusic.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.auramusic.app.MainActivity
import com.auramusic.app.R
import com.auramusic.app.core.extractor.InnerTubeClient
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

object AuraWidgetUpdater {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        updateScope.launch {
            runCatching { AuraTurntableWidget().updateAll(appContext) }
            runCatching { AuraQuickPlayWidget().updateAll(appContext) }
        }
    }
}

internal object AuraWidgetSupport {
    val CardBg = Color(0xFF121318)
    val CardSurface = Color(0xFF1D202A)
    val AccentLime = Color(0xFFD4F644)
    val TextPrimary = Color(0xFFF6F7FB)
    val TextSecondary = Color(0xFFB3B8C8)
    val PlayButtonBg = Color(0xFF2B2F3D)

    suspend fun loadWidgetArtworkBitmap(context: Context, track: AuraTrack?): Bitmap? {
        if (track == null) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val offlineUri = AuraPlayerManager.getOfflineArtworkUri(context, track.videoId)
                if (!offlineUri.isNullOrBlank()) {
                    val file = File(offlineUri.removePrefix("file://"))
                    if (file.exists()) {
                        return@runCatching decodeScaledBitmap(file.readBytes(), 192)
                    }
                }
                val url = track.highResArtworkUrl.ifBlank { track.artworkUrl }
                if (url.isBlank()) return@runCatching null
                val request = Request.Builder().url(url).build()
                InnerTubeClient.fastVerifyClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    decodeScaledBitmap(bytes, 192)
                }
            }.getOrNull()
        }
    }

    private fun decodeScaledBitmap(bytes: ByteArray, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetSizePx * 2 ||
            bounds.outHeight / sampleSize > targetSizePx * 2
        ) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        return if (decoded.width > targetSizePx || decoded.height > targetSizePx) {
            Bitmap.createScaledBitmap(decoded, targetSizePx, targetSizePx, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }

    suspend fun ensurePlayerReady(context: Context) {
        withContext(Dispatchers.Main) {
            AuraPlayerManager.initialize(context.applicationContext)
        }
    }
}

class WidgetPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AuraWidgetSupport.ensurePlayerReady(context)
        withContext(Dispatchers.Main) {
            AuraPlayerManager.skipToPrevious()
        }
        AuraWidgetUpdater.updateAll(context)
    }
}

class WidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AuraWidgetSupport.ensurePlayerReady(context)
        withContext(Dispatchers.Main) {
            AuraPlayerManager.togglePlayPause()
        }
        AuraWidgetUpdater.updateAll(context)
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AuraWidgetSupport.ensurePlayerReady(context)
        withContext(Dispatchers.Main) {
            AuraPlayerManager.skipToNext()
        }
        AuraWidgetUpdater.updateAll(context)
    }
}

class AuraTurntableWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val track = AuraPlayerManager.currentTrack.value
        val isPlaying = AuraPlayerManager.isPlaying.value
        val artwork = AuraWidgetSupport.loadWidgetArtworkBitmap(context, track)

        provideContent {
            TurntableContent(
                trackTitle = track?.title?.ifBlank { "Aura Music" } ?: "Aura Music",
                artistName = track?.artist?.ifBlank { "Tap to listen" } ?: "Tap to listen",
                isPlaying = isPlaying,
                artwork = artwork
            )
        }
    }

    @Composable
    private fun TurntableContent(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        artwork: Bitmap?
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(AuraWidgetSupport.CardBg))
                .cornerRadius(22.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(46.dp)
                        .cornerRadius(23.dp)
                        .background(ColorProvider(AuraWidgetSupport.CardSurface)),
                    contentAlignment = Alignment.Center
                ) {
                    val artProvider = if (artwork != null) {
                        ImageProvider(artwork)
                    } else {
                        ImageProvider(R.drawable.aura_app_icon_round)
                    }
                    Image(
                        provider = artProvider,
                        contentDescription = "Now playing artwork",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .size(46.dp)
                            .cornerRadius(23.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = trackTitle,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(AuraWidgetSupport.TextPrimary),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = artistName,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(AuraWidgetSupport.TextSecondary),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                TransportIconButton(
                    iconRes = android.R.drawable.ic_media_previous,
                    contentDescription = "Previous track",
                    tint = AuraWidgetSupport.TextPrimary,
                    backgroundColor = AuraWidgetSupport.CardSurface,
                    onClick = actionRunCallback<WidgetPreviousAction>()
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                TransportIconButton(
                    iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AuraWidgetSupport.CardBg,
                    backgroundColor = AuraWidgetSupport.AccentLime,
                    onClick = actionRunCallback<WidgetPlayPauseAction>()
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                TransportIconButton(
                    iconRes = android.R.drawable.ic_media_next,
                    contentDescription = "Next track",
                    tint = AuraWidgetSupport.TextPrimary,
                    backgroundColor = AuraWidgetSupport.CardSurface,
                    onClick = actionRunCallback<WidgetNextAction>()
                )
            }
        }
    }
}

@Composable
internal fun TransportIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    backgroundColor: Color,
    onClick: androidx.glance.action.Action
) {
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .cornerRadius(18.dp)
            .background(ColorProvider(backgroundColor))
            .clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
            modifier = GlanceModifier.size(18.dp)
        )
    }
}

class AuraTurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraTurntableWidget()
}
