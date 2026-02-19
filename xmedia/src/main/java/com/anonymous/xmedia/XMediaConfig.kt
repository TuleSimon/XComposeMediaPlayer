package com.anonymous.xmedia

import java.io.File

/**
 * Configuration options for XMedia player.
 *
 * Use this class to customize buffer sizes, caching behavior, track selection,
 * bandwidth estimation, and other player settings.
 *
 * @property minBufferMs Minimum buffer duration in milliseconds before playback starts
 * @property maxBufferMs Maximum buffer duration the player will try to maintain
 * @property bufferForPlaybackMs Buffer required to start/resume playback
 * @property bufferForPlaybackAfterRebufferMs Buffer required after rebuffering
 * @property cacheConfig Configuration for video caching (null to disable)
 * @property trackSelectorConfig Configuration for adaptive track selection
 * @property bandwidthConfig Configuration for bandwidth estimation
 * @property handleAudioBecomingNoisy Whether to pause when headphones are disconnected
 * @property preferredAudioLanguage Preferred audio language code (e.g., "en", "es")
 *
 * Example usage:
 * ```kotlin
 * // Use default configuration
 * val state = rememberXMediaState()
 *
 * // Use high performance configuration with caching
 * val state = rememberXMediaState(XMediaConfig.HighPerformance)
 *
 * // Custom configuration
 * val state = rememberXMediaState(
 *     XMediaConfig(
 *         minBufferMs = 5000,
 *         maxBufferMs = 60000,
 *         cacheConfig = CacheConfig(
 *             enabled = true,
 *             maxCacheSize = 200L * 1024 * 1024
 *         ),
 *         trackSelectorConfig = TrackSelectorConfig(
 *             minVideoWidth = 720,
 *             minVideoHeight = 480,
 *             bandwidthFraction = 0.8f
 *         ),
 *         bandwidthConfig = BandwidthConfig(
 *             initialBitrateEstimate = 3_000_000L
 *         )
 *     )
 * )
 * ```
 */
data class XMediaConfig(
    val minBufferMs: Int = DEFAULT_MIN_BUFFER_MS,
    val maxBufferMs: Int = DEFAULT_MAX_BUFFER_MS,
    val bufferForPlaybackMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    val bufferForPlaybackAfterRebufferMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    val cacheConfig: CacheConfig? = null,
    val trackSelectorConfig: TrackSelectorConfig = TrackSelectorConfig(),
    val bandwidthConfig: BandwidthConfig = BandwidthConfig(),
    val handleAudioBecomingNoisy: Boolean = true,
    val preferredAudioLanguage: String = "en"
) {
    companion object {
        internal const val DEFAULT_MIN_BUFFER_MS = 3_000
        internal const val DEFAULT_MAX_BUFFER_MS = 40_000
        internal const val DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2_000
        internal const val DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000

        /**
         * Default configuration suitable for most use cases.
         *
         * - 3s min buffer, 40s max buffer
         * - 2s buffer before playback starts
         * - Caching disabled
         * - Default track selection
         */
        val Default = XMediaConfig()

        /**
         * High performance configuration for better streaming quality.
         *
         * - Larger buffers for smoother playback
         * - Caching enabled (200MB) for faster replay
         * - Optimized track selection with 1.1x bandwidth fraction
         * - Higher initial bitrate estimate (3 Mbps)
         * - Recommended for streaming apps
         */
        val HighPerformance = XMediaConfig(
            minBufferMs = 5_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 3_000,
            bufferForPlaybackAfterRebufferMs = 2_000,
            cacheConfig = CacheConfig(
                enabled = true,
                maxCacheSize = 600L * 1024 * 1024 // 600 MB
            ),
            trackSelectorConfig = TrackSelectorConfig(
                minVideoWidth = 854,
                minVideoHeight = 480,
                bandwidthFraction = 1.1f
            ),
            bandwidthConfig = BandwidthConfig(
                initialBitrateEstimate = 3_000_000L, // 3 Mbps
                useExperimentalBandwidthMeter = true
            )
        )

        /**
         * Low latency configuration for live streaming.
         *
         * - Smaller buffers for reduced latency
         * - Faster start time
         * - No caching (not useful for live)
         * - May cause more rebuffering on poor connections
         */
        val LowLatency = XMediaConfig(
            minBufferMs = 1_000,
            maxBufferMs = 10_000,
            bufferForPlaybackMs = 500,
            bufferForPlaybackAfterRebufferMs = 500,
            cacheConfig = null,
            trackSelectorConfig = TrackSelectorConfig(
                allowNonSeamlessAdaptiveness = true,
                minDurationForQualityIncreaseMs = 1_000,
                maxDurationForQualityDecreaseMs = 5_000
            )
        )

        /**
         * Data saver configuration to reduce bandwidth usage.
         *
         * - Smaller buffers
         * - Caching enabled to avoid re-downloading
         * - Limited to 720p max resolution
         * - Max 2 Mbps bitrate
         */
        val DataSaver = XMediaConfig(
            minBufferMs = 2_000,
            maxBufferMs = 20_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 1_000,
            cacheConfig = CacheConfig(
                enabled = true,
                maxCacheSize = 50L * 1024 * 1024 // 50 MB
            ),
            trackSelectorConfig = TrackSelectorConfig(
                maxVideoWidth = 1280,
                maxVideoHeight = 720,
                maxVideoBitrate = 2_000_000, // 2 Mbps max
                bandwidthFraction = 0.8f
            ),
            bandwidthConfig = BandwidthConfig(
                initialBitrateEstimate = 1_000_000L // 1 Mbps conservative
            )
        )
    }
}

