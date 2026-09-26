package com.example.soundboard.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Board layout lives in board.json; audio lives in filesDir/sounds. Both are app-private. */
class BoardRepository(private val context: Context) {

    private val json = BoardJson

    private val boardFile = File(context.filesDir, "board.json")
    private val soundsDir = File(context.filesDir, "sounds")
    private val backgroundsDir = File(context.filesDir, "backgrounds")

    /**
     * Boards saved before pages existed are a flat name/rows/columns/tiles
     * object with no "pages" key. `ignoreUnknownKeys` can't help here — decoding
     * that JSON straight as [Board] would silently succeed with an empty
     * default board instead of failing, since `pages` has a default too. So an
     * old board must be detected explicitly and migrated into a single page.
     */
    fun load(): Board = runCatching {
        val text = boardFile.readText()
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
    private fun sanitizeMissingSounds(board: Board): Board {
        fun fix(tile: Tile): Tile {
            val fileName = tile.fileName ?: return tile
            return if (soundFile(fileName).exists()) tile else tile.copy(fileName = null)
        }
        return board.copy(
            pages = board.pages.map { page -> page.copy(tiles = page.tiles.map(::fix)) }
        )
    }

    /** True once a board has ever been saved — false only on a fresh install. */
    fun hasSavedBoard(): Boolean = boardFile.exists()

    fun save(board: Board) {
        runCatching { boardFile.writeText(json.encodeToString(board)) }
    }

    fun soundFile(name: String): File = File(soundsDir, name)

    /**
     * Allocates a fresh, not-yet-written file for a new recording, in the same
     * directory imported sounds live in. The recorder writes straight into it —
     * no separate copy step, unlike [importSound]'s content-resolver read.
     */
    fun newRecordingFile(): File {
        soundsDir.mkdirs()
        return File(soundsDir, "${UUID.randomUUID()}.m4a")
    }

    /**
     * Copies the picked audio into app storage and returns its local file name.
     * Copying is what lets us skip storage permissions and survive the source
     * file being moved, renamed, or deleted later.
     */
    fun importSound(uri: Uri): String? = copyIntoAppStorage(uri, soundsDir)

    /** Every sound file not in [keep], without deleting anything — the "what's unused" query behind [pruneUnused] and the stray-clip cleanup UI. */
    fun strayFiles(keep: Set<String>): List<File> =
        soundsDir.listFiles()?.filter { it.name !in keep } ?: emptyList()

    /** Deletes any audio file no longer referenced by a tile. */
    fun pruneUnused(keep: Set<String>) {
        strayFiles(keep).forEach { it.delete() }
    }

    fun backgroundFile(name: String): File = File(backgroundsDir, name)

    /** Copies the picked image into app storage and returns its local file name — same reasoning as [importSound]. */
    fun importBackgroundImage(uri: Uri): String? = copyIntoAppStorage(uri, backgroundsDir)

    /** Copies [uri]'s content into [dir] under a fresh random name, keeping its extension; returns that name, or null on failure. */
    private fun copyIntoAppStorage(uri: Uri, dir: File): String? = runCatching {
        dir.mkdirs()
        val ext = extensionFor(uri)
        val name = UUID.randomUUID().toString() + if (ext.isBlank()) "" else ".$ext"
        context.contentResolver.openInputStream(uri)!!.use { input ->
            File(dir, name).outputStream().use { output -> input.copyTo(output) }
        }
        name
    }.getOrNull()

    /** Deletes exactly the named sound files, if present. */
    fun deleteFiles(fileNames: Collection<String>) {
        fileNames.forEach { soundFile(it).delete() }
    }

    /** Zips exactly the named sound files, flat by filename, for the user to keep before deleting them. Not a re-importable backup — no board.json, no "sounds/" prefix. */
    fun exportFiles(fileNames: Collection<String>, uri: Uri): Boolean = runCatching {
        val out = context.contentResolver.openOutputStream(uri) ?: error("no output stream")
        out.use { stream ->
            ZipOutputStream(stream).use { zip ->
                fileNames.map(::soundFile).filter { it.exists() }.forEach { zip.putFile(it.name, it) }
            }
        }
    }.isSuccess

    /** Zips board.json and every sound into [uri]. The whole app state in one file. */
    fun exportTo(uri: Uri): Boolean = runCatching {
        val out = context.contentResolver.openOutputStream(uri) ?: error("no output stream")
        out.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("board.json"))
                zip.write(boardFile.readBytes())
                zip.closeEntry()

                soundsDir.listFiles()?.forEach { zip.putFile("$ZIP_SOUNDS_DIR${it.name}", it) }
                backgroundsDir.listFiles()?.forEach { zip.putFile("$ZIP_BACKGROUNDS_DIR${it.name}", it) }
            }
        }
    }.isSuccess

    /** Restores board.json and sounds from a zip made by [exportTo], overwriting both. */
    fun importFrom(uri: Uri): Boolean = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error("no input stream")
        input.use(::importZip)
    }.isSuccess

    /**
     * Same as [importFrom] but reads a built-in board bundled as an app asset, e.g. the
     * fallback board shipped under `src/main/assets/`. Silently no-ops (returns
     * false) if [assetName] isn't present, so callers don't need to guard it.
     */
    fun importFromAsset(assetName: String): Boolean = runCatching {
        context.assets.open(assetName).use(::importZip)
    }.isSuccess

    private fun importZip(stream: InputStream) {
        soundsDir.mkdirs()
        backgroundsDir.mkdirs()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "board.json" -> boardFile.outputStream().use { zip.copyTo(it) }
                    entry.name.startsWith(ZIP_SOUNDS_DIR) && !entry.isDirectory -> {
                        val target = File(soundsDir, entry.name.removePrefix(ZIP_SOUNDS_DIR))
                        target.outputStream().use { zip.copyTo(it) }
                    }
                    entry.name.startsWith(ZIP_BACKGROUNDS_DIR) && !entry.isDirectory -> {
                        val target = File(backgroundsDir, entry.name.removePrefix(ZIP_BACKGROUNDS_DIR))
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** Whether [assetName] is packaged in this build — some built-in boards only ship in debug builds. */
    fun hasAsset(assetName: String): Boolean = runCatching { context.assets.open(assetName).close() }.isSuccess

    private fun ZipOutputStream.putFile(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun extensionFor(uri: Uri): String {
        context.contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.let { return it }

        val displayName = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

        return displayName?.substringAfterLast('.', "").orEmpty()
    }

    private companion object {
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
