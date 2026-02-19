package com.anonymous.xmedia

/**
 * Represents errors that can occur during media playback.
 *
 * Use this sealed class to handle different types of playback errors appropriately.
 * Each error type contains a message and optional cause for debugging.
 *
 * Example usage:
 * ```kotlin
 * val error by state.error.collectAsState()
 *
 * error?.let { err ->
 *     when (err) {
 *         is XMediaError.NetworkError -> showNetworkErrorUI()
 *         is XMediaError.SourceError -> showInvalidSourceUI()
 *         is XMediaError.DecoderError -> showDecoderErrorUI()
 *         is XMediaError.UnknownError -> showGenericErrorUI()
 *     }
 * }
 *
 * // Or handle via callback
 * XMediaPlayer(
 *     state = state,
 *     url = url,
 *     onError = { error ->
 *         Log.e("XMedia", "Playback error: ${error.message}", error.cause)
 *     }
 * )
 * ```
 */
sealed class XMediaError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * Error loading or parsing the media source.
     *
     * This typically occurs when:
     * - The URL is invalid or unreachable
     * - The media format is unsupported
     * - The manifest (HLS/DASH) is malformed
     */
    data class SourceError(
        override val message: String,
        override val cause: Throwable? = null
    ) : XMediaError(message, cause)

    /**
     * Error initializing or using the video/audio decoder.
     *
     * This typically occurs when:
     * - The codec is not supported on this device
     * - Hardware decoder initialization failed
     * - Decoder resources are exhausted
     */
    data class DecoderError(
        override val message: String,
        override val cause: Throwable? = null
    ) : XMediaError(message, cause)

    /**
     * Network-related error during playback.
     *
     * This typically occurs when:
     * - No internet connection
     * - Connection timeout
     * - Server returned an error response
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : XMediaError(message, cause)

    /**
     * An unclassified error occurred.
     *
     * Check the cause for more details about what went wrong.
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : XMediaError(message, cause)

    /**
     * Returns true if this error is likely recoverable by retrying.
     *
     * Network errors are generally recoverable, while decoder and source errors
     * typically are not.
     */
    fun isRecoverable(): Boolean = this is NetworkError

    override fun toString(): String = "${this::class.simpleName}(message='$message')"
}
