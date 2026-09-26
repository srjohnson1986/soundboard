package com.example.soundboard.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundboard.BoardViewModel
import com.example.soundboard.BoardRef
import com.example.soundboard.data.UriPickedFile
import com.example.soundboard.data.UriSaveTarget
import com.example.soundboard.model.Board
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.TileBorder
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

/**
 * The one dialog [BoardScreen] has open, if any. Every dialog is modal, so at most one is
 * ever showing — a single value here replaces a separate show-flag or page index per dialog.
 * The page-scoped ones carry the index of the page they were opened for, which isn't
 * necessarily the page on screen (a long-pressed tab can be any page).
 */
internal sealed interface BoardDialog {
    /**
     * The tile editor for [tileId]. [fromStickyRow] marks a home-row tile tapped in the
     * sticky row on another page; it's edited the same way (by id, on whichever page holds
     * it) and only changes whether switching pages closes the dialog.
     */
    data class EditTile(val tileId: String, val fromStickyRow: Boolean) : BoardDialog
    data object Speak : BoardDialog
    data object Settings : BoardDialog
    /** One group of Settings, opened from the Settings list; Back returns to that list. */
    data class SettingsGroupDetail(val group: SettingsGroup) : BoardDialog
    data object RenameBoard : BoardDialog
    data object SaveBoardAs : BoardDialog
    data object OpenBoard : BoardDialog
    data class ConfirmOpenBoard(val ref: BoardRef, val label: String) : BoardDialog
    data object StrayCleanup : BoardDialog
    data object AddPage : BoardDialog
    data class PageOptions(val pageIndex: Int) : BoardDialog
    data class RenamePage(val pageIndex: Int) : BoardDialog
    data class GridSize(val pageIndex: Int) : BoardDialog
    data class PageAppearance(val pageIndex: Int) : BoardDialog
    data class ConfirmDeletePage(val pageIndex: Int) : BoardDialog
}

