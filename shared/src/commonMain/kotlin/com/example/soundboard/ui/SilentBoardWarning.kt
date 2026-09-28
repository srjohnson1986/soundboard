package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Says why taps can't be heard, above the board, for as long as it's so (#248): the media
 * volume is off, or the device's speech isn't working and the board has tiles that speak.
 * Otherwise a tap on a silent board just does nothing, and whoever set it up can't tell why.
 */
@Composable
internal fun SilentBoardWarning(mediaMuted: Boolean, speechUnavailable: Boolean) {
    if (!mediaMuted && !speechUnavailable) return
    Column(modifier = Modifier.fillMaxWidth()) {
        if (mediaMuted) {
            WarningRow(Icons.AutoMirrored.Filled.VolumeOff, "The media volume is off, so the board can't be heard. Turn the volume up.")
        }
        if (speechUnavailable) {
            WarningRow(
                Icons.Filled.VoiceOverOff,
                "Speech isn't working on this device, so tiles without a recording are silent. " +
                    "Check the text-to-speech settings."
            )
        }
    }
}

@Composable
private fun WarningRow(icon: ImageVector, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        // Announced when it appears, so a screen reader user hears why the board went quiet.
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
