package com.example.soundboard.data

import android.content.Context
import com.example.soundboard.model.Board
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One saved preset's identity, without loading its full board content. */
data class SavedPreset(val id: String, val name: String, val savedAt: Long)

/**
 * A saved preset is just a [Board] snapshot, stored as its own small JSON file
 * under `filesDir/presets/` — deliberately not a zip, and deliberately not
 * carrying a copy of any audio. Tiles keep pointing at the same `sounds/`
 * files the live board already uses, so saving one costs a few KB rather than
 * a full copy of every recorded clip. That also means a saved preset can't
 * survive a reinstall or cleared app data on its own (that wipes `sounds/`
 * too) — [BoardRepository.exportTo] is the self-contained, portable mechanism
 * for that; this is same-device version history only.
 */
class PresetRepository(context: Context) {

    private val presetsDir = File(context.filesDir, "presets")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Every saved preset, newest first. Scans disk directly rather than keeping a separate index, so the list can never drift from what's actually there. */
    fun list(): List<SavedPreset> =
        presetFiles()
            .mapNotNull { file -> decode(file)?.let { board -> SavedPreset(file.nameWithoutExtension, board.name, file.lastModified()) } }
            .sortedByDescending { it.savedAt }

    fun load(id: String): Board? = decode(File(presetsDir, "$id.json"))

    /** Snapshots [board] as a brand-new saved preset and returns its id. */
    fun save(board: Board): String {
        presetsDir.mkdirs()
        val id = UUID.randomUUID().toString()
        File(presetsDir, "$id.json").writeText(json.encodeToString(board))
        return id
    }

    /** Every fileName any saved preset still points at — fold this into [BoardRepository.pruneUnused]'s keep set so clearing a live tile can never delete audio a saved preset still needs. */
    fun allReferencedFileNames(): Set<String> =
        presetFiles()
            .mapNotNull(::decode)
            .flatMap { board -> (board.pages.flatMap { it.tiles } + board.pinnedTiles).mapNotNull { it.fileName } }
            .toSet()

    private fun presetFiles(): List<File> =
        presetsDir.listFiles { file -> file.extension == "json" }?.toList() ?: emptyList()

    private fun decode(file: File): Board? = runCatching { json.decodeFromString<Board>(file.readText()) }.getOrNull()
}
