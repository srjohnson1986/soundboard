package com.example.soundboard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Tile
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    /** One-off status text for the UI to show (e.g. in a Snackbar), then clear. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (!repo.hasSavedBoard()) repo.importFromAsset(TEST_PRESET_ASSET)
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

    fun resize(rows: Int, columns: Int) {
        commit(_board.value.resized(rows, columns))
    }

    fun renameBoard(name: String) {
        commit(_board.value.copy(name = name.ifBlank { "New Board" }))
    }

    /** Live-reorders tiles during a drag without touching disk; see [commitOrder]. */
    fun previewMove(fromIndex: Int, toIndex: Int) {
        _board.value = _board.value.moved(fromIndex, toIndex)
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
            if (ok) {
                player.clear()
                val loaded = withContext(ioDispatcher) { repo.load() }
                _board.value = loaded
                withContext(ioDispatcher) { loadSounds(loaded) }
                _message.value = "Imported backup"
            } else {
                _message.value = "Import failed"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun loadSounds(board: Board) {
        board.tiles.mapNotNull { it.fileName }.forEach { name ->
            player.load(name, repo.soundFile(name))
        }
    }

    private fun updateTiles(transform: (List<Tile>) -> List<Tile>) {
        commit(_board.value.copy(tiles = transform(_board.value.tiles)))
    }

    /** Single write path: update state, drop orphaned audio, persist. */
    private fun commit(board: Board) {
        val before = _board.value.tiles.mapNotNull { it.fileName }.toSet()
        val after = board.tiles.mapNotNull { it.fileName }.toSet()
        _board.value = board

        (before - after).forEach { player.unload(it) }

        viewModelScope.launch(ioDispatcher) {
            repo.save(board)
            repo.pruneUnused(after)
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BoardViewModel(BoardRepository(app), SoundPlayer()) as T
        }
    }

    companion object {
        /** Bundled only in debug builds (src/debug/assets/); see [BoardRepository.importFromAsset]. */
        private const val TEST_PRESET_ASSET = "care-board.zip"
    }
}
