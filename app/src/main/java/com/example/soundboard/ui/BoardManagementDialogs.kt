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
import com.example.soundboard.BoardRef
import com.example.soundboard.StrayClip
import com.example.soundboard.data.SavedBoard
import kotlin.time.Duration.Companion.milliseconds

/** Lists built-in boards and boards saved on this device; picking one hands the choice back for the caller's own confirm gate. */
@Composable
internal fun OpenBoardDialog(
    builtInBoards: List<BoardRef.BuiltIn>,
    savedBoards: List<SavedBoard>,
    onSelect: (BoardRef, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open board") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                builtInBoards.forEach { ref ->
                    BoardRow(name = ref.label, subtitle = "Built-in", onClick = { onSelect(ref, ref.label) })
                }
                savedBoards.forEach { saved ->
                    BoardRow(
                        name = saved.name,
                        subtitle = "Saved · ${relativeSavedAt(saved.savedAt)}",
                        onClick = { onSelect(BoardRef.Saved(saved.id), saved.name) }
                    )
                }
                if (builtInBoards.isEmpty() && savedBoards.isEmpty()) {
                    HelperText("No saved boards yet — use \"Save board as\" to create one.")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BoardRow(name: String, subtitle: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Coarse "how long ago" for a saved board's timestamp — good enough for a picker list, no date library needed. */
internal fun relativeSavedAt(savedAt: Long): String {
    val elapsedMs = (System.currentTimeMillis() - savedAt).coerceAtLeast(0)
    val minutes = elapsedMs.milliseconds.inWholeMinutes
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
 * not on the live board, not in any saved board. Offers exporting them (for safekeeping)
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
                        "totaling ${formatFileSize(totalBytes)} — not used by any page or saved board."
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

/** Confirms replacing a board that has sounds on it with the board named [label]. */
@Composable
internal fun ConfirmOpenBoardDialog(label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace current board with \"$label\"?") },
        text = {
            Text(
                "This board has sounds on it. Opening another board replaces everything — " +
                    "export a backup first if you want to keep it."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
