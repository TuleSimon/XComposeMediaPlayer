package com.anonymous.xmedia

/**
 * Configuration options for XMedia player.
 *
 * Use this class to customize buffer sizes, caching behavior, and other player settings.
 * Pre-configured options are available via companion object properties.
 *
 * @property minBufferMs Minimum buffer duration in milliseconds before playback starts
 * @property maxBufferMs Maximum buffer duration the player will try to maintain
 * @property bufferForPlaybackMs Buffer required to start/resume playback
 * @property bufferForPlaybackAfterRebufferMs Buffer required after rebuffering
 * @property enableCaching Whether to enable disk caching for downloaded content
 * @property cacheSize Maximum cache size in bytes (only used if caching is enabled)
 *
 * Example usage:
 * ```kotlin
 * // Use default configuration
 * val state = rememberXMediaState()
 *
 * // Use high performance configuration
 * val state = rememberXMediaState(XMediaConfig.HighPerformance)
 *
 * // Custom configuration
 * val state = rememberXMediaState(
 *     XMediaConfig(
 *         minBufferMs = 5000,
 *         maxBufferMs = 60000,
 *         enableCaching = true
 *     )
 * )
 * ```
 */
data class XMediaConfig(
    val minBufferMs: Int = DEFAULT_MIN_BUFFER_MS,
    val maxBufferMs: Int = DEFAULT_MAX_BUFFER_MS,
    val bufferForPlaybackMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    val bufferForPlaybackAfterRebufferMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    val enableCaching: Boolean = false,
    val cacheSize: Long = DEFAULT_CACHE_SIZE
) {
    companion object {
        internal const val DEFAULT_MIN_BUFFER_MS = 3_000
        internal const val DEFAULT_MAX_BUFFER_MS = 40_000
        internal const val DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2_000
        internal const val DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000
        internal const val DEFAULT_CACHE_SIZE = 100L * 1024 * 1024 // 100 MB

        /**
         * Default configuration suitable for most use cases.
         *
         * - 3s min buffer, 40s max buffer
         * - 2s buffer before playback starts
         * - Caching disabled
         */
        val Default = XMediaConfig()

        /**
         * High performance configuration for better streaming quality.
         *
         * - Larger buffers for smoother playback
         * - Caching enabled for faster replay
         * - Recommended for streaming apps
         */
        val HighPerformance = XMediaConfig(
            minBufferMs = 5_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 3_000,
            bufferForPlaybackAfterRebufferMs = 2_000,
            enableCaching = true,
            cacheSize = 200L * 1024 * 1024 // 200 MB
        )

        /**
         * Low latency configuration for live streaming.
         *
         * - Smaller buffers for reduced latency
         * - Faster start time
         * - May cause more rebuffering on poor connections
         */
        val LowLatency = XMediaConfig(
            minBufferMs = 1_000,
            maxBufferMs = 10_000,
            bufferForPlaybackMs = 500,
            bufferForPlaybackAfterRebufferMs = 500,
            enableCaching = false
        )

        /**
         * Data saver configuration to reduce bandwidth usage.
         *
         * - Smaller buffers
         * - Caching enabled to avoid re-downloading
         */
        val DataSaver = XMediaConfig(
            minBufferMs = 2_000,
            maxBufferMs = 20_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 1_000,
            enableCaching = true,
            cacheSize = 50L * 1024 * 1024 // 50 MB
        )
    }
}
