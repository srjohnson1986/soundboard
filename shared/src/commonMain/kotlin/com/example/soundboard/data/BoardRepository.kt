package com.example.soundboard.data

import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
        val sanitized = sanitizeMissingIcons(sanitizeMissingSounds(migrateHomePageIndex(board, root)))
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

    /**
     * Like [sanitizeMissingSounds], for tile pictures: one whose file is gone (a backup
     * without it, cleared storage) is dropped so the tile falls back to its emoji or name
     * instead of drawing nothing.
     */
    private suspend fun sanitizeMissingIcons(board: Board): Board {
        if (board.iconFileNames.isEmpty()) return board
        val present = files.list(ICONS_DIR).map { it.name }.toSet()
        fun fix(tile: Tile): Tile {
            val name = tile.imageFileName ?: return tile
            return if (name in present) tile else tile.copy(imageFileName = null)
        }
        return board.copy(pages = board.pages.map { page -> page.copy(tiles = page.tiles.map(::fix)) })
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

    /** Tile picture [name]'s content, for the UI to decode; null if it's gone. */
    suspend fun readIcon(name: String): ByteArray? = files.read("$ICONS_DIR/$name")

    /** Copies the picked picture into app storage and returns its local file name — same reasoning as [importSound]. */
    suspend fun importIcon(file: PickedFile): String? = copyIntoAppStorage(file, ICONS_DIR)

    /** Deletes exactly the named tile pictures, if present. */
    suspend fun deleteIcons(names: Collection<String>) {
        names.forEach { files.delete("$ICONS_DIR/$it") }
    }

    /** Deletes any tile picture not in [keep]. */
    suspend fun pruneUnusedIcons(keep: Set<String>) {
        files.list(ICONS_DIR).filter { it.name !in keep }.forEach { files.delete("$ICONS_DIR/${it.name}") }
    }

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

    /**
     * Zips board.json and every sound into [target]. The whole app state in one file.
     *
     * Inside the zip each sound is named after the tile that uses it (`sounds/Yes.m4a`, see
     * [ClipNamer]) and the zip's copy of board.json says so, so a backup makes sense outside the
     * app. Only the zip is renamed: the files on the device keep their IDs. Clips no tile on the
     * board uses keep their ID names.
     */
    suspend fun exportTo(target: SaveTarget): Boolean = runCatching {
        val boardBytes = files.read(BOARD_FILE) ?: error("no board.json")
        val sounds = files.list(SOUNDS_DIR).map { it.name }
        val renames = readableSoundNames(sounds)
        // If the rewrite can't be done, nothing is renamed: the ID names and the board still agree.
        val rewrittenBoard = if (renames.isEmpty()) null else rewriteFileNames(boardBytes, renames)
        val names = if (rewrittenBoard == null) emptyMap() else renames
        val entries = buildList {
            add(ZipEntryData(BOARD_FILE, rewrittenBoard ?: boardBytes))
            sounds.forEach { name ->
                files.read(soundPath(name))?.let { add(ZipEntryData("$ZIP_SOUNDS_DIR${names[name] ?: name}", it)) }
            }
            addDirectory(BACKGROUNDS_DIR, ZIP_BACKGROUNDS_DIR)
            addDirectory(ICONS_DIR, ZIP_ICONS_DIR)
        }
        target.write(zip.write(entries))
    }.isSuccess

    /**
     * A readable name for each of [sounds] (the names in `sounds/`) that a tile on the board
     * uses, keyed by its stored name. A clip several tiles share gets the first tile's name.
     * The other clips keep their names, which the readable ones steer clear of.
     */
    private suspend fun readableSoundNames(sounds: List<String>): Map<String, String> {
        val board = load()
        val present = sounds.toSet()
        val namer = ClipNamer(taken = present - board.soundFileNames)
        val renames = mutableMapOf<String, String>()
        for (page in board.pages) {
            for (tile in page.tiles) {
                val stored = tile.fileName ?: continue
                if (stored in present && stored !in renames) renames[stored] = namer.nameFor(tile, page.name, stored)
            }
        }
        return renames
    }

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
        // Remember where the board came from, for restoring a tile's original sound. Through
        // load(), so an older board.json shape is migrated first.
        save(load().copy(builtInSource = assetName))
    }.isSuccess

    /**
     * The first tile on built-in board [assetName] named [label] (ignoring case and surrounding
     * spaces) that plays or speaks something, with its sound file's content when it has one; null
     * if there's no such tile, or the board isn't packaged. The tile's own `fileName` is the one
     * inside the built-in board, not a file in app storage.
     */
    suspend fun originalTile(assetName: String, label: String): OriginalTile? = runCatching {
        val entries = zip.read(bundled.read(assetName)).associate { it.name to it.bytes }
        val board = json.decodeFromString<Board>(entries.getValue(BOARD_FILE).decodeToString())
        val wanted = label.trim()
        val tile = board.pages.asSequence().flatMap { it.tiles }
            .firstOrNull { it.label.trim().equals(wanted, ignoreCase = true) && it.hasSound }
        val sound = tile?.fileName?.let { entries["$ZIP_SOUNDS_DIR$it"] }
        when {
            tile == null -> null
            sound != null -> OriginalTile(tile, sound)
            // Its clip is missing from the built-in board: only its speech is left to restore.
            tile.speakWhenNoSound -> OriginalTile(tile.copy(fileName = null), null)
            else -> null
        }
    }.getOrNull()

    /** Copies [bytes] into app storage as a new sound file with [extension]; returns its name, or null on failure. */
    suspend fun importSoundBytes(bytes: ByteArray, extension: String): String? =
        copyIntoAppStorage(BytesFile(bytes, extension), SOUNDS_DIR)

    private class BytesFile(private val bytes: ByteArray, private val extension: String) : PickedFile {
        override suspend fun extension(): String = extension
        override suspend fun readBytes(): ByteArray = bytes
    }

    /** Whether [assetName] is packaged in this build — some built-in boards only ship in debug builds. */
    fun hasAsset(assetName: String): Boolean = bundled.has(assetName)

    /**
     * Writes a zip's board and files into app storage. Every sound gets a fresh ID name, and the
     * board's `fileName` values are rewritten to match: saved boards share `sounds/`, so a file
     * that kept the zip's name (`Yes.m4a`) could replace one a saved board still plays. board.json
     * goes in last, after every file it names.
     */
    private suspend fun importZip(bytes: ByteArray) {
        val renames = mutableMapOf<String, String>()
        var boardBytes: ByteArray? = null
        for (entry in zip.read(bytes)) {
            when {
                entry.name == BOARD_FILE -> boardBytes = entry.bytes
                entry.name.startsWith(ZIP_SOUNDS_DIR) -> entry.fileNameAfter(ZIP_SOUNDS_DIR)?.let { name ->
                    val fresh = freshSoundName(name)
                    files.write(soundPath(fresh), entry.bytes)
                    renames[name] = fresh
                }
                entry.name.startsWith(ZIP_BACKGROUNDS_DIR) ->
                    entry.fileNameAfter(ZIP_BACKGROUNDS_DIR)?.let { files.write("$BACKGROUNDS_DIR/$it", entry.bytes) }
                entry.name.startsWith(ZIP_ICONS_DIR) ->
                    entry.fileNameAfter(ZIP_ICONS_DIR)?.let { files.write("$ICONS_DIR/$it", entry.bytes) }
            }
        }
        boardBytes?.let { files.write(BOARD_FILE, if (renames.isEmpty()) it else rewriteFileNames(it, renames) ?: it) }
    }

    /** A new random name for a sound that was called [original], with its extension, that no file in `sounds/` has. */
    private suspend fun freshSoundName(original: String): String {
        val extension = ClipNames.extensionOf(original)
        while (true) {
            val name = "${Uuid.random()}$extension"
            if (!files.exists(soundPath(name))) return name
        }
    }

    /**
     * [boardJson] with every `fileName` value that's a key of [renames] replaced by its entry; null
     * if it isn't JSON. It walks the whole tree for that key rather than decoding a [Board], so
     * boards from before pages existed work too, and nothing else in the file changes.
     */
    private fun rewriteFileNames(boardJson: ByteArray, renames: Map<String, String>): ByteArray? = runCatching {
        fun JsonElement.rewritten(): JsonElement = when (this) {
            is JsonObject -> JsonObject(mapValues { (key, value) ->
                val renamed = (value as? JsonPrimitive)?.takeIf { key == "fileName" && it.isString }?.let { renames[it.content] }
                if (renamed != null) JsonPrimitive(renamed) else value.rewritten()
            })
            is JsonArray -> JsonArray(map { it.rewritten() })
            else -> this
        }
        json.encodeToString(JsonElement.serializer(), json.parseToJsonElement(boardJson.decodeToString()).rewritten()).encodeToByteArray()
    }.getOrNull()

    /** The plain file name after [prefix]; null for anything that would land outside that one folder. */
    private fun ZipEntryData.fileNameAfter(prefix: String): String? =
        name.removePrefix(prefix).takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && it != ".." }

    private companion object {
        const val BOARD_FILE = "board.json"
        const val SOUNDS_DIR = "sounds"
        const val BACKGROUNDS_DIR = "backgrounds"
        const val ICONS_DIR = "icons"

        /** Folder prefixes inside a backup zip. "background/" is singular unlike the on-device "backgrounds" dir; kept as-is so existing backups and built-in boards still import. */
        const val ZIP_SOUNDS_DIR = "sounds/"
        const val ZIP_BACKGROUNDS_DIR = "background/"
        const val ZIP_ICONS_DIR = "icons/"
    }
}

/** A tile as a built-in board has it, and its sound file's content if it has one; see [BoardRepository.originalTile]. */
class OriginalTile(val tile: Tile, val sound: ByteArray?)

/** Shape of board.json before pages existed; only used to migrate on [BoardRepository.load]. */
@Serializable
private data class LegacyBoard(
    val name: String = "New Board",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = emptyList()
)
