package com.example.soundboard.data

import android.content.Context
import com.example.soundboard.model.Board
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString

/** One saved board's identity, without loading its full board content. */
data class SavedBoard(val id: String, val name: String, val savedAt: Long)

/**
 * A saved board is just a [Board] snapshot, stored as its own small JSON file
 * under `filesDir/presets/` — deliberately not a zip, and deliberately not
 * carrying a copy of any audio. Tiles keep pointing at the same `sounds/`
 * files the live board already uses, so saving one costs a few KB rather than
 * a full copy of every recorded clip. That also means a saved board can't
 * survive a reinstall or cleared app data on its own (that wipes `sounds/`
 * too) — [BoardRepository.exportTo] is the self-contained, portable mechanism
 * for that; this is same-device version history only.
 */
class SavedBoardRepository(context: Context) {

    // Named from when saved boards were called presets; kept so existing ones still show up.
    private val savedBoardsDir = File(context.filesDir, "presets")

    private val json = BoardJson

    /** Every saved board, newest first. Scans disk directly rather than keeping a separate index, so the list can never drift from what's actually there. */
    fun list(): List<SavedBoard> =
        savedBoardFiles()
            .mapNotNull { file -> decode(file)?.let { board -> SavedBoard(file.nameWithoutExtension, board.name, file.lastModified()) } }
            .sortedByDescending { it.savedAt }

    fun load(id: String): Board? = decode(File(savedBoardsDir, "$id.json"))

    /** Snapshots [board] as a brand-new saved board and returns its id. */
    fun save(board: Board): String {
        savedBoardsDir.mkdirs()
        val id = UUID.randomUUID().toString()
        File(savedBoardsDir, "$id.json").writeText(json.encodeToString(board))
        return id
    }

    /** Every fileName any saved board still points at — fold this into [BoardRepository.pruneUnused]'s keep set so clearing a live tile can never delete audio a saved board still needs. */
    fun allReferencedFileNames(): Set<String> =
        savedBoardFiles()
            .mapNotNull(::decode)
            .flatMap { it.soundFileNames }
            .toSet()

    private fun savedBoardFiles(): List<File> =
        savedBoardsDir.listFiles { file -> file.extension == "json" }?.toList() ?: emptyList()

    private fun decode(file: File): Board? = runCatching { json.decodeFromString<Board>(file.readText()) }.getOrNull()
}