@Composable
fun BoardScreen(
    vm: BoardViewModel = viewModel(
        factory = BoardViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val board by vm.board.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val isRecording by vm.isRecording.collectAsStateWithLifecycle()
    val savedBoards by vm.savedBoards.collectAsStateWithLifecycle()
    val strayClips by vm.strayClips.collectAsStateWithLifecycle()
    val recentBoards by vm.recentBoards.collectAsStateWithLifecycle()
    val performanceModeEnabled by vm.performanceModeEnabled.collectAsStateWithLifecycle()
    val showMode by vm.showMode.collectAsStateWithLifecycle()
    val shownText by vm.shownText.collectAsStateWithLifecycle()
    var openDialog by remember { mutableStateOf<BoardDialog?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var lastInteractionAt by remember { mutableLongStateOf(0L) }
    var isDragActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    /** Restarts the auto-return idle timer (see the LaunchedEffect keyed on [lastInteractionAt]). */
    fun recordInteraction() {
        lastInteractionAt = System.currentTimeMillis()
    }

    /** Opens [dialog], first refreshing whatever list it shows from disk. */
    fun showDialog(dialog: BoardDialog) {
        when (dialog) {
            BoardDialog.OpenBoard -> vm.refreshSavedBoards()
            BoardDialog.StrayCleanup -> vm.refreshStrayClips()
            else -> Unit
        }
        openDialog = dialog
    }

    fun closeDialog() {
        openDialog = null
    }

    // A page with any sound assigned needs an explicit confirm; an all-empty
    // page is cheap to recreate, so deleting it outright isn't worth a dialog.
    fun requestDeletePage(index: Int) {
        val page = board.pages.getOrNull(index) ?: return
        if (page.hasAnySound) {
            showDialog(BoardDialog.ConfirmDeletePage(index))
        } else {
            vm.deletePage(index)
            closeDialog()
        }
    }

    // Opening another board over one with any sound needs an explicit confirm — same
    // reasoning as page deletion, just board-wide instead of one page.
    fun requestOpenBoard(ref: BoardRef, label: String) {
        if (board.hasAnySound) {
            showDialog(BoardDialog.ConfirmOpenBoard(ref, label))
        } else {
            vm.openBoard(ref)
            closeDialog()
        }
    }

    /**
     * A tile tap: plays it (and shows its words, in Show mode), or opens its editor when
     * there's nothing to play or edit mode is on.
     */
    fun onTileTap(tileId: String, isPlayable: Boolean, fromStickyRow: Boolean, play: () -> Unit) {
        recordInteraction()
        if (!isPlayable || editMode) {
            showDialog(BoardDialog.EditTile(tileId, fromStickyRow))
        } else {
            play()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // A page-tile dialog left open while switching pages would otherwise keep editing
    // a tile on the page the user just left, no longer on screen. A home-row-tile
    // dialog is unaffected — its tile stays visible (in the sticky row or on the home
    // page itself) whichever page is current. Auto-return (below) can trigger this
    // while a recording is in progress, so cancel it too.
    LaunchedEffect(board.currentPageIndex) {
        val editing = openDialog as? BoardDialog.EditTile
        if (editing != null && !editing.fromStickyRow) {
            closeDialog()
            vm.cancelRecording()
        }
    }

    // Auto-return to the home page after a few idle minutes (configurable in
    // SettingsDialog; 0 disables it). Restarts on every interaction
    // (lastInteractionAt changing cancels the previous delay). Held off while Show mode's
    // text is up, so closing it always lands back on the page it was opened from.
    LaunchedEffect(lastInteractionAt, board.homePageIndex, board.idleTimeoutMinutes, shownText != null) {
        if (shownText != null) return@LaunchedEffect
        val home = board.homePageIndex ?: return@LaunchedEffect
        if (board.idleTimeoutMinutes <= 0) return@LaunchedEffect
        delay(board.idleTimeoutMinutes.minutes)
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
                recordInteraction()
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
    ) { uri -> uri?.let { vm.exportBoard(UriSaveTarget(context, it)) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importBoard(UriPickedFile(context, it)) } }

    val strayExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { vm.exportAndDeleteStrayClips(UriSaveTarget(context, it)) } }

    val backgroundImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.setBackgroundImage(UriPickedFile(context, it)) } }

    // Performance mode drops translucency and borders board-wide, whatever the settings say.
    val globalTileOpacity = if (performanceModeEnabled) 1f else board.tileOpacity
    val globalTileBorder = if (performanceModeEnabled) TileBorder() else board.tileBorder

    Box(modifier = Modifier.fillMaxSize()) {
        val hasCustomBackground = BoardBackground(board, vm::backgroundImage)
        Scaffold(
            containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BoardTopBar(
                    board = board,
                    recentBoards = recentBoards,
                    editMode = editMode,
                    onEditModeChange = { editMode = it },
                    showModeEnabled = showMode.enabled,
                    onShowModeChange = vm::setShowModeEnabled,
                    onSelectPage = { index ->
                        recordInteraction()
                        vm.switchPage(index)
                    },
                    onOpenDialog = ::showDialog,
                    onShowRecentBoards = {
                        vm.refreshRecentBoards()
                        vm.refreshSavedBoards()
                    },
                    onOpenBoard = ::requestOpenBoard,
                    onExportBackup = { exportLauncher.launch("soundboard-backup.zip") },
                    onImportBackup = { importLauncher.launch(arrayOf("application/zip")) }
                )
            }
        ) { insets ->
            // Measured once here, above the sticky row, so every page's landscape column
            // math shares the same height budget regardless of whether the sticky row is
            // eating into that particular page's own pager space — see PageGrid's
            // referenceHeightPx doc comment.
            var contentAreaHeightPx by remember { mutableIntStateOf(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .onSizeChanged { contentAreaHeightPx = it.height }
            ) {
                val homePage = board.homePage
                if (board.stickyHomeRowEnabled && homePage != null && board.currentPageIndex != board.homePageIndex) {
                    PinnedRow(
                        homePage = homePage,
                        boardRowHeight = board.rowHeight,
                        labelStyle = board.labelStyle,
                        editMode = editMode,
                        globalTileOpacity = globalTileOpacity,
                        globalTileBorder = globalTileBorder,
                        hapticFeedbackEnabled = board.hapticFeedbackEnabled,
                        performanceModeEnabled = performanceModeEnabled,
                        speakUnrecordedTilesEnabled = board.speakUnrecordedTilesEnabled,
                        hideBlankTilesEnabled = board.hideBlankTilesEnabled,
                        onTap = { tile ->
                            onTileTap(
                                tile.id,
                                tile.isPlayable(board.speakUnrecordedTilesEnabled),
                                fromStickyRow = true
                            ) { vm.activate(tile) }
                        },
                        onPreviewSound = { tile ->
                            recordInteraction()
                            vm.activate(tile)
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
                            performanceModeEnabled = performanceModeEnabled,
                            speakUnrecordedTilesEnabled = board.speakUnrecordedTilesEnabled,
                            hideBlankTilesEnabled = board.hideBlankTilesEnabled,
                            globalTileOpacity = globalTileOpacity,
                            globalTileBorder = globalTileBorder,
                            landscapeLayout = board.landscapeLayout,
                            boardRowHeight = board.rowHeight,
                            labelStyle = board.labelStyle,
                            referenceHeightPx = contentAreaHeightPx,
                            pinFirstRow = page.isHome && board.stickyHomeRowEnabled,
                            onTap = { tile ->
                                onTileTap(
                                    tile.id,
                                    tile.isPlayable(board.speakUnrecordedTilesEnabled),
                                    fromStickyRow = false
                                ) { vm.activate(tile) }
                            },
                            onPreviewSound = { tile ->
                                recordInteraction()
                                vm.activate(tile)
                            },
                            onPreviewMove = vm::previewMove,
                            onCommitOrder = vm::commitOrder,
                            onDragActiveChanged = { isDragActive = it }
                        )
                    }
                }
            }
        }
        shownText?.let { text ->
            ShowTextOverlay(
                text = text,
                showMode = showMode,
                labelStyle = board.labelStyle,
                onDismiss = {
                    recordInteraction()
                    vm.dismissShownText()
                }
            )
        }
    }

    // A page-scoped dialog whose page has since gone (deleted, or another board opened over
    // it) simply doesn't show, same as a tile editor whose tile no longer exists.
    fun pageAt(index: Int) = board.pages.getOrNull(index)

    when (val dialog = openDialog) {
        null -> Unit

        is BoardDialog.EditTile -> board.findTile(dialog.tileId)?.let { editing ->
            EditTileDialog(
                tile = editing,
                isRecording = isRecording,
                speakUnrecordedTilesEnabled = board.speakUnrecordedTilesEnabled,
                onLabelChange = { vm.setLabel(editing.id, it) },
                onTtsScriptChange = { vm.setTtsScript(editing.id, it) },
                onSoundPicked = { vm.assignSound(editing.id, UriPickedFile(context, it)) },
                onClear = { vm.clearTile(editing.id) },
                onRemoveSound = { vm.removeSound(editing.id) },
                onVolumeChange = { vm.setVolume(editing.id, it) },
                onColorChange = { vm.setColor(editing.id, it) },
                onOpacityChange = { vm.setOpacity(editing.id, it) },
                onBorderChange = { vm.setBorder(editing.id, it) },
                onSpeakWhenNoSoundChange = { vm.setSpeakWhenNoSound(editing.id, it) },
                onPlay = vm::play,
                onStartRecording = vm::startRecording,
                onStopRecording = { vm.stopRecording(editing.id) },
                onDismiss = {
                    vm.cancelRecording()
                    closeDialog()
                }
            )
        }

        BoardDialog.Speak -> SpeakDialog(onSpeak = vm::speakAdHoc, onDismiss = ::closeDialog)

        BoardDialog.Settings -> SettingsDialog(
            board = board,
            performanceModeEnabled = performanceModeEnabled,
            showMode = showMode,
            onOpenGroup = { showDialog(BoardDialog.SettingsGroupDetail(it)) },
            onDismiss = ::closeDialog
        )

        is BoardDialog.SettingsGroupDetail -> SettingsGroupDialog(
            group = dialog.group,
            board = board,
            performanceModeEnabled = performanceModeEnabled,
            showMode = showMode,
            actions = vm,
            onPickBackgroundImage = {
                backgroundImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onBack = { showDialog(BoardDialog.Settings) },
            onDismiss = ::closeDialog
        )

        BoardDialog.RenameBoard -> TextInputDialog(
            title = "Rename board",
            label = "Board name",
            initial = board.name,
            onConfirm = {
                vm.renameBoard(it.trim())
                closeDialog()
            },
            onDismiss = ::closeDialog
        )

        BoardDialog.SaveBoardAs -> TextInputDialog(
            title = "Save board as",
            label = "Board name",
            initial = board.name,
            onConfirm = {
                vm.saveBoardAs(it.trim())
                closeDialog()
            },
            onDismiss = ::closeDialog
        )

        BoardDialog.OpenBoard -> OpenBoardDialog(
            builtInBoards = vm.builtInBoards(),
            savedBoards = savedBoards,
            onSelect = ::requestOpenBoard,
            onDismiss = ::closeDialog
        )

        is BoardDialog.ConfirmOpenBoard -> ConfirmOpenBoardDialog(
            label = dialog.label,
            onConfirm = {
                vm.openBoard(dialog.ref)
                closeDialog()
            },
            onDismiss = ::closeDialog
        )

        BoardDialog.StrayCleanup -> StrayCleanupDialog(
            clips = strayClips,
            onExportAndDelete = { strayExportLauncher.launch("unused-clips.zip") },
            onDelete = vm::deleteStrayClips,
            onDismiss = ::closeDialog
        )

        BoardDialog.AddPage -> TextInputDialog(
            title = "Add page",
            label = "Page name",
            initial = "Page ${board.pages.size + 1}",
            onConfirm = {
                vm.addPage(it.trim())
                closeDialog()
            },
            onDismiss = ::closeDialog
        )

        is BoardDialog.PageOptions -> pageAt(dialog.pageIndex)?.let { page ->
            val index = dialog.pageIndex
            PageOptionsDialog(
                pageName = page.name,
                isHome = page.isHome,
                gridSize = if (board.landscapeLayout == LandscapeLayout.PAGE_GRID) {
                    "${page.rows}x${page.columns}, landscape ${page.configuredLandscapeRows}x${page.effectiveLandscapeColumns}"
                } else {
                    "${page.rows}x${page.columns}"
                },
                canMoveLeft = index > 0,
                canMoveRight = index < board.pages.lastIndex,
                canDelete = board.pages.size > 1,
                onRename = { showDialog(BoardDialog.RenamePage(index)) },
                onSetHome = {
                    closeDialog()
                    vm.setHomePage(index)
                },
                onGridSize = { showDialog(BoardDialog.GridSize(index)) },
                onPageAppearance = { showDialog(BoardDialog.PageAppearance(index)) },
                onMoveLeft = {
                    vm.movePage(index, index - 1)
                    closeDialog()
                },
                onMoveRight = {
                    vm.movePage(index, index + 1)
                    closeDialog()
                },
                onDelete = { requestDeletePage(index) },
                onDismiss = ::closeDialog
            )
        }

        is BoardDialog.RenamePage -> pageAt(dialog.pageIndex)?.let { page ->
            TextInputDialog(
                title = "Rename page",
                label = "Page name",
                initial = page.name,
                onConfirm = {
                    vm.renamePage(dialog.pageIndex, it.trim())
                    closeDialog()
                },
                onDismiss = ::closeDialog
            )
        }

        is BoardDialog.GridSize -> pageAt(dialog.pageIndex)?.let { page ->
            val index = dialog.pageIndex
            GridSizeDialog(
                rows = page.rows,
                columns = page.columns,
                aspectRatio = page.tileAspectRatio,
                landscapeRows = page.landscapeRows,
                landscapeColumns = page.landscapeColumns,
                pageGridActive = board.landscapeLayout == LandscapeLayout.PAGE_GRID,
                rowHeight = page.rowHeight,
                boardRowHeight = board.rowHeight,
                onConfirm = { rows, columns, ratio, landscapeRows, landscapeColumns, rowHeight ->
                    vm.resize(index, rows, columns)
                    vm.setTileAspectRatio(index, ratio)
                    vm.setLandscapeGrid(index, landscapeRows, landscapeColumns)
                    vm.setPageRowHeight(index, rowHeight)
                    closeDialog()
                },
                onDismiss = ::closeDialog
            )
        }

        is BoardDialog.PageAppearance -> pageAt(dialog.pageIndex)?.let { page ->
            PageAppearanceDialog(
                page = page,
                onColorChange = { vm.setPageColor(dialog.pageIndex, it) },
                onOpacityChange = { vm.setPageOpacity(dialog.pageIndex, it) },
                onBorderChange = { vm.setPageBorder(dialog.pageIndex, it) },
                onDismiss = ::closeDialog
            )
        }

        is BoardDialog.ConfirmDeletePage -> pageAt(dialog.pageIndex)?.let { page ->
            ConfirmDeletePageDialog(
                pageName = page.name,
                soundCount = page.tiles.count { it.hasSound },
                onConfirm = {
                    vm.deletePage(dialog.pageIndex)
                    closeDialog()
                },
                onDismiss = ::closeDialog
            )
        }
    }
}

/**
 * Draws the board's custom background — its image, else its solid color — behind
 * everything, and returns whether there was one (so the Scaffold can go transparent).
 */
@Composable
private fun BoardBackground(board: Board, readImage: suspend (String) -> ByteArray?): Boolean {
    // Decoded once per file name change, not on every recomposition — a board with a
    // background image reloads this exactly once per app launch (or right after picking
    // a new one), not on every tile tap — and off the main thread.
    val backgroundImageFileName = board.backgroundImageFileName
    val backgroundBitmap: ImageBitmap? = produceState<ImageBitmap?>(null, backgroundImageFileName) {
        value = backgroundImageFileName?.let { name ->
            withContext(Dispatchers.Default) {
                runCatching {
                    readImage(name)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }.value
    val backgroundColorArgb = board.backgroundColorArgb
    if (backgroundBitmap != null) {
        Image(
            bitmap = backgroundBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else if (backgroundColorArgb != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(backgroundColorArgb))
        )
    }
    return backgroundBitmap != null || backgroundColorArgb != null
}
