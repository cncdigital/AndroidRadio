package mx.sntss1puebla.credenciales

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import java.nio.ByteBuffer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Music-only PCM analysis is a bit-exact tee. Karaoke and device effects remain opt-in. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object RadioMusicPlayerFactory {
    fun create(context: Context, sources: androidx.media3.exoplayer.source.MediaSource.Factory, karaokeProcessor: KaraokeAudioProcessor, withKaraoke: Boolean): ExoPlayer {
        val history = RadioAudioHistory()
        val analysis = RadioAnalysisAudioProcessor(history)
        RadioAudioAnalysis.attach(history)
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink {
                val processors = if (withKaraoke) arrayOf(karaokeProcessor, analysis) else arrayOf(analysis)
                val sink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(processors)
                    .build()
                return object : ForwardingAudioSink(sink) {
                    // Decode instead of compressed passthrough/offload, which bypass custom processors.
                    override fun supportsFormat(format: Format): Boolean =
                        format.sampleMimeType == MimeTypes.AUDIO_RAW && super.supportsFormat(format)
                    override fun getFormatSupport(format: Format): Int =
                        if (format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format)
                        else AudioSink.SINK_FORMAT_UNSUPPORTED
                    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED
                    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
                        analysis.anchor(presentationTimeUs)
                        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                    }
                    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
                        val position = super.getCurrentPositionUs(sourceEnded)
                        if (position != AudioSink.CURRENT_POSITION_NOT_SET) history.updatePosition(position)
                        return position
                    }
                    override fun handleDiscontinuity() { analysis.discontinuity(); super.handleDiscontinuity() }
                    override fun flush() { analysis.discontinuity(); super.flush() }
                    override fun reset() { analysis.discontinuity(); super.reset() }
                    override fun release() {
                        history.playing = false
                        history.clear()
                        RadioAudioAnalysis.detach(history)
                        super.release()
                    }
                }
            }
        }
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(sources)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        history.playing = isPlaying
                        if (!isPlaying) history.clear()
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        history.music = mediaItem != null && !mediaItem.mediaId.startsWith("commercial:")
                        history.clear()
                    }
                })
            }
    }

}
