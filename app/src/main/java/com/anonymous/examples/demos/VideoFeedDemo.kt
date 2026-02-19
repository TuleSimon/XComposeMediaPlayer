package com.anonymous.examples.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toRect
import com.anonymous.examples.R
import com.anonymous.xmedia.CacheConfig
import com.anonymous.xmedia.XMediaConfig
import com.anonymous.xmedia.XMediaPlayer
import com.anonymous.xmedia.rememberXMediaState
import kotlin.math.roundToInt

/**
 * Demo: Video Feed (LazyColumn) with Auto-Dispose
 *
 * This example demonstrates:
 * - Multiple video players in a scrollable list
 * - Each video has its own state (rememberXMediaState per item)
 * - Automatic disposal when items scroll out of view
 * - Manual play controls (not auto-play in feeds)
 * - Play overlay button pattern
 */
@Composable
fun VideoFeedDemo() {
    var parentBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val visibleItemsIndex = remember { mutableStateListOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Video Feed (LazyColumn)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        Text(
            text = "Each video has its own state and is automatically disposed when scrolled out of view",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onLayoutRectChanged {
                    parentBounds = it.boundsInWindow.toRect()
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(SampleStreams.ALL_STREAMS) { index, url ->
                VideoFeedItem(
                    url = url,
                    title = "Video ${index + 1}",
                    isActive = visibleItemsIndex.minOrNull() == index,
                    parentBounds = parentBounds,
                    onVisibility = { percentage ->
                        if (percentage > 50) {
                            visibleItemsIndex.add(index)
                        } else  {
                            visibleItemsIndex.remove(index)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun VideoFeedItem(
    url: String,
    title: String,
    isActive: Boolean,
    parentBounds: androidx.compose.ui.geometry.Rect,
    onVisibility: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberXMediaState(
        config = XMediaConfig(
            cacheConfig = CacheConfig(
                enabled = true
            )
        )
    )
    val isPlaying by state.isPlaying.collectAsState()

    LaunchedEffect(isActive) {
        if (isActive) state.play() else state.pause()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                XMediaPlayer(
                    state = state,
                    url = url,
                    autoPlay = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onVisibilityChanged(
                            parentBounds = { parentBounds },
                            onChanged = onVisibility
                        )
                        .aspectRatio(16f / 9f)
                )

                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { state.play() },
                            modifier = Modifier
                                .background(
                                    color = Color.White.copy(alpha = 0.9f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(onClick = { state.togglePlayPause() }) {
                    Icon(
                        painter = if (isPlaying) painterResource(R.drawable.baseline_pause_circle_outline_24) else
                            painterResource(R.drawable.baseline_play_circle_outline_24),
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}

fun Modifier.onVisibilityChanged(
    parentBounds: () -> androidx.compose.ui.geometry.Rect,
    debounceMillis: Long = 50,
    throttleMillis: Long = 50,
    onChanged: (percentage: Int) -> Unit,
): Modifier = this.onLayoutRectChanged(
    debounceMillis = debounceMillis,
    throttleMillis = throttleMillis,
) { coordinates ->
    val itemBounds = coordinates.boundsInWindow.toRect()
    val viewport = parentBounds()
    if (viewport.height <= 0f || itemBounds.height <= 0f) {
        onChanged(0)
        return@onLayoutRectChanged
    }
    val visibleTop = maxOf(itemBounds.top, viewport.top)
    val visibleBottom = minOf(itemBounds.bottom, viewport.bottom)
    val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)
    val percentage = ((visibleHeight / itemBounds.height) * 100)
        .roundToInt()
        .coerceIn(0, 100)
    onChanged(percentage)
}

