package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.soundboard.TranslateProgress
import com.example.soundboard.model.languageName
import com.example.soundboard.translate.TranslatorStatus

/**
 * The strip above the board while Spanish is filled in automatically (#342), in the same style as
 * [BackupReminder], and above the tiles for the same reason: the board stays usable while it works
 * (a tile without its Spanish yet speaks English), and nothing here stands between someone and a tile.
 *
 * While it works it says how far it has got and shows the translator's model downloading, with
 * **Cancel**; afterwards [note], if there is one, says why some tiles still say "no Spanish", with **OK**.
 */
@Composable
internal fun SpanishFillBanner(
    language: String,
    progress: TranslateProgress?,
    translatorStatus: TranslatorStatus,
    note: String?,
    onCancel: () -> Unit,
    onDismissNote: () -> Unit
) {
    if (progress == null && note == null) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Announced as it changes, so a screen reader user hears it start and finish.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Icon(Icons.Filled.Translate, contentDescription = null)
                Text(
                    text = if (progress != null) fillProgressText(languageName(language), progress) else note.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (progress != null) {
                if (progress.total > 0 && translatorStatus !is TranslatorStatus.Downloading) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)
                    )
                }
                if (translatorStatus.needsModel) {
                    TranslatorStatusNote(translatorStatus, Modifier.padding(top = 8.dp, end = 8.dp))
                }
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismissNote) { Text("OK") }
                }
            }
        }
    }
}

/** "Adding Spanish... 12 of 40", or just that it's getting ready when there is nothing counted yet. */
internal fun fillProgressText(languageName: String, progress: TranslateProgress): String =
    if (progress.total > 0) "Adding $languageName... ${progress.done} of ${progress.total}" else "Getting $languageName ready..."
