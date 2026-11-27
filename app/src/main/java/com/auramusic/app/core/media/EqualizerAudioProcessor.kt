package com.auramusic.app.core.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Software 10-band ISO peaking biquad equalizer + preamp processor for Media3.
 *
 * Implements RBJ Audio EQ Cookbook peaking filters at standard ISO center frequencies
 * (`31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000` Hz, Q = 1.41) with
 * thread-safe immutable coefficient snapshots, AutoEq profile layering, and zero-math
 * bypass when disabled or flat.
 */
@OptIn(UnstableApi::class)
class EqualizerAudioProcessor : BaseAudioProcessor(), EqAudioProcessorHook {

    data class BiquadCoefficients(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    private class FilterConfig(
        val isBypassed: Boolean,
        val preampLinear: Double,
        val activeBands: IntArray,
        val coeffs: Array<BiquadCoefficients>,
        val sampleRate: Int
    )

    companion object {
        const val BAND_COUNT = 10
        const val DEFAULT_Q = 1.41
        private const val FLAT_EPSILON_DB = 0.01f
        private const val DENORMAL_THRESHOLD = 1e-18
        private const val MIN_GAIN_DB = -24f
        private const val MAX_GAIN_DB = 24f

        val CENTER_FREQUENCIES_HZ = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )

        /**
         * Computes normalized RBJ Audio EQ Cookbook peaking filter coefficients in Double precision.
         */
        fun computePeakingBiquadCoefficients(
            centerFreqHz: Double,
            sampleRateHz: Int,
            gainDb: Double,
            q: Double = DEFAULT_Q
        ): BiquadCoefficients {
            if (sampleRateHz <= 0 || abs(gainDb) < FLAT_EPSILON_DB) {
                return BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
            }
            val safeQ = q.coerceAtLeast(0.1)
            val safeFreq = centerFreqHz.coerceIn(10.0, sampleRateHz * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val omega = 2.0 * PI * safeFreq / sampleRateHz.toDouble()
            val sinW = sin(omega)
            val cosW = cos(omega)
            val alpha = sinW / (2.0 * safeQ)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cosW
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cosW
            val a2 = 1.0 - alpha / a

            return BiquadCoefficients(
                b0 = b0 / a0,
                b1 = b1 / a0,
                b2 = b2 / a0,
                a1 = a1 / a0,
                a2 = a2 / a0
            )
        }
    }

    private val configLock = Any()
    private var userEnabled: Boolean = false
    private val userBandsDb = FloatArray(BAND_COUNT)
    private var userPreampDb: Float = 0f
    private var activeAutoEq: AutoEqProfile? = null
    private var configuredSampleRate: Int = 48000

    @Volatile
    private var currentConfig: FilterConfig = FilterConfig(
        isBypassed = true,
        preampLinear = 1.0,
        activeBands = IntArray(0),
        coeffs = Array(BAND_COUNT) { BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0) },
        sampleRate = 48000
    )

    // Direct Form II Transposed delay states: [channel][band * 2 + 0..1]
    private var channelStates: Array<DoubleArray> = emptyArray()

    override val audioProcessor: AudioProcessor
        get() = this

    override fun setEnabled(enabled: Boolean) {
        synchronized(configLock) {
            if (userEnabled == enabled) return
            userEnabled = enabled
            rebuildConfigLocked()
        }
    }

    override fun updateCurve(bandsDb: List<Float>, preampDb: Float) {
        synchronized(configLock) {
            for (i in 0 until BAND_COUNT) {
                userBandsDb[i] = bandsDb.getOrElse(i) { 0f }.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            }
            userPreampDb = preampDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            rebuildConfigLocked()
        }
    }

    override fun applyAutoEqProfile(profile: AutoEqProfile?) {
        synchronized(configLock) {
            activeAutoEq = profile
            rebuildConfigLocked()
        }
    }

    fun isBypassed(): Boolean = currentConfig.isBypassed

