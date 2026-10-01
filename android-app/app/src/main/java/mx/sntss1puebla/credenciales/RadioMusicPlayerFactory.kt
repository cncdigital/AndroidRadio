package mx.sntss1puebla.credenciales

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Default output for normal music; karaoke processing is opt-in. */
internal object RadioMusicPlayerFactory {
    fun create(context: Context, sources: androidx.media3.exoplayer.source.MediaSource.Factory, karaokeProcessor: KaraokeAudioProcessor, withKaraoke: Boolean): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(arrayOf(karaokeProcessor))
                    .build()
        }
        return (if (withKaraoke) ExoPlayer.Builder(context, renderers) else ExoPlayer.Builder(context))
            .setMediaSourceFactory(sources)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                repeatMode = Player.REPEAT_MODE_ALL
            }
    }

}
