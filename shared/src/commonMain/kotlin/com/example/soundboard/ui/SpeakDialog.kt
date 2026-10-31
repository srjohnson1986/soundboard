package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.soundboard.model.Tile
import com.example.soundboard.model.languageName
import com.example.soundboard.translate.TranslatorStatus
import kotlinx.coroutines.delay

const val SPEAK_PREVIEW_TAG = "speak_preview"

/** How long after the last keystroke the Spanish preview is translated, so typing isn't one translation a letter. */
private const val PREVIEW_DELAY_MILLIS = 350L

/** Free-text speech for a one-off phrase no pad covers — bypasses [Tile] entirely. */
@Composable
internal fun SpeakDialog(
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
    spokenLanguage: String? = null,
    secondLanguage: String? = null,
    translatorStatus: TranslatorStatus = TranslatorStatus.Unavailable("this build has no translator."),
    onTranslate: suspend (String) -> String? = { null },
    onPrepareTranslator: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    // The Spanish for the text above (#332), once translated: the text it is for, and what came out (null: it failed).
    var preview by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val ready = translatorStatus == TranslatorStatus.Ready
    LaunchedEffect(text, ready, secondLanguage) {
        preview = null
        if (secondLanguage == null || !ready || text.isBlank()) return@LaunchedEffect
        delay(PREVIEW_DELAY_MILLIS)
        preview = text to onTranslate(text)
    }
    val translation = preview?.takeIf { it.first == text }
    // In Spanish mode with a translator that works, what is typed is English and the Spanish is said; without one,
    // what is typed is taken to be Spanish already (see BoardViewModel.speakAdHoc).
    val translating = spokenLanguage != null && ready
    val textToSpeak = if (translating) translation?.second.orEmpty() else text

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
                    val name = languageName(spokenLanguage)
                    HelperText(
                        if (ready) "ES is on: type it in English and the box says the $name." else "ES is on: this is spoken in $name, so type it in $name."
                    )
                }
                // The live Spanish preview (#332): shown whichever way the switch is, since it's how to check the Spanish first.
                if (secondLanguage != null) {
                    TranslatorStatusNote(translatorStatus)
                    if (translatorStatus is TranslatorStatus.NeedsDownload || translatorStatus is TranslatorStatus.Failed) {
                        OutlinedButton(onClick = onPrepareTranslator, modifier = Modifier.fillMaxWidth()) {
                            Text(if (translatorStatus is TranslatorStatus.Failed) "Try again" else "Download the ${languageName(secondLanguage)} model")
                        }
                    }
                    if (ready && text.isNotBlank()) {
                        Text(languageName(secondLanguage), style = MaterialTheme.typography.labelMedium)
                        Text(
                            when {
                                translation == null -> "..."
                                translation.second == null -> "Couldn't translate that."
                                else -> translation.second.orEmpty()
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag(SPEAK_PREVIEW_TAG)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeak(textToSpeak) },
                enabled = text.isNotBlank() && textToSpeak.isNotBlank()
            ) { Text("Speak") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
