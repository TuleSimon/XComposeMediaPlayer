package com.anonymous.xmedia

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.exoplayer.ExoPlayer
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
    config: XMediaConfig,
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
}
