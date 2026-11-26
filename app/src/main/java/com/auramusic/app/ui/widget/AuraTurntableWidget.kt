package com.auramusic.app.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

internal data class WidgetThemePalette(
    val widgetBg: Color,
    val fgPrimary: Color,
    val fgSecondary: Color,
    val playButtonBg: Color,
    val playButtonIconTint: Color,
    val artSurfaceBg: Color
)

internal data class WidgetSnapshot(
    val videoId: String? = null,
    val trackTitle: String = "Aura Music",
    val artistName: String = "Tap to listen",
    val isPlaying: Boolean = false,
    val artwork: Bitmap? = null,
    val palette: WidgetThemePalette = AuraWidgetSupport.defaultPalette(),
    val revision: Long = 0L
)

object AuraWidgetUpdater {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val revisionCounter = AtomicLong(1L)
    private val REVISION_KEY = longPreferencesKey("aura_widget_rev")

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
        if (!force && sig == lastPushSignature && (now - lastPushTimeMs) < 120L) {
            return
        }
        lastPushSignature = sig
        lastPushTimeMs = now
        // 1. Emit immediate RAM-backed snapshot so active Glance sessions recompose in <10ms
        val immediate = AuraWidgetSupport.publishImmediateSnapshot(
            track = track,
            isPlaying = isPlaying,
            revision = revisionCounter.incrementAndGet()
        )
        updateScope.launch {
            pushStateAndUpdateWidgets(appContext, immediate.revision)
        }

        // 2. If artwork or palette isn't in RAM yet (or track was null), resolve in background and push
        if (track == null || (vid != null && !AuraWidgetSupport.hasCachedArtwork(vid))) {
            artworkFetchJob?.cancel()
            artworkFetchJob = updateScope.launch {
                val resolvedTrack = track ?: AuraWidgetSupport.resolveDisplayTrack(appContext)
                val latestPlaying = AuraPlayerManager.isPlaying.value
                val loaded = AuraWidgetSupport.buildFullSnapshot(
                    context = appContext,
                    track = resolvedTrack,
                    isPlaying = latestPlaying,
                    revision = revisionCounter.incrementAndGet()
                )
                AuraWidgetSupport.setSnapshot(loaded)
                pushStateAndUpdateWidgets(appContext, loaded.revision)
            }
        }
    }

    internal suspend fun pushImmediateActionState(
        context: Context,
        track: AuraTrack?,
        isPlaying: Boolean
    ) {
        val appContext = context.applicationContext
        val immediate = AuraWidgetSupport.publishImmediateSnapshot(
            track = track,
            isPlaying = isPlaying,
            revision = revisionCounter.incrementAndGet()
        )
        pushStateAndUpdateWidgets(appContext, immediate.revision)

        val vid = track?.videoId
        if (track != null && vid != null && !AuraWidgetSupport.hasCachedArtwork(vid)) {
            artworkFetchJob?.cancel()
            artworkFetchJob = updateScope.launch {
                val loaded = AuraWidgetSupport.buildFullSnapshot(
                    context = appContext,
                    track = track,
                    isPlaying = AuraPlayerManager.isPlaying.value,
                    revision = revisionCounter.incrementAndGet()
                )
                AuraWidgetSupport.setSnapshot(loaded)
                pushStateAndUpdateWidgets(appContext, loaded.revision)
            }
        }
    }

    private suspend fun pushStateAndUpdateWidgets(appContext: Context, revision: Long) {
        runCatching {
            val manager = GlanceAppWidgetManager(appContext)
            val quickIds = manager.getGlanceIds(AuraQuickPlayWidget::class.java)
            val quickWidget = AuraQuickPlayWidget()
            for (gid in quickIds) {
                runCatching {
                    updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, gid) { prefs ->
                        prefs.toMutablePreferences().apply { this[REVISION_KEY] = revision }
                    }
                    quickWidget.update(appContext, gid)
                }
            }
            val turntableIds = manager.getGlanceIds(AuraTurntableWidget::class.java)
            val turntableWidget = AuraTurntableWidget()
            for (gid in turntableIds) {
                runCatching {
                    updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, gid) { prefs ->
                        prefs.toMutablePreferences().apply { this[REVISION_KEY] = revision }
                    }
                    turntableWidget.update(appContext, gid)
                }
            }
        }
    }
}

internal object AuraWidgetSupport {
    private const val FALLBACK_BG_ARGB = 0xFF222831.toInt()
    private const val PURE_WHITE_ARGB = 0xFFFFFFFF.toInt()
    private const val WIDGET_ART_PX = 280
    private const val MAX_ART_CACHE = 12
    private const val MAX_PALETTE_CACHE = 24

