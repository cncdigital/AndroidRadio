package mx.sntss1puebla.credenciales

import android.Manifest
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import android.app.Dialog
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.widget.ImageView
import android.view.View
import android.view.WindowManager
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Button
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/** Phone player. Android Auto renders the same MediaLibrarySession using its own safe controls. */
class MainActivity : ComponentActivity() {
    companion object {
        private const val RADIO_VERSION_URL = "https://raw.githubusercontent.com/cncdigital/AndroidRadio/apk/radio-version.json"
    }
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private var browser: MediaBrowser? = null
    private var karaokeActive = false
    private var playWhenConnected = false
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var status: TextView
    private lateinit var networkWarning: TextView
    private lateinit var playButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var seek: SeekBar
    private lateinit var progress: TextView
    private lateinit var bitrate: TextView
    private lateinit var qualitySpinner: Spinner
    private lateinit var lyricsView: TextView
    private lateinit var lyricsScroll: ScrollView
    private lateinit var cover: ImageView
    private lateinit var ghost: TextView
    private var fullScreen: Dialog? = null
    private var coverBitmap: Bitmap? = null
    private var currentGhost = ""
    private val lyricExecutor = Executors.newSingleThreadExecutor()
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val tasteArtworkExecutor = Executors.newFixedThreadPool(2)
    private var currentLyricSongId = -1
    private var timedLyrics: List<TimedLyric> = emptyList()
    private var shownLyricLine = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lyricStartX = 0f
    private var lyricStartY = 0f

