package com.example.soundboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.AudioRecorder
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.audio.Speaker
import com.example.soundboard.audio.TtsSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget
import com.example.soundboard.data.appFileStore
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.RecentBoardEntry
import com.example.soundboard.data.RecentBoardKind
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoard
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BoardViewModel(
    private val boardRepo: BoardRepository,
    private val player: Player,
    private val recorder: Recorder,
    private val savedBoardRepo: SavedBoardRepository,
    private val speaker: Speaker,
    private val devicePrefs: DevicePreferences,
    private val recentBoardsRepo: RecentBoardsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(), BoardSettingsActions {

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    /** One-off status text for the UI to show (e.g. in a Snackbar), then clear. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences]. */
    private val _performanceModeEnabled = MutableStateFlow(devicePrefs.performanceModeEnabled)
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences]. */
    private val _showMode = MutableStateFlow(devicePrefs.showMode)
    val showMode: StateFlow<ShowModeSettings> = _showMode.asStateFlow()

    /** The words Show mode has on screen right now; null when the text screen is closed. */
    private val _shownText = MutableStateFlow<String?>(null)
    val shownText: StateFlow<String?> = _shownText.asStateFlow()

    private val _savedBoards = MutableStateFlow<List<SavedBoard>>(emptyList())
    val savedBoards: StateFlow<List<SavedBoard>> = _savedBoards.asStateFlow()

    /** Sound files not referenced by any tile on the live board or any saved board — see [refreshStrayClips]. */
    private val _strayClips = MutableStateFlow<List<StrayClip>>(emptyList())
    val strayClips: StateFlow<List<StrayClip>> = _strayClips.asStateFlow()

    /** Boards actually opened/saved recently, newest first — see [RecentBoardsRepository]. */
    private val _recentBoards = MutableStateFlow<List<RecentBoardItem>>(emptyList())
    val recentBoards: StateFlow<List<RecentBoardItem>> = _recentBoards.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Where the active recording is writing to; only meaningful while [isRecording] is true. */
    private var pendingRecordingPath: String? = null

    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (!boardRepo.hasSavedBoard()) boardRepo.importFromAsset(FALLBACK_BOARD_ASSET)
            }
            val loaded = withContext(ioDispatcher) { boardRepo.load() }
            // Only overrides where the pager starts, not what's saved to disk — a plain
            // switchTo(), same as any other in-session page switch, see BoardViewModel.switchPage.
            val initial = if (loaded.openOnHomePage) {
                loaded.homePageIndex?.let { loaded.withCurrentPage(it) } ?: loaded
            } else {
                loaded
            }
            _board.value = initial
            withContext(ioDispatcher) { loadSounds(initial) }
        }
    }

    fun play(tile: Tile) {
        if (!tile.isPlayable(_board.value.speakUnrecordedTilesEnabled)) return
        val name = tile.fileName
        if (name != null) player.play(name, tile.volume) else speaker.speak(tile.speechText)
    }

    /**
     * A tile used for real, by a tap or a long-press preview on the board: puts its words on
     * the Show mode screen when that's on, and plays it unless Show mode mutes sounds. The tile
     * editor's own Play button calls [play] directly, so it never opens the text screen.
     */
    fun activate(tile: Tile) {
        if (!tile.isPlayable(_board.value.speakUnrecordedTilesEnabled)) return
        val showMode = _showMode.value
        val text = tile.speechText.trim()
        if (showMode.enabled && text.isNotEmpty()) {
            _shownText.value = text
            if (showMode.muteSounds) return
        }
        play(tile)
    }

    /** Closes the Show mode text screen. Whatever it was playing carries on to the end. */
    fun dismissShownText() {
        _shownText.value = null
    }

    /** Speaks arbitrary text not tied to any tile — for a one-off phrase no pad covers. */
    fun speakAdHoc(text: String) {
        speaker.speak(text)
    }

    // The tile editors below find their tile by id on whichever page holds it (see
    // Board.updatingTile), so the same call edits a tile on the current page or a
    // home-row tile tapped from the sticky row on another page.

    fun setLabel(tileId: String, label: String) = updateTile(tileId) { it.copy(label = label) }

    /** Sets the longer text spoken instead of the label; a blank value clears it back to null (falls back to the label). */
    fun setTtsScript(tileId: String, script: String) = updateTile(tileId) { it.copy(ttsScript = script.ifBlank { null }) }

    fun setVolume(tileId: String, volume: Float) = updateTile(tileId) { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setColor(tileId: String, colorArgb: Int?) = updateTile(tileId) { it.copy(colorArgb = colorArgb) }

    /** Per-tile opacity override; null inherits the page's, then the board's. */
    fun setOpacity(tileId: String, opacity: Float?) = updateTile(tileId) { it.copy(opacity = opacity?.coerceIn(0f, 1f)) }

    /** Per-tile border override; null inherits the page's, then the board's. */
    fun setBorder(tileId: String, border: TileBorder?) = updateTile(tileId) { it.copy(border = border) }

    fun setSpeakWhenNoSound(tileId: String, value: Boolean) = updateTile(tileId) { it.copy(speakWhenNoSound = value) }

    fun assignSound(tileId: String, file: PickedFile) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { boardRepo.importSound(file) } ?: return@launch
            withContext(ioDispatcher) { player.load(name, boardRepo.soundPath(name)) }
            updateTile(tileId) { it.copy(fileName = name) }
        }
    }

    fun clearTile(tileId: String) = updateTile(tileId) { it.copy(label = "", fileName = null, speakWhenNoSound = false) }

    /** Detaches a tile's sound without touching its label or [Tile.speakWhenNoSound]. */
    fun removeSound(tileId: String) = updateTile(tileId) { it.copy(fileName = null) }

    /** Starts recording into a fresh file; call [stopRecording] or [cancelRecording] to end it. */
    fun startRecording() {
        if (_isRecording.value) return
        viewModelScope.launch {
            val path = boardRepo.newRecordingPath()
            val started = withContext(ioDispatcher) { recorder.start(path) }
            if (started) {
                pendingRecordingPath = path
                _isRecording.value = true
            } else {
                _message.value = "Couldn't start recording"
            }
        }
    }

    /** Stops the active recording and points [tileId] at the result. */
    fun stopRecording(tileId: String) {
        viewModelScope.launch {
            val name = finishRecording() ?: return@launch
            updateTile(tileId) { it.copy(fileName = name) }
        }
    }

    /** Abandons the active recording without assigning it to any tile. */
    fun cancelRecording() {
        if (!_isRecording.value) return
        viewModelScope.launch {
            withContext(ioDispatcher) { recorder.cancel() }
            _isRecording.value = false
            val path = pendingRecordingPath
            pendingRecordingPath = null
            if (path != null) withContext(ioDispatcher) { boardRepo.deleteRecording(path) }
        }
    }

    /** Stops the recorder, loads the clip into the player, and returns its file name — or null on failure. */
    private suspend fun finishRecording(): String? {
        if (!_isRecording.value) return null
        val ok = withContext(ioDispatcher) { recorder.stop() }
        _isRecording.value = false
        val path = pendingRecordingPath
        pendingRecordingPath = null
        if (!ok || path == null) {
            if (path != null) withContext(ioDispatcher) { boardRepo.deleteRecording(path) }
            _message.value = "Recording failed"
            return null
        }
        val name = path.substringAfterLast('/')
        withContext(ioDispatcher) { player.load(name, path) }
        return name
    }

    /** Whether the home page's first row shows fixed above every other page. */
    override fun setStickyHomeRowEnabled(value: Boolean) {
        commit(_board.value.copy(stickyHomeRowEnabled = value))
    }

    /** Global tile opacity; overridden per-page by [setPageOpacity] and per-tile by [setOpacity]. */
    override fun setBoardTileOpacity(value: Float) {
        commit(_board.value.copy(tileOpacity = value.coerceIn(0f, 1f)))
    }

    /** Global tile border; overridden per-page by [setPageBorder] and per-tile by [setBorder]. */
    override fun setBoardTileBorder(value: TileBorder) {
        commit(_board.value.copy(tileBorder = value))
    }

    /** Disables tile shadows to help scrolling stay smooth on slower devices. */
    override fun setPerformanceModeEnabled(value: Boolean) {
        devicePrefs.performanceModeEnabled = value
        _performanceModeEnabled.value = value
    }

    override fun setShowModeEnabled(value: Boolean) = updateShowMode { it.copy(enabled = value) }

    /** 0 turns the timer off; ignored if tap to close is off too (see [ShowModeSettings.withTimerSeconds]). */
    override fun setShowModeTimerSeconds(value: Int) = updateShowMode { it.withTimerSeconds(value) }

    /** Ignored when turning it off would leave no timer either (see [ShowModeSettings.withTapToClose]). */
    override fun setShowModeTapToClose(value: Boolean) = updateShowMode { it.withTapToClose(value) }

    override fun setShowModeMuteSounds(value: Boolean) = updateShowMode { it.copy(muteSounds = value) }

    override fun setShowModeFlipped(value: Boolean) = updateShowMode { it.copy(flipped = value) }

    private fun updateShowMode(transform: (ShowModeSettings) -> ShowModeSettings) {
        val updated = transform(_showMode.value)
        if (updated == _showMode.value) return
        devicePrefs.showMode = updated
        _showMode.value = updated
        // Turning Show mode off shouldn't leave its text screen stranded on top of the board.
        if (!updated.enabled) _shownText.value = null
    }

    override fun setDefaultPageRows(value: Int) {
        commit(_board.value.copy(defaultPageRows = value))
    }

    override fun setDefaultPageColumns(value: Int) {
        commit(_board.value.copy(defaultPageColumns = value))
    }

    fun resize(index: Int, rows: Int, columns: Int) {
        commit(_board.value.updatingPage(index) { it.withGridSize(rows, columns) })
    }

    fun setTileAspectRatio(index: Int, ratio: Float) {
        commit(_board.value.updatingPage(index) { it.copy(tileAspectRatio = ratio) })
    }

    /** Per-page landscape grid; null for either dimension derives it from the portrait grid. */
    fun setLandscapeGrid(index: Int, rows: Int?, columns: Int?) {
        commit(_board.value.updatingPage(index) { it.copy(landscapeRows = rows, landscapeColumns = columns) })
    }

    override fun setLandscapeLayout(layout: LandscapeLayout) {
        commit(_board.value.copy(landscapeLayout = layout))
    }

    /** Tile label font/size/weight/case; the size range is kept in order (min never above max). */
    override fun setLabelStyle(style: LabelStyle) {
        val min = style.minSizeSp.coerceIn(LabelStyle.SIZE_RANGE_SP)
        val max = style.maxSizeSp.coerceIn(LabelStyle.SIZE_RANGE_SP).coerceAtLeast(min)
        commit(_board.value.copy(labelStyle = style.copy(minSizeSp = min, maxSizeSp = max)))
    }

    override fun setRowHeight(rowHeight: RowHeight) {
        commit(_board.value.copy(rowHeight = rowHeight))
    }

    /** Per-page row height override; null inherits the board's global setting. */
    fun setPageRowHeight(index: Int, rowHeight: RowHeight?) {
        commit(_board.value.updatingPage(index) { it.copy(rowHeight = rowHeight) })
    }

    fun setPageColor(index: Int, colorArgb: Int?) {
        commit(_board.value.updatingPage(index) { it.copy(color = colorArgb) })
    }

    /** Per-page opacity override; null inherits the board's global setting. */
    fun setPageOpacity(index: Int, opacity: Float?) {
        commit(_board.value.updatingPage(index) { it.copy(opacity = opacity?.coerceIn(0f, 1f)) })
    }

    /** Per-page border override; null inherits the board's global setting. */
    fun setPageBorder(index: Int, border: TileBorder?) {
        commit(_board.value.updatingPage(index) { it.copy(border = border) })
    }

    /** Whether a fresh launch jumps to the home page instead of resuming the last-viewed one. */
    override fun setOpenOnHomePage(value: Boolean) {
        commit(_board.value.copy(openOnHomePage = value))
    }

    /** Minutes of inactivity before auto-return to home; 0 disables it. */
    override fun setIdleTimeoutMinutes(value: Int) {
        commit(_board.value.copy(idleTimeoutMinutes = value))
    }

    /** How long a page-tab press must be held before it counts as a long-press, in milliseconds. */
    override fun setLongPressDurationMillis(value: Int) {
        commit(_board.value.copy(longPressDurationMillis = value))
    }

    override fun setThemeMode(mode: ThemeMode) {
        commit(_board.value.copy(themeMode = mode))
    }

    override fun setKeepScreenAwake(value: Boolean) {
        commit(_board.value.copy(keepScreenAwake = value))
    }

    override fun setHapticFeedbackEnabled(value: Boolean) {
        commit(_board.value.copy(hapticFeedbackEnabled = value))
    }

    /** Whether a tile with no sound file speaks its label via TTS on tap, even without its own speak-label switch on. */
    override fun setSpeakUnrecordedTilesEnabled(value: Boolean) {
        commit(_board.value.copy(speakUnrecordedTilesEnabled = value))
    }

    /** Whether blank tiles are hidden (and untappable) outside of edit mode, to avoid a stray tap opening the editor. */
    override fun setHideBlankTilesEnabled(value: Boolean) {
        commit(_board.value.copy(hideBlankTilesEnabled = value))
    }

    /** Solid background color; setting one clears any background image. */
    override fun setBackgroundColor(argb: Int?) = replaceBackground(colorArgb = argb, imageFileName = null)

    /** Background image picked from the gallery; replaces any solid background color. */
    fun setBackgroundImage(file: PickedFile) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { boardRepo.importBackgroundImage(file) } ?: return@launch
            replaceBackground(colorArgb = null, imageFileName = name)
        }
    }

    /** Clears the background back to the plain theme surface. */
    override fun clearBackground() = replaceBackground(colorArgb = null, imageFileName = null)

    /**
     * Single write path for the background: at most one of color or image is ever set, and
     * the image file it replaces is deleted — nothing else points at it (backgrounds aren't
     * shared with saved boards the way sounds are).
     */
    private fun replaceBackground(colorArgb: Int?, imageFileName: String?) {
        val previousImage = _board.value.backgroundImageFileName
        commit(_board.value.copy(backgroundColorArgb = colorArgb, backgroundImageFileName = imageFileName))
        if (previousImage != null && previousImage != imageFileName) {
            viewModelScope.launch(ioDispatcher) { boardRepo.deleteBackground(previousImage) }
        }
    }

    /** The image behind [Board.backgroundImageFileName], for the UI to decode and render; null if it's gone. */
    suspend fun backgroundImage(name: String): ByteArray? = withContext(ioDispatcher) { boardRepo.readBackground(name) }

    fun renameBoard(name: String) {
        commit(_board.value.copy(name = name.ifBlank { "New Board" }))
    }

    fun setHomePage(index: Int) {
        commit(_board.value.withHomePage(index))
    }

    fun clearHomePage() {
        commit(_board.value.withoutHomePage())
    }

    fun addPage(name: String) {
        commit(_board.value.withPageAdded(name.ifBlank { "Page ${_board.value.pages.size + 1}" }))
    }

    fun renamePage(index: Int, name: String) {
        commit(_board.value.withPageRenamed(index, name))
    }

    fun deletePage(index: Int) {
        commit(_board.value.withPageRemoved(index))
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        commit(_board.value.withPageMoved(fromIndex, toIndex))
    }

    /** Switches the active page without touching disk — nothing about the board changed. */
    fun switchPage(index: Int) {
        _board.value = _board.value.withCurrentPage(index)
    }

    /** Live-reorders tiles during a drag without touching disk; see [commitOrder]. */
    fun previewMove(fromIndex: Int, toIndex: Int) {
        _board.value = _board.value.updatingCurrentPage { it.withTileMoved(fromIndex, toIndex) }
    }

    /** Persists whatever order a drag gesture has left the board in. */
    fun commitOrder() {
        commit(_board.value)
    }

    fun exportBoard(target: SaveTarget) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { boardRepo.exportTo(target) }
            _message.value = if (ok) "Exported backup" else "Export failed"
        }
    }

    fun importBoard(file: PickedFile) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { boardRepo.importFrom(file) }
            replaceBoardAfterImport(ok, "Imported backup", "Import failed")
        }
    }

    /** Refreshes [savedBoards] from disk; call before showing the Open board picker. */
    fun refreshSavedBoards() {
        viewModelScope.launch {
            _savedBoards.value = withContext(ioDispatcher) { savedBoardRepo.list() }
        }
    }

    /** Refreshes [strayClips] from disk; call before showing the stray-clip cleanup dialog. */
    fun refreshStrayClips() {
        viewModelScope.launch {
            _strayClips.value = withContext(ioDispatcher) {
                val keep = _board.value.soundFileNames + savedBoardRepo.allReferencedFileNames()
                boardRepo.strayFiles(keep).map { StrayClip(it.name, it.sizeBytes) }
            }
        }
    }

    /** Zips [strayClips] to [target] and, only if that succeeds, deletes them. */
    fun exportAndDeleteStrayClips(target: SaveTarget) {
        val fileNames = _strayClips.value.map { it.fileName }
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { boardRepo.exportFiles(fileNames, target) }
            if (ok) {
                withContext(ioDispatcher) { boardRepo.deleteFiles(fileNames) }
                _strayClips.value = emptyList()
                _message.value = "Exported and deleted ${fileNames.size} unused clip(s)"
            } else {
                _message.value = "Export failed"
            }
        }
    }

    /** Deletes [strayClips] without exporting them first. */
    fun deleteStrayClips() {
        val fileNames = _strayClips.value.map { it.fileName }
        viewModelScope.launch {
            withContext(ioDispatcher) { boardRepo.deleteFiles(fileNames) }
            _strayClips.value = emptyList()
            _message.value = "Deleted ${fileNames.size} unused clip(s)"
        }
    }

    /** Refreshes [recentBoards] from disk; call before showing the title bar's quick-switch dropdown. */
    fun refreshRecentBoards() {
        viewModelScope.launch {
            _recentBoards.value = withContext(ioDispatcher) { recentBoardsRepo.recent().mapNotNull { it.toItem() } }
        }
    }

    /**
     * Snapshots the current board as a brand-new saved board and renames the
     * live board to match — same-device version history, not a portable
     * backup (see [SavedBoardRepository]). [exportBoard] is still what carries a
     * board's audio off-device.
     */
    fun saveBoardAs(name: String) {
        val renamed = _board.value.copy(name = name.ifBlank { _board.value.name })
        viewModelScope.launch {
            val id = withContext(ioDispatcher) { savedBoardRepo.save(renamed) }
            commit(renamed)
            refreshSavedBoards()
            recordRecentlyUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = id, label = renamed.name, usedAt = System.currentTimeMillis()))
            _message.value = "Saved board \"${renamed.name}\""
        }
    }

    /** Replaces the live board with [ref]'s content. Caller is responsible for confirming this is wanted first. */
    fun openBoard(ref: BoardRef) {
        viewModelScope.launch {
            when (ref) {
                is BoardRef.Saved -> {
                    val loaded = withContext(ioDispatcher) { savedBoardRepo.load(ref.id) }
                    if (loaded == null) {
                        _message.value = "Couldn't open board"
                        return@launch
                    }
                    player.clear()
                    commit(loaded)
                    withContext(ioDispatcher) { loadSounds(loaded) }
                    recordRecentlyUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = ref.id, label = loaded.name, usedAt = System.currentTimeMillis()))
                    _message.value = "Loaded \"${loaded.name}\""
                }
                is BoardRef.BuiltIn -> {
                    val ok = withContext(ioDispatcher) { boardRepo.importFromAsset(ref.assetName) }
                    if (ok) {
                        recordRecentlyUsed(RecentBoardEntry(kind = RecentBoardKind.BUILT_IN, assetName = ref.assetName, label = ref.label, usedAt = System.currentTimeMillis()))
                    }
                    replaceBoardAfterImport(ok, "Loaded \"${ref.label}\"", "Couldn't open board")
                }
            }
        }
    }

    private suspend fun recordRecentlyUsed(entry: RecentBoardEntry) {
        _recentBoards.value = withContext(ioDispatcher) {
            recentBoardsRepo.recordUsed(entry)
            recentBoardsRepo.recent()
        }.mapNotNull { it.toItem() }
    }

    private fun RecentBoardEntry.toItem(): RecentBoardItem? {
        val ref = when (kind) {
            RecentBoardKind.SAVED -> id?.let { BoardRef.Saved(it) }
            RecentBoardKind.BUILT_IN -> assetName?.let { BoardRef.BuiltIn(it, label) }
        } ?: return null
        return RecentBoardItem(ref, label, usedAt)
    }

    /** Built-in boards bundled with this build — Jeremy's, Sarah's, and the TTS-only board ship in every build; Steve's only where its asset is actually packaged (debug builds). */
    fun builtInBoards(): List<BoardRef.BuiltIn> = buildList {
        add(BoardRef.BuiltIn(JEREMY_BOARD_ASSET, "Jeremy Draft Care Board"))
        add(BoardRef.BuiltIn(SARAH_BOARD_ASSET, "Sarah (ElevenLabs) Care Board"))
        add(BoardRef.BuiltIn(TTS_BOARD_ASSET, "TTS Care Board"))
        if (boardRepo.hasAsset(STEVE_BOARD_ASSET)) {
            add(BoardRef.BuiltIn(STEVE_BOARD_ASSET, "Steve Draft Care Board"))
        }
    }

    private suspend fun replaceBoardAfterImport(imported: Boolean, successMessage: String, failureMessage: String) {
        if (imported) {
            player.clear()
            val loaded = withContext(ioDispatcher) { boardRepo.load() }
            _board.value = loaded
            withContext(ioDispatcher) { loadSounds(loaded) }
            _message.value = successMessage
        } else {
            _message.value = failureMessage
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun loadSounds(board: Board) {
        board.soundFileNames.forEach { name ->
            player.load(name, boardRepo.soundPath(name))
        }
    }

    private fun updateTile(tileId: String, transform: (Tile) -> Tile) {
        commit(_board.value.updatingTile(tileId, transform))
    }

    /** Single write path: update state, drop orphaned audio, persist. */
    private fun commit(board: Board) {
        val next = board.normalized()
        val removedSounds = _board.value.soundFileNames - next.soundFileNames
        _board.value = next

        removedSounds.forEach { player.unload(it) }

        viewModelScope.launch(ioDispatcher) {
            // Back-to-back commits (e.g. Grid size's Apply) each launch a save on a pool
            // thread; without the lock they can finish out of order and leave an older
            // board on disk. Each save writes whatever is current, so the last one to run
            // always persists the latest board — and prunes against its files, not a
            // stale commit's that might not know about a sound added since.
            saveMutex.withLock {
                val latest = _board.value
                boardRepo.save(latest)
                // A saved board references sound files by name without copying them (see
                // SavedBoardRepository) — protect those from pruning too, or clearing/replacing a
                // live tile could delete audio a saved board still points at.
                boardRepo.pruneUnused(latest.soundFileNames + savedBoardRepo.allReferencedFileNames())
            }
        }
    }

    private val saveMutex = Mutex()

    override fun onCleared() {
        recorder.cancel()
        // viewModelScope is already cancelled here, so the abandoned file is deleted on a
        // scope of its own. Missing it would only leave a stray clip for the next prune.
        pendingRecordingPath?.let { path ->
            CoroutineScope(ioDispatcher + NonCancellable).launch { boardRepo.deleteRecording(path) }
        }
        player.release()
        speaker.shutdown()
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BoardViewModel(
                BoardRepository(app),
                SoundPlayer(appFileStore(app)),
                AudioRecorder(app, appFileStore(app)),
                SavedBoardRepository(app),
                TtsSpeaker(app),
                DevicePreferences(app),
                RecentBoardsRepository(app)
            ) as T
        }
    }

    companion object {
        /** Bundled in every build (src/main/assets/); see [BoardRepository.importFromAsset]. */
        private const val JEREMY_BOARD_ASSET = "jeremy-care-board.zip"

        /** Shipped as the default/fallback board for now. */
        private const val FALLBACK_BOARD_ASSET = JEREMY_BOARD_ASSET

        /** Same layout as Jeremy's board, but every tile speaks instead of playing audio — bundled in every build. */
        private const val TTS_BOARD_ASSET = "tts-care-board.zip"

        /** Same layout as Jeremy's board, recorded with ElevenLabs' Sarah voice — bundled in every build. */
        private const val SARAH_BOARD_ASSET = "sarah-care-board.zip"

        /** Debug-only (src/debug/assets/) — only actually available where that asset is packaged. */
        private const val STEVE_BOARD_ASSET = "steve-care-board.zip"
    }
}

/** A board the "Open board" picker can open — either built into the app or saved on this device. */
sealed interface BoardRef {
    data class Saved(val id: String) : BoardRef
    data class BuiltIn(val assetName: String, val label: String) : BoardRef
}

/** One entry in the title bar's quick-switch dropdown — see [BoardViewModel.recentBoards]. */
data class RecentBoardItem(val ref: BoardRef, val label: String, val usedAt: Long)

/** One unused sound file found by [BoardViewModel.refreshStrayClips]. */
data class StrayClip(val fileName: String, val sizeBytes: Long)
