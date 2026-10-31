package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundboard.model.Tile
import com.example.soundboard.model.languageName

/** Free-text speech for a one-off phrase no pad covers — bypasses [Tile] entirely. */
@Composable
internal fun SpeakDialog(onSpeak: (String) -> Unit, onDismiss: () -> Unit, spokenLanguage: String? = null) {
    var text by remember { mutableStateOf("") }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type what you want to say...") },
                    modifier = Modifier.fillMaxWidth()
                )
                // In Spanish mode what is typed is spoken as Spanish in the Spanish voice (#331).
                if (spokenLanguage != null) {
                    HelperText("ES is on: this is spoken in ${languageName(spokenLanguage)}, so type it in ${languageName(spokenLanguage)}.")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeak(text) },
                enabled = text.isNotBlank()
            ) { Text("Speak") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
