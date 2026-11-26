package com.auramusic.app.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
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
import androidx.palette.graphics.Palette
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.auramusic.app.MainActivity
import com.auramusic.app.R
import com.auramusic.app.core.extractor.InnerTubeClient
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.data.prefs.AuraPreferencesStore
import com.auramusic.app.domain.model.AuraTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import kotlin.math.abs

object AuraWidgetUpdater {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        updateScope.launch {
            updateAllNow(appContext)
        }
    }

    suspend fun updateAllNow(context: Context) {
        val appContext = context.applicationContext
        runCatching { AuraTurntableWidget().updateAll(appContext) }
        runCatching { AuraQuickPlayWidget().updateAll(appContext) }
    }

    suspend fun triggerImmediateAndFollowUp(context: Context, followUpDelayMs: Long = 350L) {
        updateAll(context)
        delay(followUpDelayMs)
        withTimeoutOrNull(3_000L) {
            updateAllNow(context)
        }
    }
}

internal data class WidgetThemePalette(
    val widgetBg: Color,
    val fgPrimary: Color,
    val fgSecondary: Color,
    val playButtonBg: Color,
    val playButtonIconTint: Color,
    val artSurfaceBg: Color
)

internal object AuraWidgetSupport {
    private const val FALLBACK_BG_ARGB = 0xFF1C1E26.toInt()
    private const val LIGHT_INK_ARGB = 0xFFF5F6FA.toInt()
    private const val DARK_INK_ARGB = 0xFF111216.toInt()
    private const val WIDGET_ART_PX = 220

