package com.example.soundboard.data

import com.example.soundboard.model.Board
import kotlin.uuid.Uuid

/** One saved board's identity, without loading its full board content. */
data class SavedBoard(val id: String, val name: String, val savedAt: Long)

/**
 * A saved board is just a [Board] snapshot, stored as its own small JSON file
 * under `presets/` — deliberately not a zip, and deliberately not
 * carrying a copy of any audio. Tiles keep pointing at the same `sounds/`
 * files the live board already uses, so saving one costs a few KB rather than
 * a full copy of every recorded clip. That also means a saved board can't
 * survive a reinstall or cleared app data on its own (that wipes `sounds/`
 * too) — [BoardRepository.exportTo] is the self-contained, portable mechanism
 * for that; this is same-device version history only.
 */
class SavedBoardRepository(private val files: FileStore) {

    private val json = BoardJson

    /** Every saved board, newest first. Scans storage directly rather than keeping a separate index, so the list can never drift from what's actually there. */
    suspend fun list(): List<SavedBoard> =
        savedBoardFiles()
            .mapNotNull { info ->
                decode(info.name)?.let { board -> SavedBoard(info.name.removeSuffix(".json"), board.name, info.lastModifiedMillis) }
            }
            .sortedByDescending { it.savedAt }

    suspend fun load(id: String): Board? = decode("$id.json")

    /** Snapshots [board] as a brand-new saved board and returns its id. */
    suspend fun save(board: Board): String {
        val id = Uuid.random().toString()
        files.writeText(pathOf("$id.json"), json.encodeToString(board))
        return id
    }

    /** Every fileName any saved board still points at — fold this into [BoardRepository.pruneUnused]'s keep set so clearing a live tile can never delete audio a saved board still needs. */
    suspend fun allReferencedFileNames(): Set<String> =
        savedBoardFiles()
            .mapNotNull { decode(it.name) }
            .flatMap { it.soundFileNames }
            .toSet()

    private suspend fun savedBoardFiles(): List<StoredFileInfo> =
        files.list(SAVED_BOARDS_DIR).filter { it.name.endsWith(".json") }

    private suspend fun decode(fileName: String): Board? =
        runCatching { json.decodeFromString<Board>(files.readText(pathOf(fileName))!!) }.getOrNull()

    private fun pathOf(fileName: String) = "$SAVED_BOARDS_DIR/$fileName"

    private companion object {
        // Named from when saved boards were called presets; kept so existing ones still show up.
        const val SAVED_BOARDS_DIR = "presets"
    }
}
