package mx.sntss1puebla.credenciales

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
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

/** Exposes the authorized portal library to Android Auto's driver-safe media UI. */
class RadioPlaybackService : MediaLibraryService() {
    private val catalogExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var songs: List<MediaItem> = emptyList()
    private lateinit var httpFactory: DefaultHttpDataSource.Factory
    private lateinit var player: ExoPlayer
    private lateinit var librarySession: MediaLibrarySession

    private val callback = object : MediaLibrarySession.Callback {
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
            val item = when (mediaId) {
                RadioCatalog.ROOT_ID -> RadioCatalog.rootItem()
                RadioCatalog.SONGS_ID -> RadioCatalog.songsFolder()
                else -> songs.firstOrNull { it.mediaId == mediaId }
            }
            return Futures.immediateFuture(if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
            mediaItems.mapNotNull { candidate -> songs.firstOrNull { it.mediaId == candidate.mediaId } },
        )

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            browser: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val selected = mediaItems.getOrNull(startIndex)
            val index = songs.indexOfFirst { it.mediaId == selected?.mediaId }
            val queue = if (mediaItems.size == 1 && index >= 0) songs
                else mediaItems.mapNotNull { candidate -> songs.firstOrNull { it.mediaId == candidate.mediaId } }
            val position = if (queue === songs) index else startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(queue, position, startPositionMs))
        }
    }

    override fun onCreate() {
        super.onCreate()
        httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setUserAgent("SNTSS1Puebla-Radio-Android/1.0")
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                repeatMode = Player.REPEAT_MODE_ALL
            }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        librarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(openApp)
            .build()
    }

    private fun refreshCatalog() {
        val cookie = RadioCatalog.sessionCookie()
        httpFactory.setDefaultRequestProperties(if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie))
        songs = if (cookie.isBlank()) emptyList() else RadioCatalog.loadSongs()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        librarySession.release()
        player.release()
        catalogExecutor.shutdownNow()
        super.onDestroy()
    }
}