    suspend fun resolveDisplayTrack(context: Context): AuraTrack? {
        AuraPlayerManager.currentTrack.value?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val appCtx = context.applicationContext
                val savedQueue = AuraStorageRepository(appCtx).loadPlaybackQueue()
                if (savedQueue.isEmpty()) return@runCatching null
                val prefs = AuraPreferencesStore.getInstance(appCtx)
                val savedIndex = prefs.getInt(AuraPreferencesStore.Keys.SAVED_QUEUE_INDEX, 0)
                    .coerceIn(0, savedQueue.lastIndex)
                savedQueue[savedIndex]
            }.getOrNull()
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun loadWidgetArtworkBitmap(context: Context, track: AuraTrack?): Bitmap? {
        if (track == null) return null
        val appCtx = context.applicationContext
        return withContext(Dispatchers.IO) {
            runCatching {
                val offlineUri = AuraPlayerManager.getOfflineArtworkUri(appCtx, track.videoId)
                if (!offlineUri.isNullOrBlank()) {
                    val file = File(offlineUri.removePrefix("file://"))
                    if (file.exists()) {
                        return@runCatching decodeScaledBitmap(file.readBytes(), WIDGET_ART_PX)
                    }
                }
                if (track.artworkUrl.startsWith("file://") || track.artworkUrl.startsWith("/")) {
                    val localFile = File(track.artworkUrl.removePrefix("file://"))
                    if (localFile.exists()) {
                        return@runCatching decodeScaledBitmap(localFile.readBytes(), WIDGET_ART_PX)
                    }
                }
                val url = track.highResArtworkUrl.ifBlank { track.artworkUrl }
                if (url.isBlank()) return@runCatching null

                val diskCache = runCatching { Coil.imageLoader(appCtx).diskCache }.getOrNull()
                val snapshot = diskCache?.openSnapshot(url) ?: diskCache?.openSnapshot(track.artworkUrl)
                snapshot?.use { snap ->
                    val cachedFile = snap.data.toFile()
                    if (cachedFile.exists()) {
                        decodeScaledBitmap(cachedFile.readBytes(), WIDGET_ART_PX)?.let { return@runCatching it }
                    }
                }

                val request = Request.Builder().url(url).build()
                InnerTubeClient.fastVerifyClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    decodeScaledBitmap(bytes, WIDGET_ART_PX)
                }
            }.getOrNull()
        }
    }

    suspend fun extractWidgetPalette(
        context: Context,
        track: AuraTrack?,
        artwork: Bitmap?
    ): WidgetThemePalette {
        if (track == null) {
            return buildContrastPalette(FALLBACK_BG_ARGB)
        }

        val paletteColorInt = withContext(Dispatchers.Default) {
            artwork?.let(::pickDominantAuraColor)
        } ?: withContext(Dispatchers.IO) {
            runCatching {
                AuraStorageRepository(context.applicationContext)
                    .loadOfflinePalette(track.videoId)
                    ?.primary
                    ?.toArgb()
            }.getOrNull()
        }

        if (paletteColorInt == null) {
            return buildContrastPalette(FALLBACK_BG_ARGB)
        }

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(paletteColorInt, hsl)
        // Clamp saturation and lightness for a rich, Spotify-style dynamic widget backdrop
        hsl[1] = hsl[1].coerceAtMost(0.58f)
        hsl[2] = when {
            hsl[2] < 0.16f -> 0.18f
            hsl[2] <= 0.56f -> hsl[2].coerceIn(0.22f, 0.35f)
            else -> hsl[2].coerceIn(0.58f, 0.68f)
        }
        val widgetBgArgb = ColorUtils.HSLToColor(hsl)
        return buildContrastPalette(widgetBgArgb)
    }

    private fun pickDominantAuraColor(bitmap: Bitmap): Int? {
        val palette = Palette.from(bitmap).maximumColorCount(32).generate()
        val swatches = palette.swatches
        val totalPopulation = swatches.sumOf { it.population }.coerceAtLeast(1)
        val maxPopulation = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1

        val scoredSwatches = swatches.filter { s ->
            val hsl = s.hsl
            s.population >= (maxPopulation * 0.08f) && hsl[2] in 0.10f..0.88f
        }.sortedByDescending { s ->
            val hsl = s.hsl
            val areaShare = s.population.toFloat() / totalPopulation
            val chromaBoost = 1f + (hsl[1].coerceIn(0f, 0.85f) * 1.6f)
            val midLightnessBoost = 1f - abs(hsl[2] - 0.45f) * 0.55f
            areaShare * chromaBoost * midLightnessBoost
        }

        val bestSwatch = scoredSwatches.firstOrNull()
            ?: palette.mutedSwatch
            ?: palette.vibrantSwatch
            ?: palette.dominantSwatch
        return bestSwatch?.rgb
    }

    private fun buildContrastPalette(widgetBgArgb: Int): WidgetThemePalette {
        val isBrightBg = ColorUtils.calculateLuminance(widgetBgArgb) > 0.24
        val widgetBg = Color(widgetBgArgb)
        val fgPrimary = Color(if (isBrightBg) DARK_INK_ARGB else LIGHT_INK_ARGB)
        val fgSecondary = fgPrimary.copy(alpha = 0.78f)
        val artSurfaceArgb = ColorUtils.blendARGB(
            widgetBgArgb,
            if (isBrightBg) DARK_INK_ARGB else LIGHT_INK_ARGB,
            0.08f
        )
        return WidgetThemePalette(
            widgetBg = widgetBg,
            fgPrimary = fgPrimary,
            fgSecondary = fgSecondary,
            playButtonBg = fgPrimary,
            playButtonIconTint = widgetBg,
            artSurfaceBg = Color(artSurfaceArgb)
        )
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

    /**
     * Initializes [AuraPlayerManager] and restores the saved queue if cold-started.
     * Returns true if restoration was initiated from an empty player state.
     */
    suspend fun ensurePlayerReadyAndRestored(
        context: Context,
        autoPlayOnRestore: Boolean
    ): Boolean {
        val appCtx = context.applicationContext
        withContext(Dispatchers.Main) {
            AuraPlayerManager.initialize(appCtx)
        }
        if (AuraPlayerManager.currentTrack.value != null) {
            return false
        }
        withContext(Dispatchers.Main) {
            AuraPlayerManager.restoreSavedPlaybackState(autoPlay = autoPlayOnRestore)
        }
        var waitedMs = 0L
        while (AuraPlayerManager.currentTrack.value == null && waitedMs < 600L) {
            delay(30L)
            waitedMs += 30L
        }
        return true
    }
}

class WidgetOpenPlayerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
            putExtra(MainActivity.EXTRA_WIDGET_AUTO_PLAY, true)
        }
        context.startActivity(intent)
    }
}

class WidgetPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AuraWidgetSupport.ensurePlayerReadyAndRestored(context, autoPlayOnRestore = true)
        withContext(Dispatchers.Main) {
            AuraPlayerManager.skipToPrevious()
        }
        AuraWidgetUpdater.triggerImmediateAndFollowUp(context)
    }
}

class WidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val restoredFromColdStart = AuraWidgetSupport.ensurePlayerReadyAndRestored(
            context = context,
            autoPlayOnRestore = true
        )
        if (!restoredFromColdStart) {
            withContext(Dispatchers.Main) {
                AuraPlayerManager.togglePlayPause()
            }
        }
        AuraWidgetUpdater.triggerImmediateAndFollowUp(context)
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AuraWidgetSupport.ensurePlayerReadyAndRestored(context, autoPlayOnRestore = true)
        withContext(Dispatchers.Main) {
            AuraPlayerManager.skipToNext()
        }
        AuraWidgetUpdater.triggerImmediateAndFollowUp(context)
    }
}

