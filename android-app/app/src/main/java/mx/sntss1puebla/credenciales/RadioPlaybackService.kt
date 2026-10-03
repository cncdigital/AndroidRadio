package mx.sntss1puebla.credenciales

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Handler
import android.os.Looper
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors
import java.io.File

/** Exposes the authorized portal library to Android Auto's driver-safe media UI. */
class RadioPlaybackService : MediaLibraryService() {
    companion object {
        const val ACTION_SET_KARAOKE = "mx.sntss1puebla.credenciales.action.SET_KARAOKE"
        const val ACTION_CLOSE_RADIO = "mx.sntss1puebla.credenciales.action.CLOSE_RADIO"
        const val ACTION_UPDATE_TASTES = "mx.sntss1puebla.credenciales.action.UPDATE_TASTES"
        const val ACTION_NETWORK_WARNING = "mx.sntss1puebla.credenciales.action.NETWORK_WARNING"
        private const val RADIO_VOICE_NORMALIZED_VOLUME = 1f // ganancia unitaria; no es una medida acústica
    }

    private val catalogExecutor = Executors.newSingleThreadExecutor()
    private val songsLoadingLock = Any()
    @Volatile private var songsLoading: ListenableFuture<List<MediaItem>>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var voicePlayer: ExoPlayer
    private val karaokeProcessor = KaraokeAudioProcessor()
    private var karaokeActive = false
    private var announcementStarted = false
    private var completedSongs = 0
    private var elapsedMusicMs = 0L
    private var lastSongPositionMs = 0L
    private var commercials: List<MediaItem> = emptyList()
    private var intervalMinutes = 0
    private var commercialIndex = 0
    private var facts: List<RadioCatalog.Fact> = emptyList()
    private var lastFactId: String? = null
    private var tastesPending = false
    private var pendingFact = false
    private var pendingIntroduction = false
    private var lastMediaId: String? = null
    private val catalogRefresh = object : Runnable {
        override fun run() {
            if (player.mediaItemCount > 0) catalogExecutor.execute { runCatching { refreshCatalog() } }
            mainHandler.postDelayed(this, 60_000)
        }
    }
    private val positionRefresh = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying && player.currentMediaItem?.mediaId?.startsWith("song:") == true)
                lastSongPositionMs = player.currentPosition
            mainHandler.postDelayed(this, 1_000)
        }
    }
    private var activeAnnouncement: String? = null
    private var lastNetworkWarningAt = 0L
    private val networkProbe = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                catalogExecutor.execute {
                    if (probeConnectionIsSlow()) mainHandler.post { announceConnectionWarning() }
                }
            }
            mainHandler.postDelayed(this, 30_000)
        }
    }
    @Volatile private var songs: List<MediaItem> = emptyList()
    private lateinit var httpFactory: DefaultHttpDataSource.Factory
    private lateinit var audioCache: SimpleCache
    private lateinit var cacheFactory: CacheDataSource.Factory
    private lateinit var player: ExoPlayer
    private lateinit var librarySession: MediaLibrarySession
    private lateinit var musicListener: Player.Listener

    private val karaokeCommand = SessionCommand("radio.karaoke.toggle", Bundle.EMPTY)
    private fun karaokeButton() = CommandButton.Builder(CommandButton.ICON_SETTINGS)
        .setDisplayName(if (karaokeActive) "Apagar karaoke" else "Karaoke · reducir voz")
        .setSessionCommand(karaokeCommand)
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build()

    private fun setKaraoke(enabled: Boolean) {
        val changed = karaokeActive != enabled
        karaokeActive = enabled
        karaokeProcessor.enabled = enabled && player.currentMediaItem?.mediaId?.startsWith("song:") == true
        if (changed && ::librarySession.isInitialized) rebuildMusicPlayer(enabled)
        if (enabled) finishAnnouncement()
        if (::librarySession.isInitialized) {
            librarySession.setSessionExtras(Bundle().apply { putBoolean("radio.karaoke.active", enabled) })
            librarySession.connectedControllers.filter { librarySession.isAutoCompanionController(it) || librarySession.isAutomotiveController(it) }
                .forEach { librarySession.setMediaButtonPreferences(it, automotiveButtons()) }
        }
    }

    private fun loadSongsIfNeeded(): ListenableFuture<List<MediaItem>> {
        if (songs.isNotEmpty()) return Futures.immediateFuture(songs)
        synchronized(songsLoadingLock) {
            if (songs.isNotEmpty()) return Futures.immediateFuture(songs)
            songsLoading?.let { return it }
            val loading = SettableFuture.create<List<MediaItem>>()
            songsLoading = loading
            catalogExecutor.execute {
                try {
                    refreshCatalog()
                    loading.set(songs)
                } catch (error: Exception) {
                    loading.setException(error)
                } finally {
                    synchronized(songsLoadingLock) {
                        if (songsLoading === loading) songsLoading = null
                    }
                }
            }
            return loading
        }
    }

    private fun automotiveButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Canción anterior")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Siguiente canción")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        karaokeButton(),
    )

    private val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (!controller.isTrusted) return super.onConnect(session, controller)
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(karaokeCommand).build())
            if (session.isAutoCompanionController(controller) || session.isAutomotiveController(controller))
                result.setMediaButtonPreferences(automotiveButtons())
            return result.build()
        }
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (command.customAction != karaokeCommand.customAction) return super.onCustomCommand(session, controller, command, args)
            setKaraoke(!karaokeActive)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(RadioCatalog.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == RadioCatalog.ROOT_ID)
                return Futures.immediateFuture(LibraryResult.ofItemList(listOf(RadioCatalog.songsFolder()), params))
            if (parentId != RadioCatalog.SONGS_ID || page < 0 || pageSize <= 0)
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val result = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            catalogExecutor.execute {
                try {
                    refreshCatalog()
                    val from = (page.toLong() * pageSize).coerceAtMost(songs.size.toLong()).toInt()
                    val to = (from.toLong() + pageSize).coerceAtMost(songs.size.toLong()).toInt()
                    result.set(LibraryResult.ofItemList(songs.subList(from, to), params))
                } catch (_: Exception) {
                    result.set(LibraryResult.ofItemList(emptyList(), params))
                }
            }
            return result
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val fixedItem = when (mediaId) {
                RadioCatalog.ROOT_ID -> RadioCatalog.rootItem()
                RadioCatalog.SONGS_ID -> RadioCatalog.songsFolder()
                else -> null
            }
            if (fixedItem != null) return Futures.immediateFuture(LibraryResult.ofItem(fixedItem, null))
            songs.firstOrNull { it.mediaId == mediaId }?.let {
                return Futures.immediateFuture(LibraryResult.ofItem(it, null))
            }
            val loaded = loadSongsIfNeeded()
            val result = SettableFuture.create<LibraryResult<MediaItem>>()
            loaded.addListener({
                try {
                    val item = loaded.get().firstOrNull { it.mediaId == mediaId }
                    result.set(if (item != null) LibraryResult.ofItem(item, null)
                        else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                } catch (error: Exception) {
                    result.setException(error)
                }
            }, catalogExecutor)
            return result
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val loaded = loadSongsIfNeeded()
            val result = SettableFuture.create<List<MediaItem>>()
            loaded.addListener({
                try {
                    val catalog = loaded.get().associateBy { it.mediaId }
                    result.set(mediaItems.mapNotNull { candidate -> catalog[candidate.mediaId] })
                } catch (error: Exception) {
                    result.setException(error)
                }
            }, catalogExecutor)
            return result
        }

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            browser: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val loaded = loadSongsIfNeeded()
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            loaded.addListener({
                try {
                    val queue = RadioQueueResolver.resolve(mediaItems, startIndex, loaded.get())
                    result.set(MediaSession.MediaItemsWithStartPosition(queue.mediaItems, queue.startIndex, startPositionMs))
                } catch (error: Exception) {
                    result.setException(error)
                }
            }, catalogExecutor)
            return result
        }
    }

    override fun onCreate() {
        super.onCreate()
        httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setUserAgent("SNTSS1Puebla-Radio-Android/1.0")
        audioCache = SimpleCache(
            File(cacheDir, "radio-audio-cache"),
            LeastRecentlyUsedCacheEvictor(250L * 1024L * 1024L),
            StandaloneDatabaseProvider(this),
        )
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        cacheFactory = CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        player = createMusicPlayer(false)
        voicePlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheFactory))
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(), false)
                volume = RADIO_VOICE_NORMALIZED_VOLUME
            }
        voicePlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (activeAnnouncement == null) return
                if (state == Player.STATE_READY && !announcementStarted) {
                    if (!player.isPlaying) finishAnnouncement()
                    else {
                        announcementStarted = true
                        player.volume = 0.40f
                    }
                } else if (state == Player.STATE_ENDED) finishAnnouncement()
            }
            override fun onPlayerError(error: PlaybackException) = finishAnnouncement()
        })
        musicListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                finishAnnouncement()
                if (karaokeActive) {
                    mainHandler.post {
                        if (karaokeActive) {
                            setKaraoke(false)
                            sendPlaybackWarning("Se restauró el audio normal porque el efecto karaoke falló. ${error.errorCodeName}")
                        }
                    }
                    return
                }
                sendPlaybackWarning("No se pudo reproducir. Pulsa Reproducir para reintentar. ${error.errorCodeName}")
                val index = player.currentMediaItemIndex
                if (player.currentMediaItem?.mediaId?.startsWith("commercial:") == true && index >= 0) {
                    player.removeMediaItem(index)
                    if (player.mediaItemCount > 0) {
                        player.prepare()
                        player.play()
                    }
                }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                karaokeProcessor.enabled = karaokeActive && item?.mediaId?.startsWith("song:") == true
                finishAnnouncement()
                if (tastesPending && item?.mediaId?.startsWith("song:") == true) mainHandler.post { applyTastes() }
                val previous = lastMediaId
                lastMediaId = item?.mediaId
                if (item?.mediaId?.startsWith("song:") == true && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previous != item.mediaId) {
                    pendingIntroduction = true
                    mainHandler.post { if (player.currentMediaItem?.mediaId == item.mediaId) announceIfDue(item) }
                }
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || item == null) return
                if (previous?.startsWith("commercial:") == true) {
                    // The inserted commercial has finished; remove it from the music queue.
                    val priorIndex = player.currentMediaItemIndex - 1
                    if (priorIndex >= 0) mainHandler.post { if (priorIndex < player.mediaItemCount && player.getMediaItemAt(priorIndex).mediaId == previous) player.removeMediaItem(priorIndex) }
                    if (item.mediaId.startsWith("song:")) announceIfDue(item)
                    return
                }
                if (previous?.startsWith("song:") != true || !item.mediaId.startsWith("song:")) return
                if (songs.size > 1 && previous == songs.last().mediaId && item.mediaId == songs.first().mediaId) {
                    val next = RadioPreferences.order(this@RadioPlaybackService, songs, previous).toMutableList()
                    if (next.first().mediaId == previous) next.add(0, next.removeAt(1))
                    songs = listOf(item) + next.filter { it.mediaId != item.mediaId }
                    player.replaceMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount, songs.drop(1))
                }
                elapsedMusicMs += lastSongPositionMs.coerceAtLeast(0)
                lastSongPositionMs = 0
                completedSongs += 1
                pendingIntroduction = completedSongs % 2 == 0
                pendingFact = completedSongs % 5 == 0
                if (!pendingIntroduction && !pendingFact) {
                    val capsuleTrackId = item.mediaId.removePrefix("song:").toIntOrNull()
                    if (capsuleTrackId != null) catalogExecutor.execute { prewarmCapsule(capsuleTrackId, completedSongs % 16) }
                }
                if (intervalMinutes >= 5 && commercials.isNotEmpty() && elapsedMusicMs >= intervalMinutes * 60_000L) {
                    val commercial = commercials[commercialIndex % commercials.size]
                    commercialIndex += 1
                    elapsedMusicMs = 0
                    val nextIndex = player.currentMediaItemIndex
                    player.addMediaItem(nextIndex, commercial)
                    player.seekTo(nextIndex, 0)
                    return
                }
                announceIfDue(item)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) finishAnnouncement()
                if (isPlaying) player.currentMediaItem?.takeIf { it.mediaId.startsWith("song:") }?.let { announceIfDue(it) }
            }
        }
        player.addListener(musicListener)
        mainHandler.post(positionRefresh)
        mainHandler.postDelayed(catalogRefresh, 60_000)
        mainHandler.postDelayed(networkProbe, 30_000)
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        librarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(openApp)
            .build()
        // Warm the public song catalog without blocking Android Auto's connection.
        loadSongsIfNeeded()
    }

    private fun createMusicPlayer(withKaraoke: Boolean): ExoPlayer = RadioMusicPlayerFactory.create(
        this, DefaultMediaSourceFactory(this).setDataSourceFactory(cacheFactory), karaokeProcessor, withKaraoke,
    )

    /** Switch renderers only when karaoke changes. Normal listening uses ExoPlayer's default sink. */
    private fun rebuildMusicPlayer(withKaraoke: Boolean) {
        finishAnnouncement()
        val old = player
        val queue = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        val index = old.currentMediaItemIndex.coerceAtLeast(0)
        val position = old.currentPosition.coerceAtLeast(0)
        val resume = old.playWhenReady
        old.removeListener(musicListener)
        player = createMusicPlayer(withKaraoke)
        player.addListener(musicListener)
        librarySession.setPlayer(player)
        old.release()
        if (queue.isNotEmpty()) {
            player.setMediaItems(queue, index.coerceAtMost(queue.lastIndex), position)
            player.prepare()
            player.playWhenReady = resume
        }
    }

    private fun sendPlaybackWarning(message: String) {
        sendBroadcast(Intent(ACTION_NETWORK_WARNING).setPackage(packageName).putExtra("message", message))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE_RADIO) {
            if (::player.isInitialized) player.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_UPDATE_TASTES) applyTastes()
        if (intent?.action == ACTION_SET_KARAOKE) {
            setKaraoke(intent.getBooleanExtra("enabled", false))
        }
        // Keep the media service eligible for restart if Android reclaims the
        // process while an active playback session is still in use.
        return START_STICKY
    }

    private fun probeConnectionIsSlow(): Boolean {
        val started = System.nanoTime()
        val connection = runCatching {
            (java.net.URL("${RadioCatalog.ORIGIN}/api/radio/catalog?probe=${System.currentTimeMillis()}").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
                setRequestProperty("Accept", "application/json")
                inputStream.use { stream -> stream.read(ByteArray(512)) }
            }
        }.getOrNull() ?: return true
        connection.disconnect()
        return (System.nanoTime() - started) / 1_000_000 > 2_500
    }

    private fun announceConnectionWarning() {
        val now = System.currentTimeMillis()
        if (now - lastNetworkWarningAt < 120_000 || !::player.isInitialized || !player.isPlaying) return
        lastNetworkWarningAt = now
        val message = "Conexión lenta o inestable: la reproducción podría verse afectada."
        sendBroadcast(Intent(ACTION_NETWORK_WARNING).setPackage(packageName).putExtra("message", message))
    }

    private fun announceIfDue(item: MediaItem) {
        if (karaokeActive) { pendingIntroduction = false; pendingFact = false; return }
        if (!player.playWhenReady || (!pendingIntroduction && !pendingFact)) return
        val trackId = item.mediaId.removePrefix("song:").toIntOrNull() ?: return
        pendingIntroduction = false
        val factDue = pendingFact
        pendingFact = false
        val fact = if (factDue) facts.filter { it.id != lastFactId }.randomOrNull() ?: facts.randomOrNull() else null
        lastFactId = fact?.id ?: lastFactId
        val style = completedSongs % 16
        val url = Uri.parse("https://sntss1puebla.com/api/radio/voice").buildUpon()
            .appendQueryParameter("trackId", trackId.toString())
            .appendQueryParameter("style", style.toString())
            .appendQueryParameter("client", "android-auto")
            .apply { if (fact != null) appendQueryParameter("factId", fact.id) }
            .build()
        val id = "radio-voice-${System.currentTimeMillis()}"
        activeAnnouncement = id
        announcementStarted = false
        voicePlayer.stop()
        voicePlayer.volume = RADIO_VOICE_NORMALIZED_VOLUME
        voicePlayer.setMediaItem(MediaItem.fromUri(url))
        voicePlayer.prepare()
        voicePlayer.play()
        // A late request must never interrupt the next song or a user's pause.
        mainHandler.postDelayed({ if (activeAnnouncement == id && !announcementStarted) finishAnnouncement() }, 12_000)
    }

    /** Generate and persist the next song capsule without interrupting playback. */
    private fun prewarmCapsule(trackId: Int, style: Int) {
        runCatching {
            val url = Uri.parse("https://sntss1puebla.com/api/radio/voice").buildUpon()
                .appendQueryParameter("trackId", trackId.toString())
                .appendQueryParameter("style", style.toString())
                .appendQueryParameter("client", "android-auto")
                .appendQueryParameter("prepare", "1")
                .build()
            val connection = (URL(url.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 15_000
                setRequestProperty("Accept", "audio/mpeg")
            }
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8_192)
                while (stream.read(buffer) >= 0) { /* Persisted by the server; discard locally. */ }
            }
            connection.disconnect()
        }
    }

    private fun refreshCatalog() {
        val program = RadioCatalog.loadProgram(RadioCatalog.qualityFor(this))
        if (program.songs.isEmpty() && songs.isNotEmpty()) return
        val previousFirst = getSharedPreferences("radio", MODE_PRIVATE).getString("first", null)
        val existing = songs.map { it.mediaId }.toSet()
        val ordered = if (existing.isEmpty()) RadioPreferences.order(this, program.songs, previousFirst).let { shuffled ->
            if (shuffled.size > 1 && shuffled.first().mediaId == previousFirst)
                shuffled.toMutableList().apply { add(0, removeAt(1)) } else shuffled
        } else songs.mapNotNull { prior -> program.songs.find { it.mediaId == prior.mediaId } } +
            RadioPreferences.order(this, program.songs.filter { it.mediaId !in existing })
        songs = ordered
        if (existing.isEmpty()) ordered.firstOrNull()?.let { getSharedPreferences("radio", MODE_PRIVATE).edit().putString("first", it.mediaId).apply() }
        mainHandler.post {
            commercials = program.commercials
            intervalMinutes = program.intervalMinutes
            facts = program.facts
            if (player.mediaItemCount > 0 && player.currentMediaItem?.mediaId?.startsWith("song:") == true) {
                val current = player.currentMediaItem ?: return@post
                val currentIndex = player.currentMediaItemIndex
                val tail = ordered.dropWhile { it.mediaId != current.mediaId }.drop(1) + ordered.takeWhile { it.mediaId != current.mediaId }
                player.replaceMediaItems(currentIndex + 1, player.mediaItemCount, tail)
            }
        }
    }

    private fun applyTastes() {
        val current = player.currentMediaItem
        val preferred = RadioPreferences.order(this, songs.filter { it.mediaId != current?.mediaId })
        if (current?.mediaId?.startsWith("commercial:") == true) {
            tastesPending = true
            return
        }
        tastesPending = false
        songs = if (current != null) listOfNotNull(songs.find { it.mediaId == current.mediaId }) + preferred else preferred
        if (current != null) player.replaceMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount, preferred)
    }

    private fun finishAnnouncement() {
        if (activeAnnouncement == null) return
        activeAnnouncement = null
        announcementStarted = false
        player.volume = 1f
        voicePlayer.stop()
        voicePlayer.clearMediaItems()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the UI away or minimizing it must not stop the radio. The
        // explicit Cerrar app action above is the only user-requested stop.
        if (!player.playWhenReady && activeAnnouncement == null && player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(catalogRefresh)
        mainHandler.removeCallbacks(positionRefresh)
        mainHandler.removeCallbacks(networkProbe)
        librarySession.release()
        finishAnnouncement()
        voicePlayer.release()
        player.release()
        audioCache.release()
        catalogExecutor.shutdownNow()
        super.onDestroy()
    }
}
