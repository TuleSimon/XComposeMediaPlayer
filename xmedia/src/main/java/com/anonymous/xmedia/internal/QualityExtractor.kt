package com.anonymous.xmedia.internal

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.anonymous.xmedia.XMediaQuality

/**
 * Extracts available video qualities from an ExoPlayer instance.
 *
 * This is used primarily for HLS and DASH streams where multiple quality
 * levels are available for selection.
 */
internal object QualityExtractor {

    /**
     * Extracts all available video qualities from the player's current tracks.
     *
     * Returns a list starting with the Auto quality option, followed by
     * available fixed quality options sorted by height (highest first).
     *
     * @param player ExoPlayer instance with loaded media
     * @return List of available qualities, empty if none found
     */
    @OptIn(UnstableApi::class)
    fun extract(player: ExoPlayer?): List<XMediaQuality> {
        if (player == null) return emptyList()

        val qualities = mutableListOf<XMediaQuality>()

        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            // Only process video tracks that are supported
            if (group.type != C.TRACK_TYPE_VIDEO || !group.isSupported) return@forEachIndexed

            for (trackIndex in 0 until group.mediaTrackGroup.length) {
                val format = group.mediaTrackGroup.getFormat(trackIndex)
                val height = format.height
                val width = format.width
                val bitrate = format.bitrate

                // Skip tracks with invalid dimensions or bitrate
                if (height <= 0 || width <= 0 || bitrate <= 0) continue

                qualities += XMediaQuality(
                    id = "${height}p",
                    label = "${height}p",
                    height = height,
                    width = width,
                    bitrate = bitrate,
                    isAuto = false,
                    trackGroupIndex = groupIndex,
                    trackIndex = trackIndex
                )
            }
        }

        // Remove duplicates (same height) and sort by height descending
        val uniqueQualities = qualities
            .distinctBy { it.height }
            .sortedByDescending { it.height }

        // Return with Auto quality at the beginning
        return buildList {
            if (uniqueQualities.isNotEmpty()) {
                add(XMediaQuality.Auto)
            }
            addAll(uniqueQualities)
        }
    }

    /**
     * Finds the best matching quality for a target height.
     *
     * @param qualities List of available qualities
     * @param targetHeight Desired video height in pixels
     * @return Matching quality or Auto if no match found
     */
    fun findQualityByHeight(
        qualities: List<XMediaQuality>,
        targetHeight: Int
    ): XMediaQuality {
        return qualities.find { !it.isAuto && it.height == targetHeight }
            ?: qualities.find { it.isAuto }
            ?: XMediaQuality.Auto
    }

    /**
     * Finds the best quality that doesn't exceed the given bitrate.
     *
     * @param qualities List of available qualities
     * @param maxBitrate Maximum bitrate in bits per second
     * @return Best quality within bitrate limit, or lowest quality if all exceed limit
     */
    fun findQualityByBitrate(
        qualities: List<XMediaQuality>,
        maxBitrate: Long
    ): XMediaQuality {
        val fixedQualities = qualities.filter { !it.isAuto && it.bitrate > 0 }

        // Find the highest quality that fits within the bitrate
        return fixedQualities
            .filter { it.bitrate <= maxBitrate }
            .maxByOrNull { it.height }
            ?: fixedQualities.minByOrNull { it.bitrate }
            ?: XMediaQuality.Auto
    }
}
