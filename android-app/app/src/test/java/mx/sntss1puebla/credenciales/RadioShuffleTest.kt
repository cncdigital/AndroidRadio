package mx.sntss1puebla.credenciales

import androidx.media3.common.MediaItem
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class RadioShuffleTest {
    @Test fun shufflingKeepsAdvertisementsAndEverySongExactlyOnce() {
        val tail = listOf(item("song:2"), item("commercial:1"), item("song:3"), item("song:4"))
        for (seed in 0 until 100) {
            val result = RadioShuffle.upcoming(tail, Random(seed))
            assertEquals(tail[1], result[1])
            assertEquals(tail.map { it.mediaId }.sorted(), result.map { it.mediaId }.sorted())
            assertNotEquals(tail, result)
        }
    }

    @Test fun twoPendingSongsAlwaysSwitchOrder() {
        val tail = listOf(item("song:2"), item("song:3"))
        for (seed in 0 until 20) assertEquals(tail.reversed(), RadioShuffle.upcoming(tail, Random(seed)))
    }

    @Test fun emptyAndSingleSongQueuesAreUnchanged() {
        for (tail in listOf(emptyList(), listOf(item("song:2")), listOf(item("commercial:1"), item("song:2")))) {
            assertEquals(tail, RadioShuffle.upcoming(tail, Random(0)))
        }
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()
}
