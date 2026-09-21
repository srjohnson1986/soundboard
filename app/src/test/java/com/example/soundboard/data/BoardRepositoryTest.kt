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
        // load() sanitizes a fileName with no backing file (see the sanitize tests
        // below), so a tile expected to round-trip as filled needs a real file.
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        repo.soundFile("c.wav").apply { parentFile?.mkdirs() }.writeText("c")
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
    fun `save then load round-trips sticky home row, home page, page color and tile aspect ratio`() {
        repo.soundFile("hey.mp3").apply { parentFile?.mkdirs() }.writeText("hey")
        val board = Board(
            pages = listOf(
                Page(name = "Trouble", color = 0xFFB74D, tileAspectRatio = 4f / 3f),
                Page(
                    name = "Needs",
                    isHome = true,
                    tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3", colorArgb = 0xE57373))
                )
            ),
            stickyHomeRowEnabled = true
        )

        repo.save(board)
        val loaded = repo.load()

        assertEquals(board, loaded)
        assertEquals(1, loaded.homePageIndex)
        assertTrue(loaded.stickyHomeRowEnabled)
    }

    @Test
    fun `a board saved before isHome moved onto Page still loads with its home page intact`() {
        // What repo.save() would have written when home page was a single board-level
        // "homePageIndex" rather than per-page "isHome" — ignoreUnknownKeys silently
        // drops that stray key, so without an explicit migration this board would
        // load with no home page at all.
        boardFile.parentFile?.mkdirs()
        boardFile.writeText(
            """
            {
              "name": "Old Board",
              "pages": [
                {"id": "p1", "name": "Page 1", "rows": 4, "columns": 4, "tiles": []},
                {"id": "p2", "name": "Page 2", "rows": 4, "columns": 4, "tiles": []}
              ],
              "currentPageIndex": 0,
              "homePageIndex": 1
            }
            """.trimIndent()
        )

        val loaded = repo.load()

        assertEquals(1, loaded.homePageIndex)
        assertTrue(loaded.pages[1].isHome)
        assertFalse(loaded.pages[0].isHome)
    }

    @Test
    fun `a board saved by today's schema still loads when it predates sticky home row, home page and page color`() {
        // What repo.save() itself would have written before stickyHomeRowEnabled/homePageIndex/
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

        assertEquals(false, loaded.stickyHomeRowEnabled)
        assertEquals(null, loaded.homePageIndex)
        assertEquals(null, loaded.pages[0].color)
        assertEquals(1f, loaded.pages[0].tileAspectRatio)
    }

    @Test
    fun `a tile whose fileName has no backing file loads as empty rather than silently unplayable`() {
        // A generic preset can ship a board.json referencing clips that were never
        // recorded (or a partial import), so a tile can claim a fileName with
        // nothing behind it. That should read as "still needs recording," not as
        // filled-but-broken.
        repo.save(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Call Mom", fileName = "missing.mp3")))))
        )

        val loaded = repo.load()

        val tile = loaded.currentPage.tiles.first { it.id == "a" }
        assertNull(tile.fileName)
        assertEquals("Call Mom", tile.label)
        assertTrue(tile.isEmpty)
    }

    @Test
    fun `a tile whose file does exist keeps its fileName`() {
        repo.soundFile("real.mp3").apply { parentFile?.mkdirs() }.writeText("real")
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", fileName = "real.mp3"))))))

        val loaded = repo.load()

        assertEquals("real.mp3", loaded.currentPage.tiles.first { it.id == "a" }.fileName)
    }

    @Test
    fun `a home page tile whose fileName has no backing file also loads as empty`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "missing.mp3")), isHome = true))))

        val loaded = repo.load()

        val tile = loaded.homePage!!.tiles.first { it.id == "hey" }
        assertNull(tile.fileName)
        assertEquals("Hey", tile.label)
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
    fun `the bundled jeremy-care-board preset imports and loads as a valid four-page board`() {
        // Regression coverage for the actual shipped preset (presets/jeremy-care-board.zip,
        // mirrored at app/src/main/assets/jeremy-care-board.zip) — guards against the zip
        // and the app's Board schema drifting apart silently.
        val imported = repo.importFromAsset("jeremy-care-board.zip")

        assertTrue(imported)
        val board = repo.load()

        assertEquals("Jeremy Draft Care Board", board.name)
        assertEquals(listOf("Trouble", "Needs", "Talking", "Well Wishes"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertTrue(board.stickyHomeRowEnabled)
        // The home page carries an extra sticky first row (4 tiles), so it's one row taller.
        board.pages.forEachIndexed { index, page ->
            if (index == board.homePageIndex) {
                assertEquals(28, page.tiles.size)
                assertEquals(7, page.rows)
            } else {
                assertEquals(24, page.tiles.size)
                assertEquals(6, page.rows)
            }
            assertEquals(4, page.columns)
        }
        // Every referenced sound file actually landed in app storage.
        val allTiles = board.pages.flatMap { it.tiles }
        allTiles.mapNotNull { it.fileName }.forEach { name ->
            assertTrue("missing sound file $name", repo.soundFile(name).exists())
        }
    }

    @Test
    fun `the steve-care-board preset imports and loads as a valid four-page board`() {
        // presets/steve-care-board.zip is debug-only now (app/src/debug/assets/), not
        // auto-loaded like jeremy-care-board.zip, so this exercises the same import
        // path a user tapping Import would, via a fake content:// uri pointed at the
        // checked-in zip.
        val zip = File("../presets/steve-care-board.zip")
        assertTrue("expected ${zip.absolutePath} to exist", zip.exists())
        val uri = Uri.parse("content://fake/steve-care-board.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, zip.inputStream())

        val imported = repo.importFrom(uri)

        assertTrue(imported)
        val board = repo.load()

        assertEquals(listOf("Trouble", "Needs", "Talking", "Well Wishes"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertTrue(board.stickyHomeRowEnabled)
        board.pages.forEachIndexed { index, page ->
            if (index == board.homePageIndex) {
                assertEquals(28, page.tiles.size)
                assertEquals(7, page.rows)
            } else {
                assertEquals(24, page.tiles.size)
                assertEquals(6, page.rows)
            }
            assertEquals(4, page.columns)
        }
        val allTiles = board.pages.flatMap { it.tiles }
        allTiles.mapNotNull { it.fileName }.forEach { name ->
            assertTrue("missing sound file $name", repo.soundFile(name).exists())
        }
    }
}
