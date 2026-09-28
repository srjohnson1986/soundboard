package com.example.soundboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A quiet reminder above the board that its changes aren't in a backup yet (#249). It sits
 * above the tiles rather than in a dialog, so it never stands between someone and the board.
 */
@Composable
internal fun BackupReminder(onBackUp: () -> Unit, onLater: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Backup, contentDescription = null)
                Text(
                    "This board has changes that aren't in a backup yet. A backup keeps its " +
                        "recordings safe if this device is lost or reset.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onLater) { Text("Later") }
                TextButton(onClick = onBackUp) { Text("Back up") }
            }
        }
    }
}
