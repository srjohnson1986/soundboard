package com.example.soundboard.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class FileSystemStoreTest : FileStoreContractTest() {
    private val roots = mutableListOf<File>()

    override fun newStore(): FileStore = FileSystemStore(createTempDirectory("filestore").toFile().also { roots += it })

    @AfterTest
    fun cleanUp() {
        roots.forEach { it.deleteRecursively() }
    }
}

class JavaZipCodecTest {
    private val codec = JavaZipCodec()

    @Test
    fun `entries survive a write then read`() {
        val entries = listOf(
            ZipEntryData("board.json", "{}".encodeToByteArray()),
            ZipEntryData("sounds/a.m4a", byteArrayOf(0, 1, 2, -1))
        )

        val read = codec.read(codec.write(entries))

        assertEquals(listOf("board.json", "sounds/a.m4a"), read.map { it.name })
        assertContentEquals(byteArrayOf(0, 1, 2, -1), read[1].bytes)
    }

    @Test
    fun `reads every built-in board shipped in presets`() {
        val presets = File("../presets").listFiles { file -> file.extension == "zip" }.orEmpty()
        check(presets.isNotEmpty()) { "expected zips in ${File("../presets").absolutePath}" }

        presets.forEach { zip ->
            val names = codec.read(zip.readBytes()).map { it.name }
            assertTrue("board.json" in names, "${zip.name} has no board.json")
        }
    }

    @Test
    fun `reads the golden backup`() = GoldenBackup.assertReadBy(codec)

    @Test
    fun `something that isn't a zip can't be read`() {
        assertFailsWith<IllegalStateException> { codec.read("not a zip".encodeToByteArray()) }
    }
}

/** The settings a device keeps (DevicePreferences) land in a SharedPreferences file that outlives the store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesStoreTest {
    private fun store() = SharedPreferencesStore(ApplicationProvider.getApplicationContext(), "test_prefs")

    @Test
    fun `values read back from a fresh store on the same file, and a missing one gives the default`() {
        store().putBoolean("on", true)
        store().putInt("count", 7)

        assertTrue(store().getBoolean("on", false))
        assertEquals(7, store().getInt("count", 0))
        assertEquals(3, store().getInt("missing", 3))
    }
}

/**
 * The Storage Access Framework side of picking and saving files. A picked file's extension
 * comes from its MIME type when the provider gives one, else from its display name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UriFilesTest {
    private val context: android.content.Context = ApplicationProvider.getApplicationContext()

    /** Serves two picks: one with a MIME type, one with only a display name. */
    class PickerProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = if (uri.lastPathSegment == "typed") "audio/mpeg" else null
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("Voice memo.m4a")) }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    @Test
    fun `a picked file's extension comes from its MIME type, else from its name`() = runTest {
        Robolectric.setupContentProvider(PickerProvider::class.java, "picker")
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("mp3", "audio/mpeg")

        assertEquals("mp3", UriPickedFile(context, Uri.parse("content://picker/typed")).extension())
        assertEquals("m4a", UriPickedFile(context, Uri.parse("content://picker/untyped")).extension())
    }

    @Test
    fun `a picked file reads its bytes, and one that can't be opened fails`() = runTest {
        val uri = Uri.parse("content://fake/clip.mp3")
        shadowOf(context.contentResolver).registerInputStream(uri, byteArrayOf(1, 2, 3).inputStream())

        assertContentEquals(byteArrayOf(1, 2, 3), UriPickedFile(context, uri).readBytes())
        assertFailsWith<Exception> { UriPickedFile(context, Uri.parse("content://fake/missing.mp3")).readBytes() }
    }

    @Test
    fun `a save target writes the bytes where the user chose`() = runTest {
        val out = File(context.cacheDir, "backup.zip")

        UriSaveTarget(context, Uri.fromFile(out)).write(byteArrayOf(4, 5))

        assertContentEquals(byteArrayOf(4, 5), out.readBytes())
    }

    @Test
    fun `a built-in board that isn't packaged isn't offered`() {
        assertFalse(AssetBundledBoards(context).has("not-packaged.zip"))
    }
}
