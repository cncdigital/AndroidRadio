package mx.sntss1puebla.credenciales

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RadioPriorityTest {
    data class Song(val id: String, val uploaded: Long, val favorite: Int = 0)
    private val now = 1_800_000_000_000L
    private fun order(songs: List<Song>, seed: Int, previous: String? = null) = RadioPriority.order(
        songs, previous, { it.id }, { it.uploaded }, { it.favorite }, now, Random(seed))
    @Test fun freshSongsWinMoreOftenButEverySongRemainsInEachRound() {
        val songs = listOf(Song("old", now - 30L * 86_400_000), Song("new", now - 1000))
        val recentFirst = (0..999).count { order(songs, it).first().id == "new" }
        assertTrue(recentFirst > 750)
        assertTrue(recentFirst < 1000)
        repeat(30) { assertEquals(setOf("old", "new"), order(songs, it).map { it.id }.toSet()) }
    }
    @Test fun avoidsImmediateRepeatDeduplicatesAndRetainsFavoriteWeight() {
        val songs = listOf(Song("a", 0), Song("b", 0, 8), Song("a", 0))
        assertTrue((0..999).count { order(songs, it).first().id == "b" } > 800)
        assertEquals("a", order(songs, 1, "b").first().id)
        assertEquals(2, order(songs, 1).size)
        assertTrue(order(emptyList(), 1).isEmpty())
    }
}
