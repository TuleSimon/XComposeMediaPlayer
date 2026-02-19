package com.anonymous.examples.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.anonymous.examples.R
import com.anonymous.xmedia.XMediaConfig
import com.anonymous.xmedia.XMediaPlayer
import com.anonymous.xmedia.rememberXMediaState
import com.anonymous.xmedia.ui.XMediaQualityPicker

/**
 * Demo: HLS Quality Selection
 *
 * This example demonstrates:
 * - Using XMediaConfig.HighPerformance for streaming
 * - Observing available qualities from HLS manifest
 * - Displaying quality options to the user
 * - Using XMediaQualityPicker dialog
 * - Quick quality selection buttons
 * - Current quality display
 */
@Composable
fun QualitySelectionDemo() {
    val state = rememberXMediaState(XMediaConfig.HighPerformance) // Use high performance config
    val qualities by state.availableQualities.collectAsState()
    val currentQuality by state.currentQuality.collectAsState()
    val isPlaying by state.isPlaying.collectAsState()
    var showQualityPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "HLS Quality Selection",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select video quality for HLS streams",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Video Player
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            XMediaPlayer(
                state = state,
                url = SampleStreams.APPLE_FMP4, // This stream has multiple qualities
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                autoPlay = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quality info and controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Current Quality",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentQuality?.getDisplayLabel() ?: "Loading...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(onClick = { state.togglePlayPause() }) {
                    Icon(
                        painter = if (isPlaying) painterResource(R.drawable.baseline_pause_circle_outline_24) else
                            painterResource(R.drawable.baseline_play_circle_outline_24),
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(
                    onClick = { showQualityPicker = true },
                    enabled = qualities.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Quality Settings"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Available qualities list
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Available Qualities (${qualities.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (qualities.isEmpty()) {
                    Text(
                        text = "Loading qualities...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    qualities.forEach { quality ->
                        val isSelected = quality.id == currentQuality?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = quality.getDisplayLabel(),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (!quality.isAuto && quality.bitrate > 0) {
                                Text(
                                    text = "${quality.bitrate / 1000} kbps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick quality selection buttons
        if (qualities.isNotEmpty()) {
            Text(
                text = "Quick Select",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { state.setAutoQuality() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Auto")
                }

                qualities.filter { !it.isAuto }.take(3).forEach { quality ->
                    Button(
                        onClick = { state.setQuality(quality) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(quality.label)
                    }
                }
            }
        }

        // Quality picker dialog
        if (showQualityPicker) {
            XMediaQualityPicker(
                state = state,
                onDismiss = { showQualityPicker = false }
            )
        }
    }
}