/**
 * Configuration for video caching.
 *
 * When enabled, downloaded video segments are stored on disk for faster
 * replay and reduced bandwidth usage.
 *
 * @property enabled Whether caching is enabled
 * @property maxCacheSize Maximum cache size in bytes (uses LRU eviction)
 * @property cacheDirectory Custom cache directory (null uses app cache dir)
 * @property cacheDirectoryName Name of cache subdirectory
 */
data class CacheConfig(
    val enabled: Boolean = true,
    val maxCacheSize: Long = 100L * 1024 * 1024, // 100 MB default
    val cacheDirectory: File? = null,
    val cacheDirectoryName: String = "xmedia_cache"
)

/**
 * Configuration for adaptive track selection.
 *
 * These settings control how the player selects video quality based on
 * network conditions and device capabilities.
 *
 * @property minDurationForQualityIncreaseMs Minimum playback duration before quality can increase
 * @property maxDurationForQualityDecreaseMs Maximum playback duration before quality must decrease
 * @property minDurationToRetainAfterDiscardMs Minimum duration to retain after discarding
 * @property minVideoWidth Minimum video width to select (0 for no minimum)
 * @property minVideoHeight Minimum video height to select (0 for no minimum)
 * @property maxVideoWidth Maximum video width to select (Int.MAX_VALUE for no limit)
 * @property maxVideoHeight Maximum video height to select (Int.MAX_VALUE for no limit)
 * @property maxVideoBitrate Maximum video bitrate in bps (Int.MAX_VALUE for no limit)
 * @property bandwidthFraction Fraction of estimated bandwidth to use (0.0-2.0, higher = more aggressive)
 * @property allowMixedMimeTypeAdaptiveness Allow switching between different codecs (e.g., AVC to HEVC)
 * @property allowNonSeamlessAdaptiveness Allow non-seamless resolution changes (brief freeze)
 * @property forceLowestBitrate Always select lowest bitrate (for testing/data saving)
 * @property forceHighestSupportedBitrate Always select highest supported bitrate
 */
data class TrackSelectorConfig(
    val minDurationForQualityIncreaseMs: Int = 3_000,
    val maxDurationForQualityDecreaseMs: Int = 20_000,
    val minDurationToRetainAfterDiscardMs: Int = 3_000,
    val minVideoWidth: Int = 0,
    val minVideoHeight: Int = 0,
    val maxVideoWidth: Int = Int.MAX_VALUE,
    val maxVideoHeight: Int = Int.MAX_VALUE,
    val maxVideoBitrate: Int = Int.MAX_VALUE,
    val bandwidthFraction: Float = 1.0f,
    val allowMixedMimeTypeAdaptiveness: Boolean = true,
    val allowNonSeamlessAdaptiveness: Boolean = true,
    val forceLowestBitrate: Boolean = false,
    val forceHighestSupportedBitrate: Boolean = false
)

/**
 * Configuration for bandwidth estimation.
 *
 * Controls how the player estimates available network bandwidth for
 * adaptive quality selection.
 *
 * @property initialBitrateEstimate Initial bandwidth estimate in bps (used before real measurement)
 * @property useExperimentalBandwidthMeter Use advanced bandwidth estimation (more accurate but experimental)
 * @property resetOnNetworkChange Reset estimate when network type changes
 */
data class BandwidthConfig(
    val initialBitrateEstimate: Long = 1_000_000L, // 1 Mbps default
    val useExperimentalBandwidthMeter: Boolean = true,
    val resetOnNetworkChange: Boolean = true
)
