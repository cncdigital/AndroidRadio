package mx.sntss1puebla.credenciales

import android.content.Context
import androidx.media3.common.MediaItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.random.Random

/** Device-local choices; only names present in the listening catalog are offered. */
internal object RadioPreferences {
    data class Tastes(val artists: Set<String>, val genres: Set<String>)
    fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("[\\u0300-\\u036f]"), "").trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    fun read(context: Context): Tastes {
        val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
        return Tastes(prefs.getStringSet("favorite_artists", emptySet())!!.toSet(), prefs.getStringSet("favorite_genres", emptySet())!!.toSet())
    }
    fun save(context: Context, tastes: Tastes) {
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit()
            .putStringSet("favorite_artists", tastes.artists.map(::key).toSet())
            .putStringSet("favorite_genres", tastes.genres.map(::key).toSet()).apply()
    }
    fun reset(context: Context) {
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit()
            .remove("favorite_artists").remove("favorite_genres").apply()
    }
    fun order(context: Context, songs: List<MediaItem>, previousId: String? = null): List<MediaItem> {
        val tastes = read(context)
        val ordered = songs.map { song ->
            val weight = 1 + (if (key(song.mediaMetadata.artist?.toString().orEmpty()) in tastes.artists) 3 else 0) +
                (if (key(song.mediaMetadata.genre?.toString().orEmpty()) in tastes.genres) 3 else 0)
            song to (-ln(Random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / weight)
        }.sortedBy { it.second }.map { it.first }.toMutableList()
        if (ordered.size > 1 && ordered.first().mediaId == previousId) java.util.Collections.swap(ordered, 0, 1)
        return ordered
    }
}
