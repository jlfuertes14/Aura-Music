package com.auramusic.app.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import com.auramusic.app.R
import com.auramusic.app.core.lyrics.AiLyricsTranslator
import com.auramusic.app.core.media.AutoEqProfiles
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.workers.AutoBackupScheduler
import com.auramusic.app.domain.model.AppThemeMode
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.ChartSupportedCountries
import com.auramusic.app.domain.model.ThemeColorSource
import com.auramusic.app.ui.AuraViewModel
import com.auramusic.app.ui.components.AuraLiquidGlassSwitch
import com.auramusic.app.ui.components.AuraSleepTimerSheet
import com.auramusic.app.ui.components.AutoEqProfilePickerSheet
import com.auramusic.app.ui.components.EqualizerWaveSection
import com.auramusic.app.ui.components.StorageBreakdownBar
import com.auramusic.app.ui.components.StorageSegment
import com.auramusic.app.ui.components.formatSleepTimerStatus
import com.auramusic.app.ui.theme.AURA_CUSTOM_ACCENT_SWATCHES
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.LocalIsDarkTheme
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextSecondary
import com.auramusic.app.ui.theme.auraGlassSheetColor
import com.auramusic.app.ui.theme.auraGlassSurface
import com.auramusic.app.ui.theme.isLyricsBlurSupported
import com.auramusic.app.ui.theme.isWallpaperDynamicColorSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val AUDIO_QUALITY_OPTIONS = listOf(
    "Auto" to "Auto (Adaptive highest quality)",
    "High Opus - 256kbps" to "High Opus - 256kbps (YT Premium / Studio)",
    "High AAC - 256kbps" to "High AAC - 256kbps (Apple / Universal)",
    "Medium - 128-160kbps" to "Medium - 128-160kbps (Balanced)",
    "Low - 66-96kbps" to "Low - 66-96kbps (Data Saver)"
)

private val VIDEO_QUALITY_OPTIONS = listOf(
    "Auto (1080p)" to "Auto (Best available resolution)",
    "1080p" to "1080p (Full High Definition)",
    "720p" to "720p (High Definition)",
    "480p" to "480p (Standard Definition)",
    "360p" to "360p (Data Saver)"
)

private val AUDIO_REVERB_OPTIONS = listOf(
    0 to "None (Off)",
    1 to "Small Room",
    2 to "Medium Room",
    3 to "Large Room",
    4 to "Medium Hall",
    5 to "Large Hall",
    6 to "Plate"
)

private val LYRICS_PROVIDER_OPTIONS = listOf(
    "SimpMusic" to "SimpMusic Lyrics (Word-by-Word RichSync + fallback)",
    "BetterLyrics" to "BetterLyrics (TTML Word-Synced + fallback)",
    "LRCLIB" to "LRCLIB (Open-source synced LRC + fallback)",
    "YouTube" to "YouTube Captions (TimedText + fallback)"
)

private val SMART_DOWNLOAD_LIMIT_OPTIONS = listOf(25, 50, 100)
private val AUTO_BACKUP_KEEP_OPTIONS = listOf(3, 5, 10, 15)
private val AI_TARGET_LANGUAGES = listOf("English", "Spanish", "Japanese", "Korean", "Chinese", "French", "German")

private val SPONSORBLOCK_CATEGORY_OPTIONS = listOf(
    "sponsor" to "Sponsor segments",
    "selfpromo" to "Unpaid / Self-promotion",
    "interaction" to "Interaction reminders (Subscribe)",
    "intro" to "Intermission / Intro animation",
    "outro" to "Endcards / Credits",
    "music_offtopic" to "Non-music section in music videos",
    "preview" to "Preview / Recap",
    "filler" to "Filler tangent / Jokes"
)

private val ROMANIZATION_LANGUAGES = listOf(
    "Japanese" to "Japanese (Romaji)",
    "Korean" to "Korean (Revised Romanization)",
    "Chinese" to "Chinese (Pinyin)",
    "Hindi" to "Hindi (Latin transliteration)",
    "Punjabi" to "Punjabi (Shahmukhi / Gurmukhi Latin)",
    "Russian" to "Russian (Cyrillic to Latin)",
    "Ukrainian" to "Ukrainian (Cyrillic to Latin)",
    "Serbian" to "Serbian (Cyrillic to Latin)",
    "Bulgarian" to "Bulgarian (Cyrillic to Latin)",
    "Belarusian" to "Belarusian (Cyrillic to Latin)",
    "Kyrgyz" to "Kyrgyz (Cyrillic to Latin)",
    "Macedonian" to "Macedonian (Cyrillic to Latin)"
)

private val CACHE_LIMIT_OPTIONS = listOf(
    512 to "512 MB",
    1024 to "1 GB (Default)",
    2048 to "2 GB",
    5120 to "5 GB",
    0 to "Unlimited"
)

private fun formatBytesLabel(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun calculateDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    var total = 0L
    dir.listFiles()?.forEach { child ->
        total += calculateDirSize(child)
    }
    return total
}

private fun formatGlobalOffsetLabel(offsetMs: Int): String {
    if (offsetMs == 0) return "0.0s (Default)"
    val sign = if (offsetMs > 0) "+" else "-"
    val absMs = abs(offsetMs)
    return "$sign${absMs / 1000}.${(absMs % 1000) / 100}s"
}

