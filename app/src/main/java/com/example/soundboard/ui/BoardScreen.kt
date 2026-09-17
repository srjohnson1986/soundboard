package com.example.soundboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundboard.BoardViewModel
import com.example.soundboard.model.Tile
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    vm: BoardViewModel = viewModel(
        factory = BoardViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val board by vm.board.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editingTileId by remember { mutableStateOf<String?>(null) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::exportBoard) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importBoard) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Soundboard") },
                actions = {
                    Box {
                        TextButton(onClick = { showMenu = true }) {
                            Text("☰")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (editMode) "Edit mode ✓" else "Edit mode") },
                                onClick = {
                                    showMenu = false
                                    editMode = !editMode
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Grid size (${board.rows} x ${board.columns})") },
                                onClick = {
                                    showMenu = false
                                    showGridDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import") },
                                onClick = {
                                    showMenu = false
                                    importLauncher.launch(arrayOf("application/zip"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export") },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("soundboard-backup.zip")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { insets ->
        val density = LocalDensity.current
        var gridWidthPx by remember { mutableIntStateOf(0) }
        var draggedIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }

        val columns = board.columns
        val spacingPx = with(density) { 8.dp.toPx() }
        val cellStepPx = if (columns > 0 && gridWidthPx > 0) {
            (gridWidthPx - spacingPx * (columns - 1)) / columns + spacingPx
        } else 0f

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { gridWidthPx = it.width },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(board.visibleTiles, key = { _, tile -> tile.id }) { index, tile ->
                val isDragged = index == draggedIndex
                TileCard(
                    tile = tile,
                    editMode = editMode,
                    modifier = Modifier
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            if (isDragged) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                shadowElevation = 8f
                                scaleX = 1.05f
                                scaleY = 1.05f
                            }
                        }
                        .zIndex(if (isDragged) 1f else 0f)
                        .pointerInput(tile.id, columns) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = Offset.Zero
                                },
                                onDragEnd = {
                                    draggedIndex = null
                                    dragOffset = Offset.Zero
                                    vm.commitOrder()
                                },
                                onDragCancel = {
                                    draggedIndex = null
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val current = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    if (cellStepPx <= 0f) return@detectDragGesturesAfterLongPress
                                    val colDelta = (dragOffset.x / cellStepPx).roundToInt()
                                    val rowDelta = (dragOffset.y / cellStepPx).roundToInt()
                                    if (colDelta == 0 && rowDelta == 0) return@detectDragGesturesAfterLongPress
                                    val target = (current + rowDelta * columns + colDelta)
                                        .coerceIn(0, board.visibleTiles.lastIndex)
                                    if (target != current) {
                                        vm.previewMove(current, target)
                                        draggedIndex = target
                                        dragOffset -= Offset(colDelta * cellStepPx, rowDelta * cellStepPx)
                                    }
                                }
                            )
                        },
                    onTap = {
                        if (tile.isEmpty || editMode) editingTileId = tile.id else vm.play(tile)
                    }
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
            onVolumeChange = { vm.setVolume(editing.id, it) },
            onColorChange = { vm.setColor(editing.id, it) },
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
private fun TileCard(
    tile: Tile,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val filled = !tile.isEmpty
    val customColor = tile.colorArgb?.let { Color(it) }
    val containerColor = customColor ?: if (filled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = customColor?.let { textColorFor(it) } ?: if (filled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
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
                color = contentColor,
                modifier = Modifier.align(Alignment.Center)
            )
            if (editMode) {
                Text(
                    text = "✎",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                )
            }
        }
    }
}

/**
 * Black or white, whichever contrasts with [background]. Material3's own
 * `contentColorFor()` only resolves a real color when [background] exactly
 * matches a theme role; for an arbitrary custom tile color it falls back to
 * the ambient theme text color, which is light in dark mode — light text on
 * a light custom background. Deciding from the color's own luminance instead
 * keeps every custom color readable regardless of theme.
 */
private fun textColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

private val presetColors = listOf<Color?>(
    null,
    Color(0xFFE57373),
    Color(0xFFFFB74D),
    Color(0xFFFFF176),
    Color(0xFF81C784),
    Color(0xFF64B5F6),
    Color(0xFFBA68C8)
)

@Composable
private fun EditTileDialog(
    tile: Tile,
    onLabelChange: (String) -> Unit,
    onSoundPicked: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onColorChange: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(tile.id) { mutableStateOf(tile.label) }
    var volume by remember(tile.id) { mutableStateOf(tile.volume) }

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
                    placeholder = { Text("e.g. \"Call Mom\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (tile.isEmpty) "Choose sound" else "Replace sound")
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
                    Text("Colour", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presetColors.forEach { color ->
                            ColorSwatch(
                                color = color,
                                selected = color?.toArgb() == tile.colorArgb,
                                onClick = { onColorChange(color?.toArgb()) }
                            )
                        }
                    }
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
private fun ColorSwatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
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
                        "Shrinking just hides the last tiles — their sounds " +
                            "stay put and come back if you grow the grid again.",
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
        TextButton(onClick = { if (value < 50) onChange(value + 1) }) { Text("+") }
    }
}
