package com.example.soundboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.ui.theme.presetColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

/** Choices offered for the auto-return-to-home idle timeout in [SettingsDialog]; 0 means "Off". */
private val IDLE_TIMEOUT_OPTIONS_MINUTES = listOf(0, 1, 2, 5, 10)

private fun idleTimeoutLabel(minutes: Int) = if (minutes == 0) "Off" else "$minutes min"

/**
 * Choices offered for the page-tab long-press duration in [SettingsDialog]. 500ms is the
 * Android platform default (ViewConfiguration.longPressTimeoutMillis); the longer options
 * are for anyone who needs more of a deliberate hold before it fires, e.g. on a shared or
 * care board someone else operates.
 */
private val LONG_PRESS_DURATION_OPTIONS_MILLIS = listOf(500, 800, 1200)

private fun longPressDurationLabel(millis: Int) = when (millis) {
    500 -> "Default"
    800 -> "Long"
    else -> "Longer"
}

/** Fixed accent for the Preview/Play clip icon in [EditTileDialog]; not theme-derived since Material3 has no "success" role. */
private val PlayGreen = Color(0xFF2E7D32)

/** Update alongside each tagged release — [RELEASE_URL] points at this tag's own notes. */
private const val APP_VERSION = "v0.1.0"

private const val RELEASES_BASE_URL = "https://github.com/srjohnson1986/soundboard/releases"
private const val RELEASE_URL = "$RELEASES_BASE_URL/tag/$APP_VERSION"

