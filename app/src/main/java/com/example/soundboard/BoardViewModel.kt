package com.example.soundboard

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.AudioRecorder
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.PresetRepository
import com.example.soundboard.data.SavedPreset
import com.example.soundboard.data.SettingsRepository
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
    private val presetRepo: PresetRepository,
    private val settingsRepo: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    private val _openOnHomePage = MutableStateFlow(settingsRepo.openOnHomePage)
    val openOnHomePage: StateFlow<Boolean> = _openOnHomePage.asStateFlow()

    /** One-off status text for the UI to show (e.g. in a Snackbar), then clear. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _presets = MutableStateFlow<List<SavedPreset>>(emptyList())
    val presets: StateFlow<List<SavedPreset>> = _presets.asStateFlow()

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
            // Only overrides where the pager starts, not what's saved to disk — a plain
            // switchTo(), same as any other in-session page switch, see BoardViewModel.switchPage.
            val initial = if (settingsRepo.openOnHomePage) {
                loaded.homePageIndex?.let { loaded.switchTo(it) } ?: loaded
            } else {
                loaded
            }
            _board.value = initial
            withContext(ioDispatcher) { loadSounds(initial) }
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

    /**
     * Materializes an empty pinned row at a fixed width; a no-op once one exists.
     * Deliberately independent of any page's column count (#15) — a future version
     * may make this configurable, but for now every board's pinned row is the same
     * size regardless of how wide its pages are.
     */
    fun addPinnedRow() {
        if (_board.value.pinnedTiles.isNotEmpty()) return
        commit(_board.value.copy(pinnedTiles = List(PINNED_ROW_SIZE) { Tile() }))
    }

    fun resize(index: Int, rows: Int, columns: Int) {
        commit(_board.value.updatingPage(index) { it.resized(rows, columns) })
    }

    fun setTileAspectRatio(index: Int, ratio: Float) {
        commit(_board.value.updatingPage(index) { it.copy(tileAspectRatio = ratio) })
    }

    fun setPageColor(index: Int, colorArgb: Int?) {
        commit(_board.value.updatingPage(index) { it.copy(color = colorArgb) })
    }

    /** Whether a fresh launch jumps to the home page instead of resuming the last-viewed one. */
    fun setOpenOnHomePage(value: Boolean) {
        settingsRepo.openOnHomePage = value
        _openOnHomePage.value = value
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

    /** Refreshes [presets] from disk; call before showing a preset picker. */
    fun refreshPresets() {
        viewModelScope.launch {
            _presets.value = withContext(ioDispatcher) { presetRepo.list() }
        }
    }

    /**
     * Snapshots the current board as a brand-new saved preset and renames the
     * live board to match — same-device version history, not a portable
     * backup (see [PresetRepository]). [exportBoard] is still what carries a
     * board's audio off-device.
     */
    fun saveAsPreset(name: String) {
        val renamed = _board.value.copy(name = name.ifBlank { _board.value.name })
        viewModelScope.launch {
            withContext(ioDispatcher) { presetRepo.save(renamed) }
            commit(renamed)
            refreshPresets()
            _message.value = "Saved preset \"${renamed.name}\""
        }
    }

    /** Replaces the live board with [ref]'s content. Caller is responsible for confirming this is wanted first. */
    fun applyPreset(ref: PresetRef) {
        viewModelScope.launch {
            when (ref) {
                is PresetRef.Saved -> {
                    val loaded = withContext(ioDispatcher) { presetRepo.load(ref.id) }
                    if (loaded == null) {
                        _message.value = "Couldn't load preset"
                        return@launch
                    }
                    player.clear()
                    commit(loaded)
                    withContext(ioDispatcher) { loadSounds(loaded) }
                    _message.value = "Loaded \"${loaded.name}\""
                }
                is PresetRef.Factory -> {
                    val ok = withContext(ioDispatcher) { repo.importFromAsset(ref.assetName) }
                    replaceBoardAfterImport(ok, "Loaded \"${ref.label}\"", "Couldn't load preset")
                }
            }
        }
    }

    /** Factory presets bundled with this build — Steve's ships in every build; Jeremy's only where its asset is actually packaged (debug builds). */
    fun factoryPresets(context: Context): List<PresetRef.Factory> = buildList {
        add(PresetRef.Factory(FALLBACK_PRESET_ASSET, "Steve Draft Care Board"))
        if (runCatching { context.assets.open(JEREMY_PRESET_ASSET).close() }.isSuccess) {
            add(PresetRef.Factory(JEREMY_PRESET_ASSET, "Jeremy Draft Care Board"))
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
            // A saved preset references sound files by name without copying them (see
            // PresetRepository) — protect those from pruning too, or clearing/replacing a
            // live tile could delete audio a saved preset still points at.
            repo.pruneUnused(after + presetRepo.allReferencedFileNames())
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
            return BoardViewModel(
                BoardRepository(app),
                SoundPlayer(),
                AudioRecorder(app),
                PresetRepository(app),
                SettingsRepository(app)
            ) as T
        }
    }

    companion object {
        /** Bundled in every build (src/main/assets/); see [BoardRepository.importFromAsset]. */
        private const val FALLBACK_PRESET_ASSET = "steve-care-board.zip"

        /** Debug-only (src/debug/assets/) — only actually available where that asset is packaged. */
        private const val JEREMY_PRESET_ASSET = "jeremy-care-board.zip"

        /** Fixed width of a newly-created pinned row — independent of any page's column count (#15). */
        private const val PINNED_ROW_SIZE = 4
    }
}

/** A preset the "Load preset" picker can apply — either bundled with the app or saved on-device. */
sealed interface PresetRef {
    data class Saved(val id: String) : PresetRef
    data class Factory(val assetName: String, val label: String) : PresetRef
}
