package com.auramusic.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.NowPlayingStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.auraPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_preferences"
)

class AuraPreferencesStore private constructor(context: Context) {

    object Keys {
        val MIGRATED_TO_ROOM_V1 = booleanPreferencesKey("migrated_to_room_v1")
        val APP_THEME_STYLE = stringPreferencesKey("setting_app_theme_style")
        val LIQUID_GLASS_ENABLED = booleanPreferencesKey("setting_liquid_glass_enabled")
        val NOW_PLAYING_STYLE = stringPreferencesKey("now_playing_style")
        val LYRICS_STYLE = stringPreferencesKey("setting_lyrics_style")
        val AUDIO_QUALITY = stringPreferencesKey("setting_audio_quality")
        val VIDEO_QUALITY = stringPreferencesKey("setting_video_quality")
        val ROMANIZATION_LANGUAGES = stringPreferencesKey("setting_romanization_languages")
        val NORMALIZE_VOLUME = booleanPreferencesKey("setting_normalize_volume")
        val SKIP_SILENCE = booleanPreferencesKey("setting_skip_silence")
        val CROSSFADE_SEC = intPreferencesKey("setting_crossfade_sec")
        val MAX_CACHE_MB = intPreferencesKey("setting_max_cache_mb")
        val EQ_BANDS = stringPreferencesKey("setting_eq_bands")
        val EQ_PREAMP = floatPreferencesKey("setting_eq_preamp")
        val EQ_PRESET = stringPreferencesKey("setting_eq_preset")
        val EQ_ENABLED = booleanPreferencesKey("setting_eq_enabled")
        val CHART_COUNTRY = stringPreferencesKey("setting_chart_country")
        val AUTO_DOWNLOAD_LIKED = booleanPreferencesKey("setting_auto_download_liked")
        val PLAY_VIDEO_FOR_VIDEO_TRACK = booleanPreferencesKey("setting_play_video_for_video_track")
        val RADIO_AUDIO_ONLY = booleanPreferencesKey("setting_radio_audio_only")
        val AUDIO_REVERB_PRESET = intPreferencesKey("setting_audio_reverb_preset")
        val SAVE_PLAYBACK_STATE = booleanPreferencesKey("setting_save_playback_state")
        val SAVED_PLAYBACK_MODE = stringPreferencesKey("setting_saved_playback_mode")
        val KILL_SERVICE_ON_EXIT = booleanPreferencesKey("setting_kill_service_on_exit")
        val SPONSORBLOCK_ENABLED = booleanPreferencesKey("setting_sponsorblock_enabled")
        val SPONSORBLOCK_CATEGORIES = stringPreferencesKey("setting_sponsorblock_categories")
        val MAIN_LYRICS_PROVIDER = stringPreferencesKey("setting_main_lyrics_provider")
        val DEFAULT_LYRICS_OFFSET_MS = intPreferencesKey("setting_default_lyrics_offset_ms")
        val RECORD_LISTENING_HISTORY = booleanPreferencesKey("setting_record_listening_history")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("setting_auto_check_updates")
        val RECOMMENDATION_SIGNALS = stringPreferencesKey("recommendation_signals")
        val EXPLORE_COVER_CACHE_JSON = stringPreferencesKey("explore_cover_cache_json")
        val SPOTIFY_RESOLVED_CACHE_JSON = stringPreferencesKey("spotify_resolved_cache_json")
        val SAVED_QUEUE_INDEX = intPreferencesKey("saved_queue_index")
        val SAVED_QUEUE_POSITION_MS = longPreferencesKey("saved_queue_position_ms")
        val HAS_STARRED_REPO = booleanPreferencesKey("has_starred_repo")
        val STAR_PROMPT_OPEN_COUNT = intPreferencesKey("star_prompt_open_count")
    }

    private val dataStore = context.applicationContext.auraPreferencesDataStore
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val snapshotLock = Any()
    private val snapshot = HashMap<String, Any?>()

    @Volatile
    private var hydrated = false