    private val cacheLock = Any()
    private val artworkCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_ART_CACHE
        }
    }
    private val paletteCache = object : LinkedHashMap<String, WidgetThemePalette>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WidgetThemePalette>?): Boolean {
            return size > MAX_PALETTE_CACHE
        }
    }

    private val _snapshotFlow = MutableStateFlow(WidgetSnapshot())
    val snapshotFlow: StateFlow<WidgetSnapshot> = _snapshotFlow.asStateFlow()

    fun defaultPalette(): WidgetThemePalette = buildWhiteContrastPalette(FALLBACK_BG_ARGB)

    fun hasCachedArtwork(videoId: String): Boolean = synchronized(cacheLock) {
        val bmp = artworkCache[videoId]
        bmp != null && !bmp.isRecycled
    }

    private fun getCachedArtwork(videoId: String): Bitmap? = synchronized(cacheLock) {
        artworkCache[videoId]?.takeIf { !it.isRecycled }
    }

    private fun getCachedPalette(videoId: String): WidgetThemePalette? = synchronized(cacheLock) {
        paletteCache[videoId]
    }

    private fun putCached(videoId: String, bitmap: Bitmap?, palette: WidgetThemePalette) {
        synchronized(cacheLock) {
            if (bitmap != null && !bitmap.isRecycled) {
                artworkCache[videoId] = bitmap
            }
            paletteCache[videoId] = palette
        }
    }

    fun setSnapshot(snapshot: WidgetSnapshot) {
        _snapshotFlow.value = snapshot
    }

    fun publishImmediateSnapshot(
        track: AuraTrack?,
        isPlaying: Boolean,
        revision: Long
    ): WidgetSnapshot {
        val current = _snapshotFlow.value
        val vid = track?.videoId
        val cachedArt = when {
            vid != null -> getCachedArtwork(vid) ?: if (current.videoId == vid) current.artwork else null
            else -> null
        }
        val cachedPalette = when {
            vid != null -> getCachedPalette(vid)
                ?: if (current.videoId == vid) current.palette else defaultPalette()
            else -> defaultPalette()
        }
        val next = WidgetSnapshot(
            videoId = vid,
            trackTitle = track?.title?.ifBlank { "Aura Music" } ?: current.trackTitle,
            artistName = track?.artist?.ifBlank { "Tap to listen" } ?: current.artistName,
            isPlaying = isPlaying,
            artwork = cachedArt,
            palette = cachedPalette,
            revision = revision
        )
        _snapshotFlow.value = next
        return next
    }

    suspend fun hydrateInitialSnapshot(context: Context): WidgetSnapshot {
        val track = resolveDisplayTrack(context)
        val isPlaying = AuraPlayerManager.isPlaying.value
        val current = _snapshotFlow.value
        val vid = track?.videoId
        if (vid != null && current.videoId == vid && current.artwork != null && !current.artwork.isRecycled) {
            val synced = current.copy(isPlaying = isPlaying)
            _snapshotFlow.value = synced
            return synced
        }
        val built = buildFullSnapshot(
            context = context,
            track = track,
            isPlaying = isPlaying,
            revision = current.revision + 1L
        )
        _snapshotFlow.value = built
        return built
    }

    suspend fun buildFullSnapshot(
        context: Context,
        track: AuraTrack?,
        isPlaying: Boolean,
        revision: Long
    ): WidgetSnapshot {
        val vid = track?.videoId
        val artwork = if (vid != null) {
            getCachedArtwork(vid) ?: loadWidgetArtworkBitmap(context, track)
        } else {
            null
        }
        val palette = if (vid != null) {
            getCachedPalette(vid) ?: extractWidgetPalette(context, track, artwork)
        } else {
            defaultPalette()
        }
        if (vid != null) {
            putCached(vid, artwork, palette)
        }
        return WidgetSnapshot(
            videoId = vid,
            trackTitle = track?.title?.ifBlank { "Aura Music" } ?: "Aura Music",
            artistName = track?.artist?.ifBlank { "Tap to listen" } ?: "Tap to listen",
            isPlaying = isPlaying,
            artwork = artwork,
            palette = palette,
            revision = revision
        )
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
            return defaultPalette()
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
        } ?: return defaultPalette()

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(paletteColorInt, hsl)
        // Rich Spotify-style muted backdrop ALWAYS tuned dark enough for crisp pure-white text & icons
        hsl[1] = hsl[1].coerceIn(0.14f, 0.56f)
        hsl[2] = hsl[2].coerceIn(0.20f, 0.36f)
        var bgArgb = ColorUtils.HSLToColor(hsl)
        // Guarantee WCAG >= 4.5:1 contrast against pure white (#FFFFFF) — never flip text to black
        while (ColorUtils.calculateContrast(PURE_WHITE_ARGB, bgArgb) < 4.5 && hsl[2] > 0.14f) {
            hsl[2] = (hsl[2] - 0.03f).coerceAtLeast(0.14f)
            bgArgb = ColorUtils.HSLToColor(hsl)
        }
        return buildWhiteContrastPalette(bgArgb)
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

    private fun buildWhiteContrastPalette(widgetBgArgb: Int): WidgetThemePalette {
        val widgetBg = Color(widgetBgArgb)
        val artSurfaceArgb = ColorUtils.blendARGB(widgetBgArgb, PURE_WHITE_ARGB, 0.08f)
        return WidgetThemePalette(
            widgetBg = widgetBg,
            fgPrimary = Color.White,
            fgSecondary = Color.White.copy(alpha = 0.78f),
            playButtonBg = Color.White,
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
            delay(25L)
            waitedMs += 25L
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
        val newTrack = withContext(Dispatchers.Main) {
            AuraPlayerManager.skipToPrevious()
            AuraPlayerManager.currentTrack.value
        }
        AuraWidgetUpdater.pushImmediateActionState(
            context = context,
            track = newTrack,
            isPlaying = true
        )
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
        val (track, nowPlaying) = withContext(Dispatchers.Main) {
            val playingAfter = if (restoredFromColdStart) {
                true
            } else {
                AuraPlayerManager.togglePlayPauseImmediate()
            }
            AuraPlayerManager.currentTrack.value to playingAfter
        }
        AuraWidgetUpdater.pushImmediateActionState(
            context = context,
            track = track,
            isPlaying = nowPlaying
        )
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AuraWidgetSupport.ensurePlayerReadyAndRestored(context, autoPlayOnRestore = true)
        val newTrack = withContext(Dispatchers.Main) {
            AuraPlayerManager.skipToNext()
            AuraPlayerManager.currentTrack.value
        }
        AuraWidgetUpdater.pushImmediateActionState(
            context = context,
            track = newTrack,
            isPlaying = true
        )
    }
}

