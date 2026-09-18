package com.example.soundboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundboard.BoardViewModel
import com.example.soundboard.PresetRef
import com.example.soundboard.data.SavedPreset
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

/** Five minutes of no interaction before the board snaps back to its home page. */
private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L

/** Which tile an open [EditTileDialog] is showing — a page tile or one from the shared pinned row. */
private sealed interface EditTarget {
    data class PageTile(val id: String) : EditTarget
    data class PinnedTile(val id: String) : EditTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    vm: BoardViewModel = viewModel(
        factory = BoardViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val board by vm.board.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val isRecording by vm.isRecording.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    var editingTarget by remember { mutableStateOf<EditTarget?>(null) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showPresetPickerDialog by remember { mutableStateOf(false) }
    var pendingPresetApply by remember { mutableStateOf<Pair<PresetRef, String>?>(null) }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var renamePageIndex by remember { mutableStateOf<Int?>(null) }
    var pageOptionsIndex by remember { mutableStateOf<Int?>(null) }
    var showPageColorDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var lastInteractionAt by remember { mutableLongStateOf(0L) }
    var isDragActive by remember { mutableStateOf(false) }
    var deletePageIndex by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun touch() {
        lastInteractionAt = System.currentTimeMillis()
    }

    // A page with any sound assigned needs an explicit confirm; an all-empty
    // page is cheap to recreate, so deleting it outright isn't worth a dialog.
    fun requestDeletePage(index: Int) {
        val page = board.pages.getOrNull(index) ?: return
        if (page.tiles.any { !it.isEmpty }) {
            deletePageIndex = index
        } else {
            vm.deletePage(index)
        }
    }

    // Applying a preset over a board with any sound needs an explicit confirm — same
    // reasoning as page deletion, just board-wide instead of one page.
    fun requestApplyPreset(ref: PresetRef, label: String) {
        if (board.hasAnySound) {
            pendingPresetApply = ref to label
        } else {
            vm.applyPreset(ref)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // A page-tile dialog left open while switching pages would otherwise resolve
    // against a tile id that belongs to the page the user just left. A pinned-tile
    // dialog is unaffected — pinned tiles are the same regardless of page. Auto-return
    // (below) can trigger this while a recording is in progress, so cancel it too.
    LaunchedEffect(board.currentPageIndex) {
        if (editingTarget is EditTarget.PageTile) {
            editingTarget = null
            vm.cancelRecording()
        }
    }

    // Auto-return to the home page after a few idle minutes. Restarts on every
    // interaction (lastInteractionAt changing cancels the previous delay).
    LaunchedEffect(lastInteractionAt, board.homePageIndex) {
        val home = board.homePageIndex ?: return@LaunchedEffect
        delay(IDLE_TIMEOUT_MS)
        if (home != board.currentPageIndex) {
            vm.switchPage(home)
        }
    }

    val pagerState = rememberPagerState(initialPage = board.currentPageIndex) { board.pages.size }

    // Swipe -> ViewModel: a settled swipe becomes the active page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != board.currentPageIndex) {
                touch()
                vm.switchPage(page)
            }
        }
    }

    // ViewModel -> pager: a tab tap or the idle timer scrolls the pager to match.
    LaunchedEffect(board.currentPageIndex) {
        if (pagerState.currentPage != board.currentPageIndex) {
            pagerState.animateScrollToPage(board.currentPageIndex)
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
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Soundboard", style = MaterialTheme.typography.labelSmall)
                            Text(
                                board.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showMenu = true }) {
                                Text("☰")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                // Mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Edit mode")
                                    Switch(
                                        checked = editMode,
                                        onCheckedChange = { editMode = it }
                                    )
                                }
                                HorizontalDivider()
                                // Page layout
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            showGridDialog = true
                                        }
                                    ) {
                                        Text("Grid size (${board.currentPage.rows}x${board.currentPage.columns})")
                                    }
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            showPageColorDialog = true
                                        }
                                    ) {
                                        Text("Page color")
                                    }
                                }
                                HorizontalDivider()
                                // Page management
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            showAddPageDialog = true
                                        }
                                    ) {
                                        Text("Add page")
                                    }
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            renamePageIndex = board.currentPageIndex
                                        }
                                    ) {
                                        Text("Rename page")
                                    }
                                    if (board.pages.size > 1) {
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                showMenu = false
                                                requestDeletePage(board.currentPageIndex)
                                            }
                                        ) {
                                            Text("Delete page")
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Home page")
                                    Switch(
                                        checked = board.homePageIndex == board.currentPageIndex,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                vm.setHomePage(board.currentPageIndex)
                                            } else {
                                                vm.clearHomePage()
                                            }
                                        }
                                    )
                                }
                                if (board.pinnedTiles.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Add pinned row") },
                                        onClick = {
                                            showMenu = false
                                            vm.addPinnedRow()
                                        }
                                    )
                                }
                                HorizontalDivider()
                                // Presets — lightweight, same-device version history (see PresetRepository)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            showSavePresetDialog = true
                                        }
                                    ) {
                                        Text("Save as preset")
                                    }
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            vm.refreshPresets()
                                            showPresetPickerDialog = true
                                        }
                                    ) {
                                        Text("Load preset")
                                    }
                                }
                                HorizontalDivider()
                                // Backup — full, portable, self-contained (structure + audio)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            exportLauncher.launch("soundboard-backup.zip")
                                        }
                                    ) {
                                        Text("Export backup")
                                    }
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showMenu = false
                                            importLauncher.launch(arrayOf("application/zip"))
                                        }
                                    ) {
                                        Text("Import backup")
                                    }
                                }
                            }
                        }
                    }
                )
                if (board.pages.size > 1) {
                    PrimaryScrollableTabRow(selectedTabIndex = board.currentPageIndex) {
                        val tabHaptics = LocalHapticFeedback.current
                        board.pages.forEachIndexed { index, page ->
                            // Long-press detection must run on the Initial (outside-in) pointer
                            // pass and win the race against Tab's own click before it can consume
                            // the eventual up event on the Main pass — see the note above.
                            Box(
                                modifier = Modifier.pointerInput(page.id, editMode) {
                                    if (!editMode) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(pass = PointerEventPass.Initial)
                                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                        }
                                        if (up == null) {
                                            tabHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            pageOptionsIndex = index
                                            // Swallow the eventual release so Tab's own click,
                                            // which runs on the later Main pass, never sees it.
                                            waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
                                        }
                                    }
                                }
                            ) {
                                Tab(
                                    selected = index == board.currentPageIndex,
                                    onClick = {
                                        touch()
                                        vm.switchPage(index)
                                    },
                                    text = { Text(page.name) },
                                    selectedContentColor = page.color?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            if (board.pinnedTiles.isNotEmpty()) {
                PinnedRow(
                    tiles = board.pinnedTiles.take(board.currentPage.columns),
                    editMode = editMode,
                    aspectRatio = board.currentPage.tileAspectRatio,
                    onTap = { tile ->
                        touch()
                        if (tile.isEmpty || editMode) {
                            editingTarget = EditTarget.PinnedTile(tile.id)
                        } else {
                            vm.play(tile)
                        }
                    }
                )
            }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isDragActive,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = board.pages.getOrNull(pageIndex)
                if (page != null) {
                    PageGrid(
                        page = page,
                        editMode = editMode,
                        isActive = pageIndex == board.currentPageIndex,
                        onTap = { tile ->
                            touch()
                            if (tile.isEmpty || editMode) {
                                editingTarget = EditTarget.PageTile(tile.id)
                            } else {
                                vm.play(tile)
                            }
                        },
                        onPreviewMove = vm::previewMove,
                        onCommitOrder = vm::commitOrder,
                        onDragActiveChanged = { isDragActive = it }
                    )
                }
            }
        }
    }

    when (val target = editingTarget) {
        is EditTarget.PageTile -> {
            val editing = board.currentPage.tiles.firstOrNull { it.id == target.id }
            if (editing != null) {
                EditTileDialog(
                    tile = editing,
                    isRecording = isRecording,
                    onLabelChange = { vm.setLabel(editing.id, it) },
                    onSoundPicked = { vm.assignSound(editing.id, it) },
                    onClear = { vm.clearTile(editing.id) },
                    onVolumeChange = { vm.setVolume(editing.id, it) },
                    onColorChange = { vm.setColor(editing.id, it) },
                    onPlay = { vm.play(editing) },
                    onStartRecording = vm::startRecording,
                    onStopRecording = { vm.stopRecording(editing.id) },
                    onDismiss = {
                        vm.cancelRecording()
                        editingTarget = null
                    }
                )
            }
        }
        is EditTarget.PinnedTile -> {
            val editing = board.pinnedTiles.firstOrNull { it.id == target.id }
            if (editing != null) {
                EditTileDialog(
                    tile = editing,
                    isRecording = isRecording,
                    onLabelChange = { vm.setPinnedLabel(editing.id, it) },
                    onSoundPicked = { vm.assignPinnedSound(editing.id, it) },
                    onClear = { vm.clearPinnedTile(editing.id) },
                    onVolumeChange = { vm.setPinnedVolume(editing.id, it) },
                    onColorChange = { vm.setPinnedColor(editing.id, it) },
                    onPlay = { vm.play(editing) },
                    onStartRecording = vm::startRecording,
                    onStopRecording = { vm.stopPinnedRecording(editing.id) },
                    onDismiss = {
                        vm.cancelRecording()
                        editingTarget = null
                    }
                )
            }
        }
        null -> Unit
    }

    if (showGridDialog) {
        GridSizeDialog(
            rows = board.currentPage.rows,
            columns = board.currentPage.columns,
            aspectRatio = board.currentPage.tileAspectRatio,
            onConfirm = { r, c, ratio ->
                vm.resize(r, c)
                vm.setTileAspectRatio(ratio)
                showGridDialog = false
            },
            onDismiss = { showGridDialog = false }
        )
    }

    if (showPageColorDialog) {
        PageColorDialog(
            current = board.currentPage.color,
            onSelect = { vm.setPageColor(it) },
            onDismiss = { showPageColorDialog = false }
        )
    }

    if (showSavePresetDialog) {
        TextInputDialog(
            title = "Save as preset",
            label = "Preset name",
            initial = board.name,
            onConfirm = {
                vm.saveAsPreset(it.trim())
                showSavePresetDialog = false
            },
            onDismiss = { showSavePresetDialog = false }
        )
    }

    if (showPresetPickerDialog) {
        PresetPickerDialog(
            factoryPresets = vm.factoryPresets(LocalContext.current),
            savedPresets = presets,
            onSelect = { ref, label ->
                showPresetPickerDialog = false
                requestApplyPreset(ref, label)
            },
            onDismiss = { showPresetPickerDialog = false }
        )
    }

    pendingPresetApply?.let { (ref, label) ->
        AlertDialog(
            onDismissRequest = { pendingPresetApply = null },
            title = { Text("Replace current board with \"$label\"?") },
            text = {
                Text(
                    "This board has sounds on it. Loading a preset replaces everything — " +
                        "export a backup first if you want to keep it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyPreset(ref)
                    pendingPresetApply = null
                }) { Text("Load") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPresetApply = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddPageDialog) {
        TextInputDialog(
            title = "Add page",
            label = "Page name",
            initial = "Page ${board.pages.size + 1}",
            onConfirm = {
                vm.addPage(it.trim())
                showAddPageDialog = false
            },
            onDismiss = { showAddPageDialog = false }
        )
    }

    renamePageIndex?.let { index ->
        board.pages.getOrNull(index)?.let { page ->
            TextInputDialog(
                title = "Rename page",
                label = "Page name",
                initial = page.name,
                onConfirm = {
                    vm.renamePage(index, it.trim())
                    renamePageIndex = null
                },
                onDismiss = { renamePageIndex = null }
            )
        }
    }

    pageOptionsIndex?.let { index ->
        board.pages.getOrNull(index)?.let { page ->
            PageOptionsDialog(
                pageName = page.name,
                canMoveLeft = index > 0,
                canMoveRight = index < board.pages.lastIndex,
                canDelete = board.pages.size > 1,
                onRename = {
                    pageOptionsIndex = null
                    renamePageIndex = index
                },
                onMoveLeft = {
                    vm.movePage(index, index - 1)
                    pageOptionsIndex = null
                },
                onMoveRight = {
                    vm.movePage(index, index + 1)
                    pageOptionsIndex = null
                },
                onDelete = {
                    pageOptionsIndex = null
                    requestDeletePage(index)
                },
                onDismiss = { pageOptionsIndex = null }
            )
        }
    }

    deletePageIndex?.let { index ->
        board.pages.getOrNull(index)?.let { page ->
            val soundCount = page.tiles.count { !it.isEmpty }
            AlertDialog(
                onDismissRequest = { deletePageIndex = null },
                title = { Text("Delete \"${page.name}\"?") },
                text = {
                    Text(
                        "This page has $soundCount tile${if (soundCount == 1) "" else "s"} " +
                            "with sounds. Deleting it can't be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deletePage(index)
                        deletePageIndex = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deletePageIndex = null }) { Text("Cancel") }
                }
            )
        }
    }
}