    private val _appThemeStyle = MutableStateFlow(AppThemeStyle.LIQUID_GLASS)
    val appThemeStyle: StateFlow<AppThemeStyle> = _appThemeStyle.asStateFlow()

    private val _audioQuality = MutableStateFlow("Auto")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _videoQuality = MutableStateFlow("Auto (1080p)")
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(true)
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _crossfadeSec = MutableStateFlow(0)
    val crossfadeSec: StateFlow<Int> = _crossfadeSec.asStateFlow()

    private val _maxCacheMb = MutableStateFlow(1024)
    val maxCacheMb: StateFlow<Int> = _maxCacheMb.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _chartCountry = MutableStateFlow("US")
    val chartCountry: StateFlow<String> = _chartCountry.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(true)
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _mainLyricsProvider = MutableStateFlow("SimpMusic")
    val mainLyricsProvider: StateFlow<String> = _mainLyricsProvider.asStateFlow()

    private val _defaultLyricsOffsetMs = MutableStateFlow(0)
    val defaultLyricsOffsetMs: StateFlow<Int> = _defaultLyricsOffsetMs.asStateFlow()

    private val _recordListeningHistory = MutableStateFlow(true)
    val recordListeningHistory: StateFlow<Boolean> = _recordListeningHistory.asStateFlow()

    init {
        ioScope.launch {
            ensureHydratedSuspend()
        }
    }

    fun ensureHydrated() {
        if (hydrated) return
        runBlocking(Dispatchers.IO) {
            ensureHydratedSuspend()
        }
    }

    private suspend fun ensureHydratedSuspend() {
        if (hydrated) return
        val prefs = runCatching { dataStore.data.first() }.getOrNull()
        synchronized(snapshotLock) {
            if (!hydrated) {
                prefs?.asMap()?.forEach { (key, value) ->
                    snapshot[key.name] = value
                }
                hydrated = true
                refreshFlowsLocked()
            }
        }
    }

    fun isMigratedToRoomV1(): Boolean = getBoolean(Keys.MIGRATED_TO_ROOM_V1, false)

    fun markMigratedToRoomV1() {
        putBoolean(Keys.MIGRATED_TO_ROOM_V1, true)
    }

    fun contains(key: Preferences.Key<*>): Boolean {
        ensureHydrated()
        synchronized(snapshotLock) {
            return snapshot.containsKey(key.name)
        }
    }

