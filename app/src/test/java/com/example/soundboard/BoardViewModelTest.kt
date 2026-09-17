package com.example.soundboard

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Tile
import java.io.ByteArrayInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BoardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: android.content.Context
    private lateinit var repo: BoardRepository
    private lateinit var player: FakePlayer

    private fun newViewModel() =
        BoardViewModel(repo, player, ioDispatcher = UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = BoardRepository(context)
        player = FakePlayer()
    }

    private fun boardWith(vararg tiles: Tile) = Board(
        rows = 1,
        columns = tiles.size,
        tiles = tiles.toList()
    )

    @Test
    fun `play on a filled tile calls through to the player`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", fileName = "a.mp3", volume = 0.7f)

        vm.play(tile)

        assertEquals(listOf("a.mp3" to 0.7f), player.played)
    }

    @Test
    fun `play on an empty tile does nothing`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", fileName = null)

        vm.play(tile)

        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `setLabel updates only the target tile`() {
        repo.save(boardWith(Tile(id = "a", label = "old"), Tile(id = "b", label = "keep")))
        val vm = newViewModel()

        vm.setLabel("a", "new")

        assertEquals("new", vm.board.value.tiles.first { it.id == "a" }.label)
        assertEquals("keep", vm.board.value.tiles.first { it.id == "b" }.label)
    }

    @Test
    fun `assignSound imports the file loads it and points the tile at the returned name`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        val uri = Uri.parse("content://fake/clip.mp3")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("clip".toByteArray()))

        vm.assignSound("a", uri)

        val tile = vm.board.value.tiles.first { it.id == "a" }
        assertTrue(tile.fileName != null)
        assertTrue(player.loaded.contains(tile.fileName))
    }

    @Test
    fun `clearTile blanks the tile unloads the clip and deletes the audio file`() {
        repo.save(boardWith(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("data")
        val vm = newViewModel()

        vm.clearTile("a")

        val tile = vm.board.value.tiles.first { it.id == "a" }
        assertEquals("", tile.label)
        assertNull(tile.fileName)
        assertTrue(player.unloaded.contains("a.mp3"))
        assertFalse(repo.soundFile("a.mp3").exists())
    }

    @Test
    fun `replacing a tiles sound unloads the old clip and deletes the old file`() {
        repo.save(boardWith(Tile(id = "a", fileName = "old.mp3")))
        repo.soundFile("old.mp3").apply { parentFile?.mkdirs() }.writeText("old")
        val vm = newViewModel()

        val uri = Uri.parse("content://fake/new.mp3")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("new".toByteArray()))

        vm.assignSound("a", uri)

        assertTrue(player.unloaded.contains("old.mp3"))
        assertFalse(repo.soundFile("old.mp3").exists())
    }

    @Test
    fun `clearing a tile whose file is shared does not delete the shared file`() {
        repo.save(
            boardWith(
                Tile(id = "a", fileName = "shared.mp3"),
                Tile(id = "b", fileName = "shared.mp3")
            )
        )
        repo.soundFile("shared.mp3").apply { parentFile?.mkdirs() }.writeText("shared")
        val vm = newViewModel()

        vm.clearTile("a")

        assertTrue(repo.soundFile("shared.mp3").exists())
    }

    @Test
    fun `shrinking the grid preserves the audio of hidden tiles`() {
        // Board.resized() never drops a tile — shrinking only hides it from
        // visibleTiles. Its sound survives until the tile is cleared directly
        // (see commit 7e67b7d, "Preserve hidden tiles' sounds when shrinking").
        repo.save(
            Board(
                rows = 1,
                columns = 2,
                tiles = listOf(Tile(id = "a", fileName = "a.mp3"), Tile(id = "b", fileName = "b.mp3"))
            )
        )
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        repo.soundFile("b.mp3").apply { parentFile?.mkdirs() }.writeText("b")
        val vm = newViewModel()

        vm.resize(1, 1)

        assertTrue(repo.soundFile("a.mp3").exists())
        assertTrue(repo.soundFile("b.mp3").exists())
        assertFalse(player.unloaded.contains("b.mp3"))
        assertEquals(1, vm.board.value.visibleTiles.size)
        assertEquals(2, vm.board.value.tiles.size)
    }

    @Test
    fun `every mutation persists across a fresh view model`() {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.setLabel("a", "new")

        val second = newViewModel()
        assertEquals("new", second.board.value.tiles.first { it.id == "a" }.label)
    }
}
