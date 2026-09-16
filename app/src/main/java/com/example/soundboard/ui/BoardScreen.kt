package com.example.soundboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundboard.BoardViewModel
import com.example.soundboard.model.Tile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(vm: BoardViewModel = viewModel()) {
    val board by vm.board.collectAsStateWithLifecycle()
    var editingTileId by remember { mutableStateOf<String?>(null) }
    var showGridDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soundboard") },
                actions = {
                    TextButton(onClick = { showGridDialog = true }) {
                        Text("${board.rows} x ${board.columns}")
                    }
                }
            )
        }
    ) { insets ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(board.columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(board.visibleTiles, key = { it.id }) { tile ->
                TileCard(
                    tile = tile,
                    onTap = {
                        if (tile.isEmpty) editingTileId = tile.id else vm.play(tile)
                    },
                    onLongPress = { editingTileId = tile.id }
                )
            }
        }
    }

    val editing = board.tiles.firstOrNull { it.id == editingTileId }
    if (editing != null) {
        EditTileDialog(
            tile = editing,
            onLabelChange = { vm.setLabel(editing.id, it) },
            onSoundPicked = { vm.assignSound(editing.id, it) },
            onClear = { vm.clearTile(editing.id) },
            onDismiss = { editingTileId = null }
        )
    }

    if (showGridDialog) {
        GridSizeDialog(
            rows = board.rows,
            columns = board.columns,
            onConfirm = { r, c ->
                vm.resize(r, c)
                showGridDialog = false
            },
            onDismiss = { showGridDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCard(tile: Tile, onTap: () -> Unit, onLongPress: () -> Unit) {
    val filled = !tile.isEmpty
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (filled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    tile.label.isNotBlank() -> tile.label
                    filled -> "Unnamed"
                    else -> "+"
                },
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = if (filled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun EditTileDialog(
    tile: Tile,
    onLabelChange: (String) -> Unit,
    onSoundPicked: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(tile.id) { mutableStateOf(tile.label) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onSoundPicked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (tile.isEmpty) "Choose sound" else "Replace sound")
                }
                if (!tile.isEmpty) {
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
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun GridSizeDialog(
    rows: Int,
    columns: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var r by remember { mutableIntStateOf(rows) }
    var c by remember { mutableIntStateOf(columns) }
    val shrinking = r * c < rows * columns

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Stepper("Rows", r) { r = it }
                Stepper("Columns", c) { c = it }
                if (shrinking) {
                    Text(
                        "Shrinking just hides the last tiles — their sounds stay put " +
                            "and come back if you grow the grid again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(r, c) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > 1) onChange(value - 1) }) { Text("-") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { if (value < 8) onChange(value + 1) }) { Text("+") }
    }
}
