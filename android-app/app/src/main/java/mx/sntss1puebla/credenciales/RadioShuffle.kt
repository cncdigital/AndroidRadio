package mx.sntss1puebla.credenciales

import androidx.media3.common.MediaItem
import kotlin.random.Random

/** Shuffle upcoming music without moving advertisements or duplicating queue entries. */
internal object RadioShuffle {
    fun upcoming(items: List<MediaItem>, random: Random = Random.Default): List<MediaItem> {
        val music = items.filter { it.mediaId.startsWith("song:") }
        if (music.size < 2) return items
        val shuffled = music.shuffled(random).toMutableList()
        if (shuffled.map { it.mediaId } == music.map { it.mediaId }) {
            shuffled.add(shuffled.removeAt(0))
        }
        val iterator = shuffled.iterator()
        return items.map { if (it.mediaId.startsWith("song:")) iterator.next() else it }
    }
}