@Composable
internal fun WidgetArtworkBox(
    artwork: Bitmap?,
    size: Dp,
    cornerRadius: Dp,
    placeholderBg: Color
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(cornerRadius)
            .background(ColorProvider(placeholderBg))
            .clickable(actionRunCallback<WidgetOpenPlayerAction>()),
        contentAlignment = Alignment.Center
    ) {
        val artProvider = if (artwork != null) {
            ImageProvider(artwork)
        } else {
            ImageProvider(R.drawable.aura_app_icon_round)
        }
        Image(
            provider = artProvider,
            contentDescription = "Album artwork",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(cornerRadius)
        )
    }
}

@Composable
internal fun SpotifyTransportRow(
    isPlaying: Boolean,
    palette: WidgetThemePalette,
    compact: Boolean
) {
    val skipIconSize = if (compact) 22.dp else 28.dp
    val skipHitSize = if (compact) 34.dp else 38.dp
    val playSize = if (compact) 38.dp else 46.dp
    val playIconSize = if (compact) 20.dp else 26.dp
    val gap = if (compact) 10.dp else 18.dp
    val skipTint = palette.fgPrimary.copy(alpha = 0.85f)

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(skipHitSize)
                .cornerRadius(skipHitSize / 2)
                .clickable(actionRunCallback<WidgetPreviousAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(android.R.drawable.ic_media_previous),
                contentDescription = "Previous track",
                colorFilter = ColorFilter.tint(ColorProvider(skipTint)),
                modifier = GlanceModifier.size(skipIconSize)
            )
        }

        Spacer(modifier = GlanceModifier.width(gap))

        Box(
            modifier = GlanceModifier
                .size(playSize)
                .cornerRadius(playSize / 2)
                .background(ColorProvider(palette.playButtonBg))
                .clickable(actionRunCallback<WidgetPlayPauseAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                colorFilter = ColorFilter.tint(ColorProvider(palette.playButtonIconTint)),
                modifier = GlanceModifier.size(playIconSize)
            )
        }

        Spacer(modifier = GlanceModifier.width(gap))

        Box(
            modifier = GlanceModifier
                .size(skipHitSize)
                .cornerRadius(skipHitSize / 2)
                .clickable(actionRunCallback<WidgetNextAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(android.R.drawable.ic_media_next),
                contentDescription = "Next track",
                colorFilter = ColorFilter.tint(ColorProvider(skipTint)),
                modifier = GlanceModifier.size(skipIconSize)
            )
        }
    }
}

class AuraTurntableWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val track = AuraWidgetSupport.resolveDisplayTrack(context)
        val isPlaying = AuraPlayerManager.isPlaying.value
        val artwork = AuraWidgetSupport.loadWidgetArtworkBitmap(context, track)
        val palette = AuraWidgetSupport.extractWidgetPalette(context, track, artwork)

        provideContent {
            TurntableContent(
                trackTitle = track?.title?.ifBlank { "Aura Music" } ?: "Aura Music",
                artistName = track?.artist?.ifBlank { "Tap to listen" } ?: "Tap to listen",
                isPlaying = isPlaying,
                artwork = artwork,
                palette = palette
            )
        }
    }

    @Composable
    private fun TurntableContent(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        artwork: Bitmap?,
        palette: WidgetThemePalette
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.widgetBg))
                .cornerRadius(22.dp)
                .clickable(actionRunCallback<WidgetOpenPlayerAction>())
                .padding(12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionRunCallback<WidgetOpenPlayerAction>()),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                WidgetArtworkBox(
                    artwork = artwork,
                    size = 46.dp,
                    cornerRadius = 10.dp,
                    placeholderBg = palette.artSurfaceBg
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = trackTitle,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.fgPrimary),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = artistName,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.fgSecondary),
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(4.dp))

                Image(
                    provider = ImageProvider(R.drawable.ic_aura_notification),
                    contentDescription = "Aura",
                    colorFilter = ColorFilter.tint(ColorProvider(palette.fgPrimary)),
                    modifier = GlanceModifier.size(18.dp)
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            SpotifyTransportRow(
                isPlaying = isPlaying,
                palette = palette,
                compact = true
            )
        }
    }
}

class AuraTurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraTurntableWidget()
}
