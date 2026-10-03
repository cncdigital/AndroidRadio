package mx.sntss1puebla.credenciales

import org.junit.Assert.*
import org.junit.Test

class RadioDiscoveryTest {
    private data class Song(val id: String, val count: Int, val uploaded: Long)
    private fun order(songs: List<Song>, previous: String? = null) =
        RadioDiscovery.order(songs, previous, { it.id }, { it.count }, { it.uploaded })
    @Test fun leastHeardThenNewestWithoutExclusions() {
        val songs = listOf(Song("song:1", 4, 300), Song("song:2", 0, 100), Song("song:3", 0, 200))
        assertEquals(listOf("song:3", "song:2", "song:1"), order(songs).map { it.id })
        assertEquals(3, order(songs).map { it.id }.toSet().size)
        assertEquals("song:1", songs.first().id)
    }
    @Test fun completedListenMovesTrackBehindLessHeardTracks() {
        val songs = listOf(Song("song:1", 1, 300), Song("song:2", 0, 100))
        assertEquals("song:2", order(songs).first().id)
    }
    @Test fun avoidsRepeatAndSupportsEmptyAndSingleCatalogs() {
        val songs = listOf(Song("song:1", 0, 300), Song("song:2", 0, 100))
        assertEquals("song:2", order(songs, "song:1").first().id)
        assertTrue(order(emptyList()).isEmpty())
        assertEquals(songs.take(1), order(songs.take(1), "song:1"))
    }
}
