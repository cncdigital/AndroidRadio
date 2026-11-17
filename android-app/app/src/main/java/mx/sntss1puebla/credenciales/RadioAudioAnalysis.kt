package mx.sntss1puebla.credenciales

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Reusable UI-owned result. These are features, never recorded or shared audio samples. */
internal class RadioAudioFrame {
    var available = false
    var rms = 0f
    var peak = 0f
    var bass = 0f
    var mid = 0f
    var treble = 0f
    val bands = FloatArray(28)
    fun clear() {
        available = false
        rms = 0f; peak = 0f; bass = 0f; mid = 0f; treble = 0f
        bands.fill(0f)
    }
}

/** Only the app's music output can publish. No microphone, audio-session capture or persistence. */
internal object RadioAudioAnalysis {
    private val consumers = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    @Volatile var requested = false
        private set
    @Volatile private var source: RadioAudioHistory? = null

    @Synchronized fun setConsumerActive(consumer: Any, active: Boolean) {
        if (active) consumers.add(consumer) else consumers.remove(consumer)
        requested = consumers.isNotEmpty()
    }
    @Synchronized fun attach(history: RadioAudioHistory) { source = history }
    @Synchronized fun detach(history: RadioAudioHistory) { if (source === history) source = null }
    fun read(frame: RadioAudioFrame): Boolean {
        val current = source
        if (!requested || current == null) { frame.clear(); return false }
        return current.read(frame)
    }
}

/** A bounded 96-window feature history (~4–6 seconds), aligned to the AudioSink playhead.
 * The sink may decode ahead: never display the latest decoded buffer as though it were audible.
 * Only small feature copies are synchronized; neither FFT nor PCM processing holds this lock.
 */
internal class RadioAudioHistory {
    private val frames = Array(96) { RadioAudioFrame() }
    private val timesUs = LongArray(96)
    private var size = 0
    private var next = 0
    private var positionUs = Long.MIN_VALUE
    private var positionAtNs = 0L
    @Volatile var playing = false
    @Volatile var music = true

    @Synchronized fun clear() {
        size = 0; next = 0
        positionUs = Long.MIN_VALUE; positionAtNs = 0L
    }
    @Synchronized fun updatePosition(timeUs: Long, nowNs: Long = System.nanoTime()) {
        positionUs = timeUs; positionAtNs = nowNs
    }
    @Synchronized fun publish(timeUs: Long, rms: Float, peak: Float, bands: FloatArray, bass: Float, mid: Float, treble: Float) {
        val frame = frames[next]
        frame.rms = rms; frame.peak = peak
        frame.bass = bass; frame.mid = mid; frame.treble = treble
        bands.copyInto(frame.bands)
        timesUs[next] = timeUs
        next = (next + 1) % frames.size
        size = min(size + 1, frames.size)
    }
    @Synchronized fun read(out: RadioAudioFrame, nowNs: Long = System.nanoTime()): Boolean {
        out.clear()
        // No wall-clock synthetic continuation if the actual sink clock stops (pause, stall, release).
        if (!playing || !music || size == 0 || positionUs == Long.MIN_VALUE ||
            nowNs - positionAtNs !in 0L..250_000_000L) return false
        var selected = -1
        for (offset in 0 until size) {
            val index = (next - 1 - offset + frames.size) % frames.size
            // Feature timestamp is the centre of its short analysis window. Do not show future audio.
            if (timesUs[index] <= positionUs) { selected = index; break }
        }
        if (selected < 0 || positionUs - timesUs[selected] !in 0L..150_000L) return false
        val frame = frames[selected]
        out.available = true
        out.rms = frame.rms; out.peak = frame.peak
        out.bass = frame.bass; out.mid = frame.mid; out.treble = frame.treble
        frame.bands.copyInto(out.bands)
        return true
    }
}

/** Stereo FFT magnitudes are combined as power, so opposite-phase channels never cancel.
 * Configuration allocates scratch arrays; the PCM hot path, FFT and publication allocate nothing.
 * Hann windows are ~43–64 ms at supported sample rates, with 28 logarithmic 40 Hz–16 kHz display bands.
 */
