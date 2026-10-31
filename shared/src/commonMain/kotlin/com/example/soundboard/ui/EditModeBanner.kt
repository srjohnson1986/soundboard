package com.example.soundboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Says, above the board and for as long as it's so, that Edit mode is on and so tiles won't
 * play (#328): holding a tile now turns Edit mode on, so it can happen without meaning to.
 * Tapping it turns Edit mode off. It sits where [SilentBoardWarning] does, and
 * [BoardScreen] never shows it while the board is locked.
 */
@Composable
internal fun EditModeBanner(timeoutSeconds: Int, onTurnOff: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        // Announced when it appears, so a screen reader user hears why the board went quiet.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clickable(role = Role.Button, onClick = onTurnOff)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Column {
                Text(
                    "Tiles won't play while Edit mode is on. Tap here, or hold a tile, to turn it off.",
                    style = MaterialTheme.typography.bodyMedium
                )
                // When it will go off by itself (#344); nothing to say when that's turned off.
                if (timeoutSeconds > 0) {
                    Text(
                        "Turns off after ${editModeTimeoutPhrase(timeoutSeconds)} idle",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** [seconds] as the banner says it: "10 seconds", "1 minute", "5 minutes". */
internal fun editModeTimeoutPhrase(seconds: Int): String = when {
    seconds == 1 -> "1 second"
    seconds < 60 -> "$seconds seconds"
    seconds == 60 -> "1 minute"
    seconds % 60 == 0 -> "${seconds / 60} minutes"
    else -> "$seconds seconds"
}
