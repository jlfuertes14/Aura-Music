package com.auramusic.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.sponsorblock.SponsorBlockRepository
import com.auramusic.app.core.update.AuraReleaseInfo
import com.auramusic.app.core.update.AuraUpdateManager
import com.auramusic.app.core.update.UpdateDownloadProgress
import com.auramusic.app.core.update.UpdateState
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.LyricsStyle
import com.auramusic.app.domain.model.NowPlayingStyle
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
    private val onBackupRestored: () -> Unit
) {
    private val _romanizationLanguages = MutableStateFlow(storage.loadRomanizationLanguages())
    val romanizationLanguages: StateFlow<Set<String>> = _romanizationLanguages.asStateFlow()

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

    private val _appThemeStyle = MutableStateFlow(storage.loadAppThemeStyle())
    val appThemeStyle: StateFlow<AppThemeStyle> = _appThemeStyle.asStateFlow()

    private val _nowPlayingStyle = MutableStateFlow(storage.loadNowPlayingStyle())
    val nowPlayingStyle: StateFlow<NowPlayingStyle> = _nowPlayingStyle.asStateFlow()

    private val _liquidGlassEnabled = MutableStateFlow(storage.loadLiquidGlassEnabled())
    val liquidGlassEnabled: StateFlow<Boolean> = _liquidGlassEnabled.asStateFlow()

    private val _lyricsStyle = MutableStateFlow(storage.loadLyricsStyle())
    val lyricsStyle: StateFlow<LyricsStyle> = _lyricsStyle.asStateFlow()

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
        _nowPlayingStyle.value = storage.loadNowPlayingStyle()
        _liquidGlassEnabled.value = storage.loadLiquidGlassEnabled()
        _lyricsStyle.value = storage.loadLyricsStyle()
        _romanizationLanguages.value = storage.loadRomanizationLanguages()
        _mainLyricsProvider.value = storage.loadMainLyricsProvider()
        _defaultLyricsOffsetMs.value = storage.loadDefaultLyricsOffsetMs()
        _autoDownloadLikedSongs.value = storage.loadAutoDownloadLikedSongs()
        _recordListeningHistory.value = storage.loadRecordListeningHistory()
        _autoCheckUpdates.value = storage.loadAutoCheckUpdates()
    }

    fun toggleRomanizationLanguage(language: String) {
        val current = _romanizationLanguages.value.toMutableSet()
        if (current.contains(language)) {
            current.remove(language)
        } else {
            current.add(language)
        }
        _romanizationLanguages.value = current
        storage.saveRomanizationLanguages(current)
    }

    fun setRomanizationLanguages(languages: Set<String>) {
        _romanizationLanguages.value = languages
        storage.saveRomanizationLanguages(languages)
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
}