internal class RadioPcmAnalyzer(private val history: RadioAudioHistory) {
    private var rate = 44100
    private var count = 0
    private var size = 2048
    private var left = FloatArray(size)
    private var right = FloatArray(size)
    private var imaginary = FloatArray(size)
    private var window = FloatArray(size)
    private var cosine = FloatArray(size / 2)
    private var sine = FloatArray(size / 2)
    private var binBands = IntArray(size / 2 + 1)
    private val bandPower = FloatArray(28)
    private val levels = FloatArray(28)
    private var sumSquares = 0.0
    private var maximum = 0f
    private var windowStartUs = 0L

    fun configure(sampleRate: Int) {
        rate = sampleRate
        size = when { rate > 96000 -> 8192; rate > 48000 -> 4096; rate > 24000 -> 2048; rate > 12000 -> 1024; else -> 512 }
        left = FloatArray(size); right = FloatArray(size); imaginary = FloatArray(size)
        window = FloatArray(size) { (0.5 - 0.5 * cos(2.0 * Math.PI * it / (size - 1))).toFloat() }
        cosine = FloatArray(size / 2) { cos(-2.0 * Math.PI * it / size).toFloat() }
        sine = FloatArray(size / 2) { sin(-2.0 * Math.PI * it / size).toFloat() }
        binBands = IntArray(size / 2 + 1) { bin ->
            val hz = bin.toDouble() * rate / size
            if (hz < 40.0 || hz > 16000.0) -1 else
                (ln(hz / 40.0) / ln(16000.0 / 40.0) * 28).toInt().coerceIn(0, 27)
        }
        reset()
    }
    fun reset() { count = 0; sumSquares = 0.0; maximum = 0f }
    fun sample(l: Float, r: Float, presentationTimeUs: Long) {
        if (count == 0) windowStartUs = presentationTimeUs
        sumSquares += (l.toDouble() * l + r.toDouble() * r) * 0.5
        maximum = max(maximum, max(abs(l), abs(r)))
        left[count] = l * window[count]
        right[count] = r * window[count]
        count++
        if (count != size) return
        bandPower.fill(0f)
        transform(left)
        accumulate(left)
        transform(right)
        accumulate(right)
        var bassPower = 0f; var midPower = 0f; var treblePower = 0f
        for (i in levels.indices) {
            // True silence stays zero, while normal recordings have a visible ~54 dB range.
            levels[i] = displayLevel(sqrt(bandPower[i]))
            when { i < 9 -> bassPower += bandPower[i]; i < 20 -> midPower += bandPower[i]; else -> treblePower += bandPower[i] }
        }
        history.publish(windowStartUs + size * 500_000L / rate,
            sqrt(sumSquares / size).toFloat(), maximum, levels,
            displayLevel(sqrt(bassPower)), displayLevel(sqrt(midPower)), displayLevel(sqrt(treblePower)))
        reset()
    }
    private fun displayLevel(amplitude: Float): Float =
        if (amplitude < 0.002f) 0f else ((20f * log10(amplitude) + 54f) / 54f).coerceIn(0f, 1f)

    private fun accumulate(real: FloatArray) {
        // Hann coherent gain is ~0.5. Divide stereo power by two, keeping equal/antiphase energy.
        val normalization = 8f / (size.toFloat() * size)
        for (bin in 1 until size / 2) {
            val band = binBands[bin]
            if (band >= 0) bandPower[band] += (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * normalization
        }
    }
    private fun transform(real: FloatArray) {
        imaginary.fill(0f)
        var reversed = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (reversed and bit != 0) { reversed = reversed xor bit; bit = bit shr 1 }
            reversed = reversed xor bit
            if (i < reversed) { val value = real[i]; real[i] = real[reversed]; real[reversed] = value }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val stride = size / length
            var start = 0
            while (start < size) {
                for (i in 0 until half) {
                    val even = start + i
                    val odd = even + half
                    val twiddle = i * stride
                    val re = real[odd] * cosine[twiddle] - imaginary[odd] * sine[twiddle]
                    val im = real[odd] * sine[twiddle] + imaginary[odd] * cosine[twiddle]
                    real[odd] = real[even] - re; imaginary[odd] = imaginary[even] - im
                    real[even] += re; imaginary[even] += im
                }
                start += length
            }
            length *= 2
        }
    }
}
