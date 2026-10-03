package mx.sntss1puebla.credenciales

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioQueueResolverTest {
    @Test
    fun selectingOneTrackRestoresTheFullCatalogAndItsPosition() {
        val first = song("song:1")
        val second = song("song:2")
        val third = song("song:3")

        val result = RadioQueueResolver.resolve(listOf(second), 0, listOf(first, second, third))

        assertEquals(listOf(first, second, third), result.mediaItems)
        assertEquals(1, result.startIndex)
    }

    @Test
    fun filteredRequestedItemsKeepTheSelectedTrackAtTheCorrectQueuePosition() {
        val first = song("song:1")
        val second = song("song:2")
        val third = song("song:3")
        val unrecognized = song("other:99")

        val result = RadioQueueResolver.resolve(
            listOf(third, unrecognized, first),
            2,
            listOf(first, second, third),
        )

        assertEquals(listOf(third, first), result.mediaItems)
        assertEquals(1, result.startIndex)
    }

    private fun song(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()
}
