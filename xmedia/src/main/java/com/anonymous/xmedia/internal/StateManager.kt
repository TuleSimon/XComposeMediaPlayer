package com.anonymous.xmedia.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.anonymous.xmedia.XMediaConfig
import com.anonymous.xmedia.XMediaError
import com.anonymous.xmedia.XMediaQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Internal state manager for XMedia player.
 *
 * Manages the ExoPlayer instance, tracks playback state via StateFlows,
 * handles quality selection, and provides playback controls.
 *
 * This class is not part of the public API and should only be accessed through XMediaState.
 */
internal class StateManager(
    private val config: XMediaConfig,
    private val scope: CoroutineScope
) {
    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private var currentContext: Context? = null

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    // Quality state
    private val _availableQualities = MutableStateFlow<List<XMediaQuality>>(emptyList())
    val availableQualities: StateFlow<List<XMediaQuality>> = _availableQualities.asStateFlow()

    private val _currentQuality = MutableStateFlow<XMediaQuality?>(null)
    val currentQuality: StateFlow<XMediaQuality?> = _currentQuality.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<XMediaError?>(null)
    val error: StateFlow<XMediaError?> = _error.asStateFlow()

    // Volume state
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // Playback ended callback
    var onPlaybackEnded: (() -> Unit)? = null

    // First frame rendered callback
    var onFirstFrameRendered: (() -> Unit)? = null

    /**
     * Returns the underlying ExoPlayer instance.
     * Use with caution - prefer using the state flows and control methods.
     */
    fun getPlayer(): ExoPlayer? = player

    /**
     * Prepares the player with a new media URL.
     *
     * If the URL is different from the current one, the existing player is released
     * and a new one is created. If the URL is the same, the existing player is reused.
     *
     * @param url Media URL to load
     * @param context Android context
     */
    @OptIn(UnstableApi::class)
    fun prepare(url: String, context: Context) {
        currentContext = context

        // If URL changed, release the current player
        if (_currentUrl.value != null && _currentUrl.value != url) {
            release()
        }

        // Create player if needed
        if (player == null) {
            player = PlayerFactory.create(context, config)
            attachListeners()
        }

        // Clear any previous error
        _error.value = null

        // Set up the media source with caching if enabled
        val mediaSource = MediaSourceFactory.create(url, context, config.cacheConfig)
        player?.setMediaSource(mediaSource)
        player?.prepare()
        _currentUrl.value = url

        // Reset quality state for new URL
        _availableQualities.value = emptyList()
        _currentQuality.value = null
    }

    // Playback controls

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToPercent(percent: Float) {
        val pos = (percent.coerceIn(0f, 1f) * _duration.value).toLong()
        seekTo(pos)
    }

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
    }

    // Quality selection

    /**
     * Sets the video quality for HLS/DASH streams.
     *
     * This will override the automatic quality selection with a fixed quality.
     * The change takes effect immediately and the player will seek back to
     * maintain the current position.
     *
     * @param quality Quality to select
     */
    @OptIn(UnstableApi::class)
    fun setQuality(quality: XMediaQuality) {
        val exoPlayer = player ?: return
        val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector ?: return

        val currentPosition = exoPlayer.currentPosition
        val builder = trackSelector.buildUponParameters()

        if (quality.isAuto) {
            // Clear overrides to enable automatic selection
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setForceHighestSupportedBitrate(false)
        } else {
            val groupIndex = quality.trackGroupIndex ?: return
            val trackIndex = quality.trackIndex ?: return

            // Ensure the track group exists
            if (groupIndex >= exoPlayer.currentTracks.groups.size) return

            val trackGroup = exoPlayer.currentTracks.groups[groupIndex].mediaTrackGroup
            val override = TrackSelectionOverride(trackGroup, listOf(trackIndex))

            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .addOverride(override)
                .setForceHighestSupportedBitrate(false)
        }

        trackSelector.setParameters(builder.build())
        _currentQuality.value = quality

        // Seek back to maintain position after quality change
        exoPlayer.seekTo(currentPosition)
    }

    /**
     * Enables automatic quality selection based on network conditions.
     */
    fun setAutoQuality() {
        setQuality(XMediaQuality.Auto)
    }

    // Volume controls

    @OptIn(UnstableApi::class)
    fun mute() {
        player?.volume = 0f
        _isMuted.value = true
        _volume.value = 0f
    }

    @OptIn(UnstableApi::class)
    fun unmute() {
        player?.volume = 1f
        _isMuted.value = false
        _volume.value = 1f
    }

    @OptIn(UnstableApi::class)
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        player?.volume = v
        _volume.value = v
        _isMuted.value = v == 0f
    }

    // Repeat mode

    fun setRepeatMode(repeat: Boolean) {
        player?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Cleanup

    /**
     * Releases the player and clears all state.
     */
    @OptIn(UnstableApi::class)
    fun release() {
        progressJob?.cancel()
        progressJob = null

        player?.stop()
        player?.release()
        player = null

        _currentUrl.value = null
        _availableQualities.value = emptyList()
        _currentQuality.value = null
        _isPlaying.value = false
        _isBuffering.value = false
        _progress.value = 0L
        _duration.value = 0L
        _error.value = null
    }

    // Player listeners

    private fun attachListeners() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                }
            }

            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    }
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        stopProgressTicker()
                        onPlaybackEnded?.invoke()
                    }
                    Player.STATE_IDLE -> {
                        // Player is idle, no action needed
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Extract available qualities when tracks change
                val qualities = QualityExtractor.extract(player)
                _availableQualities.value = qualities

                // Set initial quality to Auto if not already set
                if (_currentQuality.value == null && qualities.isNotEmpty()) {
                    _currentQuality.value = qualities.firstOrNull { it.isAuto } ?: XMediaQuality.Auto
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _error.value = mapError(error)
            }

            override fun onRenderedFirstFrame() {
                onFirstFrameRendered?.invoke()
            }
        })
    }

    private fun mapError(error: PlaybackException): XMediaError {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                XMediaError.NetworkError(
                    message = error.message ?: "Network error occurred",
                    cause = error
                )
            }

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                XMediaError.DecoderError(
                    message = error.message ?: "Decoder error occurred",
                    cause = error
                )
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                XMediaError.SourceError(
                    message = error.message ?: "Source error occurred",
                    cause = error
                )
            }

            else -> {
                XMediaError.UnknownError(
                    message = error.message ?: "Unknown error occurred",
                    cause = error
                )
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                player?.let { p ->
                    _progress.value = p.currentPosition.coerceAtLeast(0L)
                    _duration.value = p.duration.coerceAtLeast(0L)
                }
                delay(100) // Update every 100ms for smooth progress
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}
