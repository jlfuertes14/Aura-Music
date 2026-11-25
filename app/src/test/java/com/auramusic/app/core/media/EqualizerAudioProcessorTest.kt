package com.auramusic.app.core.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerAudioProcessorTest {

    @Test
    fun biquadCoefficientsAreStableAcrossAllBandsAndGainExtremes() {
        val sampleRates = listOf(44100, 48000, 96000)
        val gains = listOf(-12.0, -6.0, 0.0, 6.0, 12.0)

        for (sr in sampleRates) {
            for (freq in EqualizerAudioProcessor.CENTER_FREQUENCIES_HZ) {
                for (gain in gains) {
                    val c = EqualizerAudioProcessor.computePeakingBiquadCoefficients(
                        centerFreqHz = freq.toDouble(),
                        sampleRateHz = sr,
                        gainDb = gain,
                        q = EqualizerAudioProcessor.DEFAULT_Q
                    )
                    assertTrue(c.b0.isFinite() && c.b1.isFinite() && c.b2.isFinite())
                    assertTrue(c.a1.isFinite() && c.a2.isFinite())

                    // Jury stability criteria for 2nd-order IIR denominator 1 + a1*z^-1 + a2*z^-2
                    assertTrue("Pole radius check 1+a2>0 failed at $freq Hz @ $sr", 1.0 + c.a2 > 0.0)
                    assertTrue("Pole radius check 1-a2>0 failed at $freq Hz @ $sr", 1.0 - c.a2 > 0.0)
                    assertTrue("DC stability 1+a1+a2>0 failed at $freq Hz @ $sr", 1.0 + c.a1 + c.a2 > 0.0)
                    assertTrue("Nyquist stability 1-a1+a2>0 failed at $freq Hz @ $sr", 1.0 - c.a1 + c.a2 > 0.0)
                }
            }
        }
    }

    @Test
    fun disabledAndFlatProcessorProduceBitExactUnityOutput() {
        val processor = EqualizerAudioProcessor()
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val inputSamples = generateStereoSineSamples(freqHz = 1000.0, sampleRate = 48000, frames = 1024, amplitude = 0.4)

        // 1. Disabled state
        processor.setEnabled(false)
        processor.updateCurve(listOf(6f, 6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), preampDb = -3f)
        assertTrue(processor.isBypassed())
        val disabledOut = processSamples(processor, inputSamples, channels = 2)
        assertEquals(inputSamples.size, disabledOut.size)
        for (i in inputSamples.indices) {
            assertEquals("Disabled EQ should pass samples untouched at $i", inputSamples[i], disabledOut[i])
        }

        // 2. Enabled but flat 0 dB curve
        processor.setEnabled(true)
        processor.updateCurve(List(10) { 0f }, preampDb = 0f)
        assertTrue(processor.isBypassed())
        val flatOut = processSamples(processor, inputSamples, channels = 2)
        assertEquals(inputSamples.size, flatOut.size)
        for (i in inputSamples.indices) {
            assertEquals("Flat EQ should pass samples untouched at $i", inputSamples[i], flatOut[i])
        }
    }

    @Test
    fun bassBandBoostAndCutModifyLowFrequencyAmplitude() {
        val sampleRate = 48000
        val frames = 4800 // 100ms = >6 cycles at 62 Hz
        val input = generateStereoSineSamples(freqHz = 62.0, sampleRate = sampleRate, frames = frames, amplitude = 0.25)
        val inputRms = rmsOfTail(input, tailSamples = 2400)

        // +6 dB boost at 62 Hz (band index 1)
        val boostProcessor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            flush()
            setEnabled(true)
            updateCurve(listOf(0f, 6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), preampDb = 0f)
        }
        assertFalse(boostProcessor.isBypassed())
        val boosted = processSamples(boostProcessor, input, channels = 2)
        val boostRatio = rmsOfTail(boosted, tailSamples = 2400) / inputRms
        assertTrue("Expected ~2.0x (+6dB) bass boost ratio, was $boostRatio", boostRatio in 1.75..2.25)

        // -6 dB cut at 62 Hz (band index 1)
        val cutProcessor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            flush()
            setEnabled(true)
            updateCurve(listOf(0f, -6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), preampDb = 0f)
        }
        val cut = processSamples(cutProcessor, input, channels = 2)
        val cutRatio = rmsOfTail(cut, tailSamples = 2400) / inputRms
        assertTrue("Expected ~0.5x (-6dB) bass cut ratio, was $cutRatio", cutRatio in 0.42..0.58)
    }

    @Test
    fun trebleBandBoostAndCutModifyHighFrequencyAmplitude() {
        val sampleRate = 48000
        val frames = 2400
        val input = generateStereoSineSamples(freqHz = 8000.0, sampleRate = sampleRate, frames = frames, amplitude = 0.25)
        val inputRms = rmsOfTail(input, tailSamples = 1200)

        // +6 dB boost at 8 kHz (band index 8)
        val boostProcessor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            flush()
            setEnabled(true)
            updateCurve(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 6f, 0f), preampDb = 0f)
        }
        val boosted = processSamples(boostProcessor, input, channels = 2)
        val boostRatio = rmsOfTail(boosted, tailSamples = 1200) / inputRms
        assertTrue("Expected ~2.0x (+6dB) treble boost ratio, was $boostRatio", boostRatio in 1.75..2.25)

        // -6 dB cut at 8 kHz (band index 8)
        val cutProcessor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            flush()
            setEnabled(true)
            updateCurve(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6f, 0f), preampDb = 0f)
        }
        val cut = processSamples(cutProcessor, input, channels = 2)
        val cutRatio = rmsOfTail(cut, tailSamples = 1200) / inputRms
        assertTrue("Expected ~0.5x (-6dB) treble cut ratio, was $cutRatio", cutRatio in 0.42..0.58)
    }

    @Test
    fun preampAttenuationScalesMultiChannelOutputUniformly() {
        val sampleRate = 48000
        val channels = 4
        val frames = 512
        val input = ShortArray(frames * channels) { idx ->
            val frame = idx / channels
            val sample = (sin(2.0 * PI * 1000.0 * frame / sampleRate) * 0.6 * 32767.0).toInt()
            sample.toShort()
        }

        val processor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
            flush()
            setEnabled(true)
            updateCurve(List(10) { 0f }, preampDb = -6.0206f)
        }
        assertFalse(processor.isBypassed())

        val out = processSamples(processor, input, channels = channels)
        val ratio = rmsOfTail(out, tailSamples = out.size) / rmsOfTail(input, tailSamples = input.size)
        assertTrue("Expected ~0.5x (-6dB) preamp attenuation, was $ratio", ratio in 0.47..0.53)
    }

    @Test
    fun flushAndResetClearChannelFilterStates() {
        val sampleRate = 48000
        val processor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            flush()
            setEnabled(true)
            updateCurve(listOf(12f, 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), preampDb = 0f)
        }

        // Excite biquad internal delay states with a loud impulse/step
        val impulse = ShortArray(256) { 12000 }
        processSamples(processor, impulse, channels = 2)

        // Flush must zero delay lines so subsequent silence produces exact zero output
        processor.flush()
        val silence = ShortArray(256) { 0 }
        val afterFlush = processSamples(processor, silence, channels = 2)
        assertTrue("Post-flush silence should not leak ringing", afterFlush.all { it == 0.toShort() })

        // Excite again, then reset + reconfigure
        processSamples(processor, impulse, channels = 2)
        processor.reset()
        processor.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val afterReset = processSamples(processor, silence, channels = 2)
        assertTrue("Post-reset silence should not leak ringing", afterReset.all { it == 0.toShort() })
    }

    @Test
    fun autoEqRepositoryAndProfilesProvideValidCuratedCurves() {
        assertTrue(AutoEqRepository.curatedPresets.size >= 20)
        assertEquals(AutoEqRepository.curatedPresets.size, AutoEqProfiles.ALL_PROFILES.size)
        for (preset in AutoEqRepository.curatedPresets) {
            assertEquals("Preset ${preset.name} must have 10 ISO bands", 10, preset.bandsDb.size)
        }
        val airPods = AutoEqProfiles.findById("airpods_pro_2")
        assertNotNull(airPods)
        assertEquals("AirPods Pro 2", airPods?.name)
    }

    private fun generateStereoSineSamples(
        freqHz: Double,
        sampleRate: Int,
        frames: Int,
        amplitude: Double
    ): ShortArray {
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val s = (sin(2.0 * PI * freqHz * i / sampleRate) * amplitude * 32767.0)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }

    private fun processSamples(
        processor: EqualizerAudioProcessor,
        samples: ShortArray,
        channels: Int
    ): ShortArray {
        val inputBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
        for (s in samples) {
            inputBuffer.putShort(s)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.getOutput()
        outputBuffer.order(ByteOrder.nativeOrder())
        val result = ShortArray(outputBuffer.remaining() / 2)
        for (i in result.indices) {
            result[i] = outputBuffer.short
        }
        return result
    }

    private fun rmsOfTail(samples: ShortArray, tailSamples: Int): Double {
        val start = (samples.size - tailSamples).coerceAtLeast(0)
        var sumSq = 0.0
        var count = 0
        for (i in start until samples.size) {
            val v = samples[i].toDouble()
            sumSq += v * v
            count++
        }
        return if (count == 0) 0.0 else sqrt(sumSq / count)
    }
}
