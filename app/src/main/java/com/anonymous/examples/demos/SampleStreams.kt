package com.anonymous.examples.demos

/**
 * Sample HLS streams for testing XComposeMediaPlayer
 */
object SampleStreams {
    // Tears of Steel - High quality HLS stream
    const val TEARS_OF_STEEL = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"

    // Apple fMP4 example - Multiple qualities
    const val APPLE_FMP4 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

    // MP4 as HLS
    const val MP4_HLS = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.mp4/.m3u8"

    // Live streams
    const val LIVE_AKAMAI_1 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
    const val LIVE_AKAMAI_2 = "https://moctobpltc-i.akamaihd.net/hls/live/571329/eight/playlist.m3u8"

    // All sample streams for the feed demo
    val ALL_STREAMS = listOf(
        TEARS_OF_STEEL,
        APPLE_FMP4,
        MP4_HLS,
        LIVE_AKAMAI_1,
        LIVE_AKAMAI_2
    )
}

/**
 * Formats milliseconds to a time string (e.g., "1:23" or "1:23:45")
 */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
