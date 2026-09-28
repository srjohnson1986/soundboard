package com.example.soundboard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.soundboard.OriginalSound
import com.example.soundboard.data.PickedFile
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import kotlinx.coroutines.delay

/** Fixed accent for the Preview/Play clip icon in [EditTileDialog]; not theme-derived since Material3 has no "success" role. */
private val PlayGreen = Color(0xFF2E7D32)

/**
 * Edits a draft of [tile]: every change stays in the dialog until Save hands the whole draft
 * to [onSave], and Cancel (or dismissing) calls [onDismiss] to throw it away, including any
 * sound picked or recorded meanwhile (#207). A picked or recorded sound arrives through the
 * callback [onPickSound]/[onStopRecording] are given, as the new file's name.
 */
@Composable
internal fun EditTileDialog(
    tile: Tile,
    isRecording: Boolean,
    speakUnrecordedTilesEnabled: Boolean,
    onPickSound: (PickedFile, onPicked: (String) -> Unit) -> Unit,
    onClear: () -> Unit,
    onPlay: (Tile) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: (onRecorded: (String) -> Unit) -> Unit,
    onRestoreOriginal: (label: String, onResult: (OriginalSound) -> Unit) -> Unit,
    onSave: (Tile) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(tile.id) { mutableStateOf(tile) }
    var micPermissionDenied by remember(tile.id) { mutableStateOf(false) }
    var recordingSeconds by remember(tile.id) { mutableIntStateOf(0) }
    var restoreMessage by remember(tile.id) { mutableStateOf<String?>(null) }

    val pickSound = rememberOpenFileLauncher(listOf("audio/*")) { file ->
        onPickSound(file) { name -> draft = draft.copy(fileName = name) }
    }

    val requestMicrophone = rememberMicrophoneAccess { granted ->
        micPermissionDenied = !granted
        if (granted) onStartRecording()
    }

    // Ticks the button label while recording; restarts (and resets to 0) each
    // time recording starts, and simply stops running — no cleanup needed —
    // the moment isRecording flips back to false.
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (true) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. \"Call Mom\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (draft.fileName != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = pickSound,
                            enabled = !isRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Replace sound")
                        }
                        OutlinedButton(
                            onClick = { draft = draft.copy(fileName = null) },
                            enabled = !isRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Remove sound")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = pickSound,
                        enabled = !isRecording,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose sound")
                    }
                }
                // Puts back the sound this tile had on the built-in board the board came from (#208):
                // into the draft, like any other change, so Save keeps it and Cancel doesn't.
                TextButton(
                    onClick = {
                        restoreMessage = null
                        val name = draft.label.trim()
                        onRestoreOriginal(name) { result ->
                            restoreMessage = when (result) {
                                is OriginalSound.Restored -> {
                                    draft = draft.copy(
                                        fileName = result.tile.fileName,
                                        volume = result.tile.volume,
                                        speakWhenNoSound = result.tile.speakWhenNoSound,
                                        ttsScript = result.tile.ttsScript
                                    )
                                    "Restored the original sound from ${result.boardLabel}. Tap Save to keep it."
                                }
                                is OriginalSound.NotFound ->
                                    "No original clip found for the \u201c$name\u201d tile" +
                                        (result.boardLabel?.let { " in $it" } ?: "") + "."
                            }
                        }
                    },
                    enabled = !isRecording && draft.label.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore original sound")
                }
                restoreMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SwitchRow(
                    label = "Speak the label instead",
                    checked = draft.speakWhenNoSound,
                    onCheckedChange = { draft = draft.copy(speakWhenNoSound = it) },
                    modifier = Modifier.padding(vertical = 4.dp),
                    supportingText = "Used when there's no sound file",
                    labelStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = draft.ttsScript.orEmpty(),
                    onValueChange = { draft = draft.copy(ttsScript = it) },
                    label = { Text("What to say") },
                    placeholder = { Text("Defaults to the name above") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onPlay(draft) },
                        enabled = draft.isPlayable(speakUnrecordedTilesEnabled) && !isRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = PlayGreen,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (draft.fileName == null) "Preview" else "Play clip")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isRecording) {
                                onStopRecording { name -> draft = draft.copy(fileName = name) }
                            } else {
                                micPermissionDenied = false
                                requestMicrophone()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isRecording) "Stop (${recordingSeconds}s)" else "Record")
                    }
                }
                if (micPermissionDenied) {
                    Text(
                        "Microphone permission is needed to record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Column {
                    Text("Volume", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = draft.volume,
                        onValueChange = { draft = draft.copy(volume = it) }
                    )
                }

                Column {
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    ColorPicker(selectedArgb = draft.colorArgb, onSelect = { draft = draft.copy(colorArgb = it) }, noneLabel = "Default color")
                }

                OverrideSection(
                    label = "Override opacity",
                    value = draft.opacity,
                    initialOverride = 1f,
                    onChange = { draft = draft.copy(opacity = it) },
                    labelStyle = MaterialTheme.typography.labelMedium
                ) { opacity -> OpacityControls(opacity) { draft = draft.copy(opacity = it) } }

                OverrideSection(
                    label = "Override border",
                    value = draft.border,
                    initialOverride = TileBorder(enabled = true),
                    onChange = { draft = draft.copy(border = it) },
                    labelStyle = MaterialTheme.typography.labelMedium
                ) { border -> BorderControls(border) { draft = draft.copy(border = it) } }

                // Clearing is its own, immediate action on the saved tile, not part of the draft.
                if (tile.hasSound) {
                    TextButton(
                        onClick = {
                            onClear()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear tile")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = !isRecording) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
