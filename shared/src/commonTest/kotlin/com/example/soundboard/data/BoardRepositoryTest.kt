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
    fun `a board from before Spanish loads unchanged with no translations and Spanish on offer`() = runTest {
        files.write(repo.soundPath("x.mp3"), byteArrayOf(1))
        // A board.json as an older build wrote it: no translations on the tile, no secondLanguage.
        files.write(
            "board.json",
            """{"name":"Old","pages":[{"id":"p","name":"One","rows":1,"columns":2,"tiles":[{"id":"x","label":"Hello","fileName":"x.mp3","volume":0.5},{"id":"y"}]}],"currentPageIndex":0,"schemaVersion":1}"""
                .encodeToByteArray()
        )

        val board = repo.load()

        assertEquals("Old", board.name)
        val tile = board.findTile("x")!!
        assertEquals("Hello", tile.label)
        assertEquals("x.mp3", tile.fileName)
        assertEquals(0.5f, tile.volume)
        assertEquals(emptyMap(), tile.translations)
        assertNull(tile.translationFor("es"))
        assertEquals("es", board.secondLanguage)
    }

    @Test
    fun `a board saved by a newer build still loads with the translations ignored by an older reader`() = runTest {
        // The reverse direction: ignoreUnknownKeys is what lets an older build read a newer file.
        files.write(
            "board.json",
            """{"pages":[{"id":"p","tiles":[{"id":"x","label":"Hi","translations":{"es":"Hola","fr":"Salut"},"somethingNew":1}]}],"secondLanguage":null}"""
                .encodeToByteArray()
        )

        val board = repo.load()

        assertEquals(mapOf("es" to "Hola", "fr" to "Salut"), board.findTile("x")?.translations)
        assertNull(board.secondLanguage)
    }

    @Test
    fun `save then load round-trips a tile's Spanish and the second language and so does a backup`() = runTest {
        val board = boardWith(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua"))).copy(secondLanguage = null)
        repo.save(board)

        assertEquals(mapOf("es" to "Agua"), repo.load().findTile("a")?.translations)
        assertNull(repo.load().secondLanguage)
        exportThenImport { repo.save(Board()) }
        assertEquals(mapOf("es" to "Agua"), repo.load().findTile("a")?.translations)
        assertNull(repo.load().secondLanguage)
    }

    @Test
    fun `a saved board keeps a tile's Spanish`() = runTest {
        val saved = SavedBoardRepository(files)
        val id = saved.save(boardWith(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua"))))

        assertEquals(mapOf("es" to "Agua"), saved.load(id)?.findTile("a")?.translations)
    }

    @Test
    fun `load empties a tile whose sound file is missing`() = runTest {
        repo.save(boardWith(Tile(id = "a", label = "Hey", fileName = "gone.m4a")))

        val tile = repo.load().findTile("a")

        assertEquals("Hey", tile?.label)
        assertNull(tile?.fileName)
    }

    @Test
    fun `with nothing saved load returns a default board`() = runTest {
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
    fun `a backup export then import restores the board and its sounds and its background`() = runTest {
        files.write(repo.soundPath("a.m4a"), byteArrayOf(1, 2))
        val background = repo.importBackgroundImage(FakePickedFile(byteArrayOf(9), extension = "jpg"))!!
        val board = boardWith(Tile(id = "a", label = "Hey", fileName = "a.m4a")).copy(backgroundImageFileName = background)
        repo.save(board)
        val target = CapturingSaveTarget()
        assertTrue(repo.exportTo(target))

        val restoredFiles = InMemoryFileStore()
        val restored = BoardRepository(restoredFiles, zip, FakeBundledBoards())
        assertTrue(restored.importFrom(FakePickedFile(target.written)))

        // Same board, except that the sound now has a fresh ID (see importZip).
        val loaded = restored.load()
        val soundName = loaded.findTile("a")!!.fileName!!
        assertEquals(board.copy(pages = board.pages.map { page -> page.copy(tiles = page.tiles.map { if (it.id == "a") it.copy(fileName = soundName) else it }) }), loaded)
        assertContentEquals(byteArrayOf(1, 2), restoredFiles.read(restored.soundPath(soundName)))
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

        // The one sound that was inside its folder came in, under a fresh name.
        assertEquals(1, files.list("sounds").size)
        assertContentEquals(byteArrayOf(1), files.readNow(repo.soundPath(files.list("sounds").single().name)))
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
    fun `a bundled board imports like a backup and a missing one fails quietly`() = runTest {
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
    fun `originalTile finds a built-in tile by name ignoring case and spaces with its clip`() = runTest {
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
    fun `originalTile falls back to a tile's speech when its clip is missing and to nothing when it has no speech`() = runTest {
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
    fun `originalTile gives a speaking tile's speech and nothing for a tile that makes no sound`() = runTest {
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
    fun `save then load round-trips a tile's ttsScript and so does a backup`() = runTest {
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
    fun `save then load round-trips pages and the current page and sticky home row and home page and page color and tile shape`() = runTest {
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
    fun `a board saved before the Edit mode timeout loads with ten seconds`() = runTest {
        writeBoardJson(
            """
            {
              "name": "Old Board",
              "pages": [{"id": "p1", "name": "Page 1", "rows": 4, "columns": 4, "tiles": []}],
              "currentPageIndex": 0,
              "idleTimeoutMinutes": 2,
              "longPressDurationMillis": 800
            }
            """
        )

        val loaded = repo.load()

        assertEquals(10, loaded.editModeTimeoutSeconds)
        assertEquals(2, loaded.idleTimeoutMinutes)
    }

    @Test
    fun `a board from before sticky home row and home page and page color loads with their defaults`() = runTest {
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
    fun `a board from before pages existed still loads ignoring fields it doesn't know`() = runTest {
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
    fun `strayFiles lists exactly the sounds not kept without deleting them`() = runTest {
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
    fun `exportFiles zips exactly the requested sounds and numbers the ones nothing is known about`() = runTest {
        files.put(repo.soundPath("a.mp3"), "aaa".encodeToByteArray())
        files.put(repo.soundPath("b.mp3"), "bbb".encodeToByteArray())
        val target = CapturingSaveTarget()

        assertTrue(repo.exportFiles(listOf("a.mp3"), target))

        val entries = zip.read(target.written!!)
        assertEquals(listOf("Unused clip 1.mp3"), entries.map { it.name })
        assertEquals("aaa", entries.single().bytes.decodeToString())
    }

    @Test
    fun `importBackgroundImage keeps the extension and returns a name under backgrounds`() = runTest {
        val name = repo.importBackgroundImage(FakePickedFile("fake image bytes".encodeToByteArray(), extension = "jpg"))

        assertNotNull(name)
        assertTrue(name.endsWith(".jpg"))
        assertEquals("fake image bytes", repo.readBackground(name)?.decodeToString())
    }

    @Test
    fun `a backup export then import restores tile pictures`() = runTest {
        val name = repo.importIcon(FakePickedFile(byteArrayOf(7, 7), extension = "png"))!!
        val board = boardWith(Tile(id = "a", label = "Hey", emoji = "x", imageFileName = name, showLabel = false))
        repo.save(board)
        val target = CapturingSaveTarget()
        assertTrue(repo.exportTo(target))

        val restoredFiles = InMemoryFileStore()
        val restored = BoardRepository(restoredFiles, zip, FakeBundledBoards())
        assertTrue(restored.importFrom(FakePickedFile(target.written)))

        assertEquals(board, restored.load())
        assertContentEquals(byteArrayOf(7, 7), restored.readIcon(name))
    }

    @Test
    fun `load drops a tile picture whose file is missing and keeps the emoji`() = runTest {
        repo.save(boardWith(Tile(id = "a", label = "Hey", emoji = "x", imageFileName = "gone.png")))

        val tile = repo.load().pages.first().tiles.first()

        assertNull(tile.imageFileName)
        assertEquals("x", tile.emoji)
    }

    @Test
    fun `pruneUnusedIcons deletes exactly the pictures not kept`() = runTest {
        files.write("icons/keep.png", byteArrayOf(1))
        files.write("icons/drop.png", byteArrayOf(1))

        repo.pruneUnusedIcons(setOf("keep.png"))

        assertEquals(listOf("keep.png"), files.list("icons").map { it.name })
    }

    @Test
    fun `import ignores icon entries that would land outside their folder`() = runTest {
        val backup = zip.write(listOf(ZipEntryData("icons/ok.png", byteArrayOf(1)), ZipEntryData("icons/../evil.png", byteArrayOf(2))))

        assertTrue(repo.importFrom(FakePickedFile(backup)))

        assertEquals(listOf("ok.png"), files.list("icons").map { it.name })
        assertFalse(files.exists("evil.png"))
    }

    // Sounds in a backup are named after their tiles (#329).

    /** Puts a sound on the device under [name], holding [bytes]. */
    private fun sound(name: String, vararg bytes: Byte) = files.put(repo.soundPath(name), bytes)

    /** What a backup of the saved board holds, by entry name. */
    private suspend fun backupEntries(): Map<String, ByteArray> {
        val target = CapturingSaveTarget()
        assertTrue(repo.exportTo(target))
        return zip.read(target.written!!).associate { it.name to it.bytes }
    }

    private fun Map<String, ByteArray>.soundNames() = keys.filter { it.startsWith("sounds/") }.map { it.removePrefix("sounds/") }.toSet()

    private fun Map<String, ByteArray>.board() = BoardJson.decodeFromString<Board>(getValue("board.json").decodeToString())

    @Test
    fun `a backup names each sound after its tile and numbers duplicates by page`() = runTest {
        listOf("id1.m4a", "id2.m4a", "id3.m4a", "id4.m4a", "id5.m4a", "id6.m4a", "id7.m4a").forEachIndexed { i, name -> sound(name, i.toByte()) }
        repo.save(
            Board(
                pages = listOf(
                    Page(
                        name = "Home", rows = 1, columns = 4,
                        tiles = listOf(
                            Tile(id = "yes", label = "Yes", fileName = "id1.m4a"),
                            Tile(id = "no", label = "No?", fileName = "id2.m4a"),
                            Tile(id = "emoji", emoji = "😀", fileName = "id3.m4a"),
                            Tile(id = "blank", fileName = "id4.m4a")
                        )
                    ),
                    Page(
                        name = "Food", rows = 1, columns = 3,
                        tiles = listOf(
                            Tile(id = "yes2", label = "yes", fileName = "id5.m4a"),
                            Tile(id = "yes3", label = "YES", fileName = "id6.m4a"),
                            Tile(id = "yes4", label = "Yes", fileName = "id1.m4a")
                        )
                    )
                )
            ).normalized()
        )

        val backup = backupEntries()

        // id7 is on no tile, so it keeps its name; id1 is on two tiles and is in once, as the first.
        assertEquals(
            setOf("Yes.m4a", "No.m4a", "Happy.m4a", "Sound.m4a", "yes (Food).m4a", "YES (Food) 2.m4a", "id7.m4a"),
            backup.soundNames()
        )
        val tiles = backup.board().pages.flatMap { it.tiles }.associateBy { it.id }
        assertEquals("Yes.m4a", tiles.getValue("yes").fileName)
        assertEquals("No.m4a", tiles.getValue("no").fileName)
        assertEquals("Happy.m4a", tiles.getValue("emoji").fileName)
        assertEquals("Sound.m4a", tiles.getValue("blank").fileName)
        assertEquals("yes (Food).m4a", tiles.getValue("yes2").fileName)
        assertEquals("YES (Food) 2.m4a", tiles.getValue("yes3").fileName)
        assertEquals("Yes.m4a", tiles.getValue("yes4").fileName)
        assertContentEquals(byteArrayOf(0), backup.getValue("sounds/Yes.m4a"))
        // The device itself is untouched.
        assertEquals("id1.m4a", repo.load().findTile("yes")?.fileName)
        assertEquals(7, files.list("sounds").size)
    }

    @Test
    fun `a backup only renames fileName and leaves the rest of board json alone`() = runTest {
        sound("id1.m4a", 1)
        repo.save(boardWith(Tile(id = "a", label = "id1.m4a", ttsScript = "id1.m4a", fileName = "id1.m4a")))

        val tile = backupEntries().board().pages.first().tiles.first()

        assertEquals("id1.m4a.m4a", tile.fileName)
        assertEquals("id1.m4a", tile.label)
        assertEquals("id1.m4a", tile.ttsScript)
    }

    @Test
    fun `backing up then restoring gives every tile the same audio bytes`() = runTest {
        sound("id1.m4a", 1, 1)
        sound("id2.m4a", 2, 2)
        sound("id3.m4a", 3, 3)
        repo.save(
            Board(
                pages = listOf(
                    Page(name = "A", rows = 1, columns = 2, tiles = listOf(Tile(id = "a", label = "Hi", fileName = "id1.m4a"), Tile(id = "b", label = "Hi", fileName = "id2.m4a"))),
                    Page(name = "B", rows = 1, columns = 2, tiles = listOf(Tile(id = "c", label = "Hi", fileName = "id3.m4a"), Tile(id = "d", label = "Again", fileName = "id1.m4a")))
                )
            ).normalized()
        )
        val target = CapturingSaveTarget()
        assertTrue(repo.exportTo(target))

        val restoredFiles = InMemoryFileStore()
        val restored = BoardRepository(restoredFiles, zip, FakeBundledBoards())
        assertTrue(restored.importFrom(FakePickedFile(target.written)))
        val board = restored.load()

        fun bytesOf(tileId: String) = restoredFiles.readNow(restored.soundPath(board.findTile(tileId)!!.fileName!!))
        assertContentEquals(byteArrayOf(1, 1), bytesOf("a"))
        assertContentEquals(byteArrayOf(2, 2), bytesOf("b"))
        assertContentEquals(byteArrayOf(3, 3), bytesOf("c"))
        assertContentEquals(byteArrayOf(1, 1), bytesOf("d"))
        assertEquals(board.findTile("a")?.fileName, board.findTile("d")?.fileName)
        assertEquals(3, restoredFiles.list("sounds").size)
        // Every restored clip has a fresh ID rather than the backup's readable name.
        board.soundFileNames.forEach { name ->
            assertTrue(name.endsWith(".m4a"))
            assertFalse(name in setOf("Hi.m4a", "Hi (A).m4a", "Hi (B).m4a", "Again.m4a"), name)
        }
    }

    @Test
    fun `an old backup with ID names still restores`() = runTest {
        val backup = zip.write(
            listOf(
                ZipEntryData("board.json", GoldenBackup.BOARD_JSON.encodeToByteArray()),
                ZipEntryData("sounds/hi.m4a", GoldenBackup.SOUND_BYTES)
            )
        )

        assertTrue(repo.importFrom(FakePickedFile(backup)))

        val tile = repo.load().findTile("t")!!
        assertEquals("Hi", tile.label)
        assertContentEquals(GoldenBackup.SOUND_BYTES, files.readNow(repo.soundPath(tile.fileName!!)))
    }

    @Test
    fun `a backup from before pages existed restores its sounds too`() = runTest {
        val backup = zip.write(
            listOf(
                ZipEntryData(
                    "board.json",
                    """{"rows":1,"columns":2,"tiles":[{"id":"x","label":"Old","fileName":"x.mp3","volume":1.0},{"id":"y","fileName":null}]}""".encodeToByteArray()
                ),
                ZipEntryData("sounds/x.mp3", byteArrayOf(5, 6))
            )
        )

        assertTrue(repo.importFrom(FakePickedFile(backup)))

        val board = repo.load()
        val tile = board.findTile("x")!!
        assertContentEquals(byteArrayOf(5, 6), files.readNow(repo.soundPath(tile.fileName!!)))
        assertTrue(tile.fileName.orEmpty().endsWith(".mp3"))
        assertNull(board.findTile("y")?.fileName)
    }

    @Test
    fun `restoring never overwrites a file a saved board uses`() = runTest {
        // A saved board's tile plays sounds/Yes.m4a (a name an older build or backup left there)...
        sound("Yes.m4a", 9)
        val backup = zip.write(
            listOf(
                ZipEntryData("board.json", BoardJson.encodeToString(boardWith(Tile(id = "t", label = "Yes", fileName = "Yes.m4a"))).encodeToByteArray()),
                ZipEntryData("sounds/Yes.m4a", byteArrayOf(1))
            )
        )

        // ...and two restores of a backup with its own Yes.m4a come in.
        assertTrue(repo.importFrom(FakePickedFile(backup)))
        val first = repo.load().findTile("t")!!.fileName!!
        assertTrue(repo.importFrom(FakePickedFile(backup)))
        val second = repo.load().findTile("t")!!.fileName!!

        assertContentEquals(byteArrayOf(9), files.readNow(repo.soundPath("Yes.m4a")))
        assertContentEquals(byteArrayOf(1), files.readNow(repo.soundPath(first)))
        assertContentEquals(byteArrayOf(1), files.readNow(repo.soundPath(second)))
        assertEquals(3, files.list("sounds").size)
        assertTrue(first != "Yes.m4a" && second != "Yes.m4a" && first != second)
    }

    @Test
    fun `a built-in board's sounds come in under fresh IDs as well`() = runTest {
        val repo = BoardRepository(
            files, zip,
            FakeBundledBoards(mapOf("built-in.zip" to builtInZip(Tile(id = "w", label = "Water", fileName = "water.wav"), sounds = mapOf("water.wav" to byteArrayOf(4)))))
        )

        assertTrue(repo.importFromAsset("built-in.zip"))

        val name = repo.load().findTile("w")!!.fileName!!
        assertTrue(name != "water.wav" && name.endsWith(".wav"))
        assertContentEquals(byteArrayOf(4), files.readNow(repo.soundPath(name)))
    }

    // What each clip was, for the unused-clips cleanup (#330).

    private fun yesNoBoard() = Board(
        pages = listOf(
            Page(name = "Home", rows = 1, columns = 2, tiles = listOf(Tile(id = "a", label = "Yes", fileName = "a.m4a"), Tile(id = "b", label = "No", fileName = "b.m4a"))),
            Page(name = "Food", rows = 1, columns = 2, tiles = listOf(Tile(id = "c", label = "Yes", fileName = "c.m4a"), Tile(id = "d", emoji = "\uD83D\uDE00", fileName = "d.m4a")))
        )
    ).normalized()

    @Test
    fun `recordClipLabels remembers each clip's tile and page and keeps it when the tile is cleared`() = runTest {
        listOf("a", "b", "c", "d").forEach { sound("$it.m4a", 1) }
        val board = yesNoBoard()

        repo.recordClipLabels(board)
        repo.recordClipLabels(board.updatingTile("a") { it.copy(fileName = null) })

        val labels = repo.clipLabels()
        assertEquals(ClipLabel("Yes", null, "Home"), labels["a.m4a"])
        assertEquals(ClipLabel("Yes", null, "Food"), labels["c.m4a"])
        assertEquals("Happy", labels["d.m4a"]?.tileName)
        assertEquals("Yes, Food page", labels["c.m4a"]?.description)
    }

    @Test
    fun `a clip on two tiles is remembered under the first and a renamed tile updates it`() = runTest {
        sound("a.m4a", 1)
        val shared = boardWith(Tile(id = "a", label = "First", fileName = "a.m4a"), Tile(id = "b", label = "Second", fileName = "a.m4a"))

        repo.recordClipLabels(shared)
        assertEquals("First", repo.clipLabels().getValue("a.m4a").label)
        repo.recordClipLabels(shared.updatingTile("a") { it.copy(label = "Renamed") })

        assertEquals("Renamed", repo.clipLabels().getValue("a.m4a").label)
    }

    @Test
    fun `entries go when their clip file is deleted or pruned`() = runTest {
        listOf("a", "b", "c", "d").forEach { sound("$it.m4a", 1) }
        repo.recordClipLabels(yesNoBoard())

        repo.deleteFiles(listOf("a.m4a"))
        repo.pruneUnused(setOf("c.m4a", "d.m4a"))

        assertEquals(setOf("c.m4a", "d.m4a"), repo.clipLabels().keys)
    }

    @Test
    fun `a record of a clip whose file is gone is dropped on the next save`() = runTest {
        sound("a.m4a", 1)
        repo.recordClipLabels(boardWith(Tile(id = "a", label = "Yes", fileName = "a.m4a")))
        files.delete(repo.soundPath("a.m4a"))
        sound("b.m4a", 1)

        repo.recordClipLabels(boardWith(Tile(id = "b", label = "No", fileName = "b.m4a")))

        assertEquals(setOf("b.m4a"), repo.clipLabels().keys)
    }

    @Test
    fun `a missing or unreadable clip-labels file falls back to numbered names and is rewritten`() = runTest {
        sound("a.m4a", 1)
        sound("b.m4a", 2)
        assertTrue(repo.clipLabels().isEmpty())
        files.put("clip-labels.json", "{ not json".encodeToByteArray())

        assertTrue(repo.clipLabels().isEmpty())
        assertEquals(listOf("Unused clip 1", "Unused clip 2"), repo.unusedClipNames(listOf("a.m4a", "b.m4a")).map { it.description })
        repo.recordClipLabels(boardWith(Tile(id = "a", label = "Yes", fileName = "a.m4a")))

        assertEquals("Yes", repo.clipLabels().getValue("a.m4a").label)
    }

    @Test
    fun `the unused clips zip has a folder per page and numbers clips with no record`() = runTest {
        listOf("a", "b", "c", "d", "e", "f").forEach { sound("$it.m4a", it[0].code.toByte()) }
        val board = yesNoBoard()
        repo.recordClipLabels(board)
        // e and f were never on a tile; b is no longer.
        repo.recordClipLabels(board.updatingTile("b") { it.copy(fileName = null) })
        val target = CapturingSaveTarget()

        assertTrue(repo.exportFiles(listOf("a.m4a", "b.m4a", "c.m4a", "d.m4a", "e.m4a", "f.m4a"), target))

        val entries = zip.read(target.written!!).associate { it.name to it.bytes }
        assertEquals(
            setOf("Home/Yes.m4a", "Home/No.m4a", "Food/Yes.m4a", "Food/Happy.m4a", "Unused clip 1.m4a", "Unused clip 2.m4a"),
            entries.keys
        )
        assertContentEquals(byteArrayOf('c'.code.toByte()), entries.getValue("Food/Yes.m4a"))
        assertContentEquals(byteArrayOf('f'.code.toByte()), entries.getValue("Unused clip 2.m4a"))
    }

    @Test
    fun `two pages named alike share a folder and duplicate names follow the backup rules`() = runTest {
        listOf("a", "b", "c").forEach { sound("$it.m4a", 1) }
        val board = Board(
            pages = listOf(
                Page(name = "Food", rows = 1, columns = 2, tiles = listOf(Tile(id = "a", label = "Yes", fileName = "a.m4a"), Tile(id = "b", label = "yes", fileName = "b.m4a"))),
                Page(name = "food", rows = 1, columns = 1, tiles = listOf(Tile(id = "c", label = "YES", fileName = "c.m4a")))
            )
        ).normalized()
        repo.recordClipLabels(board)

        val names = repo.unusedClipNames(listOf("a.m4a", "b.m4a", "c.m4a")).map { it.zipPath }

        assertEquals(listOf("Food/Yes.m4a", "Food/yes (Food).m4a", "Food/YES (Food) 2.m4a"), names)
    }

    @Test
    fun `clip labels are not part of a backup`() = runTest {
        sound("a.m4a", 1)
        val board = boardWith(Tile(id = "a", label = "Yes", fileName = "a.m4a"))
        repo.save(board)
        repo.recordClipLabels(board)

        assertTrue(backupEntries().keys.none { "clip-labels" in it })
    }
}
