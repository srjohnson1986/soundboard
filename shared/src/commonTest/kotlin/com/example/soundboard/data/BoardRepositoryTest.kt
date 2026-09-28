package com.example.soundboard.data

import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * [BoardRepository]'s own logic against in-memory storage, on every platform. The real file
 * store and zip codecs have their own tests on each platform, and the boards the app ships
 * are checked in the app (ShippedBoardsTest).
 */
class BoardRepositoryTest {

    private val files = InMemoryFileStore()
    private val zip = FakeZipCodec()
    private val repo = BoardRepository(files, zip, FakeBundledBoards())

    private fun boardWith(vararg tiles: Tile) =
        Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList()))).normalized()

    @Test
    fun `save then load round-trips a board whose sounds exist`() = runTest {
        files.write(repo.soundPath("a.m4a"), byteArrayOf(1))
        val board = boardWith(Tile(id = "a", label = "Hey", fileName = "a.m4a"), Tile(id = "b"))

        repo.save(board)

        assertEquals(board, repo.load())
    }

    @Test
    fun `load empties a tile whose sound file is missing`() = runTest {
        repo.save(boardWith(Tile(id = "a", label = "Hey", fileName = "gone.m4a")))

        val tile = repo.load().findTile("a")

        assertEquals("Hey", tile?.label)
        assertNull(tile?.fileName)
    }

    @Test
    fun `with nothing saved, load returns a default board`() = runTest {
        assertFalse(repo.hasSavedBoard())
        assertEquals(Board().pages.size, repo.load().pages.size)
    }

    @Test
    fun `importSound keeps the extension and returns a name under sounds`() = runTest {
        val name = repo.importSound(FakePickedFile(byteArrayOf(7, 8), extension = "mp3"))

        assertNotNull(name)
        assertTrue(name.endsWith(".mp3"))
        assertContentEquals(byteArrayOf(7, 8), files.read(repo.soundPath(name)))
    }

    @Test
    fun `importSound returns null when the file can't be read`() = runTest {
        assertNull(repo.importSound(FakePickedFile(null)))
    }

    @Test
    fun `pruneUnused deletes exactly the sounds not kept`() = runTest {
        files.write(repo.soundPath("keep.m4a"), byteArrayOf(1))
        files.write(repo.soundPath("drop.m4a"), byteArrayOf(1))

        repo.pruneUnused(setOf("keep.m4a"))

        assertEquals(listOf("keep.m4a"), files.list("sounds").map { it.name })
    }

    @Test
    fun `a backup export then import restores the board, its sounds and its background`() = runTest {
        files.write(repo.soundPath("a.m4a"), byteArrayOf(1, 2))
        val background = repo.importBackgroundImage(FakePickedFile(byteArrayOf(9), extension = "jpg"))!!
        val board = boardWith(Tile(id = "a", label = "Hey", fileName = "a.m4a")).copy(backgroundImageFileName = background)
        repo.save(board)
        val target = CapturingSaveTarget()
        assertTrue(repo.exportTo(target))

        val restoredFiles = InMemoryFileStore()
        val restored = BoardRepository(restoredFiles, zip, FakeBundledBoards())
        assertTrue(restored.importFrom(FakePickedFile(target.written)))

        assertEquals(board, restored.load())
        assertContentEquals(byteArrayOf(1, 2), restoredFiles.read(restored.soundPath("a.m4a")))
        assertContentEquals(byteArrayOf(9), restored.readBackground(background))
    }

    @Test
    fun `import ignores entries that would land outside their folder`() = runTest {
        val backup = zip.write(
            listOf(
                ZipEntryData("sounds/ok.m4a", byteArrayOf(1)),
                ZipEntryData("sounds/../board.json", "{}".encodeToByteArray()),
                ZipEntryData("sounds/nested/deep.m4a", byteArrayOf(1)),
                ZipEntryData("background/../../escape.jpg", byteArrayOf(1))
            )
        )

        assertTrue(repo.importFrom(FakePickedFile(backup)))

        assertEquals(listOf("ok.m4a"), files.list("sounds").map { it.name })
        assertFalse(files.exists("board.json"))
        assertTrue(files.list("backgrounds").isEmpty())
    }

    @Test
    fun `importing something that isn't a backup fails and leaves the board alone`() = runTest {
        repo.save(boardWith(Tile(id = "a", label = "Hey")))

        assertFalse(repo.importFrom(FakePickedFile("not a zip".encodeToByteArray())))

        assertEquals("Hey", repo.load().findTile("a")?.label)
    }

    @Test
    fun `a bundled board imports like a backup, and a missing one fails quietly`() = runTest {
        val bundledZip = zip.write(listOf(ZipEntryData("board.json", BoardJson.encodeToString(Board(name = "Built in")).encodeToByteArray())))
        val repo = BoardRepository(files, zip, FakeBundledBoards(mapOf("built-in.zip" to bundledZip)))

        assertTrue(repo.hasAsset("built-in.zip"))
        assertTrue(repo.importFromAsset("built-in.zip"))
        assertEquals("Built in", repo.load().name)
        assertFalse(repo.hasAsset("missing.zip"))
        assertFalse(repo.importFromAsset("missing.zip"))
    }

    private fun builtInZip(vararg tiles: Tile, sounds: Map<String, ByteArray> = emptyMap()) = zip.write(
        listOf(ZipEntryData("board.json", BoardJson.encodeToString(boardWith(*tiles).copy(name = "Built in")).encodeToByteArray())) +
            sounds.map { (name, bytes) -> ZipEntryData("sounds/$name", bytes) }
    )

    @Test
    fun `importing a built-in board remembers where it came from`() = runTest {
        val repo = BoardRepository(files, zip, FakeBundledBoards(mapOf("built-in.zip" to builtInZip(Tile(id = "a", label = "Hey")))))

        repo.importFromAsset("built-in.zip")

        assertEquals("built-in.zip", repo.load().builtInSource)
    }

    @Test
    fun `originalTile finds a built-in tile by name, ignoring case and spaces, with its clip`() = runTest {
        val repo = BoardRepository(
            files, zip,
            FakeBundledBoards(mapOf("built-in.zip" to builtInZip(Tile(id = "w", label = "Water", fileName = "water.wav", volume = 0.5f), sounds = mapOf("water.wav" to byteArrayOf(4, 2)))))
        )

        val original = repo.originalTile("built-in.zip", "  wATer ")

        assertEquals("water.wav", original?.tile?.fileName)
        assertEquals(0.5f, original?.tile?.volume)
        assertContentEquals(byteArrayOf(4, 2), original?.sound)
    }

    @Test
    fun `originalTile falls back to a tile's speech when its clip is missing, and to nothing when it has no speech`() = runTest {
        val repo = BoardRepository(
            files, zip,
            FakeBundledBoards(
                mapOf(
                    "built-in.zip" to builtInZip(
                        Tile(id = "s", label = "Speaks", fileName = "gone.wav", speakWhenNoSound = true),
                        Tile(id = "q", label = "Quiet", fileName = "gone.wav")
                    )
                )
            )
        )

        val speaks = repo.originalTile("built-in.zip", "Speaks")
        assertNull(speaks?.tile?.fileName)
        assertNull(speaks?.sound)
        assertTrue(speaks?.tile?.speakWhenNoSound == true)
        assertNull(repo.originalTile("built-in.zip", "Quiet"))
    }

    @Test
    fun `originalTile gives a speaking tile's speech, and nothing for a tile that makes no sound`() = runTest {
        val repo = BoardRepository(
            files, zip,
            FakeBundledBoards(
                mapOf(
                    "built-in.zip" to builtInZip(
                        Tile(id = "s", label = "Hey", speakWhenNoSound = true, ttsScript = "Hey there"),
                        Tile(id = "b", label = "Blank label only")
                    )
                )
            )
        )

        val speech = repo.originalTile("built-in.zip", "Hey")
        assertEquals("Hey there", speech?.tile?.ttsScript)
        assertNull(speech?.sound)
        assertNull(repo.originalTile("built-in.zip", "Blank label only"))
        assertNull(repo.originalTile("built-in.zip", "Not on this board"))
        assertNull(repo.originalTile("missing.zip", "Hey"))
    }

    /** Writes board.json directly, as an older build or a damaged file might have left it. */
    private fun writeBoardJson(json: String) = files.put("board.json", json.trimIndent().encodeToByteArray())

    /** Backs up the saved board, runs [between], then imports the backup back. */
    private suspend fun exportThenImport(between: suspend () -> Unit = {}) {
        val target = CapturingSaveTarget()
        assertTrue(repo.exportTo(target))
        between()
        assertTrue(repo.importFrom(FakePickedFile(target.written)))
    }

    @Test
    fun `save then load round-trips a tile's ttsScript, and so does a backup`() = runTest {
        repo.save(boardWith(Tile(id = "a", label = "Water", ttsScript = "I would like a glass of water please")))

        assertEquals("I would like a glass of water please", repo.load().findTile("a")?.ttsScript)
        exportThenImport { repo.save(Board()) }
        assertEquals("I would like a glass of water please", repo.load().findTile("a")?.ttsScript)
    }

    @Test
    fun `a backup round-trips every layout and label setting`() = runTest {
        val board = Board(
            pages = listOf(
                Page(name = "A", landscapeRows = 2, landscapeColumns = 6, rowHeight = RowHeight.SHORT),
                Page(name = "B")
            ),
            landscapeLayout = LandscapeLayout.FIT_TO_SCREEN,
            rowHeight = RowHeight.EXTRA_TALL,
            labelStyle = LabelStyle(minSizeSp = 12f, maxSizeSp = 44f, font = LabelFont.ATKINSON_HYPERLEGIBLE, bold = true, allCaps = true)
        ).normalized()
        repo.save(board)

        exportThenImport { repo.save(Board()) }

        assertEquals(board, repo.load())
    }

    @Test
    fun `save then load round-trips pages, the current page, sticky home row, home page, page color and tile shape`() = runTest {
        files.put(repo.soundPath("hey.mp3"), byteArrayOf(1))
        val board = Board(
            pages = listOf(
                Page(name = "Trouble", color = 0xFFB74D, tileAspectRatio = 4f / 3f),
                Page(name = "Needs", isHome = true, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3", colorArgb = 0xE57373)))
            ),
            currentPageIndex = 1,
            stickyHomeRowEnabled = true
        ).normalized()

        repo.save(board)
        val loaded = repo.load()

        assertEquals(board, loaded)
        assertEquals(1, loaded.homePageIndex)
    }

    @Test
    fun `load appends a blank row when the saved board's last row is completely full`() = runTest {
        files.put(repo.soundPath("a.mp3"), byteArrayOf(1))
        files.put(repo.soundPath("b.mp3"), byteArrayOf(1))
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 2, tiles = listOf(Tile(id = "a", fileName = "a.mp3"), Tile(id = "b", fileName = "b.mp3"))))))

        val loaded = repo.load()

        assertEquals(2, loaded.currentPage.rows)
        assertEquals(4, loaded.currentPage.visibleTiles.size)
        assertTrue(loaded.currentPage.tiles.drop(2).none { it.hasSound })
    }

    @Test
    fun `a home page tile whose sound file is missing also loads as empty`() = runTest {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "missing.mp3")), isHome = true))))

        val tile = repo.load().homePage!!.tiles.first { it.id == "hey" }

        assertNull(tile.fileName)
        assertEquals("Hey", tile.label)
    }

    @Test
    fun `truncated or malformed json loads the default board rather than throwing`() = runTest {
        listOf("{\"rows\": 3, \"columns\"", "not json at all").forEach { json ->
            writeBoardJson(json)

            val loaded = repo.load()

            assertEquals(4, loaded.currentPage.rows, json)
            assertEquals(4, loaded.currentPage.columns, json)
            assertEquals(16, loaded.currentPage.tiles.size, json)
        }
    }

    @Test
    fun `a board saved before isHome moved onto Page still loads with its home page intact`() = runTest {
        // A board-level "homePageIndex" rather than per-page "isHome": ignoreUnknownKeys would
        // silently drop it, so without the migration this board would have no home page.
        writeBoardJson(
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
            """
        )

        val loaded = repo.load()

        assertEquals(1, loaded.homePageIndex)
        assertTrue(loaded.pages[1].isHome)
        assertFalse(loaded.pages[0].isHome)
    }

    @Test
    fun `a board from before sticky home row, home page and page color loads with their defaults`() = runTest {
        writeBoardJson(
            """
            {
              "name": "New Board",
              "pages": [{"id": "p1", "name": "Page 1", "rows": 4, "columns": 4, "tiles": []}],
              "currentPageIndex": 0
            }
            """
        )

        val loaded = repo.load()

        assertEquals(false, loaded.stickyHomeRowEnabled)
        assertEquals(null, loaded.homePageIndex)
        assertEquals(null, loaded.pages[0].color)
        assertEquals(1f, loaded.pages[0].tileAspectRatio)
    }

    @Test
    fun `a board from before pages existed still loads, ignoring fields it doesn't know`() = runTest {
        // Unknown fields on the board ("theme") and a tile ("isFavorite"), a tile without the
        // newer "colorArgb", and no "pages" at all: the flat shape used before pages.
        writeBoardJson(
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
            """
        )

        val loaded = repo.load()

        assertEquals(1, loaded.pages.size)
        assertEquals(0, loaded.currentPageIndex)
        assertEquals(1, loaded.currentPage.rows)
        assertEquals(2, loaded.currentPage.columns)
        assertEquals(listOf("x", "y"), loaded.currentPage.visibleTiles.map { it.id })
        assertNull(loaded.currentPage.tiles[1].colorArgb)
    }

    @Test
    fun `strayFiles lists exactly the sounds not kept, without deleting them`() = runTest {
        files.put(repo.soundPath("keep.mp3"), byteArrayOf(1))
        files.put(repo.soundPath("stray.mp3"), byteArrayOf(1))

        assertEquals(listOf("stray.mp3"), repo.strayFiles(setOf("keep.mp3")).map { it.name })
        assertEquals(setOf("keep.mp3", "stray.mp3"), files.list("sounds").map { it.name }.toSet())
    }

    @Test
    fun `deleteFiles removes exactly the named sounds`() = runTest {
        files.put(repo.soundPath("kept.mp3"), byteArrayOf(1))
        files.put(repo.soundPath("removed.mp3"), byteArrayOf(1))

        repo.deleteFiles(listOf("removed.mp3"))

        assertEquals(listOf("kept.mp3"), files.list("sounds").map { it.name })
    }

    @Test
    fun `exportFiles zips exactly the requested sounds, flat by name`() = runTest {
        files.put(repo.soundPath("a.mp3"), "aaa".encodeToByteArray())
        files.put(repo.soundPath("b.mp3"), "bbb".encodeToByteArray())
        val target = CapturingSaveTarget()

        assertTrue(repo.exportFiles(listOf("a.mp3"), target))

        val entries = zip.read(target.written!!)
        assertEquals(listOf("a.mp3"), entries.map { it.name })
        assertEquals("aaa", entries.single().bytes.decodeToString())
    }

    @Test
    fun `importBackgroundImage keeps the extension and returns a name under backgrounds`() = runTest {
        val name = repo.importBackgroundImage(FakePickedFile("fake image bytes".encodeToByteArray(), extension = "jpg"))

        assertNotNull(name)
        assertTrue(name.endsWith(".jpg"))
        assertEquals("fake image bytes", repo.readBackground(name)?.decodeToString())
    }
}
