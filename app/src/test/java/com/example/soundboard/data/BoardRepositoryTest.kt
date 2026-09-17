package com.example.soundboard.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
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
            pages = listOf(
                Page(
                    rows = 2,
                    columns = 2,
                    tiles = listOf(
                        Tile(id = "a", label = "Air horn", fileName = "a.mp3", volume = 0.5f, colorArgb = 0xFF0000),
                        Tile(id = "b", label = "", fileName = null),
                        Tile(id = "c", label = "Boo", fileName = "c.wav"),
                        Tile(id = "d")
                    )
                )
            )
        )

        repo.save(board)
        val loaded = repo.load()

        assertEquals(board, loaded)
    }

    @Test
    fun `save then load round-trips multiple pages and the current page index`() {
        val board = Board(
            pages = listOf(Page(name = "Requests"), Page(name = "Feelings")),
            currentPageIndex = 1
        )

        repo.save(board)
        val loaded = repo.load()

        assertEquals(board, loaded)
    }

    @Test
    fun `save then load round-trips pinned tiles, home page, page color and tile aspect ratio`() {
        val board = Board(
            pages = listOf(
                Page(name = "Trouble", color = 0xFFB74D, tileAspectRatio = 4f / 3f),
                Page(name = "Needs")
            ),
            homePageIndex = 1,
            pinnedTiles = listOf(
                Tile(id = "hey", label = "Hey", fileName = "hey.mp3", colorArgb = 0xE57373)
            )
        )

        repo.save(board)
        val loaded = repo.load()

        assertEquals(board, loaded)
    }

    @Test
    fun `a board saved by today's schema still loads when it predates pinned tiles, home page and page color`() {
        // What repo.save() itself would have written before pinnedTiles/homePageIndex/
        // Page.color/Page.tileAspectRatio existed: "pages" is present (so no legacy-shape
        // migration kicks in), just missing the newer fields entirely.
        boardFile.parentFile?.mkdirs()
        boardFile.writeText(
            """
            {
              "name": "New Board",
              "pages": [
                {"id": "p1", "name": "Page 1", "rows": 4, "columns": 4, "tiles": []}
              ],
              "currentPageIndex": 0
            }
            """.trimIndent()
        )

        val loaded = repo.load()

        assertEquals(emptyList<Tile>(), loaded.pinnedTiles)
        assertEquals(null, loaded.homePageIndex)
        assertEquals(null, loaded.pages[0].color)
        assertEquals(1f, loaded.pages[0].tileAspectRatio)
    }

    @Test
    fun `loading with no board json present returns the default board`() {
        assertFalse(boardFile.exists())

        val loaded = repo.load()

        assertEquals(4, loaded.currentPage.rows)
        assertEquals(4, loaded.currentPage.columns)
        assertEquals(16, loaded.currentPage.tiles.size)
    }

    @Test
    fun `loading truncated json returns the default rather than throwing`() {
        boardFile.parentFile?.mkdirs()
        boardFile.writeText("{\"rows\": 3, \"columns\"")

        val loaded = repo.load()

        assertEquals(4, loaded.currentPage.rows)
        assertEquals(4, loaded.currentPage.columns)
        assertEquals(16, loaded.currentPage.tiles.size)
    }

    @Test
    fun `loading malformed json returns the default rather than throwing`() {
        boardFile.parentFile?.mkdirs()
        boardFile.writeText("not json at all")

        val loaded = repo.load()

        assertEquals(4, loaded.currentPage.rows)
        assertEquals(4, loaded.currentPage.columns)
        assertEquals(16, loaded.currentPage.tiles.size)
    }

    @Test
    fun `a board saved by an older schema still loads`() {
        boardFile.parentFile?.mkdirs()
        // Extra unknown field at board level ("theme"), extra unknown field on a tile
        // ("isFavorite"), a tile missing the newer "colorArgb" field, and no "pages"
        // key at all — the flat shape used before pages existed.
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

        assertEquals(1, loaded.pages.size)
        assertEquals(0, loaded.currentPageIndex)
        assertEquals(1, loaded.currentPage.rows)
        assertEquals(2, loaded.currentPage.columns)
        assertEquals(listOf("x", "y"), loaded.currentPage.tiles.map { it.id })
        assertNull(loaded.currentPage.tiles[1].colorArgb)
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

    @Test
    fun `the bundled steve-care-board preset imports and loads as a valid four-page board`() {
        // Regression coverage for the actual shipped preset (presets/steve-care-board.zip,
        // mirrored at app/src/main/assets/steve-care-board.zip) — guards against the zip
        // and the app's Board schema drifting apart silently.
        val imported = repo.importFromAsset("steve-care-board.zip")

        assertTrue(imported)
        val board = repo.load()

        assertEquals(listOf("Trouble", "Needs", "Talking", "Well Wishes"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertEquals(4, board.pinnedTiles.size)
        board.pages.forEach { page ->
            assertEquals(24, page.tiles.size)
            assertEquals(6, page.rows)
            assertEquals(4, page.columns)
        }
        // Every referenced sound file actually landed in app storage.
        val allTiles = board.pages.flatMap { it.tiles } + board.pinnedTiles
        allTiles.mapNotNull { it.fileName }.forEach { name ->
            assertTrue("missing sound file $name", repo.soundFile(name).exists())
        }
    }
}
