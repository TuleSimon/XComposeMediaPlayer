package com.anonymous.examples.demos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anonymous.examples.R
import com.anonymous.xmedia.XMediaPlayer
import com.anonymous.xmedia.rememberXMediaState

/**
 * Demo: Local File Playback
 *
 * This example demonstrates:
 * - Using Android Photo Picker to select a video
 * - Playing local video files via content:// URI
 * - Handling empty state (no video selected)
 * - Full playback controls for local content
 * - Replaying and changing videos
 *
 */
@Composable
fun LocalFileDemo() {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }


    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedVideoUri = it }
    }


    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedVideoUri = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Local File Playback",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pick a video from your device to play",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // File picker buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_videocam),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Photo Picker")
            }

            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch("video/*")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("File Picker")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video player or empty state
        if (selectedVideoUri != null) {
            LocalVideoPlayer(
                uri = selectedVideoUri!!,
                onChangeVideo = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            )
        } else {
            EmptyVideoState(
                onPickVideo = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            )
        }
    }
}

@Composable
private fun LocalVideoPlayer(
    uri: Uri,
    onChangeVideo: () -> Unit
) {
    val state = rememberXMediaState()
    val isPlaying by state.isPlaying.collectAsState()
    val isBuffering by state.isBuffering.collectAsState()
    val progress by state.progress.collectAsState()
    val duration by state.duration.collectAsState()
    val isMuted by state.isMuted.collectAsState()
    val error by state.error.collectAsState()

    Column {

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            XMediaPlayer(
                state = state,
                url = uri.toString(),
                repeat = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                autoPlay = true,
                onPlaybackEnded = {

                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // File info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_videocam),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uri.lastPathSegment ?: "Local Video",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onChangeVideo) {
                    Text("Change")
                }
            }
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
                    text = if (isBuffering) "Buffering..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
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
                    painter = painterResource(
                        if (isMuted) R.drawable.baseline_volume_down_24 else R.drawable.outline_volume_up_24
                    ),
                    contentDescription = if (isMuted) "Unmute" else "Mute"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Replay from start
            IconButton(onClick = { state.seekTo(0) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_replay),
                    contentDescription = "Replay"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

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
                    painter = painterResource(
                        if (isPlaying) R.drawable.baseline_pause_circle_outline_24 else R.drawable.baseline_play_circle_outline_24
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Seek buttons
            Button(onClick = { state.seekTo((progress - 10000).coerceAtLeast(0)) }) {
                Text("-10s")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = { state.seekTo(progress + 10000) }) {
                Text("+10s")
            }
        }

        // Error display
        error?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Error: ${err.message}",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyVideoState(
    onPickVideo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_videocam),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No video selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Pick a video from your device\nto start playback",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onPickVideo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_videocam),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Video")
                }
            }
        }
    }
}
