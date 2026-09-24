package com.example.soundboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import kotlinx.coroutines.delay

/** Fixed accent for the Preview/Play clip icon in [EditTileDialog]; not theme-derived since Material3 has no "success" role. */
private val PlayGreen = Color(0xFF2E7D32)

@Composable
internal fun EditTileDialog(
    tile: Tile,
    isRecording: Boolean,
    speakUnrecordedTilesEnabled: Boolean,
    onLabelChange: (String) -> Unit,
    onTtsScriptChange: (String) -> Unit,
    onSoundPicked: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onRemoveSound: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onColorChange: (Int?) -> Unit,
    onOpacityChange: (Float?) -> Unit,
    onBorderChange: (TileBorder?) -> Unit,
    onSpeakWhenNoSoundChange: (Boolean) -> Unit,
    onPlay: (Tile) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(tile.id) { mutableStateOf(tile.label) }
    var ttsScript by remember(tile.id) { mutableStateOf(tile.ttsScript.orEmpty()) }
    var volume by remember(tile.id) { mutableStateOf(tile.volume) }
    var micPermissionDenied by remember(tile.id) { mutableStateOf(false) }
    var recordingSeconds by remember(tile.id) { mutableIntStateOf(0) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onSoundPicked) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. \"Call Mom\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tile.fileName != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("audio/*")) },
                            enabled = !isRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Replace sound")
                        }
                        OutlinedButton(
                            onClick = onRemoveSound,
                            enabled = !isRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Remove sound")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        enabled = !isRecording,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose sound")
                    }
                }
                SwitchRow(
                    label = "Speak the label instead",
                    checked = tile.speakWhenNoSound,
                    onCheckedChange = onSpeakWhenNoSoundChange,
                    modifier = Modifier.padding(vertical = 4.dp),
                    supportingText = "Used when there's no sound file",
                    labelStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = ttsScript,
                    onValueChange = { ttsScript = it },
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
                        onClick = { onPlay(tile.copy(label = label, ttsScript = ttsScript)) },
                        enabled = tile.copy(label = label, ttsScript = ttsScript).isPlayable(speakUnrecordedTilesEnabled) && !isRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = PlayGreen,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (tile.fileName == null) "Preview" else "Play clip")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isRecording) {
                                onStopRecording()
                            } else {
                                micPermissionDenied = false
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onStartRecording()
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
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
                        value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = { onVolumeChange(volume) }
                    )
                }

                Column {
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    ColorPicker(selectedArgb = tile.colorArgb, onSelect = onColorChange)
                }

                OverrideSection(
                    label = "Override opacity",
                    value = tile.opacity,
                    initialOverride = 1f,
                    onChange = onOpacityChange,
                    labelStyle = MaterialTheme.typography.labelMedium
                ) { opacity -> OpacityControls(opacity, onOpacityChange) }

                OverrideSection(
                    label = "Override border",
                    value = tile.border,
                    initialOverride = TileBorder(enabled = true),
                    onChange = onBorderChange,
                    labelStyle = MaterialTheme.typography.labelMedium
                ) { border -> BorderControls(border, onBorderChange) }

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
            TextButton(onClick = {
                onLabelChange(label.trim())
                onTtsScriptChange(ttsScript.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
