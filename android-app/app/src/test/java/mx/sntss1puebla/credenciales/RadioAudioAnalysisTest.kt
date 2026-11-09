package mx.sntss1puebla.credenciales

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class RadioAudioAnalysisTest {
    private val rate = 44100
    private fun tone(hz: Double, amplitude: Double = 0.5, antiPhase: Boolean = false): ByteArray {
        val out = ByteBuffer.allocate(2048 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2048) { i ->
            val sample = (sin(i * 2 * PI * hz / rate) * amplitude * 32767).toInt().toShort()
            out.putShort(sample).putShort(if (antiPhase) (-sample).toShort() else sample)
        }
        return out.array()
    }
    private fun setup(requested: () -> Boolean = { true }): Pair<RadioAnalysisAudioProcessor, RadioAudioHistory> {
        val history = RadioAudioHistory().apply { playing = true }
        val processor = RadioAnalysisAudioProcessor(history, requested)
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.anchor(0L)
        return processor to history
    }
    private fun feed(processor: RadioAnalysisAudioProcessor, bytes: ByteArray) {
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.BIG_ENDIAN).put(bytes)
        buffer.flip()
        processor.queueInput(buffer)
        assertFalse(buffer.hasRemaining())
        val actual = ByteArray(bytes.size)
        processor.output.get(actual)
        assertArrayEquals("The visualization must never change PCM", bytes, actual)
    }
    private fun analyze(hz: Double, amplitude: Double = 0.5, antiPhase: Boolean = false): RadioAudioFrame {
        val (processor, history) = setup()
        feed(processor, tone(hz, amplitude, antiPhase))
        history.updatePosition(23_220L)
        return RadioAudioFrame().also { assertTrue(history.read(it)) }
    }
    @Test fun stereoPeakEnergyAndOppositePhaseRemainCorrect() {
        val normal = analyze(1000.0)
        val opposite = analyze(1000.0, antiPhase = true)
        assertEquals(0.5f, normal.peak, 0.001f)
        assertEquals(0.3535f, normal.rms, 0.005f)
        assertEquals(normal.rms, opposite.rms, 0.00001f)
        assertArrayEquals(normal.bands, opposite.bands, 0.00001f)
        assertTrue(normal.mid > 0.8f)
        assertTrue(normal.mid > normal.bass)
    }
    @Test fun bandsFollowFrequencyRatherThanClockAndAmplitudeFollowsPcm() {
        val bass = analyze(100.0)
        val mid = analyze(1000.0)
        val treble = analyze(8000.0)
        assertTrue(bass.bass > bass.mid)
        assertTrue(mid.mid > mid.bass && mid.mid > mid.treble)
        assertTrue(treble.treble > treble.mid)
        val maxBass = bass.bands.indices.maxBy { bass.bands[it] }
        val maxMid = mid.bands.indices.maxBy { mid.bands[it] }
        val maxTreble = treble.bands.indices.maxBy { treble.bands[it] }
        assertTrue(maxBass < maxMid && maxMid < maxTreble)
        assertTrue(analyze(1000.0, 0.05).rms < mid.rms / 5f)
    }
    @Test fun silenceIsValidZeroSignalNotFakeRhythm() {
        val silent = analyze(1000.0, 0.0)
        assertTrue(silent.available)
        assertEquals(0f, silent.rms, 0f)
        assertEquals(0f, silent.peak, 0f)
        assertTrue(silent.bands.all { it == 0f })
        assertEquals(0f, silent.bass + silent.mid + silent.treble, 0f)
    }
    @Test fun readUsesAudiblePositionNotDecodedAheadAndExpires() {
        val history = RadioAudioHistory().apply { playing = true }
        val bands = FloatArray(28)
        history.publish(1_000_000L, 0.2f, 0.3f, bands, 0f, 0f, 0f)
        history.publish(2_000_000L, 0.8f, 0.9f, bands, 0f, 0f, 0f)
        val out = RadioAudioFrame()
        history.updatePosition(999_000L, 100L)
        assertFalse(history.read(out, 100L))
        history.updatePosition(1_020_000L, 100L)
        assertTrue(history.read(out, 100L))
        assertEquals(0.2f, out.rms, 0f)
        assertFalse(history.read(out, 300_000_101L))
        assertFalse(out.available)
        assertEquals(0f, out.rms, 0f)
        history.updatePosition(1_200_000L, 100L)
        assertFalse(history.read(out, 100L))
        history.playing = false
        history.updatePosition(2_000_000L, 100L)
        assertFalse(history.read(out, 100L))
        history.playing = true
        history.music = false
        assertFalse(history.read(out, 100L))
        history.music = true
        history.clear()
        assertFalse(history.read(out, 100L))
    }
    @Test fun historyIsBoundedAndOldFramesAreNotResurrected() {
        val history = RadioAudioHistory().apply { playing = true }
        repeat(200) { history.publish(it * 50_000L, it / 200f, 1f, FloatArray(28), 0f, 0f, 0f) }
        val out = RadioAudioFrame()
        history.updatePosition(0L)
        assertFalse(history.read(out))
        history.updatePosition(9_950_000L)
        assertTrue(history.read(out))
        assertEquals(199 / 200f, out.rms, 0f)
    }
    @Test fun fragmentedInputIsBitExactAndAnalysisResetsOnFlushAndHiddenUi() {
        var demand = true
        val (processor, history) = setup { demand }
        val bytes = tone(1000.0)
        for (start in bytes.indices step 256) feed(processor, bytes.copyOfRange(start, start + 256))
        history.updatePosition(23_220L)
        val frame = RadioAudioFrame()
        assertTrue(history.read(frame))
        processor.flush()
        assertFalse(history.read(frame))
        processor.anchor(0L)
        demand = false
        feed(processor, bytes)
        history.updatePosition(23_220L)
        assertFalse(history.read(frame))
        demand = true
        feed(processor, ByteArray(bytes.size))
        history.updatePosition(69_660L)
        assertTrue(history.read(frame))
        assertEquals(0f, frame.rms, 0f)
    }
    @Test fun lowSampleRatesUseShortWindowsAndRemainFresh() {
        for (sampleRate in listOf(8000, 16000, 22050, 48000, 96000, 192000)) {
            val history = RadioAudioHistory().apply { playing = true }
            val processor = RadioAnalysisAudioProcessor(history) { true }
            processor.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
            processor.flush(); processor.anchor(0L)
            val frames = when { sampleRate > 96000 -> 8192; sampleRate > 48000 -> 4096; sampleRate > 24000 -> 2048; sampleRate > 12000 -> 1024; else -> 512 }
            val bytes = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { i -> bytes.putShort((sin(i * 2 * PI * 440 / sampleRate) * 10000).toInt().toShort()) }
            feed(processor, bytes.array())
            history.updatePosition(frames * 1_000_000L / sampleRate)
            val out = RadioAudioFrame()
            assertTrue("Missing features at $sampleRate Hz", history.read(out))
            assertTrue(out.rms > 0.2f)
        }
    }

    @Test fun monoAndUnsupportedFormatsLeaveOutputIntactOrBypass() {
        val history = RadioAudioHistory().apply { playing = true }
        val processor = RadioAnalysisAudioProcessor(history) { true }
        processor.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        processor.flush(); processor.anchor(0L)
        feed(processor, ByteArray(2048 * 2))
        history.updatePosition(23_220L)
        assertTrue(history.read(RadioAudioFrame()))
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        assertFalse(processor.isActive)
        assertFalse(history.read(RadioAudioFrame()))
    }
}
