package com.anonymous.examples.demos

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anonymous.examples.R
import com.anonymous.xmedia.XMediaConfig
import com.anonymous.xmedia.XMediaPlayer
import com.anonymous.xmedia.rememberXMediaState

/**
 * Demo: Single Video Player with Custom Controls
 *
 * This example demonstrates:
 * - Basic XMediaPlayer usage
 * - Custom playback controls (play/pause, mute, seek)
 * - Progress slider with time display
 * - State observation via collectAsState()
 */
@Composable
fun SinglePlayerDemo() {
    val state = rememberXMediaState(config = XMediaConfig.HighPerformance)
    val isPlaying by state.isPlaying.collectAsState()
    val isBuffering by state.isBuffering.collectAsState()
    val progress by state.progress.collectAsState()
    val duration by state.duration.collectAsState()
    val isMuted by state.isMuted.collectAsState()
    val error by state.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Single Video Player",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Basic usage with custom controls",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Video Player
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Log.e("Cache size",state.getCacheSize().toString()+" bytes")
            XMediaPlayer(
                state = state,
                url = SampleStreams.TEARS_OF_STEEL,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                autoPlay = true,
                onPlaybackEnded = {
                    // Video ended - could show replay button or auto-play next
                },
                onError = { err ->
                    // Handle error - already shown by default error UI
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Slider
        Column {
            Slider(
                value = if (duration > 0) progress.toFloat() / duration else 0f,
                onValueChange = { state.seekToPercent(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(progress),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute button
            IconButton(onClick = { if (isMuted) state.unmute() else state.mute() }) {
                Icon(
                    painter = if (isMuted) painterResource(R.drawable.outline_volume_up_24) else
                        painterResource(R.drawable.baseline_volume_down_24),
                    contentDescription = if (isMuted) "Unmute" else "Mute"
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play/Pause button
            IconButton(
                onClick = { state.togglePlayPause() },
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(8.dp)
            ) {
                Icon(
                    painter = if (isPlaying) painterResource(R.drawable.baseline_pause_circle_outline_24) else
                        painterResource(R.drawable.baseline_play_circle_outline_24),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Seek buttons
            Button(onClick = { state.seekTo((progress - 10000).coerceAtLeast(0)) }) {
                Text("-10s")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = { state.seekTo(progress + 10000) }) {
                Text("+10s")
            }
        }

        // Status info
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Status: ${if (isPlaying) "Playing" else if (isBuffering) "Buffering" else "Paused"}")
                Text("Progress: ${formatTime(progress)} / ${formatTime(duration)}")
                error?.let {
                    Text("Error: ${it.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
