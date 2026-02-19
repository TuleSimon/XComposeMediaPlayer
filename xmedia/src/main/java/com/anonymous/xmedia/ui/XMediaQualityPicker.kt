package com.anonymous.xmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anonymous.xmedia.XMediaQuality
import com.anonymous.xmedia.XMediaState

/**
 * A quality selection dialog for HLS/DASH streams.
 *
 * Displays available video qualities in a dialog, allowing the user to select
 * a specific quality or enable automatic quality selection.
 *
 * @param state The XMediaState to read qualities from and apply selection to
 * @param onDismiss Called when the dialog is dismissed
 * @param modifier Modifier for the dialog
 *
 * Example usage:
 * ```kotlin
 * var showQualityPicker by remember { mutableStateOf(false) }
 *
 * Button(onClick = { showQualityPicker = true }) {
 *     Text("Quality")
 * }
 *
 * if (showQualityPicker) {
 *     XMediaQualityPicker(
 *         state = state,
 *         onDismiss = { showQualityPicker = false }
 *     )
 * }
 * ```
 */
@Composable
fun XMediaQualityPicker(
    state: XMediaState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qualities by state.availableQualities.collectAsState()
    val currentQuality by state.currentQuality.collectAsState()

    if (qualities.isEmpty()) {
        // No qualities available - show info message
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = { Text("Video Quality") },
            text = {
                Text("Quality selection is only available for streaming videos (HLS/DASH).")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Video Quality") },
        text = {
            LazyColumn {
                items(qualities) { quality ->
                    QualityItem(
                        quality = quality,
                        isSelected = quality.id == currentQuality?.id,
                        onClick = {
                            state.setQuality(quality)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A standalone quality picker that can be embedded in custom UI.
 *
 * Unlike [XMediaQualityPicker], this doesn't wrap the content in a dialog,
 * allowing you to place it in your own container (bottom sheet, screen, etc.).
 *
 * @param qualities List of available qualities
 * @param currentQuality Currently selected quality
 * @param onQualitySelected Called when a quality is selected
 * @param modifier Modifier for the container
 *
 * Example usage:
 * ```kotlin
 * ModalBottomSheet(onDismissRequest = { /* ... */ }) {
 *     val qualities by state.availableQualities.collectAsState()
 *     val currentQuality by state.currentQuality.collectAsState()
 *
 *     XMediaQualityList(
 *         qualities = qualities,
 *         currentQuality = currentQuality,
 *         onQualitySelected = { quality ->
 *             state.setQuality(quality)
 *         }
 *     )
 * }
 * ```
 */
@Composable
fun XMediaQualityList(
    qualities: List<XMediaQuality>,
    currentQuality: XMediaQuality?,
    onQualitySelected: (XMediaQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    if (qualities.isEmpty()) {
        Column(
            modifier = modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No quality options available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quality selection is only available for streaming videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier) {
        qualities.forEach { quality ->
            QualityItem(
                quality = quality,
                isSelected = quality.id == currentQuality?.id,
                onClick = { onQualitySelected(quality) }
            )
        }
    }
}

@Composable
private fun QualityItem(
    quality: XMediaQuality,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quality.getDisplayLabel(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )

            if (!quality.isAuto) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = quality.getDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Adjusts based on network conditions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
