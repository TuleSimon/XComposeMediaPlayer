package com.anonymous.xmedia

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.exoplayer.ExoPlayer
import com.anonymous.xmedia.internal.CacheManager
import com.anonymous.xmedia.internal.PlayerFactory
import com.anonymous.xmedia.internal.StateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Creates and remembers an [XMediaState] instance for managing video playback.
 *
 * This is the main entry point for using XMedia. The returned state object provides:
 * - Observable playback state (playing, buffering, progress, duration)
 * - Playback controls (play, pause, seek)
 * - HLS/DASH quality selection
 * - Volume and mute controls
 *
 * The state is automatically cleaned up when the composable leaves the composition.
 *
 * @param config Optional configuration for buffer sizes and caching. Defaults to [XMediaConfig.Default]
 * @return A stable [XMediaState] instance that survives recomposition
 *
 * Example usage:
 * ```kotlin
 * @Composable
 * fun VideoScreen() {
 *     val state = rememberXMediaState()
 *
 *     XMediaPlayer(
 *         state = state,
 *         url = "https://example.com/video.m3u8",
 *         modifier = Modifier.fillMaxWidth()
 *     )
 *
 *     // Control playback
 *     Button(onClick = { state.togglePlayPause() }) {
 *         Text(if (state.isPlaying.collectAsState().value) "Pause" else "Play")
 *     }
 *
 *     // Show progress
 *     val progress by state.progress.collectAsState()
 *     val duration by state.duration.collectAsState()
 *     LinearProgressIndicator(progress = progress.toFloat() / duration.coerceAtLeast(1L))
 *
 *     // Quality selection for HLS
 *     val qualities by state.availableQualities.collectAsState()
 *     qualities.forEach { quality ->
 *         Button(onClick = { state.setQuality(quality) }) {
 *             Text(quality.label)
 *         }
 *     }
 * }
 * ```
 */
@Composable
fun rememberXMediaState(
    config: XMediaConfig = XMediaConfig.Default
): XMediaState {
    val scope = rememberCoroutineScope()
    return remember(config) {
        XMediaState(config, scope)
    }
}

/**
 * State holder for XMedia player that provides playback control and state observation.
 *
 * This class manages a single video at a time and handles:
 * - Playback controls (play, pause, seek)
 * - State observation via StateFlow (isPlaying, isBuffering, progress, duration)
 * - HLS/DASH quality selection
 * - Volume and mute controls
 * - Lifecycle management
 *
 * Create instances using [rememberXMediaState] in a Composable context.
 *
 * @param config Configuration for the player
 * @param scope Coroutine scope for state management
 */
