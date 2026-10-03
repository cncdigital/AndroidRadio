package mx.sntss1puebla.credenciales

import android.content.Context
import androidx.media3.common.MediaItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.random.Random

/** Device-local choices; only names present in the listening catalog are offered. */
internal object RadioPreferences {
    data class Tastes(val artists: Set<String>, val genres: Set<String>, val songs: Set<String> = emptySet())
    fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("[\\u0300-\\u036f]"), "").trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    fun read(context: Context): Tastes {
        val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
        return Tastes(prefs.getStringSet("favorite_artists", emptySet())!!.toSet(), prefs.getStringSet("favorite_genres", emptySet())!!.toSet(), prefs.getStringSet("favorite_songs", emptySet())!!.toSet())
    }
    fun save(context: Context, tastes: Tastes) {
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit()
            .putStringSet("favorite_artists", tastes.artists.map(::key).toSet())
            .putStringSet("favorite_genres", tastes.genres.map(::key).toSet())
            .putStringSet("favorite_songs", tastes.songs.filter { Regex("song:[1-9][0-9]{0,12}").matches(it) }.toSet()).apply()
    }
    fun toggleSong(context: Context, id: String) {
        if (!Regex("song:[1-9][0-9]{0,12}").matches(id)) return
        val tastes = read(context)
        val songs = tastes.songs.toMutableSet()
        if (!songs.add(id)) songs.remove(id)
        save(context,tastes.copy(songs=songs))
    }
    fun isFavorite(context: Context, id: String): Boolean = context.getSharedPreferences("radio",Context.MODE_PRIVATE).getStringSet("favorite_songs",emptySet())!!.contains(id)
    fun reset(context: Context) {
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit()
            .remove("favorite_artists").remove("favorite_genres").remove("favorite_songs").apply()
    }
    fun order(context: Context, songs: List<MediaItem>, previousId: String? = null): List<MediaItem> {
        val tastes = read(context)
        if (tastes.artists.isEmpty() && tastes.genres.isEmpty() && tastes.songs.isEmpty()) {
            val history = context.getSharedPreferences("radio_listens", Context.MODE_PRIVATE)
            return RadioDiscovery.order(songs, previousId, { it.mediaId },
                { history.getInt(it.mediaId, 0).coerceAtLeast(0) },
                { it.mediaMetadata.extras?.getLong("uploadedAtEpoch") ?: 0L })
        }
        val ordered = songs.map { song ->
            val weight = 1 + (if (song.mediaId in tastes.songs) 8 else 0) + (if (key(song.mediaMetadata.artist?.toString().orEmpty()) in tastes.artists) 3 else 0) +
                (if (key(song.mediaMetadata.genre?.toString().orEmpty()) in tastes.genres) 3 else 0)
            song to (-ln(Random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / weight)
        }.sortedBy { it.second }.map { it.first }.toMutableList()
        if (ordered.size > 1 && ordered.first().mediaId == previousId) java.util.Collections.swap(ordered, 0, 1)
        return ordered
    }
    fun recordCompleted(context: Context, id: String) {
        if (!Regex("song:[1-9][0-9]{0,12}").matches(id)) return
        val history = context.getSharedPreferences("radio_listens", Context.MODE_PRIVATE)
        val count = history.getInt(id, 0).coerceAtLeast(0)
        history.edit().putInt(id, if (count == Int.MAX_VALUE) count else count + 1).apply()
    }
}
