package com.example.soundboard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BoardRepository(app)
    private val player = SoundPlayer()

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repo.load() }
            _board.value = loaded
            withContext(Dispatchers.IO) {
                loaded.tiles.mapNotNull { it.fileName }.forEach { name ->
                    player.load(name, repo.soundFile(name))
                }
            }
        }
    }

    fun play(tile: Tile) {
        tile.fileName?.let { player.play(it) }
    }

    fun setLabel(tileId: String, label: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = label) else it }
    }

    fun assignSound(tileId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { repo.importSound(uri) } ?: return@launch
            withContext(Dispatchers.IO) { player.load(name, repo.soundFile(name)) }
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

    private fun updateTiles(transform: (List<Tile>) -> List<Tile>) {
        commit(_board.value.copy(tiles = transform(_board.value.tiles)))
    }

    /** Single write path: update state, drop orphaned audio, persist. */
    private fun commit(board: Board) {
        val before = _board.value.tiles.mapNotNull { it.fileName }.toSet()
        val after = board.tiles.mapNotNull { it.fileName }.toSet()
        _board.value = board

        (before - after).forEach { player.unload(it) }

        viewModelScope.launch(Dispatchers.IO) {
            repo.save(board)
            repo.pruneUnused(after)
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
