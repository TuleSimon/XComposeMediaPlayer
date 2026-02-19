package com.anonymous.xmedia.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.anonymous.xmedia.CacheConfig

/**
 * Factory for creating MediaSource instances based on content type.
 *
 * Automatically detects the media type from the URL and creates the appropriate
 * MediaSource (HLS, DASH, SmoothStreaming, or Progressive).
 *
 * Supports optional caching through [CacheConfig].
 */
internal object MediaSourceFactory {

    /**
     * Supported media source types.
     */
    enum class SourceType {
        HLS,
        DASH,
        SMOOTH_STREAMING,
        PROGRESSIVE,
        LOCAL
    }

    /**
     * Creates a MediaSource for the given URL with optional caching.
     *
     * The source type is automatically detected from the URL extension and MIME type.
     * Supports:
     * - HLS streams (.m3u8)
     * - DASH streams (.mpd)
     * - SmoothStreaming
     * - Progressive download (MP4, WebM, etc.)
     * - Local files (file:// or content://)
     *
     * When cacheConfig is provided and enabled, downloaded segments will be cached
     * to disk for faster replay and reduced bandwidth usage.
     *
     * @param url Media URL or local file URI
     * @param context Android context
     * @param cacheConfig Optional cache configuration (null to disable caching)
     * @param dataSourceFactory Optional custom data source factory (overrides caching)
     * @return Configured MediaSource for the content
     */
    @OptIn(UnstableApi::class)
    fun create(
        url: String,
        context: Context,
        cacheConfig: CacheConfig? = null,
        dataSourceFactory: DataSource.Factory? = null
    ): MediaSource {
        // Use custom factory, or cache factory, or default
        val factory = when {
            dataSourceFactory != null -> dataSourceFactory
            cacheConfig?.enabled == true -> CacheManager.createCacheDataSourceFactory(context, cacheConfig)
            else -> DefaultDataSource.Factory(context)
        }

        val mediaItem = createMediaItem(url)
        val sourceType = detectSourceType(url)

        return when (sourceType) {
            SourceType.HLS -> HlsMediaSource.Factory(factory)
                .setAllowChunklessPreparation(false)
                .createMediaSource(mediaItem)

            SourceType.DASH -> DashMediaSource.Factory(factory)
                .createMediaSource(mediaItem)

            SourceType.SMOOTH_STREAMING -> SsMediaSource.Factory(factory)
                .createMediaSource(mediaItem)

            SourceType.PROGRESSIVE, SourceType.LOCAL -> ProgressiveMediaSource.Factory(factory)
                .createMediaSource(mediaItem)
        }
    }

    /**
     * Detects the source type from a URL.
     *
     * @param url Media URL to analyze
     * @return Detected SourceType
     */
    @OptIn(UnstableApi::class)
    fun detectSourceType(url: String): SourceType {
        // Check for local files first
        if (url.startsWith("file://") || url.startsWith("content://")) {
            return SourceType.LOCAL
        }

        // Explicit extension check (more reliable for HLS)
        val lowerUrl = url.lowercase()
        when {
            lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") -> return SourceType.HLS
            lowerUrl.endsWith(".mpd") || lowerUrl.contains(".mpd?") -> return SourceType.DASH
        }

        // Use ExoPlayer's content type inference
        return when (Util.inferContentType(url.toUri())) {
            C.CONTENT_TYPE_HLS -> SourceType.HLS
            C.CONTENT_TYPE_DASH -> SourceType.DASH
            C.CONTENT_TYPE_SS -> SourceType.SMOOTH_STREAMING
            else -> SourceType.PROGRESSIVE
        }
    }

    /**
     * Checks if the URL is a streaming source (HLS/DASH/SS) that supports quality selection.
     *
     * @param url Media URL to check
     * @return True if the source supports quality selection
     */
    fun supportsQualitySelection(url: String): Boolean {
        return when (detectSourceType(url)) {
            SourceType.HLS, SourceType.DASH, SourceType.SMOOTH_STREAMING -> true
            SourceType.PROGRESSIVE, SourceType.LOCAL -> false
        }
    }

    @OptIn(UnstableApi::class)
    private fun createMediaItem(url: String): MediaItem {
        val lowerUrl = url.lowercase()

        // Set explicit MIME type for HLS to ensure proper handling
        return if (lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?")) {
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        } else {
            MediaItem.fromUri(url)
        }
    }
}
