package mx.sntss1puebla.credenciales

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bit-exact PCM tee after karaoke, before device effects/volume. Never consumes microphone audio. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RadioAnalysisAudioProcessor(
    val history: RadioAudioHistory,
    private val requested: () -> Boolean = { RadioAudioAnalysis.requested },
) : BaseAudioProcessor() {
    private val analyzer = RadioPcmAnalyzer(history)
    private var originUs = Long.MIN_VALUE
    private var frames = 0L
    private var nextInputTimeUs = Long.MIN_VALUE
    private var analyzing = false

    fun anchor(timeUs: Long) { nextInputTimeUs = timeUs }
    fun discontinuity() { originUs = Long.MIN_VALUE; frames = 0L; analyzer.reset(); history.clear() }

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (format.encoding == C.ENCODING_PCM_16BIT && format.channelCount in 1..2 && format.sampleRate in 8000..192000) format
        else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush() {
        if (inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) analyzer.configure(inputAudioFormat.sampleRate)
        discontinuity()
        analyzing = false
    }
    override fun onReset() { discontinuity(); nextInputTimeUs = Long.MIN_VALUE; analyzing = false }

    override fun queueInput(input: ByteBuffer) {
        if (!input.hasRemaining()) return
        if (originUs == Long.MIN_VALUE) originUs = nextInputTimeUs
        val bytesPerFrame = inputAudioFormat.channelCount * 2
        val sampleRate = inputAudioFormat.sampleRate
        val start = input.position()
        val end = input.limit()
        val active = requested() && history.music && originUs != Long.MIN_VALUE
        if (active != analyzing) { analyzer.reset(); history.clear(); analyzing = active }
        if (active) {
            input.order(ByteOrder.LITTLE_ENDIAN)
            var index = start
            while (index + bytesPerFrame <= end) {
                val l = input.getShort(index) / 32768f
                val r = if (bytesPerFrame == 4) input.getShort(index + 2) / 32768f else l
                analyzer.sample(l, r, originUs + frames * 1_000_000L / sampleRate)
                frames++
                index += bytesPerFrame
            }
        } else {
            frames += input.remaining() / bytesPerFrame
        }
        // BaseAudioProcessor reuses/grows this direct buffer; no per-frame samples or objects escape.
        val output = replaceOutputBuffer(input.remaining())
        output.put(input)
        output.flip()
    }
}
