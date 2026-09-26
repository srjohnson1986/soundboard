package com.example.soundboard.data

import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Board layout lives in board.json; audio lives in sounds/. Both are app-private. */
class BoardRepository(
    private val files: FileStore,
    private val zip: ZipCodec,
    private val bundled: BundledBoards
) {

    private val json = BoardJson

    /**
     * Boards saved before pages existed are a flat name/rows/columns/tiles
     * object with no "pages" key. `ignoreUnknownKeys` can't help here — decoding
     * that JSON straight as [Board] would silently succeed with an empty
     * default board instead of failing, since `pages` has a default too. So an
     * old board must be detected explicitly and migrated into a single page.
     */
    suspend fun load(): Board = runCatching {
        val text = files.readText(BOARD_FILE) ?: error("no board.json")
        val root = json.parseToJsonElement(text).jsonObject
        val board = if ("pages" in root) {
            json.decodeFromString<Board>(text)
        } else {
            val legacy = json.decodeFromJsonElement<LegacyBoard>(root)
            Board(
                name = legacy.name,
                pages = listOf(Page(rows = legacy.rows, columns = legacy.columns, tiles = legacy.tiles))
            )
        }
        val sanitized = sanitizeMissingSounds(migrateHomePageIndex(board, root))
        sanitized.normalized()
    }.getOrElse { Board() }

    /**
     * `Page.isHome` replaced a single board-level `homePageIndex: Int?`.
     * `ignoreUnknownKeys` only covers fields that were added, not one that
     * moved — a board saved before this change would silently lose its home
     * page on load without this: read the old top-level key straight from the
     * raw JSON and translate it onto the matching page.
     */
    private fun migrateHomePageIndex(board: Board, root: JsonObject): Board {
        if (board.pages.any { it.isHome }) return board
        val index = (root["homePageIndex"] as? JsonPrimitive)?.intOrNull ?: return board
        return board.withHomePage(index)
    }

    /**
     * A tile's `fileName` can point at audio that was never actually copied in —
     * a generic built-in board shipped with only some (or none) of its clips recorded
     * yet, or a backup zip missing a file for some other reason. Rather than
     * rendering that tile as "filled" with a sound that silently does nothing
     * when tapped, drop back to `fileName = null`: same tile, same label, now
     * honestly reported as still needing a recording. Runs on every load, not
     * just right after an import, so it also self-heals a board whose sound
     * file went missing some other way.
     */
    private suspend fun sanitizeMissingSounds(board: Board): Board {
        val present = files.list(SOUNDS_DIR).map { it.name }.toSet()
        fun fix(tile: Tile): Tile {
            val fileName = tile.fileName ?: return tile
            return if (fileName in present) tile else tile.copy(fileName = null)
        }
        return board.copy(
            pages = board.pages.map { page -> page.copy(tiles = page.tiles.map(::fix)) }
        )
    }

    /** True once a board has ever been saved — false only on a fresh install. */
    suspend fun hasSavedBoard(): Boolean = files.exists(BOARD_FILE)

    suspend fun save(board: Board) {
        runCatching { files.writeText(BOARD_FILE, json.encodeToString(board)) }
    }

    /** Where sound [name] lives in the [FileStore], for the player to load it from. */
    fun soundPath(name: String): String = "$SOUNDS_DIR/$name"

    /**
     * Allocates a fresh, not-yet-written path for a new recording, in the same
     * directory imported sounds live in. The recorder writes straight into it —
     * no separate copy step, unlike [importSound].
     */
    fun newRecordingPath(extension: String = "m4a"): String = soundPath("${Uuid.random()}.$extension")

    /** Deletes a recording [newRecordingPath] handed out, e.g. one that was abandoned. */
    suspend fun deleteRecording(path: String) = files.delete(path)

    /**
     * Copies the picked audio into app storage and returns its local file name.
     * Copying is what lets us skip storage permissions and survive the source
     * file being moved, renamed, or deleted later.
     */
    suspend fun importSound(file: PickedFile): String? = copyIntoAppStorage(file, SOUNDS_DIR)

    /** Every sound file not in [keep], without deleting anything — the "what's unused" query behind [pruneUnused] and the stray-clip cleanup UI. */
    suspend fun strayFiles(keep: Set<String>): List<StoredFileInfo> =
        files.list(SOUNDS_DIR).filter { it.name !in keep }

    /** Deletes any audio file no longer referenced by a tile. */
    suspend fun pruneUnused(keep: Set<String>) {
        strayFiles(keep).forEach { files.delete(soundPath(it.name)) }
    }

    /** Background image [name]'s content, for the UI to decode; null if it's gone. */
    suspend fun readBackground(name: String): ByteArray? = files.read("$BACKGROUNDS_DIR/$name")

    suspend fun deleteBackground(name: String) = files.delete("$BACKGROUNDS_DIR/$name")

    /** Copies the picked image into app storage and returns its local file name — same reasoning as [importSound]. */
    suspend fun importBackgroundImage(file: PickedFile): String? = copyIntoAppStorage(file, BACKGROUNDS_DIR)

    /** Copies [file]'s content into [dir] under a fresh random name, keeping its extension; returns that name, or null on failure. */
    private suspend fun copyIntoAppStorage(file: PickedFile, dir: String): String? = runCatching {
        val ext = file.extension()
        val name = Uuid.random().toString() + if (ext.isBlank()) "" else ".$ext"
        files.write("$dir/$name", file.readBytes())
        name
    }.getOrNull()

    /** Deletes exactly the named sound files, if present. */
    suspend fun deleteFiles(fileNames: Collection<String>) {
        fileNames.forEach { files.delete(soundPath(it)) }
    }

    /** Zips exactly the named sound files, flat by filename, for the user to keep before deleting them. Not a re-importable backup — no board.json, no "sounds/" prefix. */
    suspend fun exportFiles(fileNames: Collection<String>, target: SaveTarget): Boolean = runCatching {
        val entries = fileNames.mapNotNull { name -> files.read(soundPath(name))?.let { ZipEntryData(name, it) } }
        target.write(zip.write(entries))
    }.isSuccess

    /** Zips board.json and every sound into [target]. The whole app state in one file. */
    suspend fun exportTo(target: SaveTarget): Boolean = runCatching {
        val entries = buildList {
            add(ZipEntryData(BOARD_FILE, files.read(BOARD_FILE) ?: error("no board.json")))
            addDirectory(SOUNDS_DIR, ZIP_SOUNDS_DIR)
            addDirectory(BACKGROUNDS_DIR, ZIP_BACKGROUNDS_DIR)
        }
        target.write(zip.write(entries))
    }.isSuccess

    private suspend fun MutableList<ZipEntryData>.addDirectory(dir: String, zipPrefix: String) {
        files.list(dir).forEach { info ->
            files.read("$dir/${info.name}")?.let { add(ZipEntryData("$zipPrefix${info.name}", it)) }
        }
    }

    /** Restores board.json and sounds from a zip made by [exportTo], overwriting both. */
    suspend fun importFrom(file: PickedFile): Boolean = runCatching {
        importZip(file.readBytes())
    }.isSuccess

    /**
     * Same as [importFrom] but reads a built-in board bundled with the app, e.g. the
     * fallback board. Silently no-ops (returns false) if [assetName] isn't present, so
     * callers don't need to guard it.
     */
    suspend fun importFromAsset(assetName: String): Boolean = runCatching {
        importZip(bundled.read(assetName))
    }.isSuccess

    /** Whether [assetName] is packaged in this build — some built-in boards only ship in debug builds. */
    fun hasAsset(assetName: String): Boolean = bundled.has(assetName)

    private suspend fun importZip(bytes: ByteArray) {
        for (entry in zip.read(bytes)) {
            when {
                entry.name == BOARD_FILE -> files.write(BOARD_FILE, entry.bytes)
                entry.name.startsWith(ZIP_SOUNDS_DIR) ->
                    entry.fileNameAfter(ZIP_SOUNDS_DIR)?.let { files.write(soundPath(it), entry.bytes) }
                entry.name.startsWith(ZIP_BACKGROUNDS_DIR) ->
                    entry.fileNameAfter(ZIP_BACKGROUNDS_DIR)?.let { files.write("$BACKGROUNDS_DIR/$it", entry.bytes) }
            }
        }
    }

    /** The plain file name after [prefix]; null for anything that would land outside that one folder. */
    private fun ZipEntryData.fileNameAfter(prefix: String): String? =
        name.removePrefix(prefix).takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && it != ".." }

    private companion object {
        const val BOARD_FILE = "board.json"
        const val SOUNDS_DIR = "sounds"
        const val BACKGROUNDS_DIR = "backgrounds"

        /** Folder prefixes inside a backup zip. "background/" is singular unlike the on-device "backgrounds" dir; kept as-is so existing backups and built-in boards still import. */
        const val ZIP_SOUNDS_DIR = "sounds/"
        const val ZIP_BACKGROUNDS_DIR = "background/"
    }
}

/** Shape of board.json before pages existed; only used to migrate on [BoardRepository.load]. */
@Serializable
private data class LegacyBoard(
    val name: String = "New Board",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = emptyList()
)
