package com.example.soundboard.data

/**
 * The platform services the repositories need, each small enough to implement twice:
 * once for Android (filesDir, java.util.zip, the APK's assets, SharedPreferences, content
 * URIs) and once for the web. Nothing in this module talks to a platform any other way.
 */

/** One file in a [FileStore] directory. */
data class StoredFileInfo(val name: String, val sizeBytes: Long, val lastModifiedMillis: Long)

/**
 * The app's private storage: files addressed by a path relative to its root, such as
 * "board.json" or "sounds/abc.m4a". Directories are implicit — writing a file creates its
 * directory. Android backs this with `filesDir`.
 */
interface FileStore {
    suspend fun exists(path: String): Boolean

    /** The file's content, or null if there's no such file. */
    suspend fun read(path: String): ByteArray?

    /** Replaces the file's content with [bytes], creating it if needed; throws if it can't. */
    suspend fun write(path: String, bytes: ByteArray)

    /** Deletes the file; does nothing if it isn't there. */
    suspend fun delete(path: String)

    /** The files directly inside [directory]; empty if it doesn't exist. */
    suspend fun list(directory: String): List<StoredFileInfo>
}

suspend fun FileStore.readText(path: String): String? = read(path)?.decodeToString()

suspend fun FileStore.writeText(path: String, text: String) = write(path, text.encodeToByteArray())

/** One file inside a zip archive: its path within the archive, and its content. */
class ZipEntryData(val name: String, val bytes: ByteArray)

/** Reads and writes zip archives — the backup format, and how built-in boards ship. */
interface ZipCodec {
    /** Every file in [zip], directories left out; throws if [zip] isn't a readable archive. */
    fun read(zip: ByteArray): List<ZipEntryData>

    fun write(entries: List<ZipEntryData>): ByteArray
}

/** A file the user picked to bring into the app, such as a sound or a backup. */
interface PickedFile {
    /** Its extension without the dot, or "" if unknown. The app's own copy keeps it. */
    suspend fun extension(): String

    /** Its whole content; throws if it can't be read. */
    suspend fun readBytes(): ByteArray
}

/** Somewhere the user picked to save a file to, such as an exported backup. */
fun interface SaveTarget {
    /** Writes [bytes] as the file's whole content; throws if it can't. */
    suspend fun write(bytes: ByteArray)
}

/** The built-in boards packaged with this build, each a backup-format zip (see presets/README.md). */
interface BundledBoards {
    /** Whether [name] is packaged in this build — some only ship in debug builds. */
    fun has(name: String): Boolean

    /** [name]'s zip; throws if it isn't packaged. */
    suspend fun read(name: String): ByteArray
}

/** Small, device-local settings storage (see [DevicePreferences]). */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
}