    fun getBandCoefficients(bandIndex: Int): BiquadCoefficients? {
        if (bandIndex !in 0 until BAND_COUNT) return null
        return currentConfig.coeffs[bandIndex]
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        synchronized(configLock) {
            configuredSampleRate = inputAudioFormat.sampleRate
            rebuildConfigLocked()
        }
        ensureChannelStates(inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val format = inputAudioFormat
        val channelCount = format.channelCount
        if (channelCount <= 0 || format.encoding != C.ENCODING_PCM_16BIT) return

        val cfg = currentConfig
        if (cfg.isBypassed) {
            val out = replaceOutputBuffer(remaining)
            out.order(inputBuffer.order())
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameCount = remaining / (channelCount * 2)
        if (frameCount <= 0) return

        ensureChannelStates(channelCount)

        val out = replaceOutputBuffer(frameCount * channelCount * 2)
        val bufferOrder = inputBuffer.order()
        out.order(bufferOrder)
        if (bufferOrder != ByteOrder.nativeOrder()) {
            // Keep consistent endianness while reading/writing 16-bit PCM samples
            inputBuffer.order(bufferOrder)
        }

        val preamp = cfg.preampLinear
        val activeBands = cfg.activeBands
        val coeffs = cfg.coeffs
        val states = channelStates

        for (frame in 0 until frameCount) {
            for (ch in 0 until channelCount) {
                val rawSample = inputBuffer.short
                var x = (rawSample / 32768.0) * preamp
                val chState = states[ch]

                for (idx in activeBands.indices) {
                    val band = activeBands[idx]
                    val c = coeffs[band]
                    val stateOffset = band * 2
                    val z1 = chState[stateOffset]
                    val z2 = chState[stateOffset + 1]

                    val y = c.b0 * x + z1
                    var nextZ1 = c.b1 * x - c.a1 * y + z2
                    var nextZ2 = c.b2 * x - c.a2 * y

                    if (!y.isFinite() || !nextZ1.isFinite() || !nextZ2.isFinite()) {
                        chState[stateOffset] = 0.0
                        chState[stateOffset + 1] = 0.0
                        x = 0.0
                        break
                    }
                    if (abs(nextZ1) < DENORMAL_THRESHOLD) nextZ1 = 0.0
                    if (abs(nextZ2) < DENORMAL_THRESHOLD) nextZ2 = 0.0

                    chState[stateOffset] = nextZ1
                    chState[stateOffset + 1] = nextZ2
                    x = y
                }

                val scaled = (x * 32768.0).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out.putShort(scaled.toShort())
            }
        }
        // Consume any trailing odd byte safely if malformed input
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        out.flip()
    }

    override fun onFlush() {
        resetFilterStates()
    }

    override fun onReset() {
        channelStates = emptyArray()
    }

    private fun ensureChannelStates(channelCount: Int) {
        if (channelStates.size != channelCount) {
            channelStates = Array(channelCount) { DoubleArray(BAND_COUNT * 2) }
        }
    }

    private fun resetFilterStates() {
        for (ch in channelStates.indices) {
            channelStates[ch].fill(0.0)
        }
    }

    private fun rebuildConfigLocked() {
        val autoEq = activeAutoEq
        val anyEnabled = userEnabled || autoEq != null
        if (!anyEnabled) {
            currentConfig = FilterConfig(
                isBypassed = true,
                preampLinear = 1.0,
                activeBands = IntArray(0),
                coeffs = Array(BAND_COUNT) { BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0) },
                sampleRate = configuredSampleRate
            )
            return
        }

        val combinedBands = FloatArray(BAND_COUNT)
        val activeList = ArrayList<Int>(BAND_COUNT)
        val coeffs = Array(BAND_COUNT) { BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0) }

        for (i in 0 until BAND_COUNT) {
            val userGain = if (userEnabled) userBandsDb[i] else 0f
            val autoGain = autoEq?.bandsDb?.getOrElse(i) { 0f } ?: 0f
            val totalGain = (userGain + autoGain).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            combinedBands[i] = totalGain
            if (abs(totalGain) >= FLAT_EPSILON_DB) {
                activeList.add(i)
                coeffs[i] = computePeakingBiquadCoefficients(
                    centerFreqHz = CENTER_FREQUENCIES_HZ[i].toDouble(),
                    sampleRateHz = configuredSampleRate,
                    gainDb = totalGain.toDouble(),
                    q = DEFAULT_Q
                )
            }
        }

        val effectivePreampDb = (
            (if (userEnabled) userPreampDb else 0f) +
                (autoEq?.preampDb ?: 0f)
            ).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)

        val isFlat = activeList.isEmpty() && abs(effectivePreampDb) < FLAT_EPSILON_DB
        val preampLinear = if (abs(effectivePreampDb) < FLAT_EPSILON_DB) {
            1.0
        } else {
            10.0.pow(effectivePreampDb.toDouble() / 20.0)
        }

        currentConfig = FilterConfig(
            isBypassed = isFlat,
            preampLinear = preampLinear,
            activeBands = activeList.toIntArray(),
            coeffs = coeffs,
            sampleRate = configuredSampleRate
        )
    }
}
