package com.anonymous.xmedia.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.anonymous.xmedia.XMediaConfig

/**
 * Factory for creating ExoPlayer instances with optimized configuration.
 *
 * This object provides a centralized way to create properly configured ExoPlayer
 * instances without requiring dependency injection.
 */
internal object PlayerFactory {

    /**
     * Creates a new ExoPlayer instance with the specified configuration.
     *
     * The player is configured with:
     * - Adaptive track selection for quality switching
     * - Custom buffer sizes based on config
     * - Audio attributes for media playback
     * - Extension renderer support
     *
     * @param context Android context
     * @param config XMedia configuration for buffer settings
     * @return Configured ExoPlayer instance
     */
    @OptIn(UnstableApi::class)
    fun create(context: Context, config: XMediaConfig): ExoPlayer {
        val loadControl = createLoadControl(config)
        val trackSelector = createTrackSelector(context)
        val renderersFactory = createRenderersFactory(context)

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
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

    @OptIn(UnstableApi::class)
    private fun createTrackSelector(context: Context): DefaultTrackSelector {
        val adaptiveFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 3_000,
            /* maxDurationForQualityDecreaseMs = */ 20_000,
            /* minDurationToRetainAfterDiscardMs = */ 3_000,
            /* minWidthDp = */ 854,
            /* minHeightDp = */ 480,
            /* bandwidthFraction = */ 1.1f
        )

        return DefaultTrackSelector(context, adaptiveFactory).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage("en")
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedSampleRateAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioNonSeamlessAdaptiveness(true)
                    .setForceHighestSupportedBitrate(false)
                    .clearVideoSizeConstraints()
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
}
