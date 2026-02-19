package com.anonymous.xmedia.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.experimental.CombinedParallelSampleBandwidthEstimator
import androidx.media3.exoplayer.upstream.experimental.ExperimentalBandwidthMeter
import androidx.media3.exoplayer.upstream.experimental.ExponentialWeightedAverageTimeToFirstByteEstimator
import androidx.media3.exoplayer.upstream.experimental.SlidingPercentileBandwidthStatistic
import com.anonymous.xmedia.BandwidthConfig
import com.anonymous.xmedia.TrackSelectorConfig
import com.anonymous.xmedia.XMediaConfig

/**
 * Factory for creating ExoPlayer instances with optimized configuration.
 *
 * This object provides a centralized way to create properly configured ExoPlayer
 * instances without requiring dependency injection.
 *
 * Uses a **shared BandwidthMeter** across all players for:
 * - Consistent bandwidth measurement across players
 * - Faster quality adaptation (measurements are retained)
 * - Better quality selection in feeds/playlists
 */
internal object PlayerFactory {

    private const val TAG = "XMedia"

    // Shared bandwidth meter - reused across all players for consistent measurement
    @Volatile
    private var sharedBandwidthMeter: BandwidthMeter? = null
    private var currentBandwidthConfig: BandwidthConfig? = null

    // Store the last bandwidth estimate (for external access)
    @Volatile
    private var lastBandwidthEstimate: Long = 0L

