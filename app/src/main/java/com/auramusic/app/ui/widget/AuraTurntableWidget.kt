package com.auramusic.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.math.abs

internal const val ACTION_WIDGET_PREV = "com.auramusic.app.widget.ACTION_PREV"
internal const val ACTION_WIDGET_PLAY_PAUSE = "com.auramusic.app.widget.ACTION_PLAY_PAUSE"
internal const val ACTION_WIDGET_NEXT = "com.auramusic.app.widget.ACTION_NEXT"

internal data class WidgetRenderState(
    val videoId: String? = null,
    val trackTitle: String = "Aura Music",
    val artistName: String = "Tap to listen",
    val isPlaying: Boolean = false,
    val roundedArtwork: Bitmap? = null,
    val bgArgb: Int = AuraWidgetSupport.FALLBACK_BG_ARGB
)

object AuraWidgetUpdater {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var artworkFetchJob: Job? = null
    @Volatile
    private var lastPushSignature: String = ""
    @Volatile
    private var lastPushTimeMs: Long = 0L

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val track = AuraPlayerManager.currentTrack.value
        val isPlaying = AuraPlayerManager.isPlaying.value
        notifyPlayerStateChanged(appContext, track, isPlaying, force = true)
    }

    fun notifyPlayerStateChanged(
        context: Context,
        track: AuraTrack?,
        isPlaying: Boolean,
        force: Boolean = false
    ) {
        val appContext = context.applicationContext
        val vid = track?.videoId
        val sig = "${vid.orEmpty()}|$isPlaying"
        val now = System.currentTimeMillis()
        if (!force && sig == lastPushSignature && (now - lastPushTimeMs) < 100L) {
            return
        }
        lastPushSignature = sig
        lastPushTimeMs = now

        // 1. Push immediate RAM-backed RemoteViews (<10ms) so Play/Pause & Next/Prev feel instant
        val immediate = AuraWidgetSupport.buildImmediateState(track, isPlaying)
        pushAllNativeWidgets(appContext, immediate)

        // 2. If artwork or palette isn't cached in RAM yet (or track was null), resolve & re-push
        if (track == null || (vid != null && !AuraWidgetSupport.hasCachedArtwork(vid))) {
            artworkFetchJob?.cancel()
            artworkFetchJob = updateScope.launch {
                val resolvedTrack = track ?: AuraWidgetSupport.resolveDisplayTrack(appContext)
                val full = AuraWidgetSupport.buildFullState(
                    context = appContext,
                    track = resolvedTrack,
                    isPlaying = AuraPlayerManager.isPlaying.value
                )
                pushAllNativeWidgets(appContext, full)
            }
        }
    }

    internal fun pushAllNativeWidgets(context: Context, state: WidgetRenderState) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)

        val quickIds = manager.getAppWidgetIds(
            ComponentName(appContext, AuraQuickPlayWidgetReceiver::class.java)
        )
        if (quickIds.isNotEmpty()) {
            val quickViews = AuraWidgetSupport.buildRemoteViews(
                context = appContext,
                layoutRes = R.layout.widget_aura_quickplay,
                receiverClass = AuraQuickPlayWidgetReceiver::class.java,
                state = state
            )
            manager.updateAppWidget(quickIds, quickViews)
        }

        val turntableIds = manager.getAppWidgetIds(
            ComponentName(appContext, AuraTurntableWidgetReceiver::class.java)
        )
        if (turntableIds.isNotEmpty()) {
            val turntableViews = AuraWidgetSupport.buildRemoteViews(
                context = appContext,
                layoutRes = R.layout.widget_aura_turntable,
                receiverClass = AuraTurntableWidgetReceiver::class.java,
                state = state
            )
            manager.updateAppWidget(turntableIds, turntableViews)
        }
    }
}

internal object AuraWidgetSupport {
    const val FALLBACK_BG_ARGB = 0xFF222831.toInt()
    private const val PURE_WHITE_ARGB = 0xFFFFFFFF.toInt()
    private const val WIDGET_ART_PX = 280
    private const val WIDGET_ART_RADIUS_PX = 42f
    private const val MAX_ART_CACHE = 12
    private const val MAX_PALETTE_CACHE = 24

