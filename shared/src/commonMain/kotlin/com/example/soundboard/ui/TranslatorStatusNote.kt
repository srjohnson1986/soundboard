package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.soundboard.translate.TranslatorStatus

/**
 * What the translator is doing or can't do, in words (#332), with a progress bar while its
 * language model downloads: a bar that fills where the platform reports how far it is, and one
 * that moves where it doesn't (ML Kit on Android gives no percentage). Shows nothing once
 * translating just works, so a control is never unexplained and never nagging.
 */
@Composable
internal fun TranslatorStatusNote(status: TranslatorStatus, modifier: Modifier = Modifier) {
    val text = status.explanation ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // Announced as it changes, so a screen reader user hears the download finish or fail.
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        if (status is TranslatorStatus.Downloading) {
            val percent = status.percent
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        HelperText(text)
    }
}
