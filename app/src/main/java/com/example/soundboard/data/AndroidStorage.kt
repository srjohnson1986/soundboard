package com.example.soundboard.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.edit

// Android's side of the storage interfaces in the shared module (Storage.kt), plus
// factory functions that wire the shared repositories to them. The factories are named
// after the classes so callers keep writing `BoardRepository(context)`.

/** The app's private files directory, as a [FileStore]. */
fun appFileStore(context: Context): FileSystemStore = FileSystemStore(context.filesDir)

fun BoardRepository(context: Context): BoardRepository =
    BoardRepository(appFileStore(context), JavaZipCodec(), AssetBundledBoards(context))

fun SavedBoardRepository(context: Context): SavedBoardRepository = SavedBoardRepository(appFileStore(context))

fun RecentBoardsRepository(context: Context): RecentBoardsRepository = RecentBoardsRepository(appFileStore(context))

fun DevicePreferences(context: Context): DevicePreferences =
    DevicePreferences(SharedPreferencesStore(context, DevicePreferences.PREFS_NAME))

/** Built-in boards packaged as APK assets (`src/main/assets/`, plus `src/debug/assets/` in debug builds). */
class AssetBundledBoards(private val context: Context) : BundledBoards {
    override fun has(name: String): Boolean = runCatching { context.assets.open(name).close() }.isSuccess

    override suspend fun read(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }
}

/** A file picked through the Storage Access Framework, read through the content resolver. */
class UriPickedFile(private val context: Context, private val uri: Uri) : PickedFile {

    override suspend fun extension(): String {
        val resolver = context.contentResolver
        resolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.let { return it }

        val displayName = resolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

        return displayName?.substringAfterLast('.', "").orEmpty()
    }

    override suspend fun readBytes(): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("can't open $uri")
}

/** A save location picked through the Storage Access Framework (`CreateDocument`). */
class UriSaveTarget(private val context: Context, private val uri: Uri) : SaveTarget {
    override suspend fun write(bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("can't open $uri")
    }
}

/** [KeyValueStore] on a SharedPreferences file. */
class SharedPreferencesStore(context: Context, name: String) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
    override fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }
}
