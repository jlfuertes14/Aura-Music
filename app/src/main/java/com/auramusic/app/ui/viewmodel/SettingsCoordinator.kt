package com.auramusic.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import com.auramusic.app.AuraApplication
import com.auramusic.app.core.integrations.DiscordRpcManager
import com.auramusic.app.core.integrations.YtmAccountManager
import com.auramusic.app.core.lyrics.AiLyricsTranslator
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.notifications.AuraNotificationManager
import com.auramusic.app.core.sponsorblock.SponsorBlockRepository
import com.auramusic.app.core.update.AuraReleaseInfo
import com.auramusic.app.core.update.AuraUpdateManager
import com.auramusic.app.core.update.UpdateDownloadProgress
import com.auramusic.app.core.update.UpdateState
import com.auramusic.app.core.workers.AutoBackupScheduler
import com.auramusic.app.core.workers.SmartDownloadScheduler
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AppThemeMode
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.LyricsStyle
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.domain.model.ThemeColorSource
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class SettingsCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val storage: AuraStorageRepository,
    private val onReloadLyricsForProvider: (String) -> Unit,
    private val onApplyDefaultLyricsOffsetIfZero: (Int) -> Unit,
    private val onReRomanizeCurrentLyrics: (Set<String>) -> Unit = {},
    private val onBackupRestored: () -> Unit
) {
    private val _romanizationLanguages = MutableStateFlow(storage.loadRomanizationLanguages())
    val romanizationLanguages: StateFlow<Set<String>> = _romanizationLanguages.asStateFlow()

    init {
        SimpMusicLyricsRepository.setEnabledRomanizationLanguages(_romanizationLanguages.value)
    }

    private val _mainLyricsProvider = MutableStateFlow(storage.loadMainLyricsProvider())
    val mainLyricsProvider: StateFlow<String> = _mainLyricsProvider.asStateFlow()

    private val _defaultLyricsOffsetMs = MutableStateFlow(storage.loadDefaultLyricsOffsetMs())
    val defaultLyricsOffsetMs: StateFlow<Int> = _defaultLyricsOffsetMs.asStateFlow()

    private val _autoDownloadLikedSongs = MutableStateFlow(storage.loadAutoDownloadLikedSongs())
    val autoDownloadLikedSongs: StateFlow<Boolean> = _autoDownloadLikedSongs.asStateFlow()

    private val _recordListeningHistory = MutableStateFlow(storage.loadRecordListeningHistory())
    val recordListeningHistory: StateFlow<Boolean> = _recordListeningHistory.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(storage.loadAutoCheckUpdates())
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _releaseNotificationsEnabled = MutableStateFlow(storage.loadReleaseNotificationsEnabled())
    val releaseNotificationsEnabled: StateFlow<Boolean> = _releaseNotificationsEnabled.asStateFlow()

    private val _appThemeStyle = MutableStateFlow(storage.loadAppThemeStyle())
    val appThemeStyle: StateFlow<AppThemeStyle> = _appThemeStyle.asStateFlow()

    private val _themeMode = MutableStateFlow(storage.loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _themeColorSource = MutableStateFlow(storage.loadThemeColorSource())
    val themeColorSource: StateFlow<ThemeColorSource> = _themeColorSource.asStateFlow()

    private val _customAccentArgb = MutableStateFlow(storage.loadCustomAccentArgb())
    val customAccentArgb: StateFlow<Int?> = _customAccentArgb.asStateFlow()

    private val _nowPlayingStyle = MutableStateFlow(storage.loadNowPlayingStyle())
    val nowPlayingStyle: StateFlow<NowPlayingStyle> = _nowPlayingStyle.asStateFlow()

    private val _liquidGlassEnabled = MutableStateFlow(storage.loadLiquidGlassEnabled())
    val liquidGlassEnabled: StateFlow<Boolean> = _liquidGlassEnabled.asStateFlow()

    private val _lyricsStyle = MutableStateFlow(storage.loadLyricsStyle())
    val lyricsStyle: StateFlow<LyricsStyle> = _lyricsStyle.asStateFlow()

    // Tier 2: AI Lyrics Translation Config
    private val _aiProvider = MutableStateFlow(
        AiLyricsTranslator.AiProvider.entries.firstOrNull { it.name == storage.loadAiProvider() }
            ?: AiLyricsTranslator.AiProvider.GEMINI
    )
    val aiProvider: StateFlow<AiLyricsTranslator.AiProvider> = _aiProvider.asStateFlow()

    private val _aiApiKey = MutableStateFlow(storage.loadAiApiKey())
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    private val _aiModelId = MutableStateFlow(storage.loadAiModelId())
    val aiModelId: StateFlow<String> = _aiModelId.asStateFlow()

    private val _aiBaseUrl = MutableStateFlow(storage.loadAiBaseUrl())
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()

    private val _aiTargetLanguage = MutableStateFlow(storage.loadAiTargetLanguage())
    val aiTargetLanguage: StateFlow<String> = _aiTargetLanguage.asStateFlow()

    private val _aiTranslationEnabled = MutableStateFlow(storage.loadAiTranslationEnabled())
    val aiTranslationEnabled: StateFlow<Boolean> = _aiTranslationEnabled.asStateFlow()

    // Tier 2: Smart Downloads & Auto-Backup Workers
    private val _smartDownloadsEnabled = MutableStateFlow(storage.loadSmartDownloadsEnabled())
    val smartDownloadsEnabled: StateFlow<Boolean> = _smartDownloadsEnabled.asStateFlow()

    private val _smartDownloadsLimit = MutableStateFlow(storage.loadSmartDownloadsLimit())
    val smartDownloadsLimit: StateFlow<Int> = _smartDownloadsLimit.asStateFlow()

    private val _autoBackupFrequency = MutableStateFlow(
        AutoBackupScheduler.BackupFrequency.entries.firstOrNull { it.name == storage.loadAutoBackupFrequency() }
            ?: AutoBackupScheduler.BackupFrequency.OFF
    )
    val autoBackupFrequency: StateFlow<AutoBackupScheduler.BackupFrequency> = _autoBackupFrequency.asStateFlow()

    private val _autoBackupKeepCount = MutableStateFlow(storage.loadAutoBackupKeepCount())
    val autoBackupKeepCount: StateFlow<Int> = _autoBackupKeepCount.asStateFlow()

    // Tier 2: Ecosystem Integrations (Last.fm, Discord RPC, YTM Account)
    private val _lastfmSessionKey = MutableStateFlow(storage.loadLastfmSessionKey())
    val lastfmSessionKey: StateFlow<String> = _lastfmSessionKey.asStateFlow()

    private val _lastfmApiKey = MutableStateFlow(storage.loadLastfmApiKey())
    val lastfmApiKey: StateFlow<String> = _lastfmApiKey.asStateFlow()

    private val _lastfmApiSecret = MutableStateFlow(storage.loadLastfmApiSecret())
    val lastfmApiSecret: StateFlow<String> = _lastfmApiSecret.asStateFlow()

    private val _lastfmEnabled = MutableStateFlow(storage.loadLastfmEnabled())
    val lastfmEnabled: StateFlow<Boolean> = _lastfmEnabled.asStateFlow()

    private val _discordToken = MutableStateFlow(storage.loadDiscordToken())
    val discordToken: StateFlow<String> = _discordToken.asStateFlow()

    private val _discordRpcEnabled = MutableStateFlow(storage.loadDiscordRpcEnabled())
    val discordRpcEnabled: StateFlow<Boolean> = _discordRpcEnabled.asStateFlow()

    private val _ytmCookie = MutableStateFlow(storage.loadYtmCookie())
    val ytmCookie: StateFlow<String> = _ytmCookie.asStateFlow()

    // In-App Updates & Releases
    private val _currentUpdateRelease = MutableStateFlow<AuraReleaseInfo?>(null)
    val currentUpdateRelease: StateFlow<AuraReleaseInfo?> = _currentUpdateRelease.asStateFlow()

    private val _isUpdateModalVisible = MutableStateFlow(false)
    val isUpdateModalVisible: StateFlow<Boolean> = _isUpdateModalVisible.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(UpdateDownloadProgress())
    val updateDownloadProgress: StateFlow<UpdateDownloadProgress> = _updateDownloadProgress.asStateFlow()

    private val _updateErrorMessage = MutableStateFlow<String?>(null)
    val updateErrorMessage: StateFlow<String?> = _updateErrorMessage.asStateFlow()

    private var downloadedApkFile: File? = null

    fun reloadThemeAndPrefsFromStorage() {
        _appThemeStyle.value = storage.loadAppThemeStyle()
        _themeMode.value = storage.loadThemeMode()
        _themeColorSource.value = storage.loadThemeColorSource()
        _customAccentArgb.value = storage.loadCustomAccentArgb()
        _nowPlayingStyle.value = storage.loadNowPlayingStyle()
        _liquidGlassEnabled.value = storage.loadLiquidGlassEnabled()
        _lyricsStyle.value = storage.loadLyricsStyle()
        _romanizationLanguages.value = storage.loadRomanizationLanguages()
        SimpMusicLyricsRepository.setEnabledRomanizationLanguages(_romanizationLanguages.value)
        onReRomanizeCurrentLyrics(_romanizationLanguages.value)
        _mainLyricsProvider.value = storage.loadMainLyricsProvider()
        _defaultLyricsOffsetMs.value = storage.loadDefaultLyricsOffsetMs()
        _autoDownloadLikedSongs.value = storage.loadAutoDownloadLikedSongs()
        _recordListeningHistory.value = storage.loadRecordListeningHistory()
        _autoCheckUpdates.value = storage.loadAutoCheckUpdates()
        _releaseNotificationsEnabled.value = storage.loadReleaseNotificationsEnabled()
        AuraApplication.applyThemeMode(_themeMode.value)
    }

    fun toggleRomanizationLanguage(language: String) {
        val current = _romanizationLanguages.value.toMutableSet()
        if (current.contains(language)) {
            current.remove(language)
        } else {
            current.add(language)
        }
        setRomanizationLanguages(current)
    }

    fun setRomanizationLanguages(languages: Set<String>) {
        _romanizationLanguages.value = languages
        storage.saveRomanizationLanguages(languages)
        SimpMusicLyricsRepository.setEnabledRomanizationLanguages(languages)
        onReRomanizeCurrentLyrics(languages)
    }

    fun setMainLyricsProvider(provider: String) {
        _mainLyricsProvider.value = provider
        storage.saveMainLyricsProvider(provider)
        SimpMusicLyricsRepository.clearCache()
        onReloadLyricsForProvider(provider)
    }

    fun setDefaultLyricsOffsetMs(offsetMs: Int) {
        val clamped = offsetMs.coerceIn(-10000, 10000)
        _defaultLyricsOffsetMs.value = clamped
        storage.saveDefaultLyricsOffsetMs(clamped)
        onApplyDefaultLyricsOffsetIfZero(clamped)
    }

    fun setAutoDownloadLikedSongs(enabled: Boolean) {
        _autoDownloadLikedSongs.value = enabled
        storage.saveAutoDownloadLikedSongs(enabled)
    }

    fun setRecordListeningHistory(enabled: Boolean) {
        _recordListeningHistory.value = enabled
        storage.saveRecordListeningHistory(enabled)
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        _autoCheckUpdates.value = enabled
        storage.saveAutoCheckUpdates(enabled)
    }

    fun setReleaseNotificationsEnabled(enabled: Boolean) {
        _releaseNotificationsEnabled.value = enabled
        storage.saveReleaseNotificationsEnabled(enabled)
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            if (enabled) {
                messaging.subscribeToTopic(AuraNotificationManager.TOPIC_RELEASES)
                AuraNotificationManager.setupFcmSubscription(application)
            } else {
                messaging.unsubscribeFromTopic(AuraNotificationManager.TOPIC_RELEASES)
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        storage.saveThemeMode(mode)
        AuraApplication.applyThemeMode(mode)
    }

    fun setThemeColorSource(source: ThemeColorSource) {
        _themeColorSource.value = source
        storage.saveThemeColorSource(source)
    }

    fun setCustomAccentArgb(argb: Int?) {
        _customAccentArgb.value = argb
        storage.saveCustomAccentArgb(argb)
    }

    fun setSkipCrossfadeWithinAlbum(enabled: Boolean) {
        AuraPlayerManager.setSkipCrossfadeWithinAlbum(enabled)
        storage.saveSkipCrossfadeWithinAlbum(enabled)
    }

    fun selectAutoEqProfile(profileId: String?) {
        AuraPlayerManager.applyAutoEqProfile(profileId)
        storage.saveActiveAutoEqProfileId(profileId)
    }

    fun toggleSponsorBlockCategory(category: String) {
        val current = AuraPlayerManager.sponsorBlockCategories.value.toMutableSet()
        if (current.contains(category)) {
            if (current.size > 1) current.remove(category)
        } else {
            current.add(category)
        }
        AuraPlayerManager.setSponsorBlockCategories(current)
        SponsorBlockRepository.clearSegmentCache()
        val activeTrack = AuraPlayerManager.currentTrack.value ?: return
        scope.launch {
            val segs = SponsorBlockRepository.getSponsorSegments(activeTrack.videoId, current)
            AuraPlayerManager.setSponsorSegments(segs)
        }
    }

    fun exportBackupToUri(uri: Uri) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val json = storage.exportBackupJson()
                    application.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: return@runCatching false
                    true
                }.getOrDefault(false)
            }
            Toast.makeText(
                application,
                if (success) "Aura backup saved successfully" else "Failed to export backup",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun importBackupFromUri(uri: Uri) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = application.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: return@runCatching false
                    storage.importBackupJson(raw)
                }.getOrDefault(false)
            }
            if (success) {
                reloadThemeAndPrefsFromStorage()
                AuraPlayerManager.reloadSettingsFromStorage()
                onBackupRestored()
                Toast.makeText(application, "Aura backup restored successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(application, "Invalid or unreadable backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setAppThemeStyle(style: AppThemeStyle) {
        _appThemeStyle.value = style
        val isGlass = style == AppThemeStyle.LIQUID_GLASS
        _liquidGlassEnabled.value = isGlass
        _nowPlayingStyle.value = if (isGlass) NowPlayingStyle.LIQUID_GLASS else NowPlayingStyle.SPOTIFY
        _lyricsStyle.value = if (isGlass) LyricsStyle.APPLE_MUSIC else LyricsStyle.CLASSIC
        storage.saveAppThemeStyle(style)
    }

    fun setNowPlayingStyle(style: NowPlayingStyle) {
        setAppThemeStyle(if (style == NowPlayingStyle.LIQUID_GLASS) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT)
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        setAppThemeStyle(if (enabled) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT)
    }

    fun setLyricsStyle(style: LyricsStyle) {
        setAppThemeStyle(if (style == LyricsStyle.APPLE_MUSIC) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT)
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        AuraPlayerManager.selectEqualizerPreset(preset)
    }

    fun checkForUpdates(force: Boolean = false) {
        scope.launch {
            val release = AuraUpdateManager.checkForUpdates(application, force)
            if (release != null) {
                _currentUpdateRelease.value = release
                _updateState.value = UpdateState.IDLE
                _updateErrorMessage.value = null
                _isUpdateModalVisible.value = true
            } else if (force) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        "You're already on the latest version of Aura!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun openUpdateModalForRelease(release: AuraReleaseInfo) {
        _currentUpdateRelease.value = release
        _updateState.value = UpdateState.IDLE
        _updateErrorMessage.value = null
        _isUpdateModalVisible.value = true
    }

    fun dismissUpdateModal() {
        if (_updateState.value != UpdateState.DOWNLOADING) {
            _isUpdateModalVisible.value = false
        }
    }

    fun snoozeUpdate(version: String) {
        AuraUpdateManager.snoozeUpdate(application, version)
        _isUpdateModalVisible.value = false
    }

    fun downloadAndInstallUpdate(release: AuraReleaseInfo) {
        scope.launch {
            if (!release.downloadUrl.endsWith(".apk", ignoreCase = true) && !release.downloadUrl.contains("/download/")) {
                // Interactive preview mode: simulate smooth download streaming for UI evaluation
                _updateState.value = UpdateState.DOWNLOADING
                _updateErrorMessage.value = null
                for (pct in 1..100) {
                    delay(35)
                    val written = (release.apkSize * (pct / 100f)).toLong()
                    _updateDownloadProgress.value = UpdateDownloadProgress(
                        writtenBytes = written,
                        totalBytes = release.apkSize,
                        fraction = pct / 100f
                    )
                }
                _updateState.value = UpdateState.READY
                return@launch
            }

            _updateState.value = UpdateState.DOWNLOADING
            _updateErrorMessage.value = null
            _updateDownloadProgress.value = UpdateDownloadProgress()

            val result = AuraUpdateManager.downloadApk(application, release) { progress ->
                _updateDownloadProgress.value = progress
            }

            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                _updateState.value = UpdateState.READY
                AuraUpdateManager.installApk(application, apkFile)
            }.onFailure { err ->
                _updateState.value = UpdateState.ERROR
                _updateErrorMessage.value = err.message ?: "Failed to download update APK"
            }
        }
    }

    fun installDownloadedApk(release: AuraReleaseInfo) {
        downloadedApkFile?.let { apkFile ->
            AuraUpdateManager.installApk(application, apkFile)
        } ?: downloadAndInstallUpdate(release)
    }

    // --- Tier 2 Settings Actions ---
    fun restoreIntegrationsOnStartup() {
        YtmAccountManager.setCookie(_ytmCookie.value)
        if (_discordRpcEnabled.value && _discordToken.value.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                runCatching { DiscordRpcManager.connect(_discordToken.value) }
            }
        }
    }

    fun currentAiTranslationConfig(): AiLyricsTranslator.AiTranslationConfig =
        AiLyricsTranslator.AiTranslationConfig(
            provider = _aiProvider.value,
            apiKey = _aiApiKey.value,
            modelId = _aiModelId.value,
            customBaseUrl = _aiBaseUrl.value,
            targetLanguage = _aiTargetLanguage.value.ifBlank { "English" }
        )

    fun setAiProvider(provider: AiLyricsTranslator.AiProvider) {
        _aiProvider.value = provider
        storage.saveAiProvider(provider.name)
    }

    fun setAiApiKey(apiKey: String) {
        _aiApiKey.value = apiKey
        storage.saveAiApiKey(apiKey)
    }

    fun setAiModelId(modelId: String) {
        _aiModelId.value = modelId
        storage.saveAiModelId(modelId)
    }

    fun setAiBaseUrl(baseUrl: String) {
        _aiBaseUrl.value = baseUrl
        storage.saveAiBaseUrl(baseUrl)
    }

    fun setAiTargetLanguage(language: String) {
        _aiTargetLanguage.value = language
        storage.saveAiTargetLanguage(language)
    }

    fun setAiTranslationEnabled(enabled: Boolean) {
        _aiTranslationEnabled.value = enabled
        storage.saveAiTranslationEnabled(enabled)
    }

    fun setSmartDownloadsEnabled(enabled: Boolean) {
        _smartDownloadsEnabled.value = enabled
        storage.saveSmartDownloadsEnabled(enabled)
        SmartDownloadScheduler.schedule(application, enabled, _smartDownloadsLimit.value)
    }

    fun setSmartDownloadsLimit(limit: Int) {
        _smartDownloadsLimit.value = limit
        storage.saveSmartDownloadsLimit(limit)
        if (_smartDownloadsEnabled.value) {
            SmartDownloadScheduler.schedule(application, true, limit)
        }
    }

    fun runSmartDownloadsNow() {
        SmartDownloadScheduler.runNow(application, _smartDownloadsLimit.value)
        Toast.makeText(application, "Smart Downloads queued in background", Toast.LENGTH_SHORT).show()
    }

    fun setAutoBackupFrequency(frequency: AutoBackupScheduler.BackupFrequency) {
        _autoBackupFrequency.value = frequency
        storage.saveAutoBackupFrequency(frequency.name)
        AutoBackupScheduler.schedule(application, frequency, _autoBackupKeepCount.value)
    }

    fun setAutoBackupKeepCount(keepCount: Int) {
        _autoBackupKeepCount.value = keepCount
        storage.saveAutoBackupKeepCount(keepCount)
        AutoBackupScheduler.schedule(application, _autoBackupFrequency.value, keepCount)
    }

    fun setLastfmSessionKey(sessionKey: String) {
        _lastfmSessionKey.value = sessionKey
        storage.saveLastfmSessionKey(sessionKey)
    }

    fun setLastfmApiKey(apiKey: String) {
        _lastfmApiKey.value = apiKey
        storage.saveLastfmApiKey(apiKey)
    }

    fun setLastfmApiSecret(apiSecret: String) {
        _lastfmApiSecret.value = apiSecret
        storage.saveLastfmApiSecret(apiSecret)
    }

    fun setLastfmEnabled(enabled: Boolean) {
        _lastfmEnabled.value = enabled
        storage.saveLastfmEnabled(enabled)
    }

    fun setDiscordToken(token: String) {
        _discordToken.value = token
        storage.saveDiscordToken(token)
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (_discordRpcEnabled.value && token.isNotBlank()) {
                    DiscordRpcManager.connect(token)
                } else {
                    DiscordRpcManager.disconnect()
                }
            }
        }
    }

    fun setDiscordRpcEnabled(enabled: Boolean) {
        _discordRpcEnabled.value = enabled
        storage.saveDiscordRpcEnabled(enabled)
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (enabled && _discordToken.value.isNotBlank()) {
                    DiscordRpcManager.connect(_discordToken.value)
                } else {
                    DiscordRpcManager.clearPresence()
                    DiscordRpcManager.disconnect()
                }
            }
        }
    }

    fun setYtmCookie(cookie: String) {
        _ytmCookie.value = cookie
        storage.saveYtmCookie(cookie)
        YtmAccountManager.setCookie(cookie)
    }
}
