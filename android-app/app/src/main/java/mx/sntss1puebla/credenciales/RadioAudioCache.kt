package mx.sntss1puebla.credenciales

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** One process-wide owner shared by playback and background prefetch. */
internal object RadioAudioCache {
    const val CAPACITY = 1024L * 1024L * 1024L
    @Volatile private var instance: SimpleCache? = null
    @Synchronized fun get(context: Context): SimpleCache = instance ?: SimpleCache(
        File(context.applicationContext.noBackupFilesDir, "radio-audio-1gb"),
        LeastRecentlyUsedCacheEvictor(CAPACITY), StandaloneDatabaseProvider(context.applicationContext),
    ).also { instance = it }
    fun complete(context: Context, key: String): Boolean {
        val cache = get(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length != C.LENGTH_UNSET.toLong() && length > 0 && cache.isCached(key, 0, length)
    }
    fun preferCached(context: Context, item: MediaItem): MediaItem {
        if (!item.mediaId.startsWith("song:")) return item
        val uri = item.localConfiguration?.uri ?: return item
        val uploaded = item.mediaMetadata.extras?.getLong("uploadedAtEpoch") ?: 0L
        val requested = uri.getQueryParameter("quality")?.toIntOrNull() ?: 320
        val qualities = listOf(requested, 192, 320, 96).distinct()
        val cached = qualities.firstOrNull { complete(context, key(item.mediaId, uploaded, it)) }
        val quality = cached ?: requested
        val target = uri.buildUpon().clearQuery().apply { if (quality != 320) appendQueryParameter("quality", "$quality") }.build()
        return item.buildUpon().setUri(target).setCustomCacheKey(key(item.mediaId, uploaded, quality)).build()
    }
    private fun key(id: String, uploaded: Long, quality: Int) = "$id:$uploaded:$quality"
}