    /**
     * Creates a new ExoPlayer instance with the specified configuration.
     *
     * The player is configured with:
     * - Adaptive track selection based on TrackSelectorConfig
     * - Custom buffer sizes based on config
     * - **Shared** bandwidth meter for quality selection
     * - Audio attributes for media playback
     * - Extension renderer support
     *
     * @param context Android context
     * @param config XMedia configuration
     * @return Configured ExoPlayer instance
     */
    @OptIn(UnstableApi::class)
    fun create(context: Context, config: XMediaConfig): ExoPlayer {
        val loadControl = createLoadControl(config)
        val bandwidthMeter = getOrCreateBandwidthMeter(context, config.bandwidthConfig)
        val trackSelector = createTrackSelector(context, config.trackSelectorConfig, config.preferredAudioLanguage)
        val renderersFactory = createRenderersFactory(context)

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setHandleAudioBecomingNoisy(config.handleAudioBecomingNoisy)
            .setAudioAttributes(createAudioAttributes(), true)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun createLoadControl(config: XMediaConfig): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(
                config.minBufferMs,
                config.maxBufferMs,
                config.bufferForPlaybackMs,
                config.bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Gets or creates the shared bandwidth meter.
     *
     * The bandwidth meter is shared across all players to:
     * - Maintain measurement history across player instances
     * - Provide consistent quality selection across concurrent players
     * - Avoid cold-start issues where each new player starts at lowest quality
     */
    @Synchronized
    @OptIn(UnstableApi::class)
    private fun getOrCreateBandwidthMeter(context: Context, config: BandwidthConfig): BandwidthMeter {
        // Return existing meter if config matches
        if (sharedBandwidthMeter != null && currentBandwidthConfig == config) {
            return sharedBandwidthMeter!!
        }

        // Create new shared meter
        val meter = if (config.useExperimentalBandwidthMeter) {
            createExperimentalBandwidthMeter(context, config)
        } else {
            createDefaultBandwidthMeter(context, config)
        }

        sharedBandwidthMeter = meter
        currentBandwidthConfig = config

        return meter
    }

    @OptIn(UnstableApi::class)
    private fun createExperimentalBandwidthMeter(
        context: Context,
        config: BandwidthConfig
    ): BandwidthMeter {
        val bandwidthMeter = ExperimentalBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(config.initialBitrateEstimate)
            .setTimeToFirstByteEstimator(
                ExponentialWeightedAverageTimeToFirstByteEstimator()
            )
            .setBandwidthEstimator(
                CombinedParallelSampleBandwidthEstimator.Builder()
                    .setBandwidthStatistic(
                        SlidingPercentileBandwidthStatistic()
                    )
                    .build()
            )
            .setResetOnNetworkTypeChange(config.resetOnNetworkChange)
            .build()

        // Add listener to track bandwidth estimates
        bandwidthMeter.addEventListener(
            Handler(Looper.getMainLooper())
        ) { elapsedMs, bytesTransferred, bitrateEstimate ->
            if (bitrateEstimate > 0) {
                lastBandwidthEstimate = bitrateEstimate
                Log.d(TAG, "Bandwidth: ${bitrateEstimate / 1000} kbps | bytes=$bytesTransferred | elapsed=${elapsedMs}ms")
            }
        }

        return bandwidthMeter
    }

    @OptIn(UnstableApi::class)
    private fun createDefaultBandwidthMeter(
        context: Context,
        config: BandwidthConfig
    ): BandwidthMeter {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(config.initialBitrateEstimate)
            .setResetOnNetworkTypeChange(config.resetOnNetworkChange)
            .build()

        // Add listener to track bandwidth estimates
        bandwidthMeter.addEventListener(
            Handler(Looper.getMainLooper())
        ) { _, _, bitrateEstimate ->
            if (bitrateEstimate > 0) {
                lastBandwidthEstimate = bitrateEstimate
            }
        }

        return bandwidthMeter
    }

    @OptIn(UnstableApi::class)
    private fun createTrackSelector(
        context: Context,
        config: TrackSelectorConfig,
        preferredAudioLanguage: String
    ): DefaultTrackSelector {
        val adaptiveFactory = AdaptiveTrackSelection.Factory(
            config.minDurationForQualityIncreaseMs,
            config.maxDurationForQualityDecreaseMs,
            config.minDurationToRetainAfterDiscardMs,
            config.bandwidthFraction
        )

        return DefaultTrackSelector(context, adaptiveFactory).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(preferredAudioLanguage)
                    // Mime type adaptiveness
                    .setAllowVideoMixedMimeTypeAdaptiveness(config.allowMixedMimeTypeAdaptiveness)
                    .setAllowAudioMixedMimeTypeAdaptiveness(config.allowMixedMimeTypeAdaptiveness)
                    .setAllowAudioMixedSampleRateAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    // Seamless adaptiveness
                    .setAllowVideoNonSeamlessAdaptiveness(config.allowNonSeamlessAdaptiveness)
                    .setAllowAudioNonSeamlessAdaptiveness(config.allowNonSeamlessAdaptiveness)
                    // Video size constraints
                    .setMinVideoSize(config.minVideoWidth, config.minVideoHeight)
                    .setMaxVideoSize(config.maxVideoWidth, config.maxVideoHeight)
                    .setMaxVideoBitrate(config.maxVideoBitrate)
                    // Bitrate forcing
                    .setForceLowestBitrate(config.forceLowestBitrate)
                    .setForceHighestSupportedBitrate(config.forceHighestSupportedBitrate)
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }

    private fun createAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    /**
     * Gets the last known bandwidth estimate in bits per second.
     * Useful for displaying network quality or making quality decisions.
     */
    fun getLastBandwidthEstimate(): Long = lastBandwidthEstimate

    /**
     * Resets the bandwidth estimate and clears the shared meter.
     *
     * Call this if you want to completely reset bandwidth measurements.
     * The next player will create a fresh bandwidth meter.
     */
    @Synchronized
    fun resetBandwidthEstimate() {
        lastBandwidthEstimate = 0L
        sharedBandwidthMeter = null
        currentBandwidthConfig = null
    }

    /**
     * Gets the shared bandwidth meter instance, if one exists.
     *
     * This can be useful for advanced use cases where you need direct
     * access to bandwidth measurements.
     */
    fun getBandwidthMeter(): BandwidthMeter? = sharedBandwidthMeter
}
