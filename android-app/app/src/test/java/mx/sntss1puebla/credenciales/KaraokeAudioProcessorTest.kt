package mx.sntss1puebla.credenciales

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class KaraokeAudioProcessorTest {
    private fun process(processor: KaraokeAudioProcessor, left: Int, right: Int): List<Int> {
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(left.toShort()).putShort(right.toShort()).flip()
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output.order(ByteOrder.nativeOrder())
        return listOf(output.short.toInt(), output.short.toInt())
    }
    @Test fun karaokeReducesCentrePreservesSidesAndRestoresOriginalWithoutReconfiguration() {
        val processor = KaraokeAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        assertEquals(listOf(10000, 10000), process(processor, 10000, 10000))
        processor.enabled = true
        assertEquals(listOf(2000, 2000), process(processor, 10000, 10000))
        assertEquals(listOf(30000, -30000), process(processor, 30000, -30000))
        assertEquals(listOf(-2000, -2000), process(processor, -10000, -10000))
        processor.enabled = false
        assertEquals(listOf(1234, -9876), process(processor, 1234, -9876))
    }
    @Test fun monoIsBypassedRatherThanSilenced() {
        val processor = KaraokeAudioProcessor()
        processor.enabled = true
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        assertFalse(processor.isActive)
    }
}
