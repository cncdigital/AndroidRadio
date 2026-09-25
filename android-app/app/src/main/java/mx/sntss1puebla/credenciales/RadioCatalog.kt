package mx.sntss1puebla.credenciales

import android.webkit.CookieManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The car receives only songs the signed-in portal already allows this device to play. */
internal object RadioCatalog {
    const val ORIGIN = "https://sntss1puebla.com"
    const val ROOT_ID = "radio-root"
    const val SONGS_ID = "radio-songs"

    fun sessionCookie(): String = CookieManager.getInstance().getCookie(ORIGIN).orEmpty()

    fun loadSongs(): List<MediaItem> {
        val cookies = sessionCookie()
        if (cookies.isBlank()) return emptyList()
        val connection = (URL("$ORIGIN/api/news/mp3").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            instanceFollowRedirects = false
            setRequestProperty("Cookie", cookies)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            // A bounded response avoids retaining a potentially large library in the car service.
            val body = connection.inputStream.bufferedReader().use { it.readTextLimited(8_000_000) }
            val tracks = JSONObject(body).optJSONArray("tracks") ?: return emptyList()
            buildList {
                for (index in 0 until minOf(tracks.length(), 2_000)) {
                    val track = tracks.optJSONObject(index) ?: continue
                    val id = track.optInt("id")
                    val title = track.optString("title").trim()
                    if (id <= 0 || title.isEmpty() || track.optString("kind", "song") != "song") continue
                    val qualities = track.optJSONArray("availableQualities")
                    val preferred = if ((0 until (qualities?.length() ?: 0)).any { qualities?.optInt(it) == 192 }) "?quality=192" else ""
                    add(MediaItem.Builder()
                        .setMediaId("song:$id")
                        .setUri("$ORIGIN/api/news/mp3/$id$preferred")
                        .setMimeType(MimeTypes.AUDIO_MPEG)
                        .setMediaMetadata(MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(track.optString("artist").ifBlank { "Radio Sindical" })
                            .setAlbumTitle(track.optString("album"))
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .build())
                        .build())
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.Reader.readTextLimited(limit: Int): String {
        val body = StringBuilder()
        val buffer = CharArray(4096)
        while (body.length <= limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit + 1 - body.length))
            if (count < 0) return body.toString()
            body.append(buffer, 0, count)
        }
        throw IllegalArgumentException("Radio catalog exceeds permitted size")
    }

    fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle("Radio Sindical")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    fun songsFolder(): MediaItem = MediaItem.Builder()
        .setMediaId(SONGS_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle("Biblioteca musical")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()
}
