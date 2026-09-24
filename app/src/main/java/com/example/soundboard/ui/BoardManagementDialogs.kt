package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundboard.BoardViewModel
import com.example.soundboard.PresetRef
import com.example.soundboard.StrayClip
import com.example.soundboard.data.SavedPreset

/** Lists bundled ("Factory") and on-device saved presets to load; picking one hands the choice back for the caller's own confirm gate. */
@Composable
internal fun PresetPickerDialog(
    factoryPresets: List<PresetRef.Factory>,
    savedPresets: List<SavedPreset>,
    onSelect: (PresetRef, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                factoryPresets.forEach { ref ->
                    PresetRow(name = ref.label, subtitle = "Factory", onClick = { onSelect(ref, ref.label) })
                }
                savedPresets.forEach { saved ->
                    PresetRow(
                        name = saved.name,
                        subtitle = "Saved · ${relativeSavedAt(saved.savedAt)}",
                        onClick = { onSelect(PresetRef.Saved(saved.id), saved.name) }
                    )
                }
                if (factoryPresets.isEmpty() && savedPresets.isEmpty()) {
                    Text(
                        "No presets yet — use \"Save as preset\" to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PresetRow(name: String, subtitle: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Coarse "how long ago" for a saved-preset timestamp — good enough for a picker list, no date library needed. */
internal fun relativeSavedAt(savedAt: Long): String {
    val elapsedMs = (System.currentTimeMillis() - savedAt).coerceAtLeast(0)
    val minutes = elapsedMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

/** Formats a byte count as a short human-readable size, e.g. "340 KB" or "2.1 MB". */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

/**
 * Sound files [BoardViewModel.refreshStrayClips] found with nothing pointing at them —
 * not on the live board, not in any saved preset. Offers exporting them (for safekeeping)
 * before deleting, or deleting outright.
 */
@Composable
internal fun StrayCleanupDialog(
    clips: List<StrayClip>,
    onExportAndDelete: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clean up unused clips") },
        text = {
            if (clips.isEmpty()) {
                Text("No unused clips found.")
            } else {
                val totalBytes = clips.sumOf { it.sizeBytes }
                Text(
                    "${clips.size} unused clip${if (clips.size == 1) "" else "s"} found, " +
                        "totaling ${formatFileSize(totalBytes)} — not used by any page or saved preset."
                )
            }
        },
        confirmButton = {
            if (clips.isEmpty()) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                TextButton(onClick = {
                    onExportAndDelete()
                    onDismiss()
                }) { Text("Export & Delete") }
            }
        },
        dismissButton = {
            if (clips.isNotEmpty()) {
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) { Text("Delete") }
                }
            }
        }
    )
}

/** Confirms replacing a board that has sounds on it with the preset named [label]. */
@Composable
internal fun ConfirmApplyPresetDialog(label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace current board with \"$label\"?") },
        text = {
            Text(
                "This board has sounds on it. Loading a preset replaces everything — " +
                    "export a backup first if you want to keep it."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Load") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