/** Which tile an open [EditTileDialog] is showing — a page tile or one from the home page's sticky row. */
private sealed interface EditTarget {
    data class PageTile(val id: String) : EditTarget
    data class HomeRowTile(val id: String) : EditTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    vm: BoardViewModel = viewModel(
        factory = BoardViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val context = LocalContext.current
    val board by vm.board.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val isRecording by vm.isRecording.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    var editingTarget by remember { mutableStateOf<EditTarget?>(null) }
    var gridDialogIndex by remember { mutableStateOf<Int?>(null) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showPresetPickerDialog by remember { mutableStateOf(false) }
    var pendingPresetApply by remember { mutableStateOf<Pair<PresetRef, String>?>(null) }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var renamePageIndex by remember { mutableStateOf<Int?>(null) }
    var pageOptionsIndex by remember { mutableStateOf<Int?>(null) }
    var pageColorDialogIndex by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSpeakDialog by remember { mutableStateOf(false) }
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
    // against a tile id that belongs to the page the user just left. A home-row-tile
    // dialog is unaffected — it always resolves against the home page regardless of
    // which page is current. Auto-return (below) can trigger this while a recording
    // is in progress, so cancel it too.
    LaunchedEffect(board.currentPageIndex) {
        if (editingTarget is EditTarget.PageTile) {
            editingTarget = null
            vm.cancelRecording()
        }
    }

    // Auto-return to the home page after a few idle minutes (configurable in
    // SettingsDialog; 0 disables it). Restarts on every interaction
    // (lastInteractionAt changing cancels the previous delay).
    LaunchedEffect(lastInteractionAt, board.homePageIndex, board.idleTimeoutMinutes) {
        val home = board.homePageIndex ?: return@LaunchedEffect
        if (board.idleTimeoutMinutes <= 0) return@LaunchedEffect
        delay(board.idleTimeoutMinutes * 60_000L)
        if (home != board.currentPageIndex) {
            vm.switchPage(home)
        }
    }

    // Prevents auto-lock while the board is on screen — meant for boards mounted or left
    // open as a standing communication aid. Cleared onDispose so leaving BoardScreen (or
    // toggling the setting off) doesn't leave the window flag stuck on.
    val view = LocalView.current
    DisposableEffect(board.keepScreenAwake) {
        view.keepScreenOn = board.keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }

    val pagerState = rememberPagerState(initialPage = board.currentPageIndex) { board.pages.size }
    // Guards the loop between the two effects below: true only while WE are
    // driving a programmatic multi-page jump (a tab tap or the idle timer),
    // so the swipe effect can ignore the intermediate pages that jump flies
    // over instead of feeding them back and cancelling it early (#72).
    var isProgrammaticPageScroll by remember { mutableStateOf(false) }

    // Swipe -> ViewModel: tracks currentPage, not settledPage, so the tab
    // indicator flips the instant a real swipe crosses the page boundary
    // instead of waiting for the whole settle animation to finish.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!isProgrammaticPageScroll && page != board.currentPageIndex) {
                touch()
                vm.switchPage(page)
            }
        }
    }

    // ViewModel -> pager: a tab tap or the idle timer scrolls the pager to match.
    LaunchedEffect(board.currentPageIndex) {
        if (pagerState.currentPage != board.currentPageIndex) {
            isProgrammaticPageScroll = true
            try {
                pagerState.animateScrollToPage(board.currentPageIndex)
            } finally {
                isProgrammaticPageScroll = false
            }
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
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                // The Edit mode row's fillMaxWidth() doesn't report an intrinsic
                                // width, so without a floor the menu can size itself too narrow
                                // and crowd the switch against the label text.
                                modifier = Modifier.widthIn(min = 260.dp)
                            ) {
                                // Mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text("Edit mode")
                                    }
                                    Switch(
                                        checked = editMode,
                                        onCheckedChange = { editMode = it }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Speak...") },
                                    leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showSpeakDialog = true
                                    }
                                )
                                HorizontalDivider()
                                // Settings — see SettingsDialog
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showSettingsDialog = true
                                    }
                                )
                                HorizontalDivider()
                                MenuSectionHeader("Page")
                                DropdownMenuItem(
                                    text = { Text("Page options (${board.currentPage.name})") },
                                    leadingIcon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        pageOptionsIndex = board.currentPageIndex
                                    }
                                )
                                HorizontalDivider()
                                // Presets — lightweight, same-device version history (see PresetRepository)
                                MenuSectionHeader("Presets")
                                DropdownMenuItem(
                                    text = { Text("Save as preset") },
                                    leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showSavePresetDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Load preset") },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        vm.refreshPresets()
                                        showPresetPickerDialog = true
                                    }
                                )
                                HorizontalDivider()
                                // Backup — full, portable, self-contained (structure + audio)
                                MenuSectionHeader("Backup")
                                DropdownMenuItem(
                                    text = { Text("Export backup") },
                                    leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        exportLauncher.launch("soundboard-backup.zip")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import backup") },
                                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        importLauncher.launch(arrayOf("application/zip"))
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            APP_VERSION,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        context.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASE_URL))
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
                PrimaryScrollableTabRow(selectedTabIndex = board.currentPageIndex) {
                    val tabHaptics = LocalHapticFeedback.current
                    board.pages.forEachIndexed { index, page ->
                        Tab(
                            selected = index == board.currentPageIndex,
                            onClick = {
                                touch()
                                vm.switchPage(index)
                            },
                            // Long-press detection must run on the Initial (outside-in) pointer
                            // pass and win the race against Tab's own click before it can consume
                            // the eventual up event on the Main pass — see the note above. Attached
                            // to Tab's own modifier (rather than an outer Box) so Tab stays the
                            // direct child PrimaryScrollableTabRow measures for its selection
                            // indicator — wrapping it in a separate Box threw off the indicator's
                            // centering, most visible on short page names.
                            modifier = Modifier.pointerInput(page.id, board.longPressDurationMillis) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                    // Races the long-press timeout against the pointer either
                                    // lifting (a tap, left for Tab's own Main-pass click) or
                                    // moving past touch-slop (a drag — most commonly scrolling
                                    // the tab row itself, #34). The while(true) loop below only
                                    // exits via an explicit return, so withTimeoutOrNull returns
                                    // null exactly when the timeout genuinely wins that race.
                                    val longPressed = withTimeoutOrNull(board.longPressDurationMillis.toLong()) {
                                        while (true) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                            val movedPastSlop = (change.position - down.position)
                                                .getDistance() > viewConfiguration.touchSlop
                                            if (change.changedToUpIgnoreConsumed() || movedPastSlop) {
                                                return@withTimeoutOrNull
                                            }
                                        }
                                    } == null
                                    if (longPressed) {
                                        if (board.hapticFeedbackEnabled) {
                                            tabHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        pageOptionsIndex = index
                                        // Swallow the eventual release so Tab's own click,
                                        // which runs on the later Main pass, never sees it.
                                        waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
                                    }
                                }
                            },
                            text = {
                                if (page.isHome) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Home,
                                            contentDescription = "Home page",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(page.name, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(page.name)
                                }
                            },
                            selectedContentColor = page.color?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.primary
                        )
                    }
                    Tab(
                        selected = false,
                        onClick = { showAddPageDialog = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Add page") }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            val homePage = board.homePage
            if (board.stickyHomeRowEnabled && homePage != null && board.currentPageIndex != board.homePageIndex) {
                PinnedRow(
                    tiles = homePage.tiles.take(homePage.columns),
                    editMode = editMode,
                    aspectRatio = board.currentPage.tileAspectRatio,
                    hapticFeedbackEnabled = board.hapticFeedbackEnabled,
                    onTap = { tile ->
                        touch()
                        if (tile.isEmpty || editMode) {
                            editingTarget = EditTarget.HomeRowTile(tile.id)
                        } else {
                            vm.play(tile)
                        }
                    },
                    onPreviewSound = { tile ->
                        touch()
                        vm.play(tile)
                    }
                )
            }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isDragActive,
                // Keeps one neighboring page's grid composed on each side at all
                // times, instead of the default 0 (which only builds a page's
                // content the moment a swipe first reveals it). That first-time
                // composition of a whole grid of tiles happening mid-gesture is
                // the main source of dropped frames on slower devices; this moves
                // the cost to idle time so the swipe itself just slides already-
                // built content.
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = board.pages.getOrNull(pageIndex)
                if (page != null) {
                    PageGrid(
                        page = page,
                        editMode = editMode,
                        isActive = pageIndex == board.currentPageIndex,
                        hapticFeedbackEnabled = board.hapticFeedbackEnabled,
                        onTap = { tile ->
                            touch()
                            if (tile.isEmpty || editMode) {
                                editingTarget = EditTarget.PageTile(tile.id)
                            } else {
                                vm.play(tile)
                            }
                        },
                        onPreviewSound = { tile ->
                            touch()
                            vm.play(tile)
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
                    onSpeakLabelChange = { vm.setSpeakLabel(editing.id, it) },
                    onPlay = vm::play,
                    onStartRecording = vm::startRecording,
                    onStopRecording = { vm.stopRecording(editing.id) },
                    onDismiss = {
                        vm.cancelRecording()
                        editingTarget = null
                    }
                )
            }
        }
        is EditTarget.HomeRowTile -> {
            val editing = board.homePage?.tiles?.firstOrNull { it.id == target.id }
            if (editing != null) {
                EditTileDialog(
                    tile = editing,
                    isRecording = isRecording,
                    onLabelChange = { vm.setHomeRowLabel(editing.id, it) },
                    onSoundPicked = { vm.assignHomeRowSound(editing.id, it) },
                    onClear = { vm.clearHomeRowTile(editing.id) },
                    onVolumeChange = { vm.setHomeRowVolume(editing.id, it) },
                    onColorChange = { vm.setHomeRowColor(editing.id, it) },
                    onSpeakLabelChange = { vm.setHomeRowSpeakLabel(editing.id, it) },
                    onPlay = vm::play,
                    onStartRecording = vm::startRecording,
                    onStopRecording = { vm.stopHomeRowRecording(editing.id) },
                    onDismiss = {
                        vm.cancelRecording()
                        editingTarget = null
                    }
                )
            }
        }
        null -> Unit
    }

    if (showSpeakDialog) {
        SpeakDialog(
            onSpeak = vm::speakAdHoc,
            onDismiss = { showSpeakDialog = false }
        )
    }

    gridDialogIndex?.let { index ->
        board.pages.getOrNull(index)?.let { page ->
            GridSizeDialog(
                rows = page.rows,
                columns = page.columns,
                aspectRatio = page.tileAspectRatio,
                onConfirm = { r, c, ratio ->
                    vm.resize(index, r, c)
                    vm.setTileAspectRatio(index, ratio)
                    gridDialogIndex = null
                },
                onDismiss = { gridDialogIndex = null }
            )
        }
    }

    pageColorDialogIndex?.let { index ->
        board.pages.getOrNull(index)?.let { page ->
            PageColorDialog(
                current = page.color,
                onSelect = { vm.setPageColor(index, it) },
                onDismiss = { pageColorDialogIndex = null }
            )
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            openOnHomePage = board.openOnHomePage,
            onOpenOnHomePageChange = vm::setOpenOnHomePage,
            idleTimeoutMinutes = board.idleTimeoutMinutes,
            onIdleTimeoutMinutesChange = vm::setIdleTimeoutMinutes,
            longPressDurationMillis = board.longPressDurationMillis,
            onLongPressDurationMillisChange = vm::setLongPressDurationMillis,
            themeMode = board.themeMode,
            onThemeModeChange = vm::setThemeMode,
            keepScreenAwake = board.keepScreenAwake,
            onKeepScreenAwakeChange = vm::setKeepScreenAwake,
            hapticFeedbackEnabled = board.hapticFeedbackEnabled,
            onHapticFeedbackEnabledChange = vm::setHapticFeedbackEnabled,
            stickyHomeRowEnabled = board.stickyHomeRowEnabled,
            hasHomePage = board.homePageIndex != null,
            onStickyHomeRowEnabledChange = vm::setStickyHomeRowEnabled,
            defaultPageRows = board.defaultPageRows,
            onDefaultPageRowsChange = vm::setDefaultPageRows,
            defaultPageColumns = board.defaultPageColumns,
            onDefaultPageColumnsChange = vm::setDefaultPageColumns,
            onDismiss = { showSettingsDialog = false }
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
            factoryPresets = vm.factoryPresets(context),
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
                isHome = page.isHome,
                gridSize = "${page.rows}x${page.columns}",
                canMoveLeft = index > 0,
                canMoveRight = index < board.pages.lastIndex,
                canDelete = board.pages.size > 1,
                onRename = {
                    pageOptionsIndex = null
                    renamePageIndex = index
                },
                onSetHome = {
                    pageOptionsIndex = null
                    vm.setHomePage(index)
                },
                onGridSize = {
                    pageOptionsIndex = null
                    gridDialogIndex = index
                },
                onPageColor = {
                    pageOptionsIndex = null
                    pageColorDialogIndex = index
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
                            "with sound or speech set up. Deleting it can't be undone."
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

/** The fixed row shown above every non-home page, mirroring the home page's own first row. */
@Composable
private fun PinnedRow(
    tiles: List<Tile>,
    editMode: Boolean,
    aspectRatio: Float,
    hapticFeedbackEnabled: Boolean,
    onTap: (Tile) -> Unit,
    onPreviewSound: (Tile) -> Unit
) {
    val haptics = LocalHapticFeedback.current
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
                onTap = { onTap(tile) },
                // Long-press previews what a pad will say without "using" it for real,
                // same as PageGrid's hold-without-moving (#4) — not offered in edit mode,
                // where a tap already opens the editor's own Play/Preview button.
                onLongClick = if (!editMode && !tile.isEmpty) {
                    {
                        if (hapticFeedbackEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onPreviewSound(tile)
                    }
                } else null
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
    hapticFeedbackEnabled: Boolean,
    onTap: (Tile) -> Unit,
    onPreviewSound: (Tile) -> Unit,
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
                    .pointerInput(tile.id, columns, isActive, hapticFeedbackEnabled) {
                        if (!isActive) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                if (hapticFeedbackEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                draggedIndex = index
                                dragOffset = Offset.Zero
                                onDragActiveChanged(true)
                            },
                            onDragEnd = {
                                // A hold that never moved the tile to a different index wasn't a
                                // reorder at all — treat it as a request to preview what the tile
                                // says/plays without "using" it for real (#4), same as PinnedRow's
                                // dedicated onLongClick (which has no competing drag gesture to share it with).
                                if (!editMode && draggedIndex == index && !tile.isEmpty) {
                                    onPreviewSound(tile)
                                }
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
    onTap: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val filled = !tile.isEmpty
    // A preset can ship a tile with a label but no recording yet (see
    // BoardRepository.sanitizeMissingSounds) — flag that distinctly from a
    // plain blank tile so it reads as "still needs recording," not "empty."
    val needsRecording = tile.isEmpty && tile.label.isNotBlank()
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
            .combinedClickable(onClick = onTap, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (filled) 2.dp else 0.dp),
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit mode",
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                )
            }
            if (needsRecording) {
                Icon(
                    Icons.Filled.MicOff,
                    contentDescription = "Needs recording",
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(16.dp)
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
private fun textColorFor(background: Color): Color {
    val bg = background.luminance()
    // WCAG contrast ratio: (lighter + 0.05) / (darker + 0.05). Comparing against
    // black (luminance 0) and white (luminance 1) picks whichever contrasts more;
    // the crossover is at bg ≈ 0.179, not the naive halfway point of 0.5.
    val contrastWithBlack = (bg + 0.05f) / 0.05f
    val contrastWithWhite = 1.05f / (bg + 0.05f)
    return if (contrastWithBlack >= contrastWithWhite) Color.Black else Color.White
}

/** Free-text speech for a one-off phrase no pad covers — bypasses [Tile] entirely. */
@Composable
private fun SpeakDialog(onSpeak: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speak") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type what you want to say...") },
                modifier = Modifier.fillMaxWidth()
            )
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

@Composable
private fun EditTileDialog(
    tile: Tile,
    isRecording: Boolean,
    onLabelChange: (String) -> Unit,
    onSoundPicked: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onColorChange: (Int?) -> Unit,
    onSpeakLabelChange: (Boolean) -> Unit,
    onPlay: (Tile) -> Unit,
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
                    Text(if (tile.fileName == null) "Choose sound" else "Replace sound")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speak the label instead", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Used when there's no sound file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = tile.speakLabel, onCheckedChange = onSpeakLabelChange)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onPlay(tile.copy(label = label)) },
                        enabled = !tile.isEmpty && !isRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = PlayGreen,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (tile.fileName == null) "Preview" else "Play clip")
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
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
    // Outer box is a full 48dp touch target (Material's minimum); the visible
    // circle stays 32dp so a full row of presets still fits without crowding.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
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
        )
    }
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
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = !wide,
                            onClick = { wide = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Square") }
                        SegmentedButton(
                            selected = wide,
                            onClick = { wide = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Wide") }
                    }
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

/** App-level preferences that aren't page content — see [com.example.soundboard.data.SettingsRepository]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    openOnHomePage: Boolean,
    onOpenOnHomePageChange: (Boolean) -> Unit,
    idleTimeoutMinutes: Int,
    onIdleTimeoutMinutesChange: (Int) -> Unit,
    longPressDurationMillis: Int,
    onLongPressDurationMillisChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    keepScreenAwake: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    hapticFeedbackEnabled: Boolean,
    onHapticFeedbackEnabledChange: (Boolean) -> Unit,
    stickyHomeRowEnabled: Boolean,
    hasHomePage: Boolean,
    onStickyHomeRowEnabledChange: (Boolean) -> Unit,
    defaultPageRows: Int,
    onDefaultPageRowsChange: (Int) -> Unit,
    defaultPageColumns: Int,
    onDefaultPageColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open on home page")
                    Switch(checked = openOnHomePage, onCheckedChange = onOpenOnHomePageChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                var idleTimeoutExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = idleTimeoutExpanded,
                    onExpandedChange = { idleTimeoutExpanded = it }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        value = idleTimeoutLabel(idleTimeoutMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Auto-return to home page after") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = idleTimeoutExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = idleTimeoutExpanded,
                        onDismissRequest = { idleTimeoutExpanded = false }
                    ) {
                        IDLE_TIMEOUT_OPTIONS_MINUTES.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(idleTimeoutLabel(minutes)) },
                                onClick = {
                                    onIdleTimeoutMinutesChange(minutes)
                                    idleTimeoutExpanded = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                var longPressExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = longPressExpanded,
                    onExpandedChange = { longPressExpanded = it }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        value = longPressDurationLabel(longPressDurationMillis),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Long-press duration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = longPressExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = longPressExpanded,
                        onDismissRequest = { longPressExpanded = false }
                    ) {
                        LONG_PRESS_DURATION_OPTIONS_MILLIS.forEach { millis ->
                            DropdownMenuItem(
                                text = { Text(longPressDurationLabel(millis)) },
                                onClick = {
                                    onLongPressDurationMillisChange(millis)
                                    longPressExpanded = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == themeMode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size)
                        ) {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep screen awake")
                    Switch(checked = keepScreenAwake, onCheckedChange = onKeepScreenAwakeChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic feedback")
                    Switch(checked = hapticFeedbackEnabled, onCheckedChange = onHapticFeedbackEnabledChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sticky home row", modifier = Modifier.weight(1f))
                    Switch(
                        checked = stickyHomeRowEnabled,
                        onCheckedChange = onStickyHomeRowEnabledChange,
                        enabled = hasHomePage
                    )
                }
                Text(
                    if (hasHomePage) {
                        "Shows your home page's first row fixed at the top of every other " +
                            "page. Nothing is deleted — other pages' content just moves down a row."
                    } else {
                        "Set a home page (Page options) to use this."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Default grid size for new pages", style = MaterialTheme.typography.bodyMedium)
                Stepper("Rows", defaultPageRows, onDefaultPageRowsChange)
                Stepper("Columns", defaultPageColumns, onDefaultPageColumnsChange)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun PageOptionsDialog(
    pageName: String,
    isHome: Boolean,
    gridSize: String,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onSetHome: () -> Unit,
    onGridSize: () -> Unit,
    onPageColor: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (isHome) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Home page",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(pageName)
                }
            } else {
                Text(pageName)
            }
        },
        text = {
            Column {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    onClick = onRename
                )
                DropdownMenuItem(
                    text = { Text("Set as home page") },
                    leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    enabled = !isHome,
                    onClick = onSetHome
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Grid size ($gridSize)") },
                    leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                    onClick = onGridSize
                )
                DropdownMenuItem(
                    text = { Text("Page color") },
                    leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    onClick = onPageColor
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onMoveLeft,
                        enabled = canMoveLeft,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Move left")
                    }
                    TextButton(
                        onClick = onMoveRight,
                        enabled = canMoveRight,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Move right")
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (canDelete) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete page", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = onDelete
                    )
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
private fun MenuSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > 1) onChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { if (value < 50) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}
