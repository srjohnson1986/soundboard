package com.example.soundboard.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.model.Board
import com.example.soundboard.model.Tile
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BoardRepositoryTest {

    private lateinit var context: android.content.Context
    private lateinit var repo: BoardRepository
    private lateinit var boardFile: File
    private lateinit var soundsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = BoardRepository(context)
        boardFile = File(context.filesDir, "board.json")
        soundsDir = File(context.filesDir, "sounds")
    }

    @Test
    fun `save then load round-trips including tile ids`() {
        val board = Board(
            rows = 2,
            columns = 2,
            tiles = listOf(
                Tile(id = "a", label = "Air horn", fileName = "a.mp3", volume = 0.5f, colorArgb = 0xFF0000),
                Tile(id = "b", label = "", fileName = null),
                Tile(id = "c", label = "Boo", fileName = "c.wav"),
                Tile(id = "d")
            )
        )

        repo.save(board)
        val loaded = repo.load()

        assertEquals(board, loaded)
    }

    @Test
    fun `loading with no board json present returns the default board`() {
        assertFalse(boardFile.exists())

        val loaded = repo.load()

        assertEquals(4, loaded.rows)
        assertEquals(4, loaded.columns)
        assertEquals(16, loaded.tiles.size)
    }

    @Test
    fun `loading truncated json returns the default rather than throwing`() {
        boardFile.parentFile?.mkdirs()
        boardFile.writeText("{\"rows\": 3, \"columns\"")

        val loaded = repo.load()

        assertEquals(4, loaded.rows)
        assertEquals(4, loaded.columns)
        assertEquals(16, loaded.tiles.size)
    }

    @Test
    fun `loading malformed json returns the default rather than throwing`() {
        boardFile.parentFile?.mkdirs()
        boardFile.writeText("not json at all")

        val loaded = repo.load()

        assertEquals(4, loaded.rows)
        assertEquals(4, loaded.columns)
        assertEquals(16, loaded.tiles.size)
    }

    @Test
    fun `a board saved by an older schema still loads`() {
        boardFile.parentFile?.mkdirs()
        // Extra unknown field at board level ("theme"), extra unknown field on a tile
        // ("isFavorite"), and a tile missing the newer "colorArgb" field entirely.
        boardFile.writeText(
            """
            {
              "rows": 1,
              "columns": 2,
              "theme": "dark",
              "tiles": [
                {"id": "x", "label": "Old", "fileName": "x.mp3", "volume": 1.0, "isFavorite": true},
                {"id": "y", "label": "", "fileName": null, "volume": 1.0}
              ]
            }
            """.trimIndent()
        )

        val loaded = repo.load()

        assertEquals(1, loaded.rows)
        assertEquals(2, loaded.columns)
        assertEquals(listOf("x", "y"), loaded.tiles.map { it.id })
        assertNull(loaded.tiles[1].colorArgb)
    }

    @Test
    fun `pruneUnused deletes exactly the files not in the keep set`() {
        soundsDir.mkdirs()
        val keep = File(soundsDir, "keep.mp3").apply { writeText("keep") }
        val drop = File(soundsDir, "drop.mp3").apply { writeText("drop") }

        repo.pruneUnused(setOf("keep.mp3"))

        assertTrue(keep.exists())
        assertFalse(drop.exists())
    }

    @Test
    fun `importSound copies bytes and returns a name that resolves through soundFile`() {
        val uri = Uri.parse("content://fake/audio.mp3")
        val bytes = "hello world".toByteArray()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))

        val name = repo.importSound(uri)

        assertTrue(name != null)
        val resolved = repo.soundFile(name!!)
        assertTrue(resolved.exists())
        assertEquals("hello world", resolved.readText())
    }

    @Test
    fun `importSound returns null when the uri cannot be opened`() {
        val uri = Uri.parse("content://fake/missing.mp3")

        val name = repo.importSound(uri)

        assertNull(name)
    }
}
