package com.example.soundboard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.AudioRecorder
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Tile
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoardViewModel(
    private val repo: BoardRepository,
    private val player: Player,
    private val recorder: Recorder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    /** One-off status text for the UI to show (e.g. in a Snackbar), then clear. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** The file the active recording is writing to; only meaningful while [isRecording] is true. */
    private var pendingRecordingFile: File? = null

    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (!repo.hasSavedBoard()) repo.importFromAsset(FALLBACK_PRESET_ASSET)
            }
            val loaded = withContext(ioDispatcher) { repo.load() }
            _board.value = loaded
            withContext(ioDispatcher) { loadSounds(loaded) }
        }
    }

    fun play(tile: Tile) {
        val name = tile.fileName ?: return
        player.play(name, tile.volume)
    }

    fun setLabel(tileId: String, label: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = label) else it }
    }

    fun setVolume(tileId: String, volume: Float) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(volume = volume.coerceIn(0f, 1f)) else it }
    }

    fun setColor(tileId: String, colorArgb: Int?) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(colorArgb = colorArgb) else it }
    }

    fun assignSound(tileId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { repo.importSound(uri) } ?: return@launch
            withContext(ioDispatcher) { player.load(name, repo.soundFile(name)) }
            updateTiles { tiles ->
                tiles.map { if (it.id == tileId) it.copy(fileName = name) else it }
            }
        }
    }

    fun clearTile(tileId: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = "", fileName = null) else it }
    }

    /** Starts recording into a fresh file; call [stopRecording] or [cancelRecording] to end it. */
    fun startRecording() {
        if (_isRecording.value) return
        viewModelScope.launch {
            val file = withContext(ioDispatcher) { repo.newRecordingFile() }
            val started = withContext(ioDispatcher) { recorder.start(file) }
            if (started) {
                pendingRecordingFile = file
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
            updateTiles { tiles -> tiles.map { if (it.id == tileId) it.copy(fileName = name) else it } }
        }
    }

    /** Abandons the active recording without assigning it to any tile. */
    fun cancelRecording() {
        if (!_isRecording.value) return
        viewModelScope.launch {
            withContext(ioDispatcher) { recorder.cancel() }
            _isRecording.value = false
            pendingRecordingFile?.delete()
            pendingRecordingFile = null
        }
    }

    /** Stops the recorder, loads the clip into the player, and returns its file name — or null on failure. */
    private suspend fun finishRecording(): String? {
        if (!_isRecording.value) return null
        val ok = withContext(ioDispatcher) { recorder.stop() }
        _isRecording.value = false
        val file = pendingRecordingFile
        pendingRecordingFile = null
        if (!ok || file == null) {
            file?.delete()
            _message.value = "Recording failed"
            return null
        }
        withContext(ioDispatcher) { player.load(file.name, file) }
        return file.name
    }

    // Pinned tiles live outside any page (Board.pinnedTiles), so they need their
    // own mutators mirroring the page-tile ones above instead of routing through
    // updatingCurrentPage.

    fun setPinnedLabel(tileId: String, label: String) = updatePinnedTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = label) else it }
    }

    fun setPinnedVolume(tileId: String, volume: Float) = updatePinnedTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(volume = volume.coerceIn(0f, 1f)) else it }
    }

    fun setPinnedColor(tileId: String, colorArgb: Int?) = updatePinnedTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(colorArgb = colorArgb) else it }
    }

    fun assignPinnedSound(tileId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { repo.importSound(uri) } ?: return@launch
            withContext(ioDispatcher) { player.load(name, repo.soundFile(name)) }
            updatePinnedTiles { tiles ->
                tiles.map { if (it.id == tileId) it.copy(fileName = name) else it }
            }
        }
    }

    fun clearPinnedTile(tileId: String) = updatePinnedTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = "", fileName = null) else it }
    }

    /** Same as [stopRecording], for a pinned tile. */
    fun stopPinnedRecording(tileId: String) {
        viewModelScope.launch {
            val name = finishRecording() ?: return@launch
            updatePinnedTiles { tiles -> tiles.map { if (it.id == tileId) it.copy(fileName = name) else it } }
        }
    }

    /** Materializes an empty pinned row sized to the current page's width; a no-op once one exists. */
    fun addPinnedRow() {
        if (_board.value.pinnedTiles.isNotEmpty()) return
        commit(_board.value.copy(pinnedTiles = List(_board.value.currentPage.columns) { Tile() }))
    }

    fun resize(rows: Int, columns: Int) {
        val resized = _board.value.updatingCurrentPage { it.resized(rows, columns) }
        // The pinned row always spans the page's column count; grow it to match
        // (never shrink — same never-drop-a-tile rule as Page.resized()).
        val pinned = resized.pinnedTiles
        val nextPinned = if (pinned.isNotEmpty() && pinned.size < columns) {
            pinned + List(columns - pinned.size) { Tile() }
        } else {
            pinned
        }
        commit(resized.copy(pinnedTiles = nextPinned))
    }

    fun setTileAspectRatio(ratio: Float) {
        commit(_board.value.updatingCurrentPage { it.copy(tileAspectRatio = ratio) })
    }

    fun setPageColor(colorArgb: Int?) {
        commit(_board.value.updatingCurrentPage { it.copy(color = colorArgb) })
    }

    fun renameBoard(name: String) {
        commit(_board.value.copy(name = name.ifBlank { "New Board" }))
    }

    fun setHomePage(index: Int) {
        commit(_board.value.withHomePage(index))
    }

    fun clearHomePage() {
        commit(_board.value.clearingHomePage())
    }

    fun addPage(name: String) {
        commit(_board.value.addPage(name.ifBlank { "Page ${_board.value.pages.size + 1}" }))
    }

    fun renamePage(index: Int, name: String) {
        commit(_board.value.renamePage(index, name))
    }

    fun deletePage(index: Int) {
        commit(_board.value.removePage(index))
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        commit(_board.value.movedPage(fromIndex, toIndex))
    }

    /** Switches the active page without touching disk — nothing about the board changed. */
    fun switchPage(index: Int) {
        _board.value = _board.value.switchTo(index)
    }

    /** Live-reorders tiles during a drag without touching disk; see [commitOrder]. */
    fun previewMove(fromIndex: Int, toIndex: Int) {
        _board.value = _board.value.updatingCurrentPage { it.moved(fromIndex, toIndex) }
    }

    /** Persists whatever order a drag gesture has left the board in. */
    fun commitOrder() {
        commit(_board.value)
    }

    fun exportBoard(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { repo.exportTo(uri) }
            _message.value = if (ok) "Exported backup" else "Export failed"
        }
    }

    fun importBoard(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { repo.importFrom(uri) }
            replaceBoardAfterImport(ok, "Imported backup", "Import failed")
        }
    }

    /** Debug-menu shortcut for loading the Jeremy test preset without hunting for the zip each time. */
    fun importJeremyTestPreset() {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { repo.importFromAsset(JEREMY_TEST_PRESET_ASSET) }
            replaceBoardAfterImport(ok, "Loaded Jeremy test preset", "Jeremy test preset not found")
        }
    }

    private suspend fun replaceBoardAfterImport(imported: Boolean, successMessage: String, failureMessage: String) {
        if (imported) {
            player.clear()
            val loaded = withContext(ioDispatcher) { repo.load() }
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
        allTiles(board).mapNotNull { it.fileName }.forEach { name ->
            player.load(name, repo.soundFile(name))
        }
    }

    private fun updateTiles(transform: (List<Tile>) -> List<Tile>) {
        commit(_board.value.updatingCurrentPage { it.copy(tiles = transform(it.tiles)) })
    }

    private fun updatePinnedTiles(transform: (List<Tile>) -> List<Tile>) {
        commit(_board.value.copy(pinnedTiles = transform(_board.value.pinnedTiles)))
    }

    /** Every tile a sound file can be referenced from: every page, plus the pinned row. */
    private fun allTiles(board: Board): List<Tile> = board.pages.flatMap { it.tiles } + board.pinnedTiles

    /** Single write path: update state, drop orphaned audio, persist. */
    private fun commit(board: Board) {
        val before = allTiles(_board.value).mapNotNull { it.fileName }.toSet()
        val after = allTiles(board).mapNotNull { it.fileName }.toSet()
        _board.value = board

        (before - after).forEach { player.unload(it) }

        viewModelScope.launch(ioDispatcher) {
            repo.save(board)
            repo.pruneUnused(after)
        }
    }

    override fun onCleared() {
        recorder.cancel()
        pendingRecordingFile?.delete()
        player.release()
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BoardViewModel(BoardRepository(app), SoundPlayer(), AudioRecorder(app)) as T
        }
    }

    companion object {
        /** Bundled in every build (src/main/assets/); see [BoardRepository.importFromAsset]. */
        private const val FALLBACK_PRESET_ASSET = "steve-care-board.zip"

        /** Debug-only (src/debug/assets/); loaded on demand via the debug-only menu item, never auto-imported. */
        private const val JEREMY_TEST_PRESET_ASSET = "jeremy-care-board.zip"
    }
}
