package com.auramusic.app.core.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.SIMPMUSIC_ALL_EQ_PRESETS
import com.auramusic.app.domain.model.findMatchingPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Headphone / IEM target compensation curve compatible with AutoEq 10-band profiles.
 * Designed as an extension point for a software Media3 [AudioProcessor] EQ stage.
 */
data class AutoEqProfile(
    val id: String,
    val name: String,
    val source: String = "AutoEq",
    val bandsDb: List<Float>,
    val preampDb: Float = 0f
)

/**
 * Extension hook allowing a software 10-band Media3 [AudioProcessor] (plus optional AutoEq
 * compensation) to replace Android's 5-band hardware [Equalizer] effect.
 */
@OptIn(UnstableApi::class)
interface EqAudioProcessorHook {
    val audioProcessor: AudioProcessor?
    fun setEnabled(enabled: Boolean)
    fun updateCurve(bandsDb: List<Float>, preampDb: Float)
    fun applyAutoEqProfile(profile: AutoEqProfile?)
}

/**
 * Encapsulates all audio DSP state and platform effect binding for [AuraPlayerManager]:
 * 10-band equalizer curves, presets, anti-clipping preamp headroom calculation,
 * ReplayGain / LoudnessEnhancer normalization, PresetReverb, system equalizer launch,
 * and Media3 [AudioProcessor] + AutoEq extension hooks.
 */
@OptIn(UnstableApi::class)
class AuraAudioDspManager {
    companion object {
        private const val TAG = "AuraAudioDspManager"
        private const val MIN_GAIN_FACTOR = 0.15f
        private const val MAX_REVERB_PRESET = 6
    }

    private val effectLock = Any()

    private var hardwareEq: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    @Volatile
    var boundAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
        private set

    @Volatile
    var currentTrackLoudnessDb: Float = 0f
        private set

    @Volatile
    var baseVolume: Float = 1.0f
        private set

    @Volatile
    private var eqProcessorHook: EqAudioProcessorHook? = null
    private val secondaryEqHooks = mutableListOf<EqAudioProcessorHook>()
    private val extraAudioProcessors = mutableListOf<AudioProcessor>()

    /** Built-in software 10-band ISO biquad equalizer processor for Media3. */
    val softwareEqProcessor: EqualizerAudioProcessor = EqualizerAudioProcessor()

    init {
        eqProcessorHook = softwareEqProcessor
    }

    private val _activeEqPreset = MutableStateFlow(
        SIMPMUSIC_ALL_EQ_PRESETS.find { it.id == "flat" } ?: SIMPMUSIC_ALL_EQ_PRESETS.first()
    )
    val activeEqPreset: StateFlow<EqualizerPreset> = _activeEqPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    private val _equalizerPreamp = MutableStateFlow(0f)
    val equalizerPreamp: StateFlow<Float> = _equalizerPreamp.asStateFlow()

    private val _activeEqPresetName = MutableStateFlow("Flat")
    val activeEqPresetName: StateFlow<String> = _activeEqPresetName.asStateFlow()

    // Default EQ to false for bit-perfect, uncolored, distortion-free audio
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _loudnessNormalization = MutableStateFlow(true)
    val loudnessNormalization: StateFlow<Boolean> = _loudnessNormalization.asStateFlow()

    private val _audioReverbPreset = MutableStateFlow(0)
    val audioReverbPreset: StateFlow<Int> = _audioReverbPreset.asStateFlow()

    private val _activeAutoEqProfile = MutableStateFlow<AutoEqProfile?>(null)
    val activeAutoEqProfile: StateFlow<AutoEqProfile?> = _activeAutoEqProfile.asStateFlow()

    /** Registers a software Media3 EQ processor hook (Tier 1 10-band + AutoEq integration). */
    fun registerEqAudioProcessorHook(hook: EqAudioProcessorHook?) {
        eqProcessorHook = hook
        hook?.let { syncEqHook(it) }
    }

    /** Creates and registers a synced secondary [AudioProcessor] chain for a crossfade player. */
    fun createSecondaryAudioProcessors(): Array<AudioProcessor> {
        val secondaryEq = EqualizerAudioProcessor()
        synchronized(effectLock) {
            secondaryEqHooks.add(secondaryEq)
            syncEqHook(secondaryEq)
            return buildList {
                add(secondaryEq)
                addAll(extraAudioProcessors)
            }.toTypedArray()
        }
    }

