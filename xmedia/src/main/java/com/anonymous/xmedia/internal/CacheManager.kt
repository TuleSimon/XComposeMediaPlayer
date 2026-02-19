package com.anonymous.xmedia.internal

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import com.anonymous.xmedia.CacheConfig
import java.io.File
import java.util.concurrent.Executors

/**
 * Manages video caching for XMedia player.
 *
 * Provides:
 * - Disk-based caching with LRU eviction
 * - CacheDataSource.Factory for cached playback
 * - HLS pre-caching functionality
 *
 * This class is thread-safe and manages a single cache instance.
 */
@OptIn(UnstableApi::class)
internal object CacheManager {

    private const val TAG = "XMedia"

    @Volatile
    private var cache: SimpleCache? = null
    private var currentConfig: CacheConfig? = null
    private val executor = Executors.newCachedThreadPool()

    /**
     * Gets or creates a SimpleCache instance based on the configuration.
     *
     * @param context Android context
     * @param config Cache configuration
     * @return SimpleCache instance
     */
    @Synchronized
    fun getCache(context: Context, config: CacheConfig): SimpleCache {
        // Return existing cache if config matches
        if (cache != null && currentConfig == config) {
            return cache!!
        }

        // Release existing cache if config changed
        cache?.release()

        val cacheDir = config.cacheDirectory ?: File(context.cacheDir, config.cacheDirectoryName)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val evictor = LeastRecentlyUsedCacheEvictor(config.maxCacheSize)
        val databaseProvider = StandaloneDatabaseProvider(context)

        cache = SimpleCache(cacheDir, evictor, databaseProvider)
        currentConfig = config

        Log.d(
            TAG,
            "Cache initialized: ${cacheDir.absolutePath}, maxSize=${config.maxCacheSize / 1024 / 1024}MB"
        )

        return cache!!
    }

    /**
     * Creates a CacheDataSource.Factory for cached playback.
     *
     * The factory creates data sources that:
     * - Read from cache when available
     * - Download and cache when not in cache
     * - Use LRU eviction when cache is full
     *
     * @param context Android context
     * @param config Cache configuration
     * @return DataSource.Factory that uses caching
     */
    fun createCacheDataSourceFactory(
        context: Context,
        config: CacheConfig
    ): DataSource.Factory {
        val simpleCache = getCache(context, config)

        val cacheSink = CacheDataSink.Factory()
            .setCache(simpleCache)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
            .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val downStreamFactory = FileDataSource.Factory()
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setCacheWriteDataSinkFactory(cacheSink)
            .setCacheReadDataSourceFactory(downStreamFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Pre-caches an HLS stream for faster playback.
     *
     * Downloads the initial segments of an HLS stream so that playback
     * can start instantly without network latency.
     *
     * This method runs asynchronously on a background thread.
     *
     * @param url HLS manifest URL (.m3u8)
     * @param context Android context
     * @param config Cache configuration
     * @param durationMs How much content to pre-cache in milliseconds (default: 10 seconds)
     * @param onProgress Called with progress updates (0.0 to 1.0)
     * @param onComplete Called when pre-caching completes successfully
     * @param onError Called if pre-caching fails
     */
    fun preCacheHls(
        url: String,
        context: Context,
        config: CacheConfig,
        durationMs: Long = 10_000L,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val simpleCache = getCache(context, config)

                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)

                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                // Default enables cache writing

                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()

                val downloader = HlsDownloader(mediaItem, cacheDataSourceFactory)

                Log.d(TAG, "Starting pre-cache for: $url (${durationMs}ms)")

                var lastProgress = 0f
                downloader.download { contentLength, bytesDownloaded, percentDownloaded ->
                    // Calculate progress based on target duration
                    // HlsDownloader doesn't provide time-based progress, so we estimate
                    val progress = percentDownloaded / 100f
                    if (progress - lastProgress >= 0.05f) { // Report every 5%
                        lastProgress = progress
                        onProgress?.invoke(progress)
                        Log.d(TAG, "Pre-cache progress: ${(progress * 100).toInt()}%")
                    }

                    // Stop after downloading enough content
                    // This is an approximation - actual duration depends on bitrate
                    val estimatedBitrate = 3_000_000L // Assume 3 Mbps average
                    val targetBytes = (estimatedBitrate * durationMs) / 8000
                    if (bytesDownloaded >= targetBytes) {
                        downloader.cancel()
                    }
                }

                Log.d(TAG, "Pre-cache completed for: $url")
                onComplete?.invoke()

            } catch (e: Exception) {
                if (e.message?.contains("Cancel") == true || e.message?.contains("cancel") == true) {
                    // Cancellation is expected when we've downloaded enough
                    Log.d(TAG, "Pre-cache stopped (target reached) for: $url")
                    onComplete?.invoke()
                } else {
                    Log.e(TAG, "Pre-cache failed for: $url", e)
                    onError?.invoke(e)
                }
            }
        }
    }

    /**
     * Gets the current cache size in bytes.
     *
     * @return Current cache size, or 0 if cache is not initialized
     */
    fun getCacheSize(): Long {
        return cache?.cacheSpace ?: 0L
    }

    /**
     * Clears all cached content.
     *
     * This removes all cached video segments from disk.
     */
    @Synchronized
    fun clearCache() {
        try {
            // SimpleCache doesn't have a clear method, so we need to release and recreate
            // The files will be deleted when we create a new cache
            val config = currentConfig
            cache?.release()
            cache = null
            currentConfig = null

            // Delete cache directory contents
            config?.let { cfg ->
                val cacheDir = cfg.cacheDirectory ?: File(
                    android.os.Environment.getExternalStorageDirectory(),
                    cfg.cacheDirectoryName
                )
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }

            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    /**
     * Releases the cache and all resources.
     *
     * Call this when the app is shutting down to clean up resources.
     */
    @Synchronized
    fun release() {
        try {
            cache?.release()
            cache = null
            currentConfig = null
            Log.d(TAG, "Cache released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release cache", e)
        }
    }
}
