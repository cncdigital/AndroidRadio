package mx.sntss1puebla.credenciales

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RadioAudioCacheTest {
    @Test fun cachedQualityIsReusedAcrossPlaybackQualityChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = RadioAudioCache.get(context)
        val key = "song:999999999:1234:192"
        cache.removeResource(key)
        try {
            val factory = CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory { ByteArrayDataSource(ByteArray(4096) { 7 }) }
            CacheWriter(factory.createDataSource(), DataSpec.Builder()
                .setUri("https://example.test/song.mp3").setKey(key).build(), null, null).cache()
            assertTrue(RadioAudioCache.complete(context, key))
            assertSame(cache, RadioAudioCache.get(context.applicationContext))
            val item = MediaItem.Builder().setMediaId("song:999999999")
                .setUri("https://sntss1puebla.com/api/radio/audio/999999999")
                .setMediaMetadata(MediaMetadata.Builder().setExtras(Bundle().apply { putLong("uploadedAtEpoch", 1234) }).build()).build()
            val selected = RadioAudioCache.preferCached(context, item)
            assertEquals(key, selected.localConfiguration?.customCacheKey)
            assertEquals("192", selected.localConfiguration?.uri?.getQueryParameter("quality"))
            assertFalse(RadioAudioCache.complete(context, "song:999999999:1234:320"))
            assertEquals(10L * 1024 * 1024 * 1024, RadioAudioCache.CAPACITY)
        } finally { cache.removeResource(key) }
    }
}
