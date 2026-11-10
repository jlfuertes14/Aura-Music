package com.auramusic.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.ui.AuraViewModel
import com.auramusic.app.ui.components.EqualizerWaveSection
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary
import java.io.File
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

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val nowPlayingStyle by viewModel.nowPlayingStyle.collectAsState()
    val showRomanized by viewModel.showRomanizedLyrics.collectAsState()
    val romanizationLanguages by viewModel.romanizationLanguages.collectAsState()
    val downloads by viewModel.downloads.collectAsState()

    // AuraPlayerManager Settings State
    val eqEnabled by AuraPlayerManager.eqEnabled.collectAsState()
    val eqBands by AuraPlayerManager.equalizerBands.collectAsState()
    val eqPreamp by AuraPlayerManager.equalizerPreamp.collectAsState()
    val eqPresetName by AuraPlayerManager.activeEqPresetName.collectAsState()

    val audioQuality by AuraPlayerManager.audioQuality.collectAsState()
    val videoQuality by AuraPlayerManager.videoQuality.collectAsState()
    val normalizeVolume by AuraPlayerManager.loudnessNormalization.collectAsState()
    val skipSilence by AuraPlayerManager.skipSilence.collectAsState()
    val crossfadeSeconds by AuraPlayerManager.crossfadeSeconds.collectAsState()
    val sponsorBlockEnabled by AuraPlayerManager.sponsorBlockEnabled.collectAsState()
    val maxCacheSizeMb by AuraPlayerManager.maxCacheSizeMb.collectAsState()

    // Dialog Dialog States
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showRomanizationDialog by remember { mutableStateOf(false) }
    var showCacheLimitDialog by remember { mutableStateOf(false) }

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
    val appVersionName = packageInfo?.versionName ?: "1.5.1"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 140.dp)
    ) {
        // 1. Header
        item {
            Column(modifier = Modifier.padding(bottom = 28.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fidelity, audio processing & interface controls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // 2. Player Appearance
        item {
            SettingsCategoryHeader(title = "Player appearance")
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF14161C), RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NowPlayingStyle.entries.forEach { style ->
                    val isSelected = nowPlayingStyle == style
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF262933) else Color.Transparent)
                            .clickable { viewModel.setNowPlayingStyle(style) }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }

        // 3. Audio & Streaming Fidelity
        item {
            SettingsCategoryHeader(title = "Audio & streaming fidelity")
            Spacer(modifier = Modifier.height(10.dp))

            // Sound Quality Selector
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

            // Video Quality Selector
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

            // 10-Band Equalizer Toggle
            ModernSwitchRow(
                icon = Icons.Rounded.Equalizer,
                title = "10-Band Equalizer",
                subtitle = if (eqEnabled) "Custom wave curve active ($eqPresetName)" else "Hardware curve bypass",
                checked = eqEnabled,
                onCheckedChange = { AuraPlayerManager.setEqualizerEnabled(it) }
            )

            // Interactive Frequency Wave Curve & Presets (when enabled)
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
                        onReset = { AuraPlayerManager.resetEqualizer() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 4. Lyrics & Romanization
        item {
            SettingsCategoryHeader(title = "Lyrics & romanization")
            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 5. Playback & Audio Processing
        item {
            SettingsCategoryHeader(title = "Playback & audio processing")
            Spacer(modifier = Modifier.height(10.dp))

            // Normalize volume (ReplayGain / Loudness Normalization)
            ModernSwitchRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Normalize Volume",
                subtitle = "Balance media loudness across tracks (ReplayGain)",
                checked = normalizeVolume,
                onCheckedChange = { AuraPlayerManager.setLoudnessNormalization(it) }
            )

            SettingsDivider()

            // Skip silence
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                        inactiveTrackColor = Color(0xFF262933)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 6.dp, top = 2.dp)
                )
            }

            SettingsDivider()

            // SponsorBlock Integration
            ModernSwitchRow(
                icon = Icons.Rounded.Block,
                title = "SponsorBlock Integration",
                subtitle = "Skip intros, sponsor spots, and non-music gaps",
                checked = sponsorBlockEnabled,
                onCheckedChange = { AuraPlayerManager.setSponsorBlockEnabled(it) }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 6. Storage & Cache
        item {
            SettingsCategoryHeader(title = "Storage & cache")
            Spacer(modifier = Modifier.height(10.dp))

            ModernSettingsRow(
                icon = Icons.Rounded.Storage,
                title = "Offline Downloads",
                subtitle = "${downloads.size} tracks cached for offline playback",
                trailingContent = { }
            )

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
                subtitle = "Release audio fragments and video segments",
                onClick = {
                    try {
                        val cacheDir = File(context.cacheDir, "aura_media3_cache")
                        if (cacheDir.exists()) {
                            cacheDir.deleteRecursively()
                            cacheDir.mkdirs()
                        }
                        Toast.makeText(context, "Stream cache successfully emptied", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cache clear notice: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                trailingContent = { }
            )

            SettingsDivider()

            ModernClickableRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Clear Image & Artwork Cache",
                subtitle = "Release album covers and cached artist avatars",
                onClick = {
                    try {
                        val imageLoader = Coil.imageLoader(context)
                        imageLoader.diskCache?.clear()
                        imageLoader.memoryCache?.clear()
                        Toast.makeText(context, "Artwork cache successfully cleared", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Notice: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                trailingContent = { }
            )

            Spacer(modifier = Modifier.height(34.dp))
        }

        // 7. Updates & Notifications
        item {
            SettingsCategoryHeader(title = "Updates & notifications")
            Spacer(modifier = Modifier.height(10.dp))

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

            ModernSettingsRow(
                icon = Icons.Rounded.Notifications,
                title = "Release Push Notifications",
                subtitle = "Receive alerts when new builds are published",
                trailingContent = { }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        // 8. About Aura
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SimpMusic & YouTube Music Engine • 0% AI Slop",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Audio Stream Quality Dialog
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
            containerColor = Color(0xFF181A22),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. Video Stream Quality Dialog
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
            containerColor = Color(0xFF181A22),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 3. Romanization Languages Multi-Select Dialog
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
            containerColor = Color(0xFF181A22),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 4. Cache Limit Dialog
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
            containerColor = Color(0xFF181A22),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextSecondary,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 48.dp, top = 2.dp, bottom = 2.dp)
    )
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
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
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AuraNeonLime,
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}