@Composable
internal fun WidgetArtworkBox(
    artwork: Bitmap?,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    placeholderBg: Color
) {
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(cornerRadius)
            .background(ColorProvider(placeholderBg))
            .clickable(actionRunCallback<WidgetOpenPlayerAction>()),
        contentAlignment = Alignment.Center
    ) {
        val artProvider = if (artwork != null && !artwork.isRecycled) {
            ImageProvider(artwork)
        } else {
            ImageProvider(R.drawable.aura_app_icon_round)
        }
        Image(
            provider = artProvider,
            contentDescription = "Album artwork",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .width(width)
                .height(height)
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
    // Equal-sized visual & touch footprint for Previous, Play/Pause, and Next;
    // flush start/end alignment so Previous lines up cleanly under Song Title/Artist
    val controlSize = if (compact) 36.dp else 42.dp
    val skipIconSize = if (compact) 26.dp else 32.dp
    val playIconSize = if (compact) 20.dp else 24.dp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(controlSize),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Previous button flush left to align with Song Title & Artist
        Box(
            modifier = GlanceModifier
                .width(controlSize)
                .height(controlSize)
                .cornerRadius(controlSize / 2)
                .clickable(actionRunCallback<WidgetPreviousAction>()),
            contentAlignment = Alignment.CenterStart
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_prev),
                contentDescription = "Previous track",
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                modifier = GlanceModifier.size(skipIconSize)
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Solid White Circular Play/Pause button (same diameter as Prev/Next boxes)
        Box(
            modifier = GlanceModifier
                .size(controlSize)
                .cornerRadius(controlSize / 2)
                .background(ColorProvider(palette.playButtonBg))
                .clickable(actionRunCallback<WidgetPlayPauseAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                colorFilter = ColorFilter.tint(ColorProvider(palette.playButtonIconTint)),
                modifier = GlanceModifier.size(playIconSize)
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Next button flush right to align with Aura App Logo
        Box(
            modifier = GlanceModifier
                .width(controlSize)
                .height(controlSize)
                .cornerRadius(controlSize / 2)
                .clickable(actionRunCallback<WidgetNextAction>()),
            contentAlignment = Alignment.CenterEnd
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_next),
                contentDescription = "Next track",
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                modifier = GlanceModifier.size(skipIconSize)
            )
        }
    }
}

class AuraTurntableWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AuraWidgetSupport.hydrateInitialSnapshot(context)

        provideContent {
            // Read Glance state key so session updates also trigger recomposition
            currentState<Preferences>()
            val snapshot by AuraWidgetSupport.snapshotFlow.collectAsState()
            TurntableContent(snapshot = snapshot)
        }
    }

    @Composable
    private fun TurntableContent(snapshot: WidgetSnapshot) {
        val palette = snapshot.palette
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
                    artwork = snapshot.artwork,
                    width = 50.dp,
                    height = 50.dp,
                    cornerRadius = 11.dp,
                    placeholderBg = palette.artSurfaceBg
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = snapshot.trackTitle,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = snapshot.artistName,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.78f)),
                            fontSize = 11.5.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Image(
                    provider = ImageProvider(R.drawable.aura_app_icon_round),
                    contentDescription = "Aura Music",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(26.dp)
                        .cornerRadius(13.dp)
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            SpotifyTransportRow(
                isPlaying = snapshot.isPlaying,
                palette = palette,
                compact = true
            )
        }
    }
}

class AuraTurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraTurntableWidget()
}
