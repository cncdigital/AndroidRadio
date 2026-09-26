package mx.sntss1puebla.credenciales

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.net.Uri

/** The car receives the public listening catalog, without administrative records. */
internal object RadioCatalog {
    data class Fact(val id: String, val text: String)
    data class Program(val songs: List<MediaItem>, val commercials: List<MediaItem>, val intervalMinutes: Int, val facts: List<Fact>)
    const val ORIGIN = "https://sntss1puebla.com"
    const val ROOT_ID = "radio-root"
    const val SONGS_ID = "radio-songs"

    fun loadSongs(context: Context? = null): List<MediaItem> = loadProgram(context).songs

    fun loadProgram(context: Context? = null): Program {
        val connection = (URL("$ORIGIN/api/radio/catalog").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw java.io.IOException("Radio catalog unavailable")
            // A bounded response avoids retaining a potentially large library in the car service.
            val body = connection.inputStream.bufferedReader().use { it.readTextLimited(8_000_000) }
            val data = JSONObject(body)
            fun playlist(key: String, commercial: Boolean): List<MediaItem> {
              val tracks = data.optJSONArray(key) ?: return emptyList()
              return buildList {
                for (index in 0 until minOf(tracks.length(), 2_000)) {
                    val track = tracks.optJSONObject(index) ?: continue
                    val id = track.optInt("id")
                    val title = track.optString("title").trim()
                    if (id <= 0 || title.isEmpty()) continue
                    val qualities = (0 until (track.optJSONArray("availableQualities")?.length() ?: 0))
                        .mapNotNull { track.optJSONArray("availableQualities")?.optInt(it) }
                        .toSet()
                    val preferredQuality = preferredQuality(context, qualities)
                    val preferred = if (preferredQuality < 320) "?quality=$preferredQuality" else ""
                    val cover = if (commercial) null else track.optString("coverUrl").takeIf { it.startsWith("/api/radio/cover/") }
                    add(MediaItem.Builder()
                        .setMediaId("${if (commercial) "commercial" else "song"}:$id")
                        .setUri("$ORIGIN/api/radio/${if (commercial) "commercial" else "audio"}/$id${if (commercial) "" else preferred}")
                        .setMimeType(MimeTypes.AUDIO_MPEG)
                        .setMediaMetadata(MediaMetadata.Builder()
                            .setTitle(if (commercial) "Comercial · $title" else title)
                            .setArtist(track.optString("artist").ifBlank { "Radio Sindical" })
                            .setAlbumTitle(track.optString("album").ifBlank { "Radio Sindical · SNTSS Sección I Puebla" })
                            .setAlbumArtist("Radio Sindical · SNTSS Sección I Puebla")
                            .setArtworkUri(cover?.let { RadioArtworkProvider.uri(id) }
                                ?: Uri.parse("android.resource://mx.sntss1puebla.credenciales/drawable/ic_radio"))
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .build())
                        .build())
                }
              }
            }
            val facts = data.optJSONArray("facts")
            Program(playlist("tracks", false), playlist("commercials", true),
                data.optInt("commercialIntervalMinutes").coerceIn(0, 180),
                (0 until minOf(facts?.length() ?: 0, 500)).mapNotNull { index ->
                    val fact = facts?.optJSONObject(index) ?: return@mapNotNull null
                    val id = fact.optString("id").take(100)
                    val words = fact.optString("text").take(500)
                    if (id.isEmpty() || words.isEmpty()) null else Fact(id, words)
                })
        } finally {
            connection.disconnect()
        }
    }

    /** Selects a stream that the current network can sustain without buffering. */
    private fun preferredQuality(context: Context?, available: Set<Int>): Int {
        val desired = runCatching {
            val manager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = manager?.activeNetwork
            val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return@runCatching 96
            val downstream = capabilities.linkDownstreamBandwidthKbps
            when {
                downstream in 1..1_999 -> 96
                downstream in 2_000..5_999 -> 192
                downstream >= 6_000 -> 320
                else -> 192
            }
        }.getOrDefault(192)
        return when {
            desired <= 96 && 96 in available -> 96
            desired <= 192 && 192 in available -> 192
            320 in available -> 320
            192 in available -> 192
            else -> 96
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