    /** Releases secondary [AudioProcessor] instances created for a crossfade player. */
    fun releaseSecondaryAudioProcessors(processors: Array<AudioProcessor>?) {
        if (processors == null) return
        synchronized(effectLock) {
            processors.forEach { proc ->
                if (proc is EqAudioProcessorHook) {
                    secondaryEqHooks.remove(proc)
                }
            }
        }
    }

    private fun syncEqHook(hook: EqAudioProcessorHook) {
        hook.setEnabled(_eqEnabled.value)
        hook.updateCurve(_equalizerBands.value, _equalizerPreamp.value)
        hook.applyAutoEqProfile(_activeAutoEqProfile.value)
    }

    /** Registers an additional Media3 [AudioProcessor] into the ExoPlayer audio sink chain. */
    fun registerAudioProcessor(processor: AudioProcessor) {
        synchronized(effectLock) {
            if (!extraAudioProcessors.contains(processor)) {
                extraAudioProcessors.add(processor)
            }
        }
    }

    /** Returns all registered Media3 [AudioProcessor] instances for ExoPlayer's audio sink. */
    fun getAudioProcessors(): Array<AudioProcessor> {
        val hookProcessor = eqProcessorHook?.audioProcessor
        return synchronized(effectLock) {
            buildList {
                if (hookProcessor != null) add(hookProcessor)
                addAll(extraAudioProcessors)
            }.toTypedArray()
        }
    }

    /** Loads persisted DSP settings from [AuraStorageRepository]. */
    fun loadFromStorage(context: Context) {
        val storage = AuraStorageRepository(context)
        _eqEnabled.value = storage.loadEqualizerEnabled()
        _equalizerBands.value = storage.loadEqualizerBands()
        _equalizerPreamp.value = storage.loadEqualizerPreamp()
        val savedPreset = storage.loadEqualizerPreset()
        _activeEqPresetName.value = savedPreset
        _activeEqPreset.value = SIMPMUSIC_ALL_EQ_PRESETS.firstOrNull { it.name == savedPreset }
            ?: SIMPMUSIC_ALL_EQ_PRESETS.first()
        _loudnessNormalization.value = storage.loadNormalizeVolume()
        _audioReverbPreset.value = storage.loadAudioReverbPreset()
        synchronized(effectLock) {
            eqProcessorHook?.let { syncEqHook(it) }
            secondaryEqHooks.forEach { syncEqHook(it) }
        }
    }

    /** Updates the active track loudness metadata without immediately applying the curve. */
    fun setTrackLoudnessDb(loudnessDb: Float) {
        currentTrackLoudnessDb = loudnessDb
    }

    /** Updates track loudness metadata and reapplies DSP gain/normalization immediately. */
    fun updateTrackLoudness(loudnessDb: Float, player: Player?, isFadeActive: Boolean = false) {
        currentTrackLoudnessDb = loudnessDb
        applyCurve(player, isFadeActive)
    }

