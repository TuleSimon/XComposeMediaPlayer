# XComposeMediaPlayer

A clean, simple, and powerful Jetpack Compose media player library for Android. Built on top of Media3/ExoPlayer with a simple state-based API.

[![](https://jitpack.io/v/TuleSimon/XComposeMediaPlayer.svg)](https://jitpack.io/#TuleSimon/XComposeMediaPlayer)

## Features

- Simple state-based API with `rememberXMediaState()` and `XMediaPlayer`
- Support for multiple media formats:
  - HLS streams (.m3u8)
  - DASH streams (.mpd)
  - Progressive video (MP4, WebM)
  - Local files
- HLS/DASH quality selection
- Lifecycle-aware (auto-pause on background)
- Screen wake lock while playing
- No dependency injection required
- Built-in loading and error states
- Customizable UI

## Installation

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.TuleSimon:XComposeMediaPlayer:1.0.0")
}
```

## Quick Start

### Basic Usage

```kotlin
@Composable
fun VideoScreen() {
    val state = rememberXMediaState()

    XMediaPlayer(
        state = state,
        url = "https://example.com/video.m3u8",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    )
}
```

### With Playback Controls

```kotlin
@Composable
fun VideoWithControls() {
    val state = rememberXMediaState()
    val isPlaying by state.isPlaying.collectAsState()
    val progress by state.progress.collectAsState()
    val duration by state.duration.collectAsState()

    Column {
        XMediaPlayer(
            state = state,
            url = "https://example.com/video.mp4",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { state.togglePlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }

        Slider(
            value = if (duration > 0) progress.toFloat() / duration else 0f,
            onValueChange = { state.seekToPercent(it) },
            modifier = Modifier.fillMaxWidth()
        )

        Text("${formatTime(progress)} / ${formatTime(duration)}")
    }
}
```

### HLS Quality Selection

```kotlin
@Composable
fun VideoWithQualityPicker() {
    val state = rememberXMediaState()
    val qualities by state.availableQualities.collectAsState()
    val currentQuality by state.currentQuality.collectAsState()
    var showQualityPicker by remember { mutableStateOf(false) }

    Column {
        XMediaPlayer(
            state = state,
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        if (qualities.isNotEmpty()) {
            Button(onClick = { showQualityPicker = true }) {
                Text("Quality: ${currentQuality?.label ?: "Auto"}")
            }
        }

        if (showQualityPicker) {
            XMediaQualityPicker(
                state = state,
                onDismiss = { showQualityPicker = false }
            )
        }
    }
}
```

### LazyColumn with Auto-Dispose

```kotlin
@Composable
fun VideoFeed(videos: List<String>) {
    LazyColumn {
        itemsIndexed(videos) { index, url ->
            val state = rememberXMediaState()

            XMediaPlayer(
                state = state,
                url = url,
                autoPlay = false, // Don't auto-play in lists
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
```

### Pause When Out of View (Pager)

```kotlin
@Composable
fun VideoPager(videos: List<String>) {
    val pagerState = rememberPagerState { videos.size }

    HorizontalPager(state = pagerState) { page ->
        val state = rememberXMediaState()
        val isCurrentPage = pagerState.currentPage == page

        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) {
                state.play()
            } else {
                state.pause()
            }
        }

        XMediaPlayer(
            state = state,
            url = videos[page],
            autoPlay = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

## API Reference

### rememberXMediaState

Creates and remembers an XMediaState instance.

```kotlin
@Composable
fun rememberXMediaState(
    config: XMediaConfig = XMediaConfig.Default
): XMediaState
```

### XMediaPlayer

The main video player composable.

```kotlin
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
)
```

### XMediaState

State holder with playback controls.

| Property | Type | Description |
|----------|------|-------------|
| `isPlaying` | `StateFlow<Boolean>` | Whether video is playing |
| `isBuffering` | `StateFlow<Boolean>` | Whether video is buffering |
| `progress` | `StateFlow<Long>` | Current position in ms |
| `duration` | `StateFlow<Long>` | Total duration in ms |
| `availableQualities` | `StateFlow<List<XMediaQuality>>` | Available quality options (HLS/DASH) |
| `currentQuality` | `StateFlow<XMediaQuality?>` | Currently selected quality |
| `error` | `StateFlow<XMediaError?>` | Current playback error |
| `isMuted` | `StateFlow<Boolean>` | Whether audio is muted |
| `player` | `ExoPlayer?` | Direct access to ExoPlayer |

| Method | Description |
|--------|-------------|
| `play()` | Start/resume playback |
| `pause()` | Pause playback |
| `togglePlayPause()` | Toggle play/pause |
| `seekTo(positionMs)` | Seek to position |
| `seekToPercent(percent)` | Seek to percentage (0.0-1.0) |
| `stop()` | Stop playback |
| `setQuality(quality)` | Set video quality |
| `setAutoQuality()` | Enable auto quality |
| `mute()` | Mute audio |
| `unmute()` | Unmute audio |
| `setVolume(volume)` | Set volume (0.0-1.0) |

### XMediaConfig

Configuration options for the player.

```kotlin
// Default configuration
val state = rememberXMediaState(XMediaConfig.Default)

// High performance (larger buffers, caching enabled)
val state = rememberXMediaState(XMediaConfig.HighPerformance)

// Low latency (for live streams)
val state = rememberXMediaState(XMediaConfig.LowLatency)

// Custom configuration
val state = rememberXMediaState(
    XMediaConfig(
        minBufferMs = 5000,
        maxBufferMs = 60000,
        enableCaching = true,
        cacheSize = 200L * 1024 * 1024 // 200 MB
    )
)
```

### XMediaQualityPicker

A dialog for selecting video quality.

```kotlin
@Composable
fun XMediaQualityPicker(
    state: XMediaState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
)
```

## Sample HLS Streams for Testing

```kotlin
// Tears of Steel
"https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"

// Apple fMP4 Example
"https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

// Live Stream (Akamai)
"https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"

// Another Live Stream
"https://moctobpltc-i.akamaihd.net/hls/live/571329/eight/playlist.m3u8"
```

## Requirements

- Android API 21+
- Jetpack Compose
- Kotlin 1.9+

## License

```
MIT License

Copyright (c) 2024 Tule Simon

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Author

**Tule Simon**

- GitHub: [@TuleSimon](https://github.com/TuleSimon)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
