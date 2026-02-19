package com.anonymous.xmedia

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * Type of surface used for video rendering.
 */
enum class XMediaSurfaceType {
    /**
     * TextureView-based surface. Better for animations and transformations.
     * Slightly higher battery usage than SurfaceView.
     */
    TextureView,

    /**
     * SurfaceView-based surface. Better battery efficiency but limited
     * support for animations and transformations.
     */
    SurfaceView
}

/**
 * A Compose video player component that displays video content.
 *
 * This is the main UI component for XMedia. It handles:
 * - Video rendering via Media3 PlayerSurface
 * - Lifecycle management (pause on background, resume on foreground)
 * - Screen wake lock while playing
 * - Loading and error states
 *
 * Supports:
 * - Local files (file:// or content://)
 * - HLS streams (.m3u8)
 * - DASH streams (.mpd)
 * - Progressive video (MP4, WebM, etc.)
 *
 * @param state The [XMediaState] from [rememberXMediaState] that controls this player
 * @param url The video URL to play (local file path, content URI, or HTTP URL)
 * @param modifier Modifier for the player container
 * @param autoPlay Whether to start playback automatically when the URL is loaded (default: true)
 * @param repeat Whether to loop the video when it reaches the end (default: false)
 * @param startMuted Whether to start with audio muted (default: false)
 * @param showLoading Custom composable for loading state, or null for default spinner
 * @param showError Custom composable for error state, or null for default error text
 * @param contentScale How to scale the video within the container
 * @param surfaceType The type of surface to use for rendering
 * @param keepScreenOn Whether to keep the screen on while video is playing (default: true)
 * @param onPlaybackEnded Callback when video reaches the end (not called if repeat is true)
 * @param onError Callback when a playback error occurs
 * @param onFirstFrameRendered Callback when the first video frame is displayed
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
 *         modifier = Modifier
 *             .fillMaxWidth()
 *             .aspectRatio(16f / 9f),
 *         autoPlay = true,
 *         onPlaybackEnded = {
 *             // Navigate to next video or show replay button
 *         }
 *     )
 * }
 * ```
 *
 * With custom loading UI:
 * ```kotlin
 * XMediaPlayer(
 *     state = state,
 *     url = url,
 *     showLoading = {
 *         Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
 *             CircularProgressIndicator(color = Color.White)
 *             Text("Loading video...", color = Color.White)
 *         }
 *     }
 * )
 * ```
 */
@OptIn(UnstableApi::class)
@Composable
fun XMediaPlayer(
    state: XMediaState,
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    repeat: Boolean = false,
    startMuted: Boolean = false,
    showLoading: @Composable (() -> Unit)? = null,
    showError: @Composable ((XMediaError) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    surfaceType: XMediaSurfaceType = XMediaSurfaceType.TextureView,
    keepScreenOn: Boolean = true,
    onPlaybackEnded: (() -> Unit)? = null,
    onError: ((XMediaError) -> Unit)? = null,
    onFirstFrameRendered: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    val isBuffering by state.isBuffering.collectAsState()
    val isPlaying by state.isPlaying.collectAsState()
    val error by state.error.collectAsState()

    var firstFrameRendered by remember { mutableStateOf(false) }

    // Set up callbacks
    LaunchedEffect(onPlaybackEnded, onFirstFrameRendered) {
        state.setOnPlaybackEnded(onPlaybackEnded)
        state.setOnFirstFrameRendered {
            firstFrameRendered = true
            onFirstFrameRendered?.invoke()
        }
    }

    // Initialize player when URL changes
    LaunchedEffect(url) {
        firstFrameRendered = false
        state.prepare(url, context)
        if (startMuted) state.mute()
        if (autoPlay) state.play()
    }

    // Handle repeat mode changes
    LaunchedEffect(repeat) {
        state.setRepeatMode(repeat)
    }

    // Lifecycle management - pause when app goes to background
    LifecycleStartEffect(Unit) {
        // Resume if autoPlay is enabled and URL is loaded
        if (autoPlay && state.currentUrl.value != null) {
            state.play()
        }
        onStopOrDispose {
            state.pause()
        }
    }

    // Screen wake lock - keep screen on while playing
    DisposableEffect(isPlaying, keepScreenOn) {
        val window = context.findActivity()?.window
        if (isPlaying && keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Error callback
    LaunchedEffect(error) {
        error?.let { onError?.invoke(it) }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            state.release()
        }
    }

    // Render the player
    Box(modifier = modifier) {
        // Video surface
        state.player?.let { player ->
            val presentationState = rememberPresentationState(player)
            val scaledModifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(contentScale, presentationState.videoSizeDp)

            PlayerSurface(
                player = player,
                modifier = scaledModifier,
                surfaceType = when (surfaceType) {
                    XMediaSurfaceType.TextureView -> SURFACE_TYPE_TEXTURE_VIEW
                    XMediaSurfaceType.SurfaceView -> SURFACE_TYPE_SURFACE_VIEW
                }
            )

            // Shutter (black background) when surface should be covered
            if (presentationState.coverSurface) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }

        // Loading indicator - show while buffering or before first frame
        if ((isBuffering || !firstFrameRendered) && error == null) {
            showLoading?.invoke() ?: DefaultLoadingIndicator()
        }

        // Error display
        error?.let { err ->
            showError?.invoke(err) ?: DefaultErrorDisplay(err)
        }
    }
}

@Composable
private fun DefaultLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DefaultErrorDisplay(error: XMediaError) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error.message,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Finds the Activity from a Context.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