/** The fixed row shown above every page's grid, identical regardless of which page is active. */
@Composable
private fun PinnedRow(
    tiles: List<Tile>,
    editMode: Boolean,
    aspectRatio: Float,
    onTap: (Tile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { tile ->
            TileCard(
                tile = tile,
                editMode = editMode,
                aspectRatio = aspectRatio,
                pageColor = null,
                modifier = Modifier.weight(1f),
                onTap = { onTap(tile) }
            )
        }
    }
}

/** One page's scrollable grid. Drag-to-reorder only attaches when [isActive] — the page actually on screen. */
@Composable
private fun PageGrid(
    page: Page,
    editMode: Boolean,
    isActive: Boolean,
    onTap: (Tile) -> Unit,
    onPreviewMove: (Int, Int) -> Unit,
    onCommitOrder: () -> Unit,
    onDragActiveChanged: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var gridWidthPx by remember { mutableIntStateOf(0) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val columns = page.columns
    val spacingPx = with(density) { 8.dp.toPx() }
    val cellStepPx = if (columns > 0 && gridWidthPx > 0) {
        (gridWidthPx - spacingPx * (columns - 1)) / columns + spacingPx
    } else 0f

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onSizeChanged { gridWidthPx = it.width },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(page.visibleTiles, key = { _, tile -> tile.id }) { index, tile ->
            val isDragged = index == draggedIndex
            TileCard(
                tile = tile,
                editMode = editMode,
                aspectRatio = page.tileAspectRatio,
                pageColor = page.color?.let { Color(it) },
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
                    .pointerInput(tile.id, columns, isActive) {
                        if (!isActive) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedIndex = index
                                dragOffset = Offset.Zero
                                onDragActiveChanged(true)
                            },
                            onDragEnd = {
                                draggedIndex = null
                                dragOffset = Offset.Zero
                                onDragActiveChanged(false)
                                onCommitOrder()
                            },
                            onDragCancel = {
                                draggedIndex = null
                                dragOffset = Offset.Zero
                                onDragActiveChanged(false)
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
                                    .coerceIn(0, page.visibleTiles.lastIndex)
                                if (target != current) {
                                    onPreviewMove(current, target)
                                    draggedIndex = target
                                    dragOffset -= Offset(colDelta * cellStepPx, rowDelta * cellStepPx)
                                }
                            }
                        )
                    },
                onTap = { onTap(tile) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCard(
    tile: Tile,
    editMode: Boolean,
    aspectRatio: Float,
    pageColor: Color?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val filled = !tile.isEmpty
    val customColor = tile.colorArgb?.let { Color(it) }
    val defaultColor = pageColor.takeIf { filled }
    val containerColor = customColor ?: defaultColor ?: if (filled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = (customColor ?: defaultColor)?.let { textColorFor(it) } ?: if (filled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .aspectRatio(aspectRatio)
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
    isRecording: Boolean,
    onLabelChange: (String) -> Unit,
    onSoundPicked: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onColorChange: (Int?) -> Unit,
    onPlay: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(tile.id) { mutableStateOf(tile.label) }
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
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    enabled = !isRecording,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (tile.isEmpty) "Choose sound" else "Replace sound")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onPlay,
                        enabled = !tile.isEmpty && !isRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Play clip")
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
    aspectRatio: Float,
    onConfirm: (Int, Int, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var r by remember { mutableIntStateOf(rows) }
    var c by remember { mutableIntStateOf(columns) }
    var wide by remember { mutableStateOf(aspectRatio != 1f) }
    val shrinking = r * c < rows * columns

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Stepper("Rows", r) { r = it }
                Stepper("Columns", c) { c = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tile shape", modifier = Modifier.weight(1f))
                    TextButton(onClick = { wide = false }) { Text(if (!wide) "Square ✓" else "Square") }
                    TextButton(onClick = { wide = true }) { Text(if (wide) "Wide ✓" else "Wide") }
                }
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
        confirmButton = {
            TextButton(onClick = { onConfirm(r, c, if (wide) 4f / 3f else 1f) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PageColorDialog(current: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page color") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetColors.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color?.toArgb() == current,
                        onClick = { onSelect(color?.toArgb()) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun PageOptionsDialog(
    pageName: String,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pageName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                    Text("Rename")
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onMoveLeft,
                        enabled = canMoveLeft,
                        modifier = Modifier.weight(1f)
                    ) { Text("Move left") }
                    TextButton(
                        onClick = onMoveRight,
                        enabled = canMoveRight,
                        modifier = Modifier.weight(1f)
                    ) { Text("Move right") }
                }
                if (canDelete) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete page")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Lists bundled ("Factory") and on-device saved presets to load; picking one hands the choice back for the caller's own confirm gate. */
@Composable
private fun PresetPickerDialog(
    factoryPresets: List<PresetRef.Factory>,
    savedPresets: List<SavedPreset>,
    onSelect: (PresetRef, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                factoryPresets.forEach { ref ->
                    PresetRow(name = ref.label, subtitle = "Factory", onClick = { onSelect(ref, ref.label) })
                }
                savedPresets.forEach { saved ->
                    PresetRow(
                        name = saved.name,
                        subtitle = "Saved · ${relativeSavedAt(saved.savedAt)}",
                        onClick = { onSelect(PresetRef.Saved(saved.id), saved.name) }
                    )
                }
                if (factoryPresets.isEmpty() && savedPresets.isEmpty()) {
                    Text(
                        "No presets yet — use \"Save as preset\" to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PresetRow(name: String, subtitle: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Coarse "how long ago" for a saved-preset timestamp — good enough for a picker list, no date library needed. */
private fun relativeSavedAt(savedAt: Long): String {
    val elapsedMs = (System.currentTimeMillis() - savedAt).coerceAtLeast(0)
    val minutes = elapsedMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
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