@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val appThemeStyle by viewModel.appThemeStyle.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val themeColorSource by viewModel.themeColorSource.collectAsState()
    val customAccentArgb by viewModel.customAccentArgb.collectAsState()
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val isDarkTheme = LocalIsDarkTheme.current
    val primaryAccent = MaterialTheme.colorScheme.primary
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedCountry by viewModel.selectedChartCountry.collectAsState()
    val showRomanized by viewModel.showRomanizedLyrics.collectAsState()
    val romanizationLanguages by viewModel.romanizationLanguages.collectAsState()
    val mainLyricsProvider by viewModel.mainLyricsProvider.collectAsState()
    val defaultLyricsOffsetMs by viewModel.defaultLyricsOffsetMs.collectAsState()
    val autoDownloadLiked by viewModel.autoDownloadLikedSongs.collectAsState()
    val recordListeningHistory by viewModel.recordListeningHistory.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()
    val releaseNotificationsEnabled by viewModel.releaseNotificationsEnabled.collectAsState()
    val downloads by viewModel.downloads.collectAsState()

    // Tier 2 State
    val aiProvider by viewModel.aiProvider.collectAsState()
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val aiModelId by viewModel.aiModelId.collectAsState()
    val aiBaseUrl by viewModel.aiBaseUrl.collectAsState()
    val aiTargetLanguage by viewModel.aiTargetLanguage.collectAsState()
    val aiTranslationEnabled by viewModel.aiTranslationEnabled.collectAsState()
    val smartDownloadsEnabled by viewModel.smartDownloadsEnabled.collectAsState()
    val smartDownloadsLimit by viewModel.smartDownloadsLimit.collectAsState()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsState()
    val autoBackupKeepCount by viewModel.autoBackupKeepCount.collectAsState()
    val lastfmSessionKey by viewModel.lastfmSessionKey.collectAsState()
    val lastfmApiKey by viewModel.lastfmApiKey.collectAsState()
    val lastfmApiSecret by viewModel.lastfmApiSecret.collectAsState()
    val lastfmEnabled by viewModel.lastfmEnabled.collectAsState()
    val discordToken by viewModel.discordToken.collectAsState()
    val discordRpcEnabled by viewModel.discordRpcEnabled.collectAsState()
    val ytmCookie by viewModel.ytmCookie.collectAsState()

    // AuraPlayerManager Settings State
    val eqEnabled by AuraPlayerManager.eqEnabled.collectAsState()
    val eqBands by AuraPlayerManager.equalizerBands.collectAsState()
    val eqPreamp by AuraPlayerManager.equalizerPreamp.collectAsState()
    val eqPresetName by AuraPlayerManager.activeEqPresetName.collectAsState()
    val activeAutoEqProfileId by viewModel.activeAutoEqProfileId.collectAsState()
    val skipCrossfadeWithinAlbum by viewModel.skipCrossfadeWithinAlbum.collectAsState()
    val sleepTimerRemainingMin by viewModel.sleepTimerRemainingMin.collectAsState()
    val sleepTimerEndOfTrack by viewModel.sleepTimerEndOfTrack.collectAsState()

    val audioQuality by AuraPlayerManager.audioQuality.collectAsState()
    val videoQuality by AuraPlayerManager.videoQuality.collectAsState()
    val audioReverbPreset by AuraPlayerManager.audioReverbPreset.collectAsState()
    val normalizeVolume by AuraPlayerManager.loudnessNormalization.collectAsState()
    val skipSilence by AuraPlayerManager.skipSilence.collectAsState()
    val crossfadeSeconds by AuraPlayerManager.crossfadeSeconds.collectAsState()
    val savePlaybackState by AuraPlayerManager.savePlaybackState.collectAsState()
    val killServiceOnExit by AuraPlayerManager.killServiceOnExit.collectAsState()
    val playVideoForVideoTrack by AuraPlayerManager.playVideoForVideoTrack.collectAsState()
    val radioAudioOnly by AuraPlayerManager.radioAudioOnly.collectAsState()
    val sponsorBlockEnabled by AuraPlayerManager.sponsorBlockEnabled.collectAsState()
    val sponsorBlockCategories by AuraPlayerManager.sponsorBlockCategories.collectAsState()
    val maxCacheSizeMb by AuraPlayerManager.maxCacheSizeMb.collectAsState()

    // Live storage telemetry
    val coroutineScope = rememberCoroutineScope()
    var cacheRefreshTick by remember { mutableIntStateOf(0) }
    var streamCacheBytes by remember { mutableLongStateOf(0L) }
    var imageCacheBytes by remember { mutableLongStateOf(0L) }
    var downloadsBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cacheRefreshTick, downloads.size) {
        withContext(Dispatchers.IO) {
            streamCacheBytes = AuraPlayerManager.getMediaCacheSizeBytes()
            imageCacheBytes = calculateDirSize(File(context.cacheDir, "image_cache"))
            downloadsBytes = downloads.sumOf { track ->
                val f = track.localFilePath?.let { File(it) } ?: AuraPlayerManager.getOfflineFile(context, track.videoId)
                if (f.exists()) f.length() else 0L
            }
        }
    }

    // SAF Backup & Restore Launchers
    val backupCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(uri)
        }
    }

    // Dialog States
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showReverbDialog by remember { mutableStateOf(false) }
    var showLyricsProviderDialog by remember { mutableStateOf(false) }
    var showLyricsOffsetDialog by remember { mutableStateOf(false) }
    var showRomanizationDialog by remember { mutableStateOf(false) }
    var showSponsorBlockCategoriesDialog by remember { mutableStateOf(false) }
    var showCacheLimitDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearDownloadsConfirm by remember { mutableStateOf(false) }
    var showAutoEqSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAiTranslationDialog by remember { mutableStateOf(false) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }
    var showYtmCookieDialog by remember { mutableStateOf(false) }
    var showLastfmDialog by remember { mutableStateOf(false) }
    var showDiscordDialog by remember { mutableStateOf(false) }

    val dialogContainerColor = auraGlassSheetColor(
        if (isDarkTheme) Color(0xFF181A22) else MaterialTheme.colorScheme.surface
    )

    val packageInfo = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }
    val appVersionName = packageInfo?.versionName ?: "1.5.3"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 140.dp)
    ) {
        // 1. Header
        item {
            Column(modifier = Modifier.padding(bottom = 28.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fidelity, audio processing & interface controls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
        }

        // 2. Appearance (Theme Style, Theme Mode, Accent Color)
        item {
            SettingsCategoryHeader(title = "Appearance")
            Spacer(modifier = Modifier.height(10.dp))

            ModernClickableRow(
                icon = Icons.Rounded.Palette,
                title = "Theme",
                subtitle = "${appThemeStyle.label} — ${appThemeStyle.subtitle}",
                onClick = { showThemeDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select Theme",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            // Theme Mode Segmented Control (System / Dark / Light)
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsRowIcon(icon = Icons.Rounded.DarkMode)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Theme Mode",
                            color = primaryTextColor,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Switch between System, Dark, and Light appearance",
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDarkTheme) Color.White.copy(alpha = 0.06f)
                            else Color.Black.copy(alpha = 0.06f)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) primaryAccent else Color.Transparent)
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else secondaryTextColor,
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            SettingsDivider()

            // Accent Color Segmented Control (Default / Wallpaper / Custom)
            val wallpaperSupported = isWallpaperDynamicColorSupported()
            val accentSources = remember(wallpaperSupported) {
                if (wallpaperSupported) {
                    listOf(ThemeColorSource.DEFAULT, ThemeColorSource.WALLPAPER, ThemeColorSource.CUSTOM)
                } else {
                    listOf(ThemeColorSource.DEFAULT, ThemeColorSource.CUSTOM)
                }
            }
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsRowIcon(icon = Icons.Rounded.ColorLens)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Accent Color",
                            color = primaryTextColor,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (themeColorSource) {
                                ThemeColorSource.DEFAULT -> "Aura signature Neon Lime"
                                ThemeColorSource.WALLPAPER -> "Material You dynamic wallpaper palette"
                                ThemeColorSource.CUSTOM -> "Custom accent swatch"
                            },
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDarkTheme) Color.White.copy(alpha = 0.06f)
                            else Color.Black.copy(alpha = 0.06f)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    accentSources.forEach { source ->
                        val selected = themeColorSource == source
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) primaryAccent else Color.Transparent)
                                .clickable {
                                    if (source == ThemeColorSource.CUSTOM && customAccentArgb == null) {
                                        viewModel.setCustomAccentArgb(
                                            AURA_CUSTOM_ACCENT_SWATCHES.first().second.toInt()
                                        )
                                    }
                                    viewModel.setThemeColorSource(source)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = source.label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else secondaryTextColor,
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = themeColorSource == ThemeColorSource.CUSTOM) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AURA_CUSTOM_ACCENT_SWATCHES.forEach { (swatchName, argbLong) ->
                            val swatchInt = argbLong.toInt()
                            val swatchColor = Color(argbLong)
                            val isSelected = customAccentArgb == swatchInt
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, primaryTextColor, CircleShape)
                                        } else {
                                            Modifier.border(
                                                1.dp,
                                                primaryTextColor.copy(alpha = 0.22f),
                                                CircleShape
                                            )
                                        }
                                    )
                                    .clickable {
                                        viewModel.setCustomAccentArgb(swatchInt)
                                        viewModel.setThemeColorSource(ThemeColorSource.CUSTOM)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "$swatchName selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 3. Content & Discovery (SimpMusic Content section)
        item {
            SettingsCategoryHeader(title = "Content & discovery")
            Spacer(modifier = Modifier.height(10.dp))

            ModernClickableRow(
                icon = Icons.Rounded.Public,
                title = "Content Country",
                subtitle = "${ChartSupportedCountries.getCountryName(selectedCountry)} ($selectedCountry)",
                onClick = { showCountryDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select Content Country",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Download,
                title = "Auto-Download Liked Songs",
                subtitle = "Automatically save songs offline when you tap the heart",
                checked = autoDownloadLiked,
                onCheckedChange = { viewModel.setAutoDownloadLikedSongs(it) }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.SmartDisplay,
                title = "Play Video for Video Tracks",
                subtitle = "Open official music videos in video mode instead of audio-only",
                checked = playVideoForVideoTrack,
                onCheckedChange = { AuraPlayerManager.setPlayVideoForVideoTrack(it) }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Radio,
                title = "Radio Audio Only",
                subtitle = "Keep endless auto-play radio queues focused on audio tracks",
                checked = radioAudioOnly,
                onCheckedChange = { AuraPlayerManager.setRadioAudioOnly(it) }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 4. Audio & Streaming Fidelity
        item {
            SettingsCategoryHeader(title = "Audio & streaming fidelity")
            Spacer(modifier = Modifier.height(10.dp))

            ModernClickableRow(
                icon = Icons.Rounded.Speed,
                title = "Audio Stream Quality",
                subtitle = AUDIO_QUALITY_OPTIONS.firstOrNull { it.first == audioQuality }?.second
                    ?: "Up to 256kbps direct stream (Opus / AAC)",
                onClick = { showAudioQualityDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select Audio Quality",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Videocam,
                title = "Video Stream Quality",
                subtitle = VIDEO_QUALITY_OPTIONS.firstOrNull { it.first == videoQuality }?.second
                    ?: "Up to 1080p Full HD video stream",
                onClick = { showVideoQualityDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select Video Quality",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Equalizer,
                title = "10-Band Equalizer",
                subtitle = if (eqEnabled) "Custom wave curve active ($eqPresetName)" else "Hardware curve bypass",
                checked = eqEnabled,
                onCheckedChange = { AuraPlayerManager.setEqualizerEnabled(it) }
            )

            AnimatedVisibility(
                visible = eqEnabled,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                    EqualizerWaveSection(
                        bands = eqBands,
                        preampDb = eqPreamp,
                        presetName = eqPresetName,
                        onBandsChange = { AuraPlayerManager.setEqualizerBands(it) },
                        onPreampChange = { AuraPlayerManager.setEqualizerPreamp(it) },
                        onPresetSelect = { AuraPlayerManager.applyEqualizerPreset(it) },
                        onReset = { AuraPlayerManager.resetEqualizer() },
                        activeAutoEqProfileId = activeAutoEqProfileId,
                        onSelectAutoEqProfile = { viewModel.selectAutoEqProfile(it) }
                    )
                }
            }

            SettingsDivider()

            val activeAutoEqName = remember(activeAutoEqProfileId) {
                activeAutoEqProfileId?.let { id ->
                    AutoEqProfiles.ALL_PROFILES.firstOrNull { it.id == id }?.name
                } ?: "None (Manual)"
            }
            ModernClickableRow(
                icon = Icons.Rounded.Headphones,
                title = "AutoEq Headphone Profile",
                subtitle = activeAutoEqName,
                onClick = { showAutoEqSheet = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select AutoEq Headphone Profile",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.SurroundSound,
                title = "Audio Reverb",
                subtitle = AUDIO_REVERB_OPTIONS.firstOrNull { it.first == audioReverbPreset }?.second ?: "None (Off)",
                onClick = { showReverbDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select Audio Reverb",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Tune,
                title = "Open System Equalizer",
                subtitle = "Launch Android's built-in Dolby / system audio effects panel",
                onClick = {
                    try {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AuraPlayerManager.getAudioSessionId())
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "No system equalizer app installed on this device", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to open system equalizer", Toast.LENGTH_SHORT).show()
                    }
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Open System Equalizer",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 5. Lyrics & Romanization
        item {
            SettingsCategoryHeader(title = "Lyrics & romanization")
            Spacer(modifier = Modifier.height(10.dp))

            ModernClickableRow(
                icon = Icons.Rounded.MusicNote,
                title = "Main Lyrics Provider",
                subtitle = LYRICS_PROVIDER_OPTIONS.firstOrNull { it.first == mainLyricsProvider }?.second
                    ?: mainLyricsProvider,
                onClick = { showLyricsProviderDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Select Lyrics Provider",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Timer,
                title = "Default Lyrics Offset",
                subtitle = "Global lyric timing shift: ${formatGlobalOffsetLabel(defaultLyricsOffsetMs)}",
                onClick = { showLyricsOffsetDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Adjust Default Lyrics Offset",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Lyrics,
                title = "Romanized Lyrics",
                subtitle = "Transliterate non-Latin scripts to Latin alphabet",
                checked = showRomanized,
                onCheckedChange = { viewModel.toggleRomanizedLyrics() }
            )

            AnimatedVisibility(
                visible = showRomanized,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Column {
                    SettingsDivider()
                    val langCount = romanizationLanguages.size
                    val langSummary = if (langCount == ROMANIZATION_LANGUAGES.size) {
                        "All 12 languages enabled"
                    } else if (langCount > 0) {
                        romanizationLanguages.take(3).joinToString(", ") + if (langCount > 3) " +${langCount - 3} more" else ""
                    } else {
                        "None selected"
                    }

                    ModernClickableRow(
                        icon = Icons.Rounded.Translate,
                        title = "Romanization Languages",
                        subtitle = langSummary,
                        onClick = { showRomanizationDialog = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Select Languages",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "AI Lyrics Translation",
                subtitle = "Auto-translate synced lyrics to $aiTargetLanguage using ${aiProvider.label}",
                checked = aiTranslationEnabled,
                onCheckedChange = { viewModel.setAiTranslationEnabled(it) }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Key,
                title = "AI Translation Config",
                subtitle = if (aiApiKey.isNotBlank() || aiBaseUrl.isNotBlank()) {
                    "${aiProvider.label} · ${aiModelId.ifBlank { "Default model" }} · $aiTargetLanguage"
                } else {
                    "Configure ${aiProvider.label} API key, model & language"
                },
                onClick = { showAiTranslationDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Configure AI Lyrics Translation",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 6. Playback & Audio Processing
        item {
            SettingsCategoryHeader(title = "Playback & audio processing")
            Spacer(modifier = Modifier.height(10.dp))

            ModernSwitchRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Normalize Volume",
                subtitle = "Balance media loudness across tracks (ReplayGain)",
                checked = normalizeVolume,
                onCheckedChange = { AuraPlayerManager.setLoudnessNormalization(it) }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Skip Silence",
                subtitle = "Skip silent parts and long dead air automatically",
                checked = skipSilence,
                onCheckedChange = { AuraPlayerManager.setSkipSilence(it) }
            )

            SettingsDivider()

            // Crossfade duration slider
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsRowIcon(icon = Icons.Rounded.Timer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Crossfade Duration",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (crossfadeSeconds == 0) "Off (Immediate gapless)" else "$crossfadeSeconds seconds overlap",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        text = "${crossfadeSeconds}s",
                        color = AuraNeonLime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Slider(
                    value = crossfadeSeconds.toFloat(),
                    onValueChange = { AuraPlayerManager.setCrossfadeSeconds(it.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = AuraNeonLime,
                        activeTrackColor = AuraNeonLime,
                        inactiveTrackColor = if (liquidGlassEnabled) Color.White.copy(alpha = 0.14f) else Color(0xFF262933)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 6.dp, top = 2.dp)
                )
            }

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Album,
                title = "Skip crossfade within same album",
                subtitle = "Keep consecutive album tracks gapless without crossfading",
                checked = skipCrossfadeWithinAlbum,
                onCheckedChange = { viewModel.setSkipCrossfadeWithinAlbum(it) }
            )

            SettingsDivider()

            val sleepTimerSummary = formatSleepTimerStatus(sleepTimerRemainingMin, sleepTimerEndOfTrack)
            ModernClickableRow(
                icon = Icons.Rounded.Bedtime,
                title = "Sleep Timer",
                subtitle = sleepTimerSummary ?: "Off — pause playback after a duration or track",
                onClick = { showSleepTimerSheet = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Configure Sleep Timer",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.BookmarkAdded,
                title = "Save Playback State",
                subtitle = "Remember shuffle and repeat mode across app restarts",
                checked = savePlaybackState,
                onCheckedChange = { AuraPlayerManager.setSavePlaybackState(it) }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Stop Playback on Exit",
                subtitle = "Stop background music service when swiped from recents",
                checked = killServiceOnExit,
                onCheckedChange = { AuraPlayerManager.setKillServiceOnExit(it) }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Block,
                title = "SponsorBlock Integration",
                subtitle = "Skip intros, sponsor spots, and non-music gaps",
                checked = sponsorBlockEnabled,
                onCheckedChange = { AuraPlayerManager.setSponsorBlockEnabled(it) }
            )

            AnimatedVisibility(
                visible = sponsorBlockEnabled,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Column {
                    SettingsDivider()
                    val catCount = sponsorBlockCategories.size
                    val catSummary = "$catCount of ${SPONSORBLOCK_CATEGORY_OPTIONS.size} segment types enabled"
                    ModernClickableRow(
                        icon = Icons.Rounded.Tune,
                        title = "SponsorBlock Categories",
                        subtitle = catSummary,
                        onClick = { showSponsorBlockCategoriesDialog = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Configure SponsorBlock Categories",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 7. Listening History (SimpMusic Listening History section)
        item {
            SettingsCategoryHeader(title = "Listening history")
            Spacer(modifier = Modifier.height(10.dp))

            ModernSwitchRow(
                icon = Icons.Rounded.History,
                title = "Record Listening History",
                subtitle = "Track recently played songs and local taste recommendations",
                checked = recordListeningHistory,
                onCheckedChange = { viewModel.setRecordListeningHistory(it) }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Clear Listening History",
                subtitle = "Erase recently played tracks and local listening stats",
                onClick = { showClearHistoryConfirm = true },
                trailingContent = { }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 8. Storage & Cache
        item {
            SettingsCategoryHeader(title = "Storage & cache")
            Spacer(modifier = Modifier.height(10.dp))

            StorageBreakdownBar(
                segments = listOf(
                    StorageSegment("Downloads", downloadsBytes, AuraNeonLime),
                    StorageSegment("Stream Cache", streamCacheBytes, Color(0xFF38BDF8)),
                    StorageSegment("Artwork", imageCacheBytes, Color(0xFFA78BFA))
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ModernSwitchRow(
                icon = Icons.Rounded.CloudDownload,
                title = "Smart Downloads",
                subtitle = "Auto-download top $smartDownloadsLimit played & liked songs on Wi-Fi",
                checked = smartDownloadsEnabled,
                onCheckedChange = { viewModel.setSmartDownloadsEnabled(it) }
            )

            AnimatedVisibility(visible = smartDownloadsEnabled) {
                Column(modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SMART_DOWNLOAD_LIMIT_OPTIONS.forEach { limit ->
                            val selected = smartDownloadsLimit == limit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) primaryAccent
                                        else if (isDarkTheme) Color.White.copy(alpha = 0.06f)
                                        else Color.Black.copy(alpha = 0.06f)
                                    )
                                    .clickable { viewModel.setSmartDownloadsLimit(limit) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$limit songs",
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else secondaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.runSmartDownloadsNow() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Run Smart Downloads Now",
                            color = primaryAccent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            SettingsDivider()

            ModernSettingsRow(
                icon = Icons.Rounded.Storage,
                title = "Offline Downloads",
                subtitle = "${downloads.size} tracks cached (${formatBytesLabel(downloadsBytes)})",
                trailingContent = { }
            )

            if (downloads.isNotEmpty()) {
                SettingsDivider()
                ModernClickableRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Clear All Downloads",
                    subtitle = "Remove all ${downloads.size} downloaded audio files (${formatBytesLabel(downloadsBytes)})",
                    onClick = { showClearDownloadsConfirm = true },
                    trailingContent = { }
                )
            }

            SettingsDivider()

            val cacheLimitLabel = CACHE_LIMIT_OPTIONS.firstOrNull { it.first == maxCacheSizeMb }?.second ?: "$maxCacheSizeMb MB"
            ModernClickableRow(
                icon = Icons.Rounded.Tune,
                title = "Player Cache Limit",
                subtitle = "Max disk allocation for fast scrubbing: $cacheLimitLabel",
                onClick = { showCacheLimitDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Set Cache Limit",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Clear Media Stream Cache",
                subtitle = "Release audio & video fragments (${formatBytesLabel(streamCacheBytes)})",
                onClick = {
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                AuraPlayerManager.clearMediaCache()
                            }
                            cacheRefreshTick++
                            Toast.makeText(context, "Stream cache successfully emptied", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cache clear notice: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                trailingContent = { }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Clear Image & Artwork Cache",
                subtitle = "Release album covers and avatars (${formatBytesLabel(imageCacheBytes)})",
                onClick = {
                    try {
                        val imageLoader = Coil.imageLoader(context)
                        imageLoader.diskCache?.clear()
                        imageLoader.memoryCache?.clear()
                        cacheRefreshTick++
                        Toast.makeText(context, "Artwork cache successfully cleared", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Notice: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                trailingContent = { }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 9. Backup & Restore (SimpMusic Backup section)
        item {
            SettingsCategoryHeader(title = "Backup & restore")
            Spacer(modifier = Modifier.height(10.dp))

            ModernClickableRow(
                icon = Icons.Rounded.Backup,
                title = "Backup Data",
                subtitle = "Export playlists, favorites, history & settings to JSON",
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    backupCreateLauncher.launch("aura_backup_$stamp.json")
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Export Backup",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Restore,
                title = "Restore Data",
                subtitle = "Import a previously saved Aura JSON backup file",
                onClick = {
                    backupRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Restore Backup",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Schedule,
                title = "Scheduled Auto-Backup",
                subtitle = if (autoBackupFrequency == AutoBackupScheduler.BackupFrequency.OFF) {
                    "Off — tap to schedule automatic local backups"
                } else {
                    "${autoBackupFrequency.label} · Keep last $autoBackupKeepCount backups"
                },
                onClick = { showAutoBackupDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Configure Auto-Backup",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 9b. Accounts & Integrations
        item {
            SettingsCategoryHeader(title = "Accounts & integrations")
            Spacer(modifier = Modifier.height(10.dp))

            ModernClickableRow(
                icon = Icons.Rounded.Person,
                title = "YouTube Music Account",
                subtitle = if (ytmCookie.isNotBlank()) {
                    "Signed in via browser cookie (authenticated InnerTube)"
                } else {
                    "Anonymous mode (tap to add YTM browser cookie)"
                },
                onClick = { showYtmCookieDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Configure YouTube Music Cookie",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Last.fm Scrobbling",
                subtitle = if (lastfmEnabled) "Scrobbling & Now Playing active" else "Disabled",
                checked = lastfmEnabled,
                onCheckedChange = { viewModel.setLastfmEnabled(it) }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Key,
                title = "Last.fm Credentials",
                subtitle = if (lastfmSessionKey.isNotBlank() && lastfmApiKey.isNotBlank()) {
                    "Session & API credentials configured"
                } else {
                    "Set Session Key, API Key & API Secret"
                },
                onClick = { showLastfmDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Configure Last.fm",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Headphones,
                title = "Discord Rich Presence",
                subtitle = if (discordRpcEnabled) "Broadcasting active track to Discord" else "Disabled",
                checked = discordRpcEnabled,
                onCheckedChange = { viewModel.setDiscordRpcEnabled(it) }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.Key,
                title = "Discord Gateway Token",
                subtitle = if (discordToken.isNotBlank()) "Token configured" else "Set Discord token for Rich Presence",
                onClick = { showDiscordDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Configure Discord Token",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 10. Updates & Notifications
        item {
            SettingsCategoryHeader(title = "Updates & notifications")
            Spacer(modifier = Modifier.height(10.dp))

            ModernSwitchRow(
                icon = Icons.Rounded.Update,
                title = "Auto Check for Updates",
                subtitle = "Automatically check GitHub Releases on app launch",
                checked = autoCheckUpdates,
                onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for Updates",
                subtitle = "Query GitHub Releases directly for new APK builds",
                onClick = { viewModel.checkForUpdates(force = true) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Check for Updates",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            SettingsDivider()

            ModernSwitchRow(
                icon = Icons.Rounded.Notifications,
                title = "Release Push Notifications",
                subtitle = if (releaseNotificationsEnabled) {
                    "Subscribed to new build alerts (releases)"
                } else {
                    "Unsubscribed from new build alerts"
                },
                checked = releaseNotificationsEnabled,
                onCheckedChange = { viewModel.setReleaseNotificationsEnabled(it) }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 11. About Aura
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.aura_logo),
                    contentDescription = "Aura Logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Aura v$appVersionName",
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Powered by SimpMusic & YouTube Music Innertube",
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // 0. Unified Theme Selector Dialog
    if (showThemeDialog) {
        val blurSupported = isLyricsBlurSupported()
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(text = "Theme", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppThemeStyle.entries.forEach { option ->
                        val isSelected = appThemeStyle == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setAppThemeStyle(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setAppThemeStyle(option)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) AuraNeonLime else Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = if (option == AppThemeStyle.LIQUID_GLASS && !blurSupported) {
                                        "Static frosted glass & Apple Music style (live blur on Android 12+)"
                                    } else {
                                        option.subtitle
                                    },
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 1. Content Country Dialog
    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = {
                Text(text = "Content Country", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(ChartSupportedCountries.items.size) { index ->
                        val code = ChartSupportedCountries.items[index]
                        val name = ChartSupportedCountries.itemsData[index]
                        val isSelected = selectedCountry == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.selectChartCountry(code)
                                    showCountryDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectChartCountry(code)
                                    showCountryDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$name ($code)",
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. Audio Stream Quality Dialog
    if (showAudioQualityDialog) {
        AlertDialog(
            onDismissRequest = { showAudioQualityDialog = false },
            title = {
                Text(text = "Audio Stream Quality", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AUDIO_QUALITY_OPTIONS.forEach { (key, label) ->
                        val isSelected = audioQuality == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AuraPlayerManager.setAudioQuality(key)
                                    showAudioQualityDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AuraPlayerManager.setAudioQuality(key)
                                    showAudioQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioQualityDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 3. Video Stream Quality Dialog
    if (showVideoQualityDialog) {
        AlertDialog(
            onDismissRequest = { showVideoQualityDialog = false },
            title = {
                Text(text = "Video Stream Quality", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VIDEO_QUALITY_OPTIONS.forEach { (key, label) ->
                        val isSelected = videoQuality == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AuraPlayerManager.setVideoQuality(key)
                                    showVideoQualityDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AuraPlayerManager.setVideoQuality(key)
                                    showVideoQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoQualityDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 4. Audio Reverb Dialog
    if (showReverbDialog) {
        AlertDialog(
            onDismissRequest = { showReverbDialog = false },
            title = {
                Text(text = "Audio Reverb", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AUDIO_REVERB_OPTIONS.forEach { (presetId, label) ->
                        val isSelected = audioReverbPreset == presetId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AuraPlayerManager.setAudioReverbPreset(presetId)
                                    showReverbDialog = false
                                }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AuraPlayerManager.setAudioReverbPreset(presetId)
                                    showReverbDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReverbDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 5. Main Lyrics Provider Dialog
    if (showLyricsProviderDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsProviderDialog = false },
            title = {
                Text(text = "Main Lyrics Provider", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LYRICS_PROVIDER_OPTIONS.forEach { (key, label) ->
                        val isSelected = mainLyricsProvider == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setMainLyricsProvider(key)
                                    showLyricsProviderDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setMainLyricsProvider(key)
                                    showLyricsProviderDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsProviderDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 6. Default Lyrics Offset Dialog
    if (showLyricsOffsetDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsOffsetDialog = false },
            title = {
                Text(text = "Default Lyrics Offset", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Applies to tracks without a custom per-song lyric timing offset (negative delays lyrics, positive advances).",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.setDefaultLyricsOffsetMs(defaultLyricsOffsetMs - 100) },
                            modifier = Modifier
                                .size(44.dp)
                                .auraGlassSurface(shape = CircleShape, fallbackColor = Color(0xFF262A36))
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease 0.1s", tint = Color.White)
                        }
                        Text(
                            text = formatGlobalOffsetLabel(defaultLyricsOffsetMs),
                            color = if (defaultLyricsOffsetMs != 0) AuraNeonLime else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = { viewModel.setDefaultLyricsOffsetMs(defaultLyricsOffsetMs + 100) },
                            modifier = Modifier
                                .size(44.dp)
                                .auraGlassSurface(shape = CircleShape, fallbackColor = Color(0xFF262A36))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase 0.1s", tint = Color.White)
                        }
                    }
                    Slider(
                        value = defaultLyricsOffsetMs.toFloat(),
                        onValueChange = { raw ->
                            val snapped = (raw / 100f).roundToInt() * 100
                            viewModel.setDefaultLyricsOffsetMs(snapped)
                        },
                        valueRange = -5000f..5000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AuraNeonLime,
                            activeTrackColor = AuraNeonLime,
                            inactiveTrackColor = Color(0xFF2B2F3B)
                        )
                    )
                }
            },
            dismissButton = {
                if (defaultLyricsOffsetMs != 0) {
                    TextButton(onClick = { viewModel.setDefaultLyricsOffsetMs(0) }) {
                        Text("Reset", color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsOffsetDialog = false }) {
                    Text("Done", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 7. Romanization Languages Multi-Select Dialog
    if (showRomanizationDialog) {
        AlertDialog(
            onDismissRequest = { showRomanizationDialog = false },
            title = {
                Text(text = "Romanization Languages", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(ROMANIZATION_LANGUAGES.size) { index ->
                        val (key, label) = ROMANIZATION_LANGUAGES[index]
                        val isChecked = romanizationLanguages.contains(key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleRomanizationLanguage(key) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleRomanizationLanguage(key) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AuraNeonLime,
                                    checkmarkColor = Color.Black,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isChecked) Color.White else TextSecondary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRomanizationDialog = false }) {
                    Text("Done", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 8. SponsorBlock Categories Multi-Select Dialog
    if (showSponsorBlockCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showSponsorBlockCategoriesDialog = false },
            title = {
                Text(text = "SponsorBlock Categories", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(SPONSORBLOCK_CATEGORY_OPTIONS.size) { index ->
                        val (key, label) = SPONSORBLOCK_CATEGORY_OPTIONS[index]
                        val isChecked = sponsorBlockCategories.contains(key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleSponsorBlockCategory(key) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleSponsorBlockCategory(key) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AuraNeonLime,
                                    checkmarkColor = Color.Black,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isChecked) Color.White else TextSecondary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSponsorBlockCategoriesDialog = false }) {
                    Text("Done", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 9. Cache Limit Dialog
    if (showCacheLimitDialog) {
        AlertDialog(
            onDismissRequest = { showCacheLimitDialog = false },
            title = {
                Text(text = "Player Cache Limit", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CACHE_LIMIT_OPTIONS.forEach { (sizeMb, label) ->
                        val isSelected = maxCacheSizeMb == sizeMb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AuraPlayerManager.setMaxCacheSizeMb(sizeMb)
                                    showCacheLimitDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AuraPlayerManager.setMaxCacheSizeMb(sizeMb)
                                    showCacheLimitDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCacheLimitDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 10. Clear Listening History Confirmation Dialog
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = {
                Text(text = "Clear Listening History?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will permanently remove your recently played list and local listening statistics. Your favorites, playlists, and downloads will not be affected.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearListeningHistory()
                        showClearHistoryConfirm = false
                    }
                ) {
                    Text("Clear", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 11. Clear All Downloads Confirmation Dialog
    if (showClearDownloadsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsConfirm = false },
            title = {
                Text(text = "Remove All Downloads?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will delete all ${downloads.size} offline audio tracks (${formatBytesLabel(downloadsBytes)}) from device storage.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDownloads()
                        cacheRefreshTick++
                        showClearDownloadsConfirm = false
                    }
                ) {
                    Text("Delete All", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAutoEqSheet) {
        AutoEqProfilePickerSheet(
            activeProfileId = activeAutoEqProfileId,
            onSelectProfile = { profileId ->
                viewModel.selectAutoEqProfile(profileId)
                showAutoEqSheet = false
            },
            onDismiss = { showAutoEqSheet = false },
            containerColor = dialogContainerColor
        )
    }

    if (showSleepTimerSheet) {
        AuraSleepTimerSheet(
            remainingMinutes = sleepTimerRemainingMin,
            endOfTrackEnabled = sleepTimerEndOfTrack,
            onSelectMinutes = { viewModel.setSleepTimerMinutes(it) },
            onToggleEndOfTrack = { viewModel.setSleepTimerEndOfTrack(it) },
            onDismiss = { showSleepTimerSheet = false },
            containerColor = dialogContainerColor
        )
    }

    if (showAiTranslationDialog) {
        var draftProvider by remember { mutableStateOf(aiProvider) }
        var draftApiKey by remember { mutableStateOf(aiApiKey) }
        var draftModel by remember { mutableStateOf(aiModelId) }
        var draftBaseUrl by remember { mutableStateOf(aiBaseUrl) }
        var draftLang by remember { mutableStateOf(aiTargetLanguage) }

        AlertDialog(
            onDismissRequest = { showAiTranslationDialog = false },
            title = {
                Text("AI Lyrics Translation", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiLyricsTranslator.AiProvider.entries.forEach { p ->
                            val selected = draftProvider == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AuraNeonLime else Color.White.copy(alpha = 0.08f))
                                    .clickable { draftProvider = p }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (p == AiLyricsTranslator.AiProvider.CUSTOM) "Custom" else p.label,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draftApiKey,
                        onValueChange = { draftApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = draftModel,
                        onValueChange = { draftModel = it },
                        label = { Text("Model ID (optional)") },
                        placeholder = {
                            Text(
                                when (draftProvider) {
                                    AiLyricsTranslator.AiProvider.GEMINI -> "gemini-2.0-flash"
                                    AiLyricsTranslator.AiProvider.OPENAI -> "gpt-4o-mini"
                                    AiLyricsTranslator.AiProvider.CUSTOM -> "llama-3.3-70b"
                                }
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    if (draftProvider == AiLyricsTranslator.AiProvider.CUSTOM) {
                        OutlinedTextField(
                            value = draftBaseUrl,
                            onValueChange = { draftBaseUrl = it },
                            label = { Text("Custom OpenAI-Compatible Base URL") },
                            placeholder = { Text("https://api.example.com/v1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraNeonLime,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                    OutlinedTextField(
                        value = draftLang,
                        onValueChange = { draftLang = it },
                        label = { Text("Target Language") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AI_TARGET_LANGUAGES.forEach { lang ->
                            val selected = draftLang.equals(lang, ignoreCase = true)
                            Surface(
                                onClick = { draftLang = lang },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) AuraNeonLime else Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = lang,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiTranslationDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setAiProvider(draftProvider)
                        viewModel.setAiApiKey(draftApiKey.trim())
                        viewModel.setAiModelId(draftModel.trim())
                        viewModel.setAiBaseUrl(draftBaseUrl.trim())
                        viewModel.setAiTargetLanguage(draftLang.trim().ifBlank { "English" })
                        showAiTranslationDialog = false
                    }
                ) {
                    Text("Save", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAutoBackupDialog) {
        AlertDialog(
            onDismissRequest = { showAutoBackupDialog = false },
            title = {
                Text("Scheduled Auto-Backup", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutoBackupScheduler.BackupFrequency.entries.forEach { freq ->
                        val isSelected = autoBackupFrequency == freq
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setAutoBackupFrequency(freq) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setAutoBackupFrequency(freq) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuraNeonLime,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = freq.label,
                                color = if (isSelected) AuraNeonLime else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Backups to keep",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AUTO_BACKUP_KEEP_OPTIONS.forEach { keep ->
                            val selected = autoBackupKeepCount == keep
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AuraNeonLime else Color.White.copy(alpha = 0.08f))
                                    .clickable { viewModel.setAutoBackupKeepCount(keep) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$keep",
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoBackupDialog = false }) {
                    Text("Done", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showYtmCookieDialog) {
        var draftCookie by remember { mutableStateOf(ytmCookie) }
        AlertDialog(
            onDismissRequest = { showYtmCookieDialog = false },
            title = {
                Text("YouTube Music Cookie", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste your music.youtube.com browser Cookie header for personalized authenticated InnerTube requests, or leave blank for anonymous mode.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = draftCookie,
                        onValueChange = { draftCookie = it },
                        label = { Text("Cookie Header") },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setYtmCookie("")
                        showYtmCookieDialog = false
                    }
                ) {
                    Text("Sign Out", color = Color(0xFFFF7A7A))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setYtmCookie(draftCookie.trim())
                        showYtmCookieDialog = false
                    }
                ) {
                    Text("Save", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showLastfmDialog) {
        var draftSession by remember { mutableStateOf(lastfmSessionKey) }
        var draftKey by remember { mutableStateOf(lastfmApiKey) }
        var draftSecret by remember { mutableStateOf(lastfmApiSecret) }
        AlertDialog(
            onDismissRequest = { showLastfmDialog = false },
            title = {
                Text("Last.fm Credentials", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftSession,
                        onValueChange = { draftSession = it },
                        label = { Text("Session Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = draftKey,
                        onValueChange = { draftKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = draftSecret,
                        onValueChange = { draftSecret = it },
                        label = { Text("API Secret") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraNeonLime,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLastfmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setLastfmSessionKey(draftSession.trim())
                        viewModel.setLastfmApiKey(draftKey.trim())
                        viewModel.setLastfmApiSecret(draftSecret.trim())
                        showLastfmDialog = false
                    }
                ) {
                    Text("Save", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showDiscordDialog) {
        var draftToken by remember { mutableStateOf(discordToken) }
        AlertDialog(
            onDismissRequest = { showDiscordDialog = false },
            title = {
                Text("Discord Rich Presence Token", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = draftToken,
                    onValueChange = { draftToken = it },
                    label = { Text("Discord Token") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraNeonLime,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setDiscordToken("")
                        showDiscordDialog = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFFF7A7A))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDiscordToken(draftToken.trim())
                        showDiscordDialog = false
                    }
                ) {
                    Text("Save", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = dialogContainerColor,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun SettingsDivider() {
    val isDark = LocalIsDarkTheme.current
    HorizontalDivider(
        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.07f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 48.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRowIcon(icon: ImageVector) {
    val isGlass = LocalLiquidGlassEnabled.current
    val isDark = LocalIsDarkTheme.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .then(
                if (isGlass) {
                    Modifier.auraGlassSurface(shape = CircleShape, glassAlpha = 0.09f)
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.05f)
                            else Color.Black.copy(alpha = 0.06f)
                        )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ModernSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsRowIcon(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        trailingContent()
    }
}

@Composable
private fun ModernClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsRowIcon(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        trailingContent()
    }
}

@Composable
private fun ModernSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            SettingsRowIcon(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        AuraLiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