class XMediaState internal constructor(
    private val config: XMediaConfig,
    scope: CoroutineScope
) {
    private val stateManager = StateManager(config, scope)

    // ==================== Observable State ====================

    /**
     * Whether the video is currently playing.
     *
     * This will be false when paused, buffering, or stopped.
     */
    val isPlaying: StateFlow<Boolean> = stateManager.isPlaying

    /**
     * Whether the player is currently buffering.
     *
     * Use this to show a loading indicator.
     */
    val isBuffering: StateFlow<Boolean> = stateManager.isBuffering

    /**
     * Current playback position in milliseconds.
     *
     * Updated approximately every 100ms while playing.
     */
    val progress: StateFlow<Long> = stateManager.progress

    /**
     * Total duration of the video in milliseconds.
     *
     * May be 0 until the video is loaded. For live streams, this may be the
     * duration of the seekable window.
     */
    val duration: StateFlow<Long> = stateManager.duration

    /**
     * Currently loaded media URL, or null if no media is loaded.
     */
    val currentUrl: StateFlow<String?> = stateManager.currentUrl

    /**
     * Available video qualities for HLS/DASH streams.
     *
     * The first item is always the Auto quality option (if qualities are available).
     * For non-streaming content (MP4, local files), this will be empty.
     *
     * Populated after the stream metadata is loaded.
     */
    val availableQualities: StateFlow<List<XMediaQuality>> = stateManager.availableQualities

    /**
     * Currently selected video quality, or null if Auto/unknown.
     */
    val currentQuality: StateFlow<XMediaQuality?> = stateManager.currentQuality

    /**
     * Current playback error, or null if no error.
     *
     * Cleared when a new media is prepared.
     */
    val error: StateFlow<XMediaError?> = stateManager.error

    /**
     * Whether audio is currently muted.
     */
    val isMuted: StateFlow<Boolean> = stateManager.isMuted

    /**
     * Current volume level from 0.0 (muted) to 1.0 (full volume).
     */
    val volume: StateFlow<Float> = stateManager.volume

    // ==================== Playback Controls ====================

    /**
     * Starts or resumes playback.
     *
     * If playback ended, this will restart from the beginning
     * (or from the start position if repeat mode is enabled).
     */
    fun play() = stateManager.play()

    /**
     * Pauses playback.
     *
     * The current position is maintained and can be resumed with [play].
     */
    fun pause() = stateManager.pause()

    /**
     * Toggles between playing and paused states.
     *
     * Convenience method equivalent to calling [play] or [pause] based on current state.
     */
    fun togglePlayPause() = stateManager.togglePlayPause()

    /**
     * Seeks to a specific position.
     *
     * @param positionMs Position in milliseconds from the start of the video
     */
    fun seekTo(positionMs: Long) = stateManager.seekTo(positionMs)

    /**
     * Seeks to a position based on percentage.
     *
     * @param percent Position as a percentage from 0.0 (start) to 1.0 (end)
     */
    fun seekToPercent(percent: Float) = stateManager.seekToPercent(percent)

    /**
     * Stops playback and clears the current media.
     *
     * After calling stop, you'll need to prepare new media to play again.
     */
    fun stop() = stateManager.stop()

    // ==================== Quality Selection ====================

    /**
     * Sets the video quality for HLS/DASH streams.
     *
     * This overrides automatic quality selection with a fixed quality.
     * The change takes effect immediately while maintaining the current position.
     *
     * For non-streaming content, this has no effect.
     *
     * @param quality Quality to select from [availableQualities]
     *
     * Example:
     * ```kotlin
     * val qualities by state.availableQualities.collectAsState()
     * val hd = qualities.find { it.height == 720 }
     * if (hd != null) state.setQuality(hd)
     * ```
     */
    fun setQuality(quality: XMediaQuality) = stateManager.setQuality(quality)

    /**
     * Enables automatic quality selection based on network conditions.
     *
     * The player will automatically choose the best quality based on
     * available bandwidth. This is the default mode.
     */
    fun setAutoQuality() = stateManager.setAutoQuality()

    // ==================== Volume Controls ====================

    /**
     * Mutes audio playback.
     *
     * The video continues playing but with no sound.
     */
    fun mute() = stateManager.mute()

    /**
     * Unmutes audio playback to full volume.
     */
    fun unmute() = stateManager.unmute()

    /**
     * Sets the audio volume.
     *
     * @param volume Volume level from 0.0 (muted) to 1.0 (full volume)
     */
    fun setVolume(volume: Float) = stateManager.setVolume(volume)

    // ==================== Caching ====================

    /**
     * Pre-caches an HLS stream for instant playback.
     *
     * Downloads the initial segments of an HLS stream in the background so that
     * playback can start instantly without network latency. This is useful for
     * pre-loading the next video in a playlist or feed.
     *
     * **Requires caching to be enabled** in [XMediaConfig.cacheConfig].
     * If caching is not enabled, this method does nothing.
     *
     * @param url HLS manifest URL (.m3u8)
     * @param context Android context
     * @param durationMs How much content to pre-cache in milliseconds (default: 10 seconds)
     * @param onProgress Called with progress updates (0.0 to 1.0)
     * @param onComplete Called when pre-caching completes successfully
     * @param onError Called if pre-caching fails
     *
     * Example usage:
     * ```kotlin
     * val state = rememberXMediaState(XMediaConfig.HighPerformance)
     *
     * // Pre-cache the next video while current one plays
     * LaunchedEffect(nextVideoUrl) {
     *     state.preCacheHls(
     *         url = nextVideoUrl,
     *         context = context,
     *         durationMs = 15_000L, // Pre-cache 15 seconds
     *         onProgress = { progress ->
     *             Log.d("XMedia", "Pre-cache: ${(progress * 100).toInt()}%")
     *         },
     *         onComplete = {
     *             Log.d("XMedia", "Pre-cache complete!")
     *         }
     *     )
     * }
     * ```
     */
    fun preCacheHls(
        url: String,
        context: Context,
        durationMs: Long = 10_000L,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val cacheConfig = config.cacheConfig
        if (cacheConfig == null || !cacheConfig.enabled) {
            onError?.invoke(IllegalStateException("Caching is not enabled in XMediaConfig"))
            return
        }

        CacheManager.preCacheHls(
            url = url,
            context = context,
            config = cacheConfig,
            durationMs = durationMs,
            onProgress = onProgress,
            onComplete = onComplete,
            onError = onError
        )
    }

    /**
     * Gets the current cache size in bytes.
     *
     * Returns 0 if caching is not enabled.
     */
    fun getCacheSize(): Long = CacheManager.getCacheSize()

    /**
     * Clears all cached video content.
     *
     * This removes all cached video segments from disk. Use this to free up
     * storage space or reset the cache.
     */
    fun clearCache() = CacheManager.clearCache()

    // ==================== Bandwidth ====================

    /**
     * Gets the last measured bandwidth estimate in bits per second.
     *
     * This value is shared across all XMedia players and persists across
     * player instances. It's useful for:
     * - Displaying network quality to users
     * - Making decisions about video quality
     * - Pre-selecting appropriate quality before playback starts
     *
     * Returns 0 if no measurement has been made yet.
     *
     * Example:
     * ```kotlin
     * val bandwidthBps = state.getLastBandwidthEstimate()
     * val bandwidthMbps = bandwidthBps / 1_000_000.0
     * Text("Network: %.1f Mbps".format(bandwidthMbps))
     * ```
     */
    fun getLastBandwidthEstimate(): Long = PlayerFactory.getLastBandwidthEstimate()

    /**
     * Resets the bandwidth estimate.
     *
     * The next player will start with the initial estimate from [BandwidthConfig]
     * instead of using the last measured value.
     */
    fun resetBandwidthEstimate() = PlayerFactory.resetBandwidthEstimate()

    // ==================== Advanced Access ====================

    /**
     * Direct access to the underlying ExoPlayer instance.
     *
     * Use this for advanced operations not exposed through XMediaState.
     * Note that the player may be null if no media has been prepared yet.
     *
     * **Caution:** Modifying player state directly may cause inconsistencies
     * with the StateFlows. Prefer using the XMediaState methods when possible.
     */
    val player: ExoPlayer?
        get() = stateManager.getPlayer()

    // ==================== Internal Methods ====================

    /**
     * Prepares the player with a media URL.
     *
     * Called internally by [XMediaPlayer]. Usually you don't need to call this directly.
     *
     * @param url Media URL to load
     * @param context Android context
     */
    internal fun prepare(url: String, context: Context) = stateManager.prepare(url, context)

    /**
     * Releases the player and all resources.
     *
     * Called internally when [XMediaPlayer] leaves composition.
     */
    fun release() = stateManager.release()

    /**
     * Sets the repeat mode.
     *
     * @param repeat If true, video loops when it reaches the end
     */
    internal fun setRepeatMode(repeat: Boolean) = stateManager.setRepeatMode(repeat)

    /**
     * Sets callback for when playback ends.
     */
    internal fun setOnPlaybackEnded(callback: (() -> Unit)?) {
        stateManager.onPlaybackEnded = callback
    }

    /**
     * Sets callback for when the first frame is rendered.
     */
    internal fun setOnFirstFrameRendered(callback: (() -> Unit)?) {
        stateManager.onFirstFrameRendered = callback
    }

    companion object {
        /**
         * Pre-caches an HLS stream without needing a player instance.
         *
         * This static method allows pre-caching videos before a player is created,
         * useful for preloading content during app initialization or when browsing
         * a video list.
         *
         * @param url HLS manifest URL (.m3u8)
         * @param context Android context
         * @param cacheConfig Cache configuration
         * @param durationMs How much content to pre-cache in milliseconds (default: 10 seconds)
         * @param onProgress Called with progress updates (0.0 to 1.0)
         * @param onComplete Called when pre-caching completes successfully
         * @param onError Called if pre-caching fails
         *
         * Example:
         * ```kotlin
         * // In ViewModel or Application
         * val cacheConfig = CacheConfig(enabled = true, maxCacheSize = 200L * 1024 * 1024)
         *
         * XMediaState.preCacheHls(
         *     url = "https://example.com/video.m3u8",
         *     context = applicationContext,
         *     cacheConfig = cacheConfig,
         *     durationMs = 15_000L
         * )
         * ```
         */
        fun preCacheHls(
            url: String,
            context: Context,
            cacheConfig: CacheConfig,
            durationMs: Long = 10_000L,
            onProgress: ((Float) -> Unit)? = null,
            onComplete: (() -> Unit)? = null,
            onError: ((Exception) -> Unit)? = null
        ) {
            if (!cacheConfig.enabled) {
                onError?.invoke(IllegalStateException("CacheConfig.enabled must be true"))
                return
            }

            CacheManager.preCacheHls(
                url = url,
                context = context,
                config = cacheConfig,
                durationMs = durationMs,
                onProgress = onProgress,
                onComplete = onComplete,
                onError = onError
            )
        }

        /**
         * Gets the last measured bandwidth estimate in bits per second.
         *
         * This static method provides access to bandwidth info without a player instance.
         * Useful for pre-selecting video quality before playback.
         */
        fun getLastBandwidthEstimate(): Long = PlayerFactory.getLastBandwidthEstimate()

        /**
         * Resets the bandwidth estimate.
         */
        fun resetBandwidthEstimate() = PlayerFactory.resetBandwidthEstimate()

        /**
         * Gets the current cache size in bytes.
         */
        fun getCacheSize(): Long = CacheManager.getCacheSize()

        /**
         * Clears all cached video content.
         */
        fun clearCache() = CacheManager.clearCache()

        /**
         * Releases the cache and all resources.
         *
         * Call this when the app is shutting down to clean up resources.
         * Typically called in Application.onTerminate() or similar.
         */
        fun releaseCache() = CacheManager.release()
    }
}
