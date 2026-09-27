package com.example.soundboard.data

import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
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
 * [BoardRepository]'s own logic against in-memory storage, on every platform. The Android
 * app's Robolectric tests cover the same class wired to real files, zips and assets.
 */
class BoardRepositoryCommonTest {

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

    @Test
    fun `saved boards list newest first and report every sound they reference`() = runTest {
        val saved = SavedBoardRepository(files)
        val first = saved.save(boardWith(Tile(id = "a", fileName = "a.m4a")).copy(name = "First"))
        val second = saved.save(boardWith(Tile(id = "b", fileName = "b.m4a")).copy(name = "Second"))

        assertEquals(listOf(second, first), saved.list().map { it.id })
        assertEquals(setOf("a.m4a", "b.m4a"), saved.allReferencedFileNames())
        assertEquals("First", saved.load(first)?.name)
    }
}