    fun getString(key: Preferences.Key<String>, default: String? = null): String? {
        ensureHydrated()
        synchronized(snapshotLock) {
            return (snapshot[key.name] as? String) ?: default
        }
    }

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean): Boolean {
        ensureHydrated()
        synchronized(snapshotLock) {
            return (snapshot[key.name] as? Boolean) ?: default
        }
    }

    fun getInt(key: Preferences.Key<Int>, default: Int): Int {
        ensureHydrated()
        synchronized(snapshotLock) {
            return (snapshot[key.name] as? Int) ?: default
        }
    }

    fun getLong(key: Preferences.Key<Long>, default: Long): Long {
        ensureHydrated()
        synchronized(snapshotLock) {
            return (snapshot[key.name] as? Long) ?: default
        }
    }

    fun getFloat(key: Preferences.Key<Float>, default: Float): Float {
        ensureHydrated()
        synchronized(snapshotLock) {
            return (snapshot[key.name] as? Float) ?: default
        }
    }

    fun putString(key: Preferences.Key<String>, value: String) {
        updateSingle(key, value)
    }

    fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        updateSingle(key, value)
    }

    fun putInt(key: Preferences.Key<Int>, value: Int) {
        updateSingle(key, value)
    }

    fun putLong(key: Preferences.Key<Long>, value: Long) {
        updateSingle(key, value)
    }

    fun putFloat(key: Preferences.Key<Float>, value: Float) {
        updateSingle(key, value)
    }

    fun remove(key: Preferences.Key<*>) {
        ensureHydrated()
        synchronized(snapshotLock) {
            snapshot.remove(key.name)
            refreshFlowsLocked()
        }
        persistOps(listOf { it.remove(key) })
    }

    fun editBatch(updates: Map<Preferences.Key<*>, Any?>) {
        if (updates.isEmpty()) return
        ensureHydrated()
        val ops = mutableListOf<(MutablePreferences) -> Unit>()
        synchronized(snapshotLock) {
            updates.forEach { (key, value) ->
                if (value == null) {
                    snapshot.remove(key.name)
                    ops.add { it.remove(key) }
                } else {
                    snapshot[key.name] = value
                    @Suppress("UNCHECKED_CAST")
                    val typedKey = key as Preferences.Key<Any>
                    ops.add { it[typedKey] = value }
                }
            }
            refreshFlowsLocked()
        }
        persistOps(ops)
    }

    suspend fun flush() {
        writeMutex.withLock { }
    }

    private fun <T> updateSingle(key: Preferences.Key<T>, value: T) {
        ensureHydrated()
        synchronized(snapshotLock) {
            snapshot[key.name] = value
            refreshFlowsLocked()
        }
        persistOps(listOf { it[key] = value })
    }

    private fun persistOps(ops: List<(MutablePreferences) -> Unit>) {
        ioScope.launch {
            writeMutex.withLock {
                runCatching {
                    dataStore.edit { prefs ->
                        ops.forEach { op -> op(prefs) }
                    }
                }
            }
        }
    }

    private fun refreshFlowsLocked() {
        _appThemeStyle.value = resolveAppThemeStyleLocked()
        _audioQuality.value = (snapshot[Keys.AUDIO_QUALITY.name] as? String) ?: "Auto"
        _videoQuality.value = (snapshot[Keys.VIDEO_QUALITY.name] as? String) ?: "Auto (1080p)"
        _normalizeVolume.value = (snapshot[Keys.NORMALIZE_VOLUME.name] as? Boolean) ?: true
        _skipSilence.value = (snapshot[Keys.SKIP_SILENCE.name] as? Boolean) ?: false
        _crossfadeSec.value = (snapshot[Keys.CROSSFADE_SEC.name] as? Int) ?: 0
        _maxCacheMb.value = (snapshot[Keys.MAX_CACHE_MB.name] as? Int) ?: 1024
        _equalizerEnabled.value = (snapshot[Keys.EQ_ENABLED.name] as? Boolean) ?: false
        _chartCountry.value = (snapshot[Keys.CHART_COUNTRY.name] as? String) ?: "US"
        _sponsorBlockEnabled.value = (snapshot[Keys.SPONSORBLOCK_ENABLED.name] as? Boolean) ?: true
        _mainLyricsProvider.value = (snapshot[Keys.MAIN_LYRICS_PROVIDER.name] as? String) ?: "SimpMusic"
        _defaultLyricsOffsetMs.value = (snapshot[Keys.DEFAULT_LYRICS_OFFSET_MS.name] as? Int) ?: 0
        _recordListeningHistory.value = (snapshot[Keys.RECORD_LISTENING_HISTORY.name] as? Boolean) ?: true
    }

    private fun resolveAppThemeStyleLocked(): AppThemeStyle {
        val saved = snapshot[Keys.APP_THEME_STYLE.name] as? String
        if (saved != null) {
            return AppThemeStyle.entries.firstOrNull { it.name == saved } ?: AppThemeStyle.LIQUID_GLASS
        }
        val legacyStyle = snapshot[Keys.NOW_PLAYING_STYLE.name] as? String
        val legacyGlass = snapshot[Keys.LIQUID_GLASS_ENABLED.name] as? Boolean
        return when {
            legacyStyle == NowPlayingStyle.SPOTIFY.name && legacyGlass == false -> AppThemeStyle.DEFAULT
            else -> AppThemeStyle.LIQUID_GLASS
        }
    }

    companion object {
        @Volatile
        private var instance: AuraPreferencesStore? = null

        fun getInstance(context: Context): AuraPreferencesStore {
            return instance ?: synchronized(this) {
                instance ?: AuraPreferencesStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