    private fun floatingLyricDragListener(root: FrameLayout, lyric: TextView): View.OnTouchListener =
        View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    lyricStartX = lyric.translationX
                    lyricStartY = lyric.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = ((root.width - lyric.width) / 2f).coerceAtLeast(0f)
                    val maxY = ((root.height - lyric.height) / 2f).coerceAtLeast(0f)
                    lyric.translationX = (lyricStartX + event.rawX - dragStartX).coerceIn(-maxX, maxX)
                    lyric.translationY = (lyricStartY + event.rawY - dragStartY).coerceIn(-maxY, maxY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    private val networkWarningReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            networkWarning.text = intent?.getStringExtra("message").orEmpty()
            networkWarning.visibility = if (networkWarning.text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            karaokeActive = browser?.sessionExtras?.getBoolean("radio.karaoke.active", karaokeActive) ?: karaokeActive
            renderProgress()
            updateFullScreen()
            handler.postDelayed(this, 200)
        }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = render()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            networkWarning.text = "No se pudo reproducir. Pulsa Reproducir para reintentar. ${error.errorCodeName}"
            networkWarning.visibility = View.VISIBLE
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) networkWarning.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = findViewById(R.id.song_title)
        artist = findViewById(R.id.song_artist)
        status = findViewById(R.id.radio_status)
        networkWarning = findViewById(R.id.network_warning)
        playButton = findViewById(R.id.play_native_radio)
        previousButton = findViewById(R.id.previous_song)
        nextButton = findViewById(R.id.next_song)
        seek = findViewById(R.id.song_seek)
        progress = findViewById(R.id.song_progress)
        bitrate = findViewById(R.id.song_bitrate)
        qualitySpinner = findViewById(R.id.quality_spinner)
        lyricsView = findViewById(R.id.song_lyrics)
        lyricsScroll = findViewById(R.id.lyrics_scroll)
        cover = findViewById(R.id.song_cover)
        ghost = findViewById(R.id.ghost_lyric)
        findViewById<Button>(R.id.close_radio).setOnClickListener { closeRadioApp() }
        findViewById<Button>(R.id.minimize_radio).setOnClickListener { moveTaskToBack(true) }
        findViewById<Button>(R.id.radio_favorite).setOnClickListener { toggleFavorite() }
        findViewById<Button>(R.id.radio_tastes).setOnClickListener { showTastes() }
        findViewById<Button>(R.id.maximize_radio).setOnClickListener { showFullScreen() }
        val qualityLabels = listOf(
            getString(R.string.radio_quality_auto), getString(R.string.radio_quality_96),
            getString(R.string.radio_quality_192), getString(R.string.radio_quality_320)
        )
        qualitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, qualityLabels)
        val savedQuality = getSharedPreferences("radio", MODE_PRIVATE).getInt("quality", 0)
        qualitySpinner.setSelection(listOf(0, 96, 192, 320).indexOf(savedQuality).coerceAtLeast(0))
        qualitySpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = listOf(0, 96, 192, 320)[position]
                getSharedPreferences("radio", MODE_PRIVATE).edit().putInt("quality", selected).apply()
                updateBitrateLabel()
            }
        })
        updateBitrateLabel()
        ContextCompat.registerReceiver(this, networkWarningReceiver, IntentFilter(RadioPlaybackService.ACTION_NETWORK_WARNING), ContextCompat.RECEIVER_NOT_EXPORTED)
        playButton.setOnClickListener {
            val player = browser
            if (player == null || player.mediaItemCount == 0) startRadio()
            else if (player.isPlaying) player.pause() else resumePlayback(player)
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
        checkForRadioUpdate()
    }

    private fun checkForRadioUpdate() {
        updateExecutor.execute {
            val update = runCatching {
                val connection = java.net.URL(RADIO_VERSION_URL).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                try {
                    if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) null
                    else connection.inputStream.bufferedReader().use { RadioAppUpdate.parse(it.readText()) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            val installedVersion = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull() ?: return@execute
            if (update?.isNewerThan(installedVersion.versionCode) != true) return@execute
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                AlertDialog.Builder(this)
                    .setTitle("Actualización disponible")
                    .setMessage("Ya está disponible Radio Sindical ${update.versionName}. Tu versión es ${installedVersion.versionName}. Actualiza para tener las últimas mejoras.")
                    .setNegativeButton("Después", null)
                    .setPositiveButton("Actualizar") { _, _ ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.apkUrl)))
                        }.onFailure {
                            Toast.makeText(this, "No se pudo abrir la descarga. Intenta desde la página de Radio Sindical.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .show()
            }
        }
    }

    private fun resumePlayback(player: MediaBrowser) {
        networkWarning.visibility = View.GONE
        RadioPlaybackActions.resume(player)
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

    private fun showTastes() {
        val controller = browser ?: return
        val future = controller.getChildren(RadioCatalog.SONGS_ID, 0, 2000, null)
        future.addListener({
            val songs = runCatching { future.get().value }.getOrNull()
            if (songs.isNullOrEmpty()) {
                Toast.makeText(this, "La biblioteca no está disponible. Intenta de nuevo.", Toast.LENGTH_LONG).show()
                return@addListener
            }
            val tastes = RadioPreferences.read(this)
            val artists = tastes.artists.toMutableSet()
            val genres = tastes.genres.toMutableSet()
            val options = songs.filter { !it.mediaMetadata.artist.isNullOrBlank() && it.mediaMetadata.artist.toString() != "Radio Sindical" }
                .groupBy { RadioPreferences.key(it.mediaMetadata.artist.toString()) }
                .entries.sortedBy { it.value.first().mediaMetadata.artist.toString() }
            val genreNames = songs.map { it.mediaMetadata.genre?.toString().orEmpty() }.filter { it.isNotBlank() }
                .distinctBy(RadioPreferences::key).sorted()
            val pages = maxOf(1, (options.size + 5) / 6)
            var page = 0
            var genreStep = false
            var generation = 0
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 12, 20, 12)
            }
            val scroll = ScrollView(this).apply { addView(content) }
            val dialog = AlertDialog.Builder(this).setTitle("Mis gustos musicales").setView(scroll)
                .setPositiveButton("Siguiente", null).setNeutralButton("Anterior", null)
                .setNegativeButton("Cancelar", null).create()
            fun renderChoices() {
                generation += 1
                val activeGeneration = generation
                content.removeAllViews()
                content.addView(TextView(this).apply {
                    text = if (genreStep) "Último paso: géneros musicales" else "Artistas: pantalla ${page + 1} de $pages"
                    textSize = 18f; setPadding(0, 0, 0, 12)
                })
                content.addView(TextView(this).apply {
                    text = "Toca tus favoritos. Tu selección se conserva al avanzar y se guarda al finalizar."
                    textSize = 16f; setPadding(0, 0, 0, 12)
                })
                if (!genreStep) {
                    options.drop(page * 6).take(6).chunked(2).forEach { row ->
                        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        row.forEach { entry ->
                            val name = entry.value.first().mediaMetadata.artist.toString()
                            val selected = CheckBox(this).apply { text = name; textSize = 16f; isChecked = entry.key in artists }
                            val background = GradientDrawable().apply { cornerRadius = 24f; setColor(Color.rgb(19, 48, 70)) }
                            val card = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 8)
                                this.background = background
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(5, 5, 5, 5) }
                            }
                            val picture = ImageView(this).apply {
                                setImageResource(R.drawable.ic_radio); scaleType = ImageView.ScaleType.CENTER_CROP
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (125 * resources.displayMetrics.density).toInt())
                            }
                            selected.setTextColor(Color.WHITE)
                            fun showSelection() { background.setStroke((if (selected.isChecked) 3 else 1) * resources.displayMetrics.density.toInt().coerceAtLeast(1), if (selected.isChecked) Color.rgb(255, 206, 87) else Color.rgb(62, 91, 111)) }
                            selected.setOnCheckedChangeListener { _, checked ->
                                if (checked) artists.add(entry.key) else artists.remove(entry.key)
                                showSelection()
                            }
                            card.setOnClickListener { selected.isChecked = !selected.isChecked }
                            showSelection(); card.addView(picture); card.addView(selected); line.addView(card)
                            val uri = entry.value.firstOrNull { it.mediaMetadata.artworkUri?.scheme == "content" }?.mediaMetadata?.artworkUri
                            if (uri != null) tasteArtworkExecutor.execute {
                                val bitmap = runCatching {
                                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    bytes?.let {
                                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                                        if (bounds.outWidth > 0 && bounds.outHeight > 0) BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 384).coerceAtLeast(1) }) else null
                                    }
                                }.getOrNull()
                                runOnUiThread { if (!isDestroyed && dialog.isShowing && generation == activeGeneration && bitmap != null) picture.setImageBitmap(bitmap) }
                            }
                        }
                        content.addView(line)
                    }
                    content.addView(TextView(this).apply { text = "Imágenes de las portadas de la biblioteca."; textSize = 14f; setPadding(0, 12, 0, 12) })
                } else {
                    genreNames.forEach { name ->
                        val key = RadioPreferences.key(name)
                        content.addView(CheckBox(this).apply {
                            text = name; textSize = 16f; isChecked = key in genres
                            setOnCheckedChangeListener { _, checked -> if (checked) genres.add(key) else genres.remove(key) }
                        })
                    }
                    if (genreNames.isEmpty()) content.addView(TextView(this).apply { text = "Aún no hay géneros registrados. Puedes guardar tus artistas favoritos."; textSize = 16f })
                }
                content.addView(Button(this).apply {
                    text = "Me gusta de todo"
                    setOnClickListener { RadioPreferences.reset(this@MainActivity); applyTastes(); dialog.dismiss() }
                })
                content.addView(Button(this).apply {
                    text = "Reiniciar gustos"
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setMessage("¿Reiniciar tus artistas y géneros favoritos?")
                            .setPositiveButton("Reiniciar") { _, _ -> RadioPreferences.reset(this@MainActivity); applyTastes(); dialog.dismiss() }
                            .setNegativeButton("Cancelar", null).show()
                    }
                })
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = genreStep || page > 0
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = if (genreStep) "Finalizar y guardar" else if (page + 1 == pages) "Siguiente: géneros" else "Siguiente"
            }
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (genreStep) genreStep = false else page = (page - 1).coerceAtLeast(0)
                renderChoices(); scroll.scrollTo(0, 0)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (genreStep) {
                    RadioPreferences.save(this, RadioPreferences.Tastes(artists, genres, RadioPreferences.read(this).songs)); applyTastes(); dialog.dismiss()
                } else {
                    if (page + 1 < pages) page += 1 else genreStep = true
                    renderChoices(); scroll.scrollTo(0, 0)
                }
            }
            renderChoices()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFavorite() {
        val id = browser?.currentMediaItem?.mediaId ?: return
        if (!id.startsWith("song:")) return
        RadioPreferences.toggleSong(this,id)
        startService(Intent(this,RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_TASTES))
        updateFavoriteButtons()
    }
    private fun updateFavoriteButtons() {
        val id = browser?.currentMediaItem?.mediaId.orEmpty()
        val selected = RadioPreferences.isFavorite(this,id)
        listOfNotNull(findViewById<Button>(R.id.radio_favorite),fullScreen?.findViewById<Button>(R.id.full_favorite)).forEach {
            it.text = if (selected) "♥ Favorita" else "♡ Favorito"
            it.contentDescription = if (selected) "Quitar canción de favoritos" else "Guardar canción favorita"
            it.isEnabled = id.startsWith("song:")
            it.isSelected = selected
            it.setTextColor(if (selected) Color.rgb(173,23,70) else Color.rgb(7,29,54))
        }
    }
    private fun applyTastes() {
        startService(Intent(this, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_TASTES))
        Toast.makeText(this, "Gustos guardados. Se aplican a las próximas canciones.", Toast.LENGTH_SHORT).show()
    }

    private fun closeRadioApp() {
        browser?.stop()
        startService(Intent(this, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_CLOSE_RADIO))
        finishAndRemoveTask()
    }

    private fun render() {
        val player = browser
        updateFavoriteButtons()
        val item = player?.currentMediaItem
        title.text = item?.mediaMetadata?.title ?: getString(R.string.radio_title)
        artist.text = item?.mediaMetadata?.artist ?: getString(R.string.radio_subtitle)
        playButton.text = getString(if (player?.isPlaying == true) R.string.radio_paused else R.string.play_radio_car)
        previousButton.isEnabled = player?.hasPreviousMediaItem() == true
        nextButton.isEnabled = player?.hasNextMediaItem() == true
        if (item != null) status.setText(R.string.radio_ready)
        val songId = item?.mediaId?.removePrefix("song:")?.toIntOrNull() ?: -1
        if (songId != currentLyricSongId) {
            currentLyricSongId = songId
            coverBitmap = null
            currentGhost = ""
            cover.setImageResource(R.drawable.ic_radio)
            ghost.visibility = View.GONE
            timedLyrics = emptyList()
            shownLyricLine = -1
            lyricsView.setText(R.string.song_lyrics_empty)
            if (songId > 0 && item?.mediaMetadata?.artworkUri?.scheme == "content") lyricExecutor.execute {
                val uri = RadioArtworkProvider.uri(songId)
                val bytes = runCatching { contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() < 3 * 1024 * 1024) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } }.getOrNull()
                val bitmap = bytes?.let {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 512).coerceAtLeast(1)
                        }
                        BitmapFactory.decodeByteArray(it, 0, it.size, options)
                    } else null
                }
                runOnUiThread {
                    if (!isDestroyed && currentLyricSongId == songId && bitmap != null) {
                        coverBitmap = bitmap
                        cover.setImageBitmap(bitmap)
                        fullScreen?.findViewById<ImageView>(R.id.full_cover)?.setImageBitmap(bitmap)
                    }
                }
            }
            if (songId > 0) lyricExecutor.execute {
                val raw = runCatching { RadioLyrics.fetch(songId) }.getOrDefault("")
                val parsed = RadioLyrics.parse(raw)
                runOnUiThread {
                    if (currentLyricSongId != songId || isDestroyed) return@runOnUiThread
                    timedLyrics = parsed
                    lyricsView.text = if (parsed.isNotEmpty()) parsed.joinToString("\n") { it.text.ifBlank { "♪" } }
                        else raw.replace(Regex("(?m)^\\[\\d{1,2}:\\d{2}[^]]*]\\s*"), "").ifBlank { getString(R.string.song_lyrics_empty) }
                    if (parsed.isEmpty()) {
                        currentGhost = lyricsView.text.toString().lineSequence().firstOrNull { it.isNotBlank() }?.take(140).orEmpty()
                        updateFullScreen()
                    }
                    lyricsScroll.scrollTo(0, 0)
                    renderLyricProgress()
                }
            }
        }
        renderProgress()
        updateFullScreen()
    }

    private fun renderProgress() {
        val player = browser
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        seek.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (!seek.isPressed) seek.progress = (player?.currentPosition ?: 0L).coerceIn(0L, duration).toInt()
        progress.text = "${formatTime(player?.currentPosition ?: 0L)} / ${formatTime(duration)}"
        updateBitrateLabel()
        renderLyricProgress()
    }

    private fun updateBitrateLabel() {
        if (!::bitrate.isInitialized) return
        val prefs = getSharedPreferences("radio", MODE_PRIVATE)
        val manual = prefs.getInt("quality", 0)
        val effective = if (manual in setOf(96, 192, 320)) manual else RadioCatalog.qualityFor(this)
        bitrate.text = "Bitrate: ${effective} kbps${if (manual == 0) " · automático" else " · manual"}"
    }

    private fun renderLyricProgress() {
        if (timedLyrics.isEmpty()) return
        val position = browser?.currentPosition ?: return
        val current = timedLyrics.indexOfLast { it.atMs <= position }
        if (current < 0) {
            shownLyricLine = -1
            currentGhost = "La letra comienza enseguida"
            ghost.visibility = View.GONE
            lyricsView.text = timedLyrics.joinToString("\n") { it.text.ifBlank { "♪" } }
            updateFullScreen()
            return
        }
        if (current == shownLyricLine) {
            ghost.text = coloredCurrentLyric()
            return
        }
        shownLyricLine = current
        val offsets = timedLyrics.map { it.text.ifBlank { "♪" } }
        val full = offsets.joinToString("\n")
        val begin = offsets.take(current).sumOf { it.length + 1 }
        val end = begin + offsets[current].length
        val styled = SpannableString(full)
        styled.setSpan(ForegroundColorSpan(Color.rgb(242, 179, 33)), begin, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        styled.setSpan(StyleSpan(Typeface.BOLD), begin, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        styled.setSpan(RelativeSizeSpan(1.3f), begin, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        lyricsView.text = styled
        ghost.text = coloredCurrentLyric()
        currentGhost = offsets[current]
        ghost.visibility = View.VISIBLE
        updateFullScreen()
        lyricsView.post {
            val line = lyricsView.layout?.getLineForOffset(begin) ?: return@post
            lyricsScroll.smoothScrollTo(0, (lyricsView.layout.getLineTop(line) - lyricsScroll.height / 3).coerceAtLeast(0))
        }
    }

    private fun coloredCurrentLyric(): CharSequence {
        val position = browser?.currentPosition ?: 0L
        val index = timedLyrics.indexOfLast { it.atMs <= position }
        if (index < 0) return currentGhost.ifBlank { getString(R.string.song_lyrics_empty) }
        val text = timedLyrics[index].text.ifBlank { "♪" }
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            for (word in timedLyrics[index].words) {
                if (position>=word.startMs && word.from>=0 && word.to<=text.length && word.to>word.from)
                    setSpan(ForegroundColorSpan(Color.rgb(255,189,53)), word.from, word.to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1_000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun showFullScreen() {
        if (fullScreen?.isShowing == true) return
        val dialog = Dialog(this, android.R.style.Theme_Material_NoActionBar)
        dialog.setContentView(R.layout.dialog_radio_fullscreen)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Android 15+ can draw a full-screen dialog behind the gesture/navigation bar.
        // Keep artwork edge-to-edge while reserving the system insets for the controls.
        dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        val fullRoot = dialog.findViewById<FrameLayout>(R.id.full_root)
        ViewCompat.setOnApplyWindowInsetsListener(fullRoot) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        dialog.setOnDismissListener { setKaraoke(false); if (fullScreen === dialog) fullScreen = null }
        dialog.findViewById<Button>(R.id.full_favorite).setOnClickListener { toggleFavorite() }
        dialog.findViewById<Button>(R.id.full_karaoke).setOnClickListener { setKaraoke(!karaokeActive) }
        dialog.findViewById<Button>(R.id.full_close).setOnClickListener { dialog.dismiss() }
        val fullLyric = dialog.findViewById<TextView>(R.id.full_lyric)
        val dragLyric = floatingLyricDragListener(fullRoot, fullLyric)
        fullLyric.setOnTouchListener(dragLyric)
        dialog.findViewById<Button>(R.id.full_lyric_move).setOnTouchListener(dragLyric)
        dialog.findViewById<Button>(R.id.full_previous).setOnClickListener { browser?.seekToPreviousMediaItem() }
        dialog.findViewById<Button>(R.id.full_next).setOnClickListener { browser?.seekToNextMediaItem() }
        dialog.findViewById<Button>(R.id.full_play).setOnClickListener {
            val player = browser
            if (player == null || player.mediaItemCount == 0) startRadio()
            else if (player.isPlaying) player.pause() else resumePlayback(player)
        }
        fullScreen = dialog
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        ViewCompat.requestApplyInsets(fullRoot)
        updateFullScreen()
        updateFavoriteButtons()
    }

    private fun setKaraoke(enabled: Boolean) {
        karaokeActive = enabled
        startService(Intent(this, RadioPlaybackService::class.java)
            .setAction(RadioPlaybackService.ACTION_SET_KARAOKE).putExtra("enabled", enabled))
        updateFullScreen()
    }

    private fun updateFullScreen() {
        val dialog = fullScreen?.takeIf { it.isShowing } ?: return
        val image = dialog.findViewById<ImageView>(R.id.full_cover)
        if (coverBitmap != null) image.setImageBitmap(coverBitmap)
        else image.setImageResource(R.drawable.ic_radio)
        dialog.findViewById<TextView>(R.id.full_lyric).text = coloredCurrentLyric()
        val position = browser?.currentPosition?.coerceAtLeast(0) ?: 0L
        val duration = browser?.duration ?: 0L
        dialog.findViewById<android.widget.ProgressBar>(R.id.full_progress).progress =
            if (duration > 0) (position.toDouble() / duration * 1000).toInt().coerceIn(0, 1000) else 0
        dialog.findViewById<TextView>(R.id.full_time).text = "${formatTime(position)} / ${if (duration > 0) formatTime(duration) else "—:—"}"
        dialog.findViewById<TextView>(R.id.full_title).text = title.text
        dialog.findViewById<TextView>(R.id.full_artist).text = artist.text
        dialog.findViewById<Button>(R.id.full_play).text = playButton.text
        dialog.findViewById<Button>(R.id.full_karaoke).text = if (karaokeActive) "Apagar karaoke" else "Karaoke · reducir voz"
        dialog.findViewById<Button>(R.id.full_karaoke).isSelected = karaokeActive
    }

    override fun onDestroy() {
        fullScreen?.dismiss()
        handler.removeCallbacks(tick)
        browser?.removeListener(listener)
        unregisterReceiver(networkWarningReceiver)
        lyricExecutor.shutdownNow()
        updateExecutor.shutdownNow()
        tasteArtworkExecutor.shutdownNow()
        MediaBrowser.releaseFuture(browserFuture)
        super.onDestroy()
    }
}
