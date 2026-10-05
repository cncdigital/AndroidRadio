package mx.sntss1puebla.credenciales

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
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

    @Test fun shuffleButtonIsVisibleOnPhoneAndFullscreenPlayer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val phoneLayout = LayoutInflater.from(context).inflate(R.layout.activity_main, null, false)
        val fullscreenLayout = LayoutInflater.from(context).inflate(R.layout.dialog_radio_fullscreen, null, false)
        assertNotNull(phoneLayout.findViewById<Button>(R.id.radio_shuffle))
        assertNotNull(fullscreenLayout.findViewById<Button>(R.id.full_shuffle))
    }

    @Test fun queueAdvancesAndWrapsWithoutEndingPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = tone()
        val transitions = java.util.concurrent.CopyOnWriteArrayList<String>()
        val player = onMain {
            RadioMusicPlayerFactory.create(context, DefaultMediaSourceFactory(context), KaraokeAudioProcessor(), false).apply {
                setAudioAttributes(audioAttributes, false)
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) transitions.add(item?.mediaId.orEmpty())
                    }
                })
                setMediaItems(listOf("song:1", "song:2").map {
                    MediaItem.Builder().setMediaId(it).setUri(Uri.fromFile(wav)).build()
                })
                prepare()
                play()
            }
        }
        try {
            awaitMusic(player)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (transitions.size < 2 && System.nanoTime() < deadline) {
                assertNull(onMain { player.playerError })
                Thread.sleep(50)
            }
            assertEquals(listOf("song:2", "song:1"), transitions.take(2))
            assertTrue(onMain { player.playWhenReady })
            assertNotEquals(Player.STATE_ENDED, onMain { player.playbackState })
        } finally {
            onMain { player.release() }
            wav.delete()
        }
    }

    @Test fun ordinaryControllerKeepsDefaultTransportAndCanResumePlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = tone()
        val player = onMain {
            RadioMusicPlayerFactory.create(context, DefaultMediaSourceFactory(context), KaraokeAudioProcessor(), false).apply {
                setAudioAttributes(audioAttributes, false)
                volume = 0f
                setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
                prepare()
                play()
            }
        }
        awaitMusic(player)
        val session = onMain {
            MediaLibraryService.MediaLibrarySession.Builder(context, player, RadioPlaybackService().callback).build()
        }
        val future = onMain { MediaController.Builder(context, session.token).buildAsync() }
        var controller: MediaController? = null
        try {
            controller = future.get(10, TimeUnit.SECONDS)
            onMain {
                // The Auto-specific command filter must not affect the phone/app controller.
                assertTrue("Default seek-back is missing", controller!!.availableCommands.contains(Player.COMMAND_SEEK_BACK))
                assertTrue("Default seek-forward is missing", controller!!.availableCommands.contains(Player.COMMAND_SEEK_FORWARD))
                assertTrue("Play/pause is missing", controller!!.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
                assertTrue(
                    "Phone controller cannot shuffle upcoming songs",
                    controller!!.isSessionCommandAvailable(SessionCommand("radio.queue.shuffle", Bundle.EMPTY))
                )
                controller!!.play()
            }
            awaitMusic(player)
            onMain { controller!!.pause() }
            val pauseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (onMain { player.playWhenReady } && System.nanoTime() < pauseDeadline) Thread.sleep(25)
            assertFalse("Controller pause did not reach the player", onMain { player.playWhenReady })
            onMain { controller!!.play() }
            awaitMusic(player)
        } finally {
            onMain { controller?.release(); session.release(); player.release() }
            wav.delete()
        }
    }

    @Test fun normalAndKaraokeOutputActuallyAdvanceAndResumeAfterStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = tone()
        for (karaoke in listOf(false, true, false)) {
            val player = onMain {
                RadioMusicPlayerFactory.create(context, DefaultMediaSourceFactory(context), KaraokeAudioProcessor().apply { enabled = karaoke }, karaoke).apply {
                    // The instrumentation process is not a foreground music UI on Android 15+.
                    // Test the actual output pipeline without requesting background audio focus.
                    setAudioAttributes(audioAttributes, false)
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
