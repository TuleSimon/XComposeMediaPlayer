package com.anonymous.xmedia

import androidx.annotation.Keep

/**
 * Represents a video quality level for HLS and adaptive streaming.
 *
 * This class contains metadata about a specific video quality track that can be
 * selected for playback. For HLS streams, multiple qualities are typically available.
 *
 * @property id Unique identifier for this quality level (e.g., "720p", "auto")
 * @property label Human-readable label displayed to users (e.g., "720p", "Auto")
 * @property height Video height in pixels (0 for Auto quality)
 * @property width Video width in pixels (0 for Auto quality)
 * @property bitrate Bitrate in bits per second (0 for Auto quality)
 * @property isAuto True if this represents adaptive/automatic quality selection
 *
 * Example usage:
 * ```kotlin
 * val state = rememberXMediaState()
 *
 * // Get available qualities (populated after stream loads)
 * val qualities by state.availableQualities.collectAsState()
 *
 * // Set a specific quality
 * state.setQuality(qualities.find { it.height == 720 } ?: XMediaQuality.Auto)
 *
 * // Set auto quality
 * state.setAutoQuality()
 * ```
 */
@Keep
data class XMediaQuality(
    val id: String,
    val label: String,
    val height: Int,
    val width: Int,
    val bitrate: Int = 0,
    val isAuto: Boolean = false,
    internal val trackGroupIndex: Int? = null,
    internal val trackIndex: Int? = null
) {
    companion object {
        /**
         * Automatic quality selection.
         *
         * When selected, the player will automatically choose the best quality
         * based on network conditions and available bandwidth.
         */
        val Auto = XMediaQuality(
            id = "auto",
            label = "Auto",
            height = 0,
            width = 0,
            bitrate = 0,
            isAuto = true
        )
    }

    /**
     * Returns a user-friendly display label.
     *
     * @return "Auto" for automatic quality, or the label (e.g., "720p") for fixed qualities
     */
    fun getDisplayLabel(): String = when {
        isAuto -> "Auto"
        else -> label
    }

    /**
     * Returns a formatted bitrate string.
     *
     * @return Bitrate in kbps or Mbps format, or empty string if bitrate is 0
     */
    fun getFormattedBitrate(): String = when {
        bitrate <= 0 -> ""
        bitrate >= 1_000_000 -> "${bitrate / 1_000_000.0}Mbps"
        else -> "${bitrate / 1000}kbps"
    }

    /**
     * Returns a description of this quality level.
     *
     * @return Human-readable description including resolution and bitrate
     */
    fun getDescription(): String = when {
        isAuto -> "Automatically adjusts based on network conditions"
        else -> "${width}x${height} at ${getFormattedBitrate()}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is XMediaQuality) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
