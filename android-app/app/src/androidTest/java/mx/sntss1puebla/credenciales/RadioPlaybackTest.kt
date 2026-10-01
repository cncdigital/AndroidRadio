package mx.sntss1puebla.credenciales

import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Runs the production audio output on Android, beyond a compiler/PCM-only check. */
@RunWith(AndroidJUnit4::class)
class RadioPlaybackTest {
    private fun <T> onMain(action: () -> T): T {
        val task = FutureTask<T> { action() }
        Handler(Looper.getMainLooper()).post(task)
        return task.get(10, TimeUnit.SECONDS)
    }
    private fun tone(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rate = 44100
        val frames = rate * 4
        val dataBytes = frames * 4
        val out = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVEfmt ".toByteArray())
        out.putInt(16).putShort(1.toShort()).putShort(2.toShort()).putInt(rate).putInt(rate * 4).putShort(4.toShort()).putShort(16.toShort())
        out.put("data".toByteArray()).putInt(dataBytes)
        repeat(frames) { i ->
            val sample = (kotlin.math.sin(i * 2 * Math.PI * 440 / rate) * 5000).toInt().toShort()
            out.putShort(sample).putShort(sample)
        }
        return File(context.cacheDir, "radio-playback-test.wav").apply { writeBytes(out.array()) }
    }
    private fun awaitMusic(player: ExoPlayer) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val info = onMain { Triple(player.playerError, player.isPlaying, player.currentPosition) }
            assertNull("Audio output failed: ${info.first?.errorCodeName}", info.first)
            if (info.second && info.third >= 200) return
            Thread.sleep(50)
        }
        fail("Reproducir remained stalled at ${onMain { player.currentPosition }} ms")
    }
    @Test fun normalAndKaraokeOutputActuallyAdvanceAndResumeAfterStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = tone()
        for (karaoke in listOf(false, true, false)) {
            val player = onMain {
                RadioMusicPlayerFactory.create(context, DefaultMediaSourceFactory(context), KaraokeAudioProcessor().apply { enabled = karaoke }, karaoke).apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
                    RadioPlaybackActions.resume(this)
                }
            }
            try {
                awaitMusic(player)
                onMain {
                    player.pause()
                    assertFalse(player.playWhenReady)
                    RadioPlaybackActions.resume(player)
                }
                awaitMusic(player)
                onMain {
                    player.stop()
                    player.seekTo(0)
                    assertEquals(Player.STATE_IDLE, player.playbackState)
                    RadioPlaybackActions.resume(player)
                }
                awaitMusic(player)
            } finally { onMain { player.release() } }
        }
        wav.delete()
    }
}
