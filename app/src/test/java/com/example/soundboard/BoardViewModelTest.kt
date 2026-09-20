package com.example.soundboard

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.PresetRepository
import com.example.soundboard.data.SettingsRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
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
    private lateinit var recorder: FakeRecorder
    private lateinit var presetRepo: PresetRepository
    private lateinit var settingsRepo: SettingsRepository

    private fun newViewModel() =
        BoardViewModel(repo, player, recorder, presetRepo, settingsRepo, ioDispatcher = UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = BoardRepository(context)
        player = FakePlayer()
        recorder = FakeRecorder()
        presetRepo = PresetRepository(context)
        settingsRepo = SettingsRepository(context)
    }

    private fun boardWith(vararg tiles: Tile) = Board(
        pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList()))
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

        assertEquals("new", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
        assertEquals("keep", vm.board.value.currentPage.tiles.first { it.id == "b" }.label)
    }

    @Test
    fun `assignSound imports the file loads it and points the tile at the returned name`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        val uri = Uri.parse("content://fake/clip.mp3")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("clip".toByteArray()))

        vm.assignSound("a", uri)

        val tile = vm.board.value.currentPage.tiles.first { it.id == "a" }
        assertTrue(tile.fileName != null)
        assertTrue(player.loaded.contains(tile.fileName))
    }

    @Test
    fun `recording start-stop points the tile at the recorded file and loads it`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.startRecording()
        assertTrue(vm.isRecording.value)
        val recordedFile = recorder.startedFile
        assertTrue(recordedFile != null)

        vm.stopRecording("a")

        assertFalse(vm.isRecording.value)
        val tile = vm.board.value.currentPage.tiles.first { it.id == "a" }
        assertEquals(recordedFile!!.name, tile.fileName)
        assertTrue(player.loaded.contains(tile.fileName))
    }

    @Test
    fun `a failed stop leaves the tile untouched deletes the partial file and reports a message`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        recorder.stopSucceeds = false

        vm.startRecording()
        val recordedFile = recorder.startedFile!!.apply { parentFile?.mkdirs(); writeText("partial") }

        vm.stopRecording("a")

        assertFalse(vm.isRecording.value)
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.fileName)
        assertFalse(recordedFile.exists())
        assertEquals("Recording failed", vm.message.value)
    }

    @Test
    fun `cancelRecording discards the in-progress file without touching the tile`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.startRecording()
        val recordedFile = recorder.startedFile!!.apply { parentFile?.mkdirs(); writeText("abandoned") }

        vm.cancelRecording()

        assertFalse(vm.isRecording.value)
        assertTrue(recorder.cancelled)
        assertFalse(recordedFile.exists())
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.fileName)
    }

    @Test
    fun `stopPinnedRecording points a pinned tile at the recorded file`() {
        repo.save(Board(pinnedTiles = listOf(Tile(id = "hey", label = "Hey"))))
        val vm = newViewModel()

        vm.startRecording()
        val recordedFile = recorder.startedFile!!

        vm.stopPinnedRecording("hey")

        val tile = vm.board.value.pinnedTiles.first { it.id == "hey" }
        assertEquals(recordedFile.name, tile.fileName)
    }

    @Test
    fun `clearTile blanks the tile unloads the clip and deletes the audio file`() {
        repo.save(boardWith(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("data")
        val vm = newViewModel()

        vm.clearTile("a")

        val tile = vm.board.value.currentPage.tiles.first { it.id == "a" }
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
                pages = listOf(
                    Page(
                        rows = 1,
                        columns = 2,
                        tiles = listOf(Tile(id = "a", fileName = "a.mp3"), Tile(id = "b", fileName = "b.mp3"))
                    )
                )
            )
        )
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        repo.soundFile("b.mp3").apply { parentFile?.mkdirs() }.writeText("b")
        val vm = newViewModel()

        vm.resize(0, 1, 1)

        assertTrue(repo.soundFile("a.mp3").exists())
        assertTrue(repo.soundFile("b.mp3").exists())
        assertFalse(player.unloaded.contains("b.mp3"))
        assertEquals(1, vm.board.value.currentPage.visibleTiles.size)
        assertEquals(2, vm.board.value.currentPage.tiles.size)
    }

    @Test
    fun `every mutation persists across a fresh view model`() {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.setLabel("a", "new")

        val second = newViewModel()
        assertEquals("new", second.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `renameBoard updates the board name and persists it`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.renameBoard("Family Board")

        assertEquals("Family Board", vm.board.value.name)
        val second = newViewModel()
        assertEquals("Family Board", second.board.value.name)
    }

    @Test
    fun `renameBoard falls back to New Board when given a blank name`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.renameBoard("   ")

        assertEquals("New Board", vm.board.value.name)
    }

    @Test
    fun `addPage appends a page and switches to it and persists`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.addPage("Feelings")

        assertEquals(listOf("Page 1", "Feelings"), vm.board.value.pages.map { it.name })
        assertEquals(1, vm.board.value.currentPageIndex)
        val second = newViewModel()
        assertEquals(listOf("Page 1", "Feelings"), second.board.value.pages.map { it.name })
    }

    @Test
    fun `renamePage updates the page name and persists it`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.renamePage(0, "Requests")

        assertEquals("Requests", vm.board.value.pages[0].name)
        val second = newViewModel()
        assertEquals("Requests", second.board.value.pages[0].name)
    }

    @Test
    fun `deletePage removes the page when more than one exists`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.deletePage(0)

        assertEquals(listOf("B"), vm.board.value.pages.map { it.name })
    }

    @Test
    fun `switchPage changes the current page without persisting`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.switchPage(1)

        assertEquals(1, vm.board.value.currentPageIndex)
        val second = newViewModel()
        assertEquals(0, second.board.value.currentPageIndex)
    }

    @Test
    fun `a sound referenced only on a non-current page is preloaded and survives pruning`() {
        repo.save(
            Board(
                pages = listOf(
                    Page(name = "A", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", fileName = "a.mp3"))),
                    Page(name = "B", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", fileName = "b.mp3")))
                )
            )
        )
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        repo.soundFile("b.mp3").apply { parentFile?.mkdirs() }.writeText("b")
        val vm = newViewModel()

        assertTrue(player.loaded.contains("b.mp3"))

        vm.setLabel("a", "renamed")

        assertTrue(repo.soundFile("b.mp3").exists())
    }

    @Test
    fun `setHomePage marks the current page as home and persists it`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B")), currentPageIndex = 1))
        val vm = newViewModel()

        vm.setHomePage(1)

        assertEquals(1, vm.board.value.homePageIndex)
        val second = newViewModel()
        assertEquals(1, second.board.value.homePageIndex)
    }

    @Test
    fun `a fresh view model resumes the last-viewed page by default`() {
        repo.save(Board(pages = listOf(Page(name = "A", isHome = true), Page(name = "B")), currentPageIndex = 1))

        val vm = newViewModel()

        assertEquals(1, vm.board.value.currentPageIndex)
    }

    @Test
    fun `openOnHomePage jumps a fresh view model to the home page without touching disk`() {
        repo.save(Board(pages = listOf(Page(name = "A", isHome = true), Page(name = "B")), currentPageIndex = 1))
        settingsRepo.openOnHomePage = true

        val vm = newViewModel()

        assertEquals(0, vm.board.value.currentPageIndex)
        // Only the in-memory pager start changed — the saved page (from switchPage's own
        // page-switch semantics) is untouched, same as any other in-session page switch.
        assertEquals(1, repo.load().currentPageIndex)
    }

    @Test
    fun `openOnHomePage is a no-op when no page is marked home`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B")), currentPageIndex = 1))
        settingsRepo.openOnHomePage = true

        val vm = newViewModel()

        assertEquals(1, vm.board.value.currentPageIndex)
    }

    @Test
    fun `setOpenOnHomePage persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setOpenOnHomePage(true)

        assertTrue(vm.openOnHomePage.value)
        val second = newViewModel()
        assertTrue(second.openOnHomePage.value)
    }

    @Test
    fun `setLongPressDurationMillis persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setLongPressDurationMillis(800)

        assertEquals(800, vm.longPressDurationMillis.value)
        val second = newViewModel()
        assertEquals(800, second.longPressDurationMillis.value)
    }

    @Test
    fun `addPinnedRow materializes a fixed-width empty row regardless of the current page and persists`() {
        // Fixed at 4 regardless of the page's own column count (3 here) — the
        // pinned row is deliberately decoupled from page grid width (#15).
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b"), Tile(id = "c")))
        val vm = newViewModel()

        vm.addPinnedRow()

        assertEquals(4, vm.board.value.pinnedTiles.size)
        assertTrue(vm.board.value.pinnedTiles.all { it.isEmpty })
        val second = newViewModel()
        assertEquals(4, second.board.value.pinnedTiles.size)
    }

    @Test
    fun `addPinnedRow is a no-op once a pinned row already exists`() {
        repo.save(Board(pinnedTiles = listOf(Tile(id = "hey", label = "Hey"))))
        val vm = newViewModel()

        vm.addPinnedRow()

        assertEquals(listOf("hey"), vm.board.value.pinnedTiles.map { it.id })
    }

    @Test
    fun `resize does not touch the pinned row`() {
        repo.save(
            Board(
                pages = listOf(Page(rows = 1, columns = 2)),
                pinnedTiles = listOf(Tile(id = "hey", label = "Hey"), Tile(id = "sos", label = "911"))
            )
        )
        val vm = newViewModel()

        vm.resize(0, 1, 4)

        assertEquals(listOf("hey", "sos"), vm.board.value.pinnedTiles.map { it.id })
    }

    @Test
    fun `resize, setTileAspectRatio and setPageColor target the given page, not the current one`() {
        // PageOptionsDialog opens for whichever page was long-pressed, which may not be
        // the page currently on screen — these must not silently fall back to currentPage.
        repo.save(Board(pages = listOf(Page(name = "A"), Page(rows = 1, columns = 2, name = "B")), currentPageIndex = 0))
        val vm = newViewModel()

        vm.resize(1, 3, 3)
        vm.setTileAspectRatio(1, 4f / 3f)
        vm.setPageColor(1, 0xFF00FF00.toInt())

        val untouched = vm.board.value.pages[0]
        assertEquals(4, untouched.rows)
        assertEquals(4, untouched.columns)
        assertEquals(1f, untouched.tileAspectRatio)
        assertEquals(null, untouched.color)

        val targeted = vm.board.value.pages[1]
        assertEquals(3, targeted.rows)
        assertEquals(3, targeted.columns)
        assertEquals(4f / 3f, targeted.tileAspectRatio)
        assertEquals(0xFF00FF00.toInt(), targeted.color)
    }

    @Test
    fun `setPinnedLabel updates a pinned tile and leaves page tiles alone`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "old")))), pinnedTiles = listOf(Tile(id = "hey", label = "old"))))
        val vm = newViewModel()

        vm.setPinnedLabel("hey", "Hey!")

        assertEquals("Hey!", vm.board.value.pinnedTiles.first { it.id == "hey" }.label)
        assertEquals("old", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `clearing a pinned tile unloads and deletes its sound`() {
        repo.save(Board(pinnedTiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3"))))
        repo.soundFile("hey.mp3").apply { parentFile?.mkdirs() }.writeText("hey")
        val vm = newViewModel()

        vm.clearPinnedTile("hey")

        val tile = vm.board.value.pinnedTiles.first { it.id == "hey" }
        assertEquals("", tile.label)
        assertNull(tile.fileName)
        assertTrue(player.unloaded.contains("hey.mp3"))
        assertFalse(repo.soundFile("hey.mp3").exists())
    }

    @Test
    fun `a sound referenced only by a pinned tile is preloaded and survives pruning`() {
        repo.save(
            Board(
                pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", fileName = "a.mp3")))),
                pinnedTiles = listOf(Tile(id = "hey", fileName = "hey.mp3"))
            )
        )
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        repo.soundFile("hey.mp3").apply { parentFile?.mkdirs() }.writeText("hey")
        val vm = newViewModel()

        assertTrue(player.loaded.contains("hey.mp3"))

        vm.setLabel("a", "renamed")

        assertTrue(repo.soundFile("hey.mp3").exists())
    }

    @Test
    fun `applyPreset with a factory ref loads the bundled asset`() {
        val vm = newViewModel()

        vm.applyPreset(PresetRef.Factory("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals("Jeremy Draft Care Board", vm.board.value.name)
        assertEquals(listOf("Trouble", "Needs", "Talking", "Well Wishes"), vm.board.value.pages.map { it.name })
        assertEquals("Loaded \"Jeremy Draft Care Board\"", vm.message.value)
    }

    @Test
    fun `saveAsPreset renames the board and appears in presets`() {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.saveAsPreset("My Layout")

        assertEquals("My Layout", vm.board.value.name)
        assertEquals(listOf("My Layout"), vm.presets.value.map { it.name })
    }

    @Test
    fun `applyPreset with a saved ref restores that snapshot`() {
        repo.save(boardWith(Tile(id = "a", label = "first")))
        val vm = newViewModel()
        vm.saveAsPreset("Version 1")
        val savedId = vm.presets.value.first().id

        vm.setLabel("a", "changed")
        vm.applyPreset(PresetRef.Saved(savedId))

        assertEquals("first", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `applyPreset with an unknown saved id reports failure without touching the board`() {
        repo.save(boardWith(Tile(id = "a", label = "unchanged")))
        val vm = newViewModel()

        vm.applyPreset(PresetRef.Saved("does-not-exist"))

        assertEquals("unchanged", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
        assertEquals("Couldn't load preset", vm.message.value)
    }

    @Test
    fun `saving a preset protects its sound from being pruned after the live tile changes`() {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        val vm = newViewModel()

        vm.saveAsPreset("Backup layout")
        vm.clearTile("a")

        assertTrue(repo.soundFile("a.mp3").exists())
    }
}