    private val cacheLock = Any()
    private val artworkCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_ART_CACHE
        }
    }
    private val bgColorCache = object : LinkedHashMap<String, Int>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean {
            return size > MAX_PALETTE_CACHE
        }
    }

    @Volatile
    private var lastState: WidgetRenderState = WidgetRenderState()

    fun hasCachedArtwork(videoId: String): Boolean = synchronized(cacheLock) {
        val bmp = artworkCache[videoId]
        bmp != null && !bmp.isRecycled
    }

    private fun getCachedArtwork(videoId: String): Bitmap? = synchronized(cacheLock) {
        artworkCache[videoId]?.takeIf { !it.isRecycled }
    }

    private fun getCachedBgColor(videoId: String): Int? = synchronized(cacheLock) {
        bgColorCache[videoId]
    }

    private fun putCached(videoId: String, roundedBitmap: Bitmap?, bgArgb: Int) {
        synchronized(cacheLock) {
            if (roundedBitmap != null && !roundedBitmap.isRecycled) {
                artworkCache[videoId] = roundedBitmap
            }
            bgColorCache[videoId] = bgArgb
        }
    }

    fun buildImmediateState(track: AuraTrack?, isPlaying: Boolean): WidgetRenderState {
        val prev = lastState
        val vid = track?.videoId
        val art = when {
            vid != null -> getCachedArtwork(vid) ?: if (prev.videoId == vid) prev.roundedArtwork else null
            else -> prev.roundedArtwork
        }
        val bg = when {
            vid != null -> getCachedBgColor(vid) ?: if (prev.videoId == vid) prev.bgArgb else FALLBACK_BG_ARGB
            else -> prev.bgArgb
        }
        val state = WidgetRenderState(
            videoId = vid ?: prev.videoId,
            trackTitle = track?.title?.ifBlank { "Aura Music" } ?: prev.trackTitle,
            artistName = track?.artist?.ifBlank { "Tap to listen" } ?: prev.artistName,
            isPlaying = isPlaying,
            roundedArtwork = art,
            bgArgb = bg
        )
        lastState = state
        return state
    }

    suspend fun buildFullState(
        context: Context,
        track: AuraTrack?,
        isPlaying: Boolean
    ): WidgetRenderState {
        val vid = track?.videoId
        val rawAndRounded = if (vid != null) {
            val cached = getCachedArtwork(vid)
            if (cached != null) {
                null to cached
            } else {
                val raw = loadWidgetArtworkBitmap(context, track)
                val rounded = raw?.let { createRoundedSquareBitmap(it, WIDGET_ART_RADIUS_PX) }
                raw to rounded
            }
        } else {
            null to null
        }

        val bgArgb = if (vid != null) {
            getCachedBgColor(vid) ?: extractDarkWhiteContrastBg(context, track, rawAndRounded.first ?: rawAndRounded.second)
        } else {
            FALLBACK_BG_ARGB
        }

        if (vid != null) {
            putCached(vid, rawAndRounded.second, bgArgb)
        }

        val state = WidgetRenderState(
            videoId = vid,
            trackTitle = track?.title?.ifBlank { "Aura Music" } ?: "Aura Music",
            artistName = track?.artist?.ifBlank { "Tap to listen" } ?: "Tap to listen",
            isPlaying = isPlaying,
            roundedArtwork = rawAndRounded.second,
            bgArgb = bgArgb
        )
        lastState = state
        return state
    }

    fun buildRemoteViews(
        context: Context,
        layoutRes: Int,
        receiverClass: Class<out AppWidgetProvider>,
        state: WidgetRenderState
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)

        // 1. Dynamic artwork-extracted background tinted onto @android:id/background so
        //    Android 12+ & Xiaomi HyperOS preserve the rounded outline for expand/collapse morphs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                android.R.id.background,
                "setBackgroundTintList",
                ColorStateList.valueOf(state.bgArgb)
            )
        } else {
            views.setInt(android.R.id.background, "setBackgroundColor", state.bgArgb)
        }

        // 2. Title & Artist (pure white always)
        views.setTextViewText(R.id.widget_title, state.trackTitle)
        views.setTextViewText(R.id.widget_artist, state.artistName)

        // 3. Pre-rounded high-res artwork (fallback to actual Aura app icon)
        val bmp = state.roundedArtwork
        if (bmp != null && !bmp.isRecycled) {
            views.setImageViewBitmap(R.id.widget_artwork, bmp)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.aura_app_icon_round)
        }

        // 4. Play/Pause icon & widget-background-tinted glyph inside white circle
        views.setImageViewResource(
            R.id.widget_play_pause_icon,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setInt(R.id.widget_play_pause_icon, "setColorFilter", state.bgArgb)

        // 5. Direct Activity PendingIntent on @android:id/background (NO Glance trampoline!)
        //    This enables native HyperOS & Android 12+ Spotify-style widget expand/collapse animations.
        //    Auto-play is FALSE so tapping the widget opens the player without starting playback.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_FROM_WIDGET
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
            putExtra(MainActivity.EXTRA_WIDGET_AUTO_PLAY, false)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val requestBase = if (receiverClass == AuraQuickPlayWidgetReceiver::class.java) 1000 else 2000
        val openPi = PendingIntent.getActivity(
            context,
            requestBase + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(android.R.id.background, openPi)

        // 6. Direct Broadcast PendingIntents for instant transport controls
        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            createActionBroadcast(context, receiverClass, ACTION_WIDGET_PREV, requestBase + 11)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_play_pause,
            createActionBroadcast(context, receiverClass, ACTION_WIDGET_PLAY_PAUSE, requestBase + 12)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            createActionBroadcast(context, receiverClass, ACTION_WIDGET_NEXT, requestBase + 13)
        )

        return views
    }

    private fun createActionBroadcast(
        context: Context,
        receiverClass: Class<out AppWidgetProvider>,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, receiverClass).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun handleTransportAction(context: Context, action: String) {
        val appCtx = context.applicationContext
        val restoredFromColdStart = ensurePlayerReadyAndRestored(
            context = appCtx,
            autoPlayOnRestore = true
        )
        when (action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val (track, nowPlaying) = withContext(Dispatchers.Main) {
                    val playingAfter = if (restoredFromColdStart) {
                        true
                    } else {
                        AuraPlayerManager.togglePlayPauseImmediate()
                    }
                    AuraPlayerManager.currentTrack.value to playingAfter
                }
                AuraWidgetUpdater.notifyPlayerStateChanged(appCtx, track, nowPlaying, force = true)
            }
            ACTION_WIDGET_PREV -> {
                val track = withContext(Dispatchers.Main) {
                    AuraPlayerManager.skipToPrevious()
                    AuraPlayerManager.currentTrack.value
                }
                AuraWidgetUpdater.notifyPlayerStateChanged(appCtx, track, isPlaying = true, force = true)
            }
            ACTION_WIDGET_NEXT -> {
                val track = withContext(Dispatchers.Main) {
                    AuraPlayerManager.skipToNext()
                    AuraPlayerManager.currentTrack.value
                }
                AuraWidgetUpdater.notifyPlayerStateChanged(appCtx, track, isPlaying = true, force = true)
            }
        }
    }

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
    private suspend fun loadWidgetArtworkBitmap(context: Context, track: AuraTrack?): Bitmap? {
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

    private suspend fun extractDarkWhiteContrastBg(
        context: Context,
        track: AuraTrack?,
        artwork: Bitmap?
    ): Int {
        if (track == null) return FALLBACK_BG_ARGB

        val paletteColorInt = withContext(Dispatchers.Default) {
            artwork?.let(::pickDominantAuraColor)
        } ?: withContext(Dispatchers.IO) {
            runCatching {
                AuraStorageRepository(context.applicationContext)
                    .loadOfflinePalette(track.videoId)
                    ?.primary
                    ?.toArgb()
            }.getOrNull()
        } ?: return FALLBACK_BG_ARGB

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(paletteColorInt, hsl)
        hsl[1] = hsl[1].coerceIn(0.14f, 0.56f)
        hsl[2] = hsl[2].coerceIn(0.20f, 0.36f)
        var bgArgb = ColorUtils.HSLToColor(hsl)
        while (ColorUtils.calculateContrast(PURE_WHITE_ARGB, bgArgb) < 4.5 && hsl[2] > 0.14f) {
            hsl[2] = (hsl[2] - 0.03f).coerceAtLeast(0.14f)
            bgArgb = ColorUtils.HSLToColor(hsl)
        }
        return bgArgb
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
            val midLightnessBoost = 1f - abs(hsl[2] - 0.42f) * 0.55f
            areaShare * chromaBoost * midLightnessBoost
        }

        val bestSwatch = scoredSwatches.firstOrNull()
            ?: palette.mutedSwatch
            ?: palette.vibrantSwatch
            ?: palette.dominantSwatch
        return bestSwatch?.rgb
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

    private fun createRoundedSquareBitmap(source: Bitmap, radiusPx: Float): Bitmap {
        val size = WIDGET_ART_PX
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = Path().apply {
            addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radiusPx, radiusPx, Path.Direction.CW)
        }
        canvas.clipPath(path)

        // Center-crop source into square output
        val srcW = source.width.toFloat().coerceAtLeast(1f)
        val srcH = source.height.toFloat().coerceAtLeast(1f)
        val scale = maxOf(size / srcW, size / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val left = (size - scaledW) / 2f
        val top = (size - scaledH) / 2f
        val dst = RectF(left, top, left + scaledW, top + scaledH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, dst, paint)
        return output
    }

    private suspend fun ensurePlayerReadyAndRestored(
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
            delay(25L)
            waitedMs += 25L
        }
        return true
    }
}

abstract class BaseAuraWidgetProvider : AppWidgetProvider() {
    companion object {
        private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        AuraWidgetUpdater.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_WIDGET_PREV ||
            action == ACTION_WIDGET_PLAY_PAUSE ||
            action == ACTION_WIDGET_NEXT
        ) {
            val pendingResult = goAsync()
            actionScope.launch {
                try {
                    AuraWidgetSupport.handleTransportAction(context, action)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class AuraTurntableWidgetReceiver : BaseAuraWidgetProvider()
