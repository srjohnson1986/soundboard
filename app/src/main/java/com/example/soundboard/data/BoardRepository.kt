package com.example.soundboard.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.soundboard.model.Board
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Board layout lives in board.json; audio lives in filesDir/sounds. Both are app-private. */
class BoardRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val boardFile = File(context.filesDir, "board.json")
    private val soundsDir = File(context.filesDir, "sounds")

    fun load(): Board =
        runCatching { json.decodeFromString<Board>(boardFile.readText()) }
            .getOrElse { Board() }

    fun save(board: Board) {
        runCatching { boardFile.writeText(json.encodeToString(board)) }
    }

    fun soundFile(name: String): File = File(soundsDir, name)

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
