package mx.sntss1puebla.credenciales

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import java.util.concurrent.TimeUnit

/** Prefetch only over validated, unmetered Wi-Fi; playback may still stream on mobile. */
class RadioCacheWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    @Volatile private var writer: CacheWriter? = null
    companion object {
        fun wifiAllowed(context: Context): Boolean {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                !manager.isActiveNetworkMetered
        }
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true).build()
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.enqueueUniquePeriodicWork("radio-wifi-cache-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RadioCacheWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
            manager.enqueueUniqueWork("radio-wifi-cache-now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RadioCacheWorker>().setConstraints(constraints).build())
        }
    }
    override fun doWork(): Result {
        if (!wifiAllowed(applicationContext)) return Result.success()
        return try {
            val context = applicationContext
            val cache = RadioAudioCache.get(context)
            val songs = RadioPreferences.order(context, RadioCatalog.loadProgram(192, context).songs)
            val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory().setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000))
            var failed = false
            for (song in songs) {
                if (isStopped || !wifiAllowed(context)) break
                // A 10 GiB capacity, filled progressively when storage and the catalog allow it.
                if (cache.cacheSpace >= RadioAudioCache.CAPACITY - 16L * 1024L * 1024L || context.noBackupFilesDir.usableSpace < 256L * 1024L * 1024L) break
                val config = song.localConfiguration ?: continue
                val key = config.customCacheKey ?: continue
                if (RadioAudioCache.complete(context, key)) continue
                val task = CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(config.uri).setKey(key).build(), null) { _, _, _ ->
                    if (isStopped || !wifiAllowed(context)) writer?.cancel()
                }
                writer = task
                try { task.cache() } catch (_: Exception) { failed = true }
                finally { writer = null }
            }
            if (failed && wifiAllowed(context) && !isStopped) Result.retry() else Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    override fun onStopped() { writer?.cancel(); super.onStopped() }
}
