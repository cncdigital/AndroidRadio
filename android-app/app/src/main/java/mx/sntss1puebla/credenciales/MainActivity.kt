package mx.sntss1puebla.credenciales

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Phone player. Android Auto renders the same MediaLibrarySession using its own safe controls. */
class MainActivity : ComponentActivity() {
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private var browser: MediaBrowser? = null
    private var playWhenConnected = false
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var seek: SeekBar
    private lateinit var progress: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderProgress()
            handler.postDelayed(this, 750)
        }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = findViewById(R.id.song_title)
        artist = findViewById(R.id.song_artist)
        status = findViewById(R.id.radio_status)
        playButton = findViewById(R.id.play_native_radio)
        previousButton = findViewById(R.id.previous_song)
        nextButton = findViewById(R.id.next_song)
        seek = findViewById(R.id.song_seek)
        progress = findViewById(R.id.song_progress)
        playButton.setOnClickListener {
            val player = browser
            if (player == null || player.mediaItemCount == 0) startRadio()
            else if (player.isPlaying) player.pause() else player.play()
        }
        previousButton.setOnClickListener { browser?.seekToPreviousMediaItem() }
        nextButton.setOnClickListener { browser?.seekToNextMediaItem() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) progress.text = formatTime(value.toLong())
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) { browser?.seekTo(bar.progress.toLong()) }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(this, token).buildAsync()
        browserFuture.addListener({
            browser = runCatching { browserFuture.get() }.getOrNull()
            browser?.addListener(listener)
            render()
            if (playWhenConnected) {
                playWhenConnected = false
                startRadio()
            }
        }, ContextCompat.getMainExecutor(this))
        handler.post(tick)
    }

    private fun startRadio() {
        val controller = browser ?: run {
            playWhenConnected = true
            status.setText(R.string.radio_connecting)
            return
        }
        status.setText(R.string.radio_connecting)
        playButton.isEnabled = false
        val songsFuture = controller.getChildren(RadioCatalog.SONGS_ID, 0, 500, null)
        songsFuture.addListener({
            playButton.isEnabled = true
            val songs = runCatching { songsFuture.get().value }.getOrNull()
            if (songs.isNullOrEmpty()) {
                status.setText(R.string.radio_sign_in)
                return@addListener
            }
            controller.setMediaItems(songs)
            controller.prepare()
            controller.play()
            render()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun render() {
        val player = browser
        val item = player?.currentMediaItem
        title.text = item?.mediaMetadata?.title ?: getString(R.string.radio_title)
        artist.text = item?.mediaMetadata?.artist ?: getString(R.string.radio_subtitle)
        playButton.text = getString(if (player?.isPlaying == true) R.string.radio_paused else R.string.play_radio_car)
        previousButton.isEnabled = player?.hasPreviousMediaItem() == true
        nextButton.isEnabled = player?.hasNextMediaItem() == true
        if (item != null) status.setText(R.string.radio_ready)
        renderProgress()
    }

    private fun renderProgress() {
        val player = browser
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        seek.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (!seek.isPressed) seek.progress = (player?.currentPosition ?: 0L).coerceIn(0L, duration).toInt()
        progress.text = "${formatTime(player?.currentPosition ?: 0L)} / ${formatTime(duration)}"
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1_000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        browser?.removeListener(listener)
        MediaBrowser.releaseFuture(browserFuture)
        super.onDestroy()
    }
}
