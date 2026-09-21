package com.example.soundboard

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.PresetRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.ThemeMode
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
    private lateinit var speaker: FakeSpeaker

    private fun newViewModel() =
        BoardViewModel(repo, player, recorder, presetRepo, speaker, ioDispatcher = UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = BoardRepository(context)
        player = FakePlayer()
        recorder = FakeRecorder()
        presetRepo = PresetRepository(context)
        speaker = FakeSpeaker()
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
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play on a tile with speakLabel set but no sound speaks its label`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "I need water", speakLabel = true)

        vm.play(tile)

        assertEquals(listOf("I need water"), speaker.spoken)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `play prefers a sound file over speaking even when speakLabel is set`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Air horn", fileName = "a.mp3", speakLabel = true)

        vm.play(tile)

        assertEquals(listOf("a.mp3" to 1f), player.played)
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play on a speakLabel tile with a blank label does nothing`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "", speakLabel = true)

        vm.play(tile)

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `a speakLabel tile is not empty`() {
        assertFalse(Tile(speakLabel = true).isEmpty)
    }

    @Test
    fun `setSpeakLabel updates only the target tile`() {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setSpeakLabel("a", true)

        assertTrue(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakLabel)
        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "b" }.speakLabel)
    }

    @Test
    fun `setHomeRowSpeakLabel updates the home page tile and leaves the current page alone`() {
        repo.save(
            Board(
                pages = listOf(
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))),
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey")), isHome = true)
                ),
                currentPageIndex = 0
            )
        )
        val vm = newViewModel()

        vm.setHomeRowSpeakLabel("hey", true)

        assertTrue(vm.board.value.homePage!!.tiles.first { it.id == "hey" }.speakLabel)
        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakLabel)
    }

    @Test
    fun `clearTile also turns off speakLabel`() {
        repo.save(boardWith(Tile(id = "a", label = "Water", speakLabel = true)))
        val vm = newViewModel()

        vm.clearTile("a")

        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakLabel)
    }

    @Test
    fun `clearHomeRowTile also turns off speakLabel`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", speakLabel = true)), isHome = true))))
        val vm = newViewModel()

        vm.clearHomeRowTile("hey")

        assertFalse(vm.board.value.homePage!!.tiles.first { it.id == "hey" }.speakLabel)
    }

    @Test
    fun `speakAdHoc speaks arbitrary text without touching any tile`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.speakAdHoc("I'll be there in five minutes")

        assertEquals(listOf("I'll be there in five minutes"), speaker.spoken)
    }

    @Test
    fun `setSpeakLabel persists across a fresh view model`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.setSpeakLabel("a", true)

        val second = newViewModel()
        assertTrue(second.board.value.currentPage.tiles.first { it.id == "a" }.speakLabel)
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
    fun `stopHomeRowRecording points the home page tile at the recorded file`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey")), isHome = true))))
        val vm = newViewModel()

        vm.startRecording()
        val recordedFile = recorder.startedFile!!

        vm.stopHomeRowRecording("hey")

        val tile = vm.board.value.homePage!!.tiles.first { it.id == "hey" }
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
        repo.save(
            Board(
                pages = listOf(Page(name = "A", isHome = true), Page(name = "B")),
                currentPageIndex = 1,
                openOnHomePage = true
            )
        )

        val vm = newViewModel()

        assertEquals(0, vm.board.value.currentPageIndex)
        // Only the in-memory pager start changed — the saved page (from switchPage's own
        // page-switch semantics) is untouched, same as any other in-session page switch.
        assertEquals(1, repo.load().currentPageIndex)
    }

    @Test
    fun `openOnHomePage is a no-op when no page is marked home`() {
        repo.save(
            Board(
                pages = listOf(Page(name = "A"), Page(name = "B")),
                currentPageIndex = 1,
                openOnHomePage = true
            )
        )

        val vm = newViewModel()

        assertEquals(1, vm.board.value.currentPageIndex)
    }

    @Test
    fun `setOpenOnHomePage persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setOpenOnHomePage(true)

        assertTrue(vm.board.value.openOnHomePage)
        val second = newViewModel()
        assertTrue(second.board.value.openOnHomePage)
    }

    @Test
    fun `setIdleTimeoutMinutes persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setIdleTimeoutMinutes(2)

        assertEquals(2, vm.board.value.idleTimeoutMinutes)
        val second = newViewModel()
        assertEquals(2, second.board.value.idleTimeoutMinutes)
    }

    @Test
    fun `setLongPressDurationMillis persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setLongPressDurationMillis(800)

        assertEquals(800, vm.board.value.longPressDurationMillis)
        val second = newViewModel()
        assertEquals(800, second.board.value.longPressDurationMillis)
    }

    @Test
    fun `setThemeMode persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, vm.board.value.themeMode)
        val second = newViewModel()
        assertEquals(ThemeMode.DARK, second.board.value.themeMode)
    }

    @Test
    fun `setKeepScreenAwake persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setKeepScreenAwake(true)

        assertTrue(vm.board.value.keepScreenAwake)
        val second = newViewModel()
        assertTrue(second.board.value.keepScreenAwake)
    }

    @Test
    fun `setHapticFeedbackEnabled persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setHapticFeedbackEnabled(false)

        assertFalse(vm.board.value.hapticFeedbackEnabled)
        val second = newViewModel()
        assertFalse(second.board.value.hapticFeedbackEnabled)
    }

    @Test
    fun `settings are captured by saveAsPreset and restored by applyPreset`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        vm.setOpenOnHomePage(true)
        vm.setIdleTimeoutMinutes(2)
        vm.setLongPressDurationMillis(800)
        vm.setThemeMode(ThemeMode.DARK)
        vm.setKeepScreenAwake(true)
        vm.setHapticFeedbackEnabled(false)
        vm.setStickyHomeRowEnabled(true)
        vm.setDefaultPageRows(2)
        vm.setDefaultPageColumns(6)

        vm.saveAsPreset("Version 1")
        val savedId = vm.presets.value.first().id
        vm.setOpenOnHomePage(false)
        vm.setIdleTimeoutMinutes(5)
        vm.setLongPressDurationMillis(500)
        vm.setThemeMode(ThemeMode.SYSTEM)
        vm.setKeepScreenAwake(false)
        vm.setHapticFeedbackEnabled(true)
        vm.setStickyHomeRowEnabled(false)
        vm.setDefaultPageRows(4)
        vm.setDefaultPageColumns(4)

        vm.applyPreset(PresetRef.Saved(savedId))

        assertTrue(vm.board.value.openOnHomePage)
        assertEquals(2, vm.board.value.idleTimeoutMinutes)
        assertEquals(800, vm.board.value.longPressDurationMillis)
        assertEquals(ThemeMode.DARK, vm.board.value.themeMode)
        assertTrue(vm.board.value.keepScreenAwake)
        assertFalse(vm.board.value.hapticFeedbackEnabled)
        assertTrue(vm.board.value.stickyHomeRowEnabled)
        assertEquals(2, vm.board.value.defaultPageRows)
        assertEquals(6, vm.board.value.defaultPageColumns)
    }

    @Test
    fun `setStickyHomeRowEnabled persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setStickyHomeRowEnabled(true)

        assertTrue(vm.board.value.stickyHomeRowEnabled)
        val second = newViewModel()
        assertTrue(second.board.value.stickyHomeRowEnabled)
    }

    @Test
    fun `setDefaultPageRows and setDefaultPageColumns persist across a fresh view model`() {
        val vm = newViewModel()

        vm.setDefaultPageRows(2)
        vm.setDefaultPageColumns(6)

        assertEquals(2, vm.board.value.defaultPageRows)
        assertEquals(6, vm.board.value.defaultPageColumns)
        val second = newViewModel()
        assertEquals(2, second.board.value.defaultPageRows)
        assertEquals(6, second.board.value.defaultPageColumns)
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
    fun `setHomeRowLabel updates the home page tile regardless of the current page`() {
        repo.save(
            Board(
                pages = listOf(
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "old"))),
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "old")), isHome = true)
                ),
                currentPageIndex = 0
            )
        )
        val vm = newViewModel()

        vm.setHomeRowLabel("hey", "Hey!")

        assertEquals("Hey!", vm.board.value.homePage!!.tiles.first { it.id == "hey" }.label)
        assertEquals("old", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `clearing a home row tile unloads and deletes its sound`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true))))
        repo.soundFile("hey.mp3").apply { parentFile?.mkdirs() }.writeText("hey")
        val vm = newViewModel()

        vm.clearHomeRowTile("hey")

        val tile = vm.board.value.homePage!!.tiles.first { it.id == "hey" }
        assertEquals("", tile.label)
        assertNull(tile.fileName)
        assertTrue(player.unloaded.contains("hey.mp3"))
        assertFalse(repo.soundFile("hey.mp3").exists())
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
