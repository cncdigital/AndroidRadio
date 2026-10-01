package mx.sntss1puebla.credenciales

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/** Stereo centre reduction on decoded PCM. The original file and voice/commercial audio stay intact. */
class KaraokeAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled = false
    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (format.channelCount == 2 && format.encoding == C.ENCODING_PCM_16BIT) format
        else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(input: ByteBuffer) {
        val output = replaceOutputBuffer(input.remaining())
        val reduce = enabled
        while (input.remaining() >= 4) {
            val left = input.short.toInt()
            val right = input.short.toInt()
            val centre = if (reduce) ((left + right) * 0.4).toInt() else 0
            output.putShort((left - centre).coerceIn(-32768, 32767).toShort())
            output.putShort((right - centre).coerceIn(-32768, 32767).toShort())
        }
        output.put(input)
        output.flip()
    }
}