    /** Attaches Android audio effects (`Equalizer`, `LoudnessEnhancer`, `PresetReverb`) to [audioSessionId]. */
    fun attachAudioSession(audioSessionId: Int, player: Player?, isFadeActive: Boolean = false) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        synchronized(effectLock) {
            if (boundAudioSessionId == audioSessionId && hardwareEq != null && loudnessEnhancer != null) {
                applyCurveLocked(player, isFadeActive)
                return
            }
            boundAudioSessionId = audioSessionId
            val useHardwareEq = _eqEnabled.value && eqProcessorHook == null
            try {
                hardwareEq?.release()
                hardwareEq = Equalizer(0, audioSessionId).apply {
                    enabled = useHardwareEq
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hardware Equalizer binding notice: ${e.message}")
            }
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = _loudnessNormalization.value
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer binding notice: ${e.message}")
            }
            try {
                presetReverb?.release()
                presetReverb = PresetReverb(0, audioSessionId).apply {
                    val presetShort = _audioReverbPreset.value.coerceIn(0, MAX_REVERB_PRESET).toShort()
                    preset = presetShort
                    enabled = presetShort != PresetReverb.PRESET_NONE
                }
            } catch (e: Exception) {
                Log.w(TAG, "PresetReverb binding notice: ${e.message}")
            }
            applyCurveLocked(player, isFadeActive)
        }
    }

    /** Applies the current EQ curve, preamp headroom, loudness normalization, and reverb preset. */
    fun applyCurve(player: Player?, isFadeActive: Boolean = false) {
        synchronized(effectLock) {
            applyCurveLocked(player, isFadeActive)
        }
    }

    private fun applyCurveLocked(player: Player?, isFadeActive: Boolean) {
        val enabled = _eqEnabled.value
        val bands = _equalizerBands.value
        val preampDb = _equalizerPreamp.value
        val normEnabled = _loudnessNormalization.value
        val hook = eqProcessorHook

        if (hook != null) {
            val maxBoost = if (enabled) bands.maxOrNull()?.coerceAtLeast(0f) ?: 0f else 0f
            val headroomPreampDb = if (enabled) minOf(preampDb, -maxBoost) else preampDb
            hook.setEnabled(enabled)
            hook.updateCurve(bands, headroomPreampDb)
            secondaryEqHooks.forEach { sec ->
                sec.setEnabled(enabled)
                sec.updateCurve(bands, headroomPreampDb)
            }
            try {
                hardwareEq?.enabled = false
            } catch (e: Exception) {
                Log.w(TAG, "Hardware Equalizer bypass notice: ${e.message}")
            }
        } else {
            try {
                val eq = hardwareEq
                if (eq != null) {
                    eq.enabled = enabled
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange // millibels, e.g. [-1500, 1500]
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()

                    for (b in 0 until numBands) {
                        val mappedIdx = ((b.toFloat() / numBands.coerceAtLeast(1)) * bands.size)
                            .toInt()
                            .coerceIn(0, bands.lastIndex)
                        val targetMb = if (enabled) (bands[mappedIdx] * 100f).toInt() else 0
                        eq.setBandLevel(b.toShort(), targetMb.coerceIn(minMb, maxMb).toShort())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer curve apply notice: ${e.message}")
            }
        }

        // Loudness Normalization (ReplayGain via YouTube loudnessDb + hardware LoudnessEnhancer)
        val normDb = if (normEnabled) (-currentTrackLoudnessDb).coerceIn(-10f, 8f) else 0f
        try {
            loudnessEnhancer?.let { enhancer ->
                enhancer.enabled = normEnabled
                if (normEnabled) {
                    val targetMb = when {
                        currentTrackLoudnessDb == 0f -> 250 // Gentle +2.5 dB dynamic leveling when metadata is absent
                        normDb > 0f -> (normDb * 100f).toInt().coerceIn(0, 800)
                        else -> 0
                    }
                    enhancer.setTargetGain(targetMb)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer apply notice: ${e.message}")
        }

        try {
            presetReverb?.let { reverb ->
                val presetShort = _audioReverbPreset.value.coerceIn(0, MAX_REVERB_PRESET).toShort()
                reverb.preset = presetShort
                reverb.enabled = presetShort != PresetReverb.PRESET_NONE
            }
        } catch (e: Exception) {
            Log.w(TAG, "PresetReverb apply notice: ${e.message}")
        }

        // Anti-Clipping Headroom + loudness attenuation for tracks louder than -14 LUFS
        val computedVolume = computeTargetBaseVolume(
            eqEnabled = enabled && hook == null,
            bands = bands,
            preampDb = preampDb,
            normEnabled = normEnabled,
            normDb = normDb
        )
        baseVolume = computedVolume
        if (player != null && !isFadeActive) {
            player.volume = computedVolume
        }
    }

    /** Computes linear gain factor combining anti-clipping EQ headroom, preamp, and loudness attenuation. */
    fun computeTargetBaseVolume(
        eqEnabled: Boolean,
        bands: List<Float>,
        preampDb: Float,
        normEnabled: Boolean,
        normDb: Float
    ): Float {
        val maxBoost = if (eqEnabled) bands.maxOrNull()?.coerceAtLeast(0f) ?: 0f else 0f
        val safeHeadroom = if (maxBoost > 0f) 10.0.pow(-maxBoost.toDouble() / 20.0).toFloat() else 1.0f
        val preampFactor = if (eqEnabled) 10.0.pow(preampDb.toDouble() / 20.0).toFloat() else 1.0f
        val eqGain = if (eqEnabled) (preampFactor * safeHeadroom) else 1.0f
        val normAttenuation = if (normEnabled && normDb < 0f) 10.0.pow(normDb.toDouble() / 20.0).toFloat() else 1.0f
        return (eqGain * normAttenuation).coerceIn(MIN_GAIN_FACTOR, 1.0f)
    }

    fun setEqualizerBands(
        context: Context?,
        bands: List<Float>,
        player: Player?,
        isFadeActive: Boolean = false
    ) {
        _equalizerBands.value = bands
        val match = findMatchingPreset(bands)
        _activeEqPresetName.value = match?.name ?: "Custom"
        if (match != null) {
            _activeEqPreset.value = match
        }
        applyCurve(player, isFadeActive)
        context?.let {
            val storage = AuraStorageRepository(it)
            storage.saveEqualizerBands(bands)
            storage.saveEqualizerPreset(_activeEqPresetName.value)
        }
    }

    fun setEqualizerPreamp(
        context: Context?,
        preampDb: Float,
        player: Player?,
        isFadeActive: Boolean = false
    ) {
        _equalizerPreamp.value = preampDb
        applyCurve(player, isFadeActive)
        context?.let { AuraStorageRepository(it).saveEqualizerPreamp(preampDb) }
    }

    fun applyEqualizerPreset(
        context: Context?,
        preset: EqualizerPreset,
        player: Player?,
        isFadeActive: Boolean = false
    ) {
        _activeEqPreset.value = preset
        _activeEqPresetName.value = preset.name
        _equalizerBands.value = preset.bandsDb
        _equalizerPreamp.value = preset.preampDb
        applyCurve(player, isFadeActive)
        context?.let {
            val storage = AuraStorageRepository(it)
            storage.saveEqualizerBands(preset.bandsDb)
            storage.saveEqualizerPreamp(preset.preampDb)
            storage.saveEqualizerPreset(preset.name)
        }
    }

    fun resetEqualizer(context: Context?, player: Player?, isFadeActive: Boolean = false) {
        val flat = SIMPMUSIC_ALL_EQ_PRESETS.firstOrNull { it.id == "flat" }
            ?: EqualizerPreset("flat", "Flat", "Flat response", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        applyEqualizerPreset(context, flat, player, isFadeActive)
    }

    fun setEqualizerEnabled(
        context: Context?,
        enabled: Boolean,
        player: Player?,
        isFadeActive: Boolean = false
    ) {
        _eqEnabled.value = enabled
        applyCurve(player, isFadeActive)
        context?.let { AuraStorageRepository(it).saveEqualizerEnabled(enabled) }
    }

    fun setLoudnessNormalization(
        context: Context?,
        enabled: Boolean,
        player: Player?,
        isFadeActive: Boolean = false
    ) {
        _loudnessNormalization.value = enabled
        applyCurve(player, isFadeActive)
        context?.let { AuraStorageRepository(it).saveNormalizeVolume(enabled) }
    }

    fun setAudioReverbPreset(
        context: Context?,
        preset: Int,
        player: Player?,
        isFadeActive: Boolean = false
    ) {
        _audioReverbPreset.value = preset.coerceIn(0, MAX_REVERB_PRESET)
        applyCurve(player, isFadeActive)
        context?.let { AuraStorageRepository(it).saveAudioReverbPreset(_audioReverbPreset.value) }
    }

    /** Applies or clears an AutoEq headphone compensation profile for Tier 1 processors. */
    fun applyAutoEqProfile(
        profile: AutoEqProfile?,
        player: Player? = null,
        isFadeActive: Boolean = false
    ) {
        _activeAutoEqProfile.value = profile
        synchronized(effectLock) {
            eqProcessorHook?.applyAutoEqProfile(profile)
            secondaryEqHooks.forEach { it.applyAutoEqProfile(profile) }
            applyCurveLocked(player, isFadeActive)
        }
    }

    fun getAudioSessionId(playerAudioSessionId: Int? = null): Int {
        return playerAudioSessionId ?: boundAudioSessionId
    }

    /** Builds an [Intent] targeting Android's built-in audio effect / Dolby equalizer panel. */
    fun createSystemEqualizerIntent(
        packageName: String,
        playerAudioSessionId: Int? = null
    ): Intent {
        return Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId(playerAudioSessionId))
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
    }

    /** Launches the device's system equalizer UI if available; returns `true` if opened. */
    fun openSystemEqualizer(context: Context, playerAudioSessionId: Int? = null): Boolean {
        return try {
            val intent = createSystemEqualizerIntent(context.packageName, playerAudioSessionId).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open system equalizer: ${e.message}")
            false
        }
    }

    /** Releases all bound Android [AudioEffect] handles. */
    fun release() {
        synchronized(effectLock) {
            runCatching { hardwareEq?.release() }
            runCatching { loudnessEnhancer?.release() }
            runCatching { presetReverb?.release() }
            hardwareEq = null
            loudnessEnhancer = null
            presetReverb = null
            boundAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }
}
