package com.example.soundboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.soundboard.BuildConfig
import com.example.soundboard.BoardRef
import com.example.soundboard.RecentBoardItem
import com.example.soundboard.model.Board
import kotlinx.coroutines.withTimeoutOrNull

/** Shown at the bottom of the menu; comes from `versionName` in app/build.gradle.kts, so there's one place to bump per release. */
private const val APP_VERSION = "v${BuildConfig.VERSION_NAME}"

private const val RELEASES_BASE_URL = "https://github.com/srjohnson1986/soundboard/releases"

/** This version's own release notes, rather than the bare releases list. */
private const val RELEASE_URL = "$RELEASES_BASE_URL/tag/$APP_VERSION"

/**
 * The app title, page tabs and hamburger menu. Every menu entry that opens a dialog goes
 * through [onOpenDialog]; the rest are the few actions that don't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardTopBar(
    board: Board,
    recentBoards: List<RecentBoardItem>,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    showModeEnabled: Boolean,
    onShowModeChange: (Boolean) -> Unit,
    onSelectPage: (Int) -> Unit,
    onOpenDialog: (BoardDialog) -> Unit,
    onShowRecentBoards: () -> Unit,
    onOpenBoard: (BoardRef, String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val menu: @Composable () -> Unit = {
        HamburgerMenu(
            board = board,
            recentBoards = recentBoards,
            editMode = editMode,
            onEditModeChange = onEditModeChange,
            showModeEnabled = showModeEnabled,
            onShowModeChange = onShowModeChange,
            onOpenDialog = onOpenDialog,
            onShowRecentBoards = onShowRecentBoards,
            onOpenBoard = onOpenBoard,
            onExportBackup = onExportBackup,
            onImportBackup = onImportBackup
        )
    }

    // Landscape has little vertical room to spare, so the title, page tabs, and
    // hamburger menu share one row instead of stacking title-bar-then-tab-row —
    // the tabs already scroll horizontally (PrimaryScrollableTabRow) when they
    // don't fit, so there's no loss of access, just less height spent on chrome.
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Applied before windowInsetsPadding so the color extends the full
                    // width AND behind the status bar, matching how TopAppBar's own
                    // background reaches edge-to-edge in portrait — otherwise the
                    // board's background (theme color or a custom image) shows through
                    // behind the title/tabs/menu here, and text contrast isn't guaranteed.
                    .background(MaterialTheme.colorScheme.surface)
                    // TopAppBar gets this for free; a plain Row doesn't, so without it
                    // the header renders under the status bar and steals its touch area.
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Soundboard", style = MaterialTheme.typography.titleMedium)
                PageTabs(board, onSelectPage, onOpenDialog, modifier = Modifier.weight(1f))
                menu()
            }
        }
    } else {
        Column {
            TopAppBar(
                title = { Text("Soundboard", style = MaterialTheme.typography.titleLarge) },
                actions = { menu() }
            )
            PageTabs(board, onSelectPage, onOpenDialog, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HamburgerMenu(
    board: Board,
    recentBoards: List<RecentBoardItem>,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    showModeEnabled: Boolean,
    onShowModeChange: (Boolean) -> Unit,
    onOpenDialog: (BoardDialog) -> Unit,
    onShowRecentBoards: () -> Unit,
    onOpenBoard: (BoardRef, String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showRecentBoardsMenu by remember { mutableStateOf(false) }

    /** Closes the menu, then runs [action] — every entry except Recent boards and the mode switches. */
    fun menuAction(action: () -> Unit): () -> Unit = {
        showMenu = false
        action()
    }

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
            MenuSectionHeader("Boards")
            DropdownMenuItem(
                text = { Text("Rename board (${board.name})") },
                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.RenameBoard) }
            )
            Box {
                DropdownMenuItem(
                    text = { Text("Recent boards") },
                    leadingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    onClick = {
                        onShowRecentBoards()
                        showRecentBoardsMenu = true
                    }
                )
                DropdownMenu(
                    expanded = showRecentBoardsMenu,
                    onDismissRequest = { showRecentBoardsMenu = false },
                    modifier = Modifier.widthIn(min = 240.dp)
                ) {
                    if (recentBoards.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No recent boards yet") },
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        recentBoards.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.label)
                                        Text(
                                            "${if (item.ref is BoardRef.BuiltIn) "Built-in" else "Saved"} · ${relativeSavedAt(item.usedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    showRecentBoardsMenu = false
                                    showMenu = false
                                    onOpenBoard(item.ref, item.label)
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("See all boards...") },
                        onClick = {
                            showRecentBoardsMenu = false
                            showMenu = false
                            onOpenDialog(BoardDialog.OpenBoard)
                        }
                    )
                }
            }
            // Saving a board keeps a same-device copy (see SavedBoardRepository) — lighter
            // than a backup, which also carries the audio off the device.
            DropdownMenuItem(
                text = { Text("Save board as...") },
                leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.SaveBoardAs) }
            )
            DropdownMenuItem(
                text = { Text("Open board...") },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.OpenBoard) }
            )
            HorizontalDivider()
            // Mode
            MenuSwitchRow(Icons.Filled.Edit, "Edit mode", editMode, onEditModeChange)
            // Its timer, tap-to-close and mute options live in Settings → Show mode.
            MenuSwitchRow(Icons.Filled.Visibility, "Show mode", showModeEnabled, onShowModeChange)
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Speak...") },
                leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.Speak) }
            )
            HorizontalDivider()
            // Settings — see SettingsDialog
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.Settings) }
            )
            HorizontalDivider()
            MenuSectionHeader("Page")
            DropdownMenuItem(
                text = { Text("Page options (${board.currentPage.name})") },
                leadingIcon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.PageOptions(board.currentPageIndex)) }
            )
            HorizontalDivider()
            // Backup — full, portable, self-contained (structure + audio)
            MenuSectionHeader("Backup")
            DropdownMenuItem(
                text = { Text("Export backup") },
                leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                onClick = menuAction(onExportBackup)
            )
            DropdownMenuItem(
                text = { Text("Import backup") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = menuAction(onImportBackup)
            )
            DropdownMenuItem(
                text = { Text("Clean up unused clips") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = menuAction { onOpenDialog(BoardDialog.StrayCleanup) }
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
                onClick = menuAction {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASE_URL))
                    )
                }
            )
        }
    }
}

/** One tab per page plus a trailing "+" tab; long-pressing a page's tab opens its Page options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTabs(
    board: Board,
    onSelectPage: (Int) -> Unit,
    onOpenDialog: (BoardDialog) -> Unit,
    modifier: Modifier
) {
    PrimaryScrollableTabRow(selectedTabIndex = board.currentPageIndex, modifier = modifier) {
        val tabHaptics = LocalHapticFeedback.current
        board.pages.forEachIndexed { index, page ->
            Tab(
                selected = index == board.currentPageIndex,
                onClick = { onSelectPage(index) },
                // Long-press detection must run on the Initial (outside-in) pointer
                // pass and win the race against Tab's own click before it can consume
                // the eventual up event on the Main pass (the race itself is explained
                // inside awaitEachGesture below). Attached
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
                            onOpenDialog(BoardDialog.PageOptions(index))
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
            onClick = { onOpenDialog(BoardDialog.AddPage) },
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add page") }
        )
    }
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

/** A menu row with a switch that flips in place, leaving the menu open. */
@Composable
private fun MenuSwitchRow(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(label)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
