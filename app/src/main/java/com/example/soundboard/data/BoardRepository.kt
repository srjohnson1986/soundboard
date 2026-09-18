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
import kotlinx.serialization.json.Json
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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val boardFile = File(context.filesDir, "board.json")
    private val soundsDir = File(context.filesDir, "sounds")

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
        migrateHomePageIndex(board, root)
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
    fun importSound(uri: Uri): String? = runCatching {
        soundsDir.mkdirs()
        val ext = extensionFor(uri)
        val name = UUID.randomUUID().toString() + if (ext.isBlank()) "" else ".$ext"
        val target = File(soundsDir, name)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        name
    }.getOrNull()

    /** Deletes any audio file no longer referenced by a tile. */
    fun pruneUnused(keep: Set<String>) {
        soundsDir.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    /** Zips board.json and every sound into [uri]. The whole app state in one file. */
    fun exportTo(uri: Uri): Boolean = runCatching {
        val out = context.contentResolver.openOutputStream(uri) ?: error("no output stream")
        out.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("board.json"))
                zip.write(boardFile.readBytes())
                zip.closeEntry()

                soundsDir.listFiles()?.forEach { file ->
                    zip.putNextEntry(ZipEntry("sounds/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }.isSuccess

    /** Restores board.json and sounds from a zip made by [exportTo], overwriting both. */
    fun importFrom(uri: Uri): Boolean = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error("no input stream")
        input.use(::importZip)
    }.isSuccess

    /**
     * Same as [importFrom] but reads a preset bundled as an app asset, e.g. the
     * fallback board shipped under `src/main/assets/`. Silently no-ops (returns
     * false) if [assetName] isn't present, so callers don't need to guard it.
     */
    fun importFromAsset(assetName: String): Boolean = runCatching {
        context.assets.open(assetName).use(::importZip)
    }.isSuccess

    private fun importZip(stream: InputStream) {
        soundsDir.mkdirs()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "board.json" -> boardFile.outputStream().use { zip.copyTo(it) }
                    entry.name.startsWith("sounds/") && !entry.isDirectory -> {
                        val target = File(soundsDir, entry.name.removePrefix("sounds/"))
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
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
}

/** Shape of board.json before pages existed; only used to migrate on [BoardRepository.load]. */
@Serializable
private data class LegacyBoard(
    val name: String = "New Board",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = emptyList()
)
