package com.example.soundboard

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
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
    private lateinit var savedBoardRepo: SavedBoardRepository
    private lateinit var speaker: FakeSpeaker
    private lateinit var devicePrefs: DevicePreferences
    private lateinit var recentBoardsRepo: RecentBoardsRepository

    private fun newViewModel() =
        BoardViewModel(repo, player, recorder, savedBoardRepo, speaker, devicePrefs, recentBoardsRepo, ioDispatcher = UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = BoardRepository(context)
        player = FakePlayer()
        recorder = FakeRecorder()
        savedBoardRepo = SavedBoardRepository(context)
        speaker = FakeSpeaker()
        recentBoardsRepo = RecentBoardsRepository(context)
        devicePrefs = DevicePreferences(context)
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
    fun `play on a tile with speakWhenNoSound set but no sound speaks its label`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "I need water", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("I need water"), speaker.spoken)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `play prefers a sound file over speaking even when speakWhenNoSound is set`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Air horn", fileName = "a.mp3", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("a.mp3" to 1f), player.played)
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play on a speakWhenNoSound tile with a blank label does nothing`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "", speakWhenNoSound = true)

        vm.play(tile)

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play speaks ttsScript instead of the label when set`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Water", ttsScript = "I would like a glass of water please", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("I would like a glass of water please"), speaker.spoken)
    }

    @Test
    fun `play falls back to the label when ttsScript is null`() {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Water", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("Water"), speaker.spoken)
    }

    @Test
    fun `a speakWhenNoSound tile has sound`() {
        assertTrue(Tile(speakWhenNoSound = true).hasSound)
    }

    @Test
    fun `play speaks an unrecorded labeled tile when the fallback setting is on`() {
        repo.save(boardWith(Tile(id = "a", label = "Water")))
        val vm = newViewModel()
        assertTrue(vm.board.value.speakUnrecordedTilesEnabled)

        vm.play(vm.board.value.currentPage.tiles.first { it.id == "a" })

        assertEquals(listOf("Water"), speaker.spoken)
    }

    @Test
    fun `play does nothing for an unrecorded labeled tile when the fallback setting is off`() {
        repo.save(boardWith(Tile(id = "a", label = "Water")).copy(speakUnrecordedTilesEnabled = false))
        val vm = newViewModel()

        vm.play(vm.board.value.currentPage.tiles.first { it.id == "a" })

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `setSpeakWhenNoSound updates only the target tile`() {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setSpeakWhenNoSound("a", true)

        assertTrue(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "b" }.speakWhenNoSound)
    }

    @Test
    fun `setSpeakWhenNoSound edits a home page tile from another page and leaves the current page alone`() {
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

        vm.setSpeakWhenNoSound("hey", true)

        assertTrue(vm.board.value.homePage!!.tiles.first { it.id == "hey" }.speakWhenNoSound)
        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
    }

    @Test
    fun `setTtsScript updates only the target tile`() {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setTtsScript("a", "I would like a glass of water please")

        assertEquals("I would like a glass of water please", vm.board.value.currentPage.tiles.first { it.id == "a" }.ttsScript)
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "b" }.ttsScript)
    }

    @Test
    fun `setTtsScript stores null instead of a blank script`() {
        repo.save(boardWith(Tile(id = "a", ttsScript = "old script")))
        val vm = newViewModel()

        vm.setTtsScript("a", "   ")

        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.ttsScript)
    }

    @Test
    fun `setTtsScript edits a home page tile from another page and leaves the current page alone`() {
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

        vm.setTtsScript("hey", "Come here please")

        assertEquals("Come here please", vm.board.value.homePage!!.tiles.first { it.id == "hey" }.ttsScript)
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.ttsScript)
    }

    @Test
    fun `clearTile also turns off speakWhenNoSound`() {
        repo.save(boardWith(Tile(id = "a", label = "Water", speakWhenNoSound = true)))
        val vm = newViewModel()

        vm.clearTile("a")

        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
    }

    @Test
    fun `clearTile on a home page tile also turns off speakWhenNoSound`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", speakWhenNoSound = true)), isHome = true))))
        val vm = newViewModel()

        vm.clearTile("hey")

        assertFalse(vm.board.value.homePage!!.tiles.first { it.id == "hey" }.speakWhenNoSound)
    }

    @Test
    fun `removeSound clears the file but keeps the label and speakWhenNoSound`() {
        repo.save(boardWith(Tile(id = "a", label = "Water", fileName = "a.mp3", speakWhenNoSound = true)))
        val vm = newViewModel()

        vm.removeSound("a")

        val tile = vm.board.value.currentPage.tiles.first { it.id == "a" }
        assertNull(tile.fileName)
        assertEquals("Water", tile.label)
        assertTrue(tile.speakWhenNoSound)
    }

    @Test
    fun `removeSound on a home page tile clears the file but keeps the label and speakWhenNoSound`() {
        repo.save(
            Board(
                pages = listOf(
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3", speakWhenNoSound = true)), isHome = true)
                )
            )
        )
        val vm = newViewModel()

        vm.removeSound("hey")

        val tile = vm.board.value.homePage!!.tiles.first { it.id == "hey" }
        assertNull(tile.fileName)
        assertEquals("Hey", tile.label)
        assertTrue(tile.speakWhenNoSound)
    }

    @Test
    fun `removeSound prunes the now-orphaned file`() {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        val vm = newViewModel()

        vm.removeSound("a")

        assertFalse(repo.soundFile("a.mp3").exists())
    }

    @Test
    fun `speakAdHoc speaks arbitrary text without touching any tile`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.speakAdHoc("I'll be there in five minutes")

        assertEquals(listOf("I'll be there in five minutes"), speaker.spoken)
    }

    @Test
    fun `setSpeakWhenNoSound persists across a fresh view model`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.setSpeakWhenNoSound("a", true)

        val second = newViewModel()
        assertTrue(second.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
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
    fun `filling the last empty tile in the last row grows the page by one row`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        val uri = Uri.parse("content://fake/clip.mp3")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("clip".toByteArray()))
        vm.assignSound("a", uri)

        val page = vm.board.value.currentPage
        assertEquals(2, page.rows)
        assertEquals(2, page.visibleTiles.size)
        assertFalse(page.visibleTiles[1].hasSound)
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
    fun `stopRecording points a home page tile at the recorded file`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey")), isHome = true))))
        val vm = newViewModel()

        vm.startRecording()
        val recordedFile = recorder.startedFile!!

        vm.stopRecording("hey")

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
    fun `shrinking the grid never hides or drops a tile with a sound`() {
        // Page.withGridSize() never drops a tile, and Page.normalized() keeps rows from
        // shrinking past the last tile with content — so a sound is never hidden in
        // either orientation, and survives until the tile is cleared directly.
        repo.save(
            Board(
                pages = listOf(
                    Page(
                        rows = 1,
                        columns = 3,
                        tiles = listOf(Tile(id = "a", fileName = "a.mp3"), Tile(id = "b", fileName = "b.mp3"), Tile(id = "c"))
                    )
                )
            )
        )
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        repo.soundFile("b.mp3").apply { parentFile?.mkdirs() }.writeText("b")
        val vm = newViewModel()

        vm.resize(0, 1, 1)

        val page = vm.board.value.currentPage
        assertTrue(repo.soundFile("a.mp3").exists())
        assertTrue(repo.soundFile("b.mp3").exists())
        assertFalse(player.unloaded.contains("b.mp3"))
        assertEquals(1, page.columns)
        // Two rows reach "b"; that last row is then full, so one blank row follows it.
        assertEquals(listOf("a", "b", "c"), page.visibleTiles.map { it.id })
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
    fun `performanceModeEnabled defaults to false`() {
        val vm = newViewModel()

        assertFalse(vm.performanceModeEnabled.value)
    }

    @Test
    fun `setPerformanceModeEnabled persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setPerformanceModeEnabled(true)

        assertTrue(vm.performanceModeEnabled.value)
        val second = newViewModel()
        assertTrue(second.performanceModeEnabled.value)
    }

    @Test
    fun `setPerformanceModeEnabled survives loading a different board`() {
        // Device-local (DevicePreferences), not board content — a preset with no
        // opinion of its own on this setting must not silently flip it back off.
        val vm = newViewModel()
        vm.setPerformanceModeEnabled(true)

        vm.openBoard(BoardRef.Saved(savedBoardRepo.save(Board(name = "Other"))))

        assertTrue(vm.performanceModeEnabled.value)
    }

    @Test
    fun `activate with Show mode off plays the tile and shows nothing`() {
        val vm = newViewModel()

        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertEquals(listOf("a.mp3"), player.playedKeys)
        assertNull(vm.shownText.value)
    }

    @Test
    fun `activate in Show mode shows the script and still plays`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)

        vm.activate(Tile(id = "a", label = "Water", ttsScript = "I would like some water", speakWhenNoSound = true))

        assertEquals("I would like some water", vm.shownText.value)
        assertEquals(listOf("I would like some water"), speaker.spoken)
    }

    @Test
    fun `activate in Show mode falls back to the label for a recorded tile`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)

        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertEquals("Water", vm.shownText.value)
        assertEquals(listOf("a.mp3"), player.playedKeys)
    }

    @Test
    fun `activate in Show mode with mute on shows the text without playing`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.setShowModeMuteSounds(true)

        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertEquals("Water", vm.shownText.value)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `activate in Show mode with nothing to show just plays, even when muted`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.setShowModeMuteSounds(true)

        vm.activate(Tile(id = "a", label = " ", fileName = "a.mp3"))

        assertNull(vm.shownText.value)
        assertEquals(listOf("a.mp3"), player.playedKeys)
    }

    @Test
    fun `activate on an unplayable tile shows nothing`() {
        val vm = newViewModel()
        vm.setSpeakUnrecordedTilesEnabled(false)
        vm.setShowModeEnabled(true)

        vm.activate(Tile(id = "a", label = "Awaiting a recording"))

        assertNull(vm.shownText.value)
    }

    @Test
    fun `play never opens the Show mode text`() {
        // The tile editor's Play button calls play() directly — previewing a recording
        // while editing shouldn't black out the screen.
        val vm = newViewModel()
        vm.setShowModeEnabled(true)

        vm.play(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertNull(vm.shownText.value)
    }

    @Test
    fun `dismissShownText closes the text`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        vm.dismissShownText()

        assertNull(vm.shownText.value)
    }

    @Test
    fun `turning Show mode off closes any text on screen`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        vm.setShowModeEnabled(false)

        assertNull(vm.shownText.value)
    }

    @Test
    fun `Show mode can't be left with neither a timer nor tap to close`() {
        val vm = newViewModel()

        vm.setShowModeTimerSeconds(0)
        vm.setShowModeTapToClose(false)
        assertTrue(vm.showMode.value.tapToClose)

        vm.setShowModeTimerSeconds(5)
        vm.setShowModeTapToClose(false)
        vm.setShowModeTimerSeconds(0)
        assertEquals(5, vm.showMode.value.timerSeconds)
        assertFalse(vm.showMode.value.tapToClose)
    }

    @Test
    fun `Show mode settings persist across a fresh view model and survive loading a different board`() {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.setShowModeTimerSeconds(30)
        vm.setShowModeMuteSounds(true)

        vm.openBoard(BoardRef.Saved(savedBoardRepo.save(Board(name = "Other"))))

        val expected = ShowModeSettings(enabled = true, timerSeconds = 30, tapToClose = true, muteSounds = true)
        assertEquals(expected, vm.showMode.value)
        assertEquals(expected, newViewModel().showMode.value)
    }

    @Test
    fun `settings are captured by saveBoardAs and restored by openBoard`() {
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

        vm.saveBoardAs("Version 1")
        val savedId = vm.savedBoards.value.first().id
        vm.setOpenOnHomePage(false)
        vm.setIdleTimeoutMinutes(5)
        vm.setLongPressDurationMillis(500)
        vm.setThemeMode(ThemeMode.SYSTEM)
        vm.setKeepScreenAwake(false)
        vm.setHapticFeedbackEnabled(true)
        vm.setStickyHomeRowEnabled(false)
        vm.setDefaultPageRows(4)
        vm.setDefaultPageColumns(4)

        vm.openBoard(BoardRef.Saved(savedId))

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
    fun `layout and label settings are captured by saveBoardAs and restored by openBoard`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        val labelStyle = LabelStyle(minSizeSp = 18f, maxSizeSp = 40f, font = LabelFont.LEXEND, bold = true, allCaps = true)
        vm.setLandscapeLayout(LandscapeLayout.FIT_TO_SCREEN)
        vm.setLandscapeGrid(0, 3, 5)
        vm.setRowHeight(RowHeight.TALL)
        vm.setPageRowHeight(0, RowHeight.SHORT)
        vm.setLabelStyle(labelStyle)

        vm.saveBoardAs("Version 1")
        val savedId = vm.savedBoards.value.first().id
        vm.setLandscapeLayout(LandscapeLayout.PAGE_GRID)
        vm.setLandscapeGrid(0, null, null)
        vm.setRowHeight(RowHeight.STANDARD)
        vm.setPageRowHeight(0, null)
        vm.setLabelStyle(LabelStyle())

        vm.openBoard(BoardRef.Saved(savedId))

        val board = vm.board.value
        assertEquals(LandscapeLayout.FIT_TO_SCREEN, board.landscapeLayout)
        assertEquals(3, board.pages[0].landscapeRows)
        assertEquals(5, board.pages[0].landscapeColumns)
        assertEquals(RowHeight.TALL, board.rowHeight)
        assertEquals(RowHeight.SHORT, board.pages[0].rowHeight)
        assertEquals(labelStyle, board.labelStyle)
    }

    @Test
    fun `rapid back-to-back commits on a real IO dispatcher leave the latest board on disk`() {
        // Regression for the save race fixed in #144: each commit launches its own save,
        // and on a real thread pool they used to be able to finish out of order.
        repo.save(boardWith(Tile(id = "a")))
        val vm = BoardViewModel(repo, player, recorder, savedBoardRepo, speaker, devicePrefs, recentBoardsRepo, ioDispatcher = Dispatchers.IO)
        waitUntil { vm.board.value.currentPage.tiles.any { it.id == "a" } }

        repeat(40) { i ->
            vm.setLandscapeGrid(0, (i % 5) + 1, (i % 7) + 1)
            vm.setRowHeight(RowHeight.entries[i % RowHeight.entries.size])
        }
        val expected = vm.board.value

        waitUntil { BoardRepository(context).load() == expected }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("condition not met within ${timeoutMillis}ms", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }

    @Test
    fun `recording onto a landscape-only tile grows the portrait grid to show it`() {
        // A 4x4 page shows 8x3 = 24 slots in landscape; slot 20 doesn't exist in portrait.
        repo.save(Board(pages = listOf(Page(rows = 4, columns = 4, tiles = List(16) { Tile(id = "t$it") }))))
        val vm = newViewModel()
        val landscapeOnly = vm.board.value.currentPage.landscapeTiles[19]
        assertFalse(vm.board.value.currentPage.visibleTiles.contains(landscapeOnly))

        vm.startRecording()
        vm.stopRecording(landscapeOnly.id)

        val page = vm.board.value.currentPage
        assertEquals(5, page.rows)
        assertTrue(page.visibleTiles.any { it.id == landscapeOnly.id })
    }

    @Test
    fun `once a tile's sound is cleared, shrinking the grid can hide it again`() {
        repo.save(
            Board(pages = listOf(Page(rows = 2, columns = 2, tiles = listOf(Tile(id = "a"), Tile(id = "b"), Tile(id = "c"), Tile(id = "d", label = "Water", fileName = "d.mp3")))))
        )
        repo.soundFile("d.mp3").apply { parentFile?.mkdirs() }.writeText("d")
        val vm = newViewModel()

        vm.resize(0, 1, 2)
        assertEquals(2, vm.board.value.currentPage.rows)

        vm.clearTile("d")
        vm.resize(0, 1, 2)
        assertEquals(1, vm.board.value.currentPage.rows)
        assertFalse(vm.board.value.currentPage.visibleTiles.any { it.id == "d" })
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
    fun `setHideBlankTilesEnabled persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setHideBlankTilesEnabled(true)

        assertTrue(vm.board.value.hideBlankTilesEnabled)
        val second = newViewModel()
        assertTrue(second.board.value.hideBlankTilesEnabled)
    }

    @Test
    fun `setBackgroundColor persists and clears any background image`() {
        val vm = newViewModel()
        val uri = Uri.parse("content://fake/bg.jpg")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("bg".toByteArray()))
        vm.setBackgroundImage(uri)
        assertTrue(vm.board.value.backgroundImageFileName != null)

        vm.setBackgroundColor(0xFF00FF00.toInt())

        assertEquals(0xFF00FF00.toInt(), vm.board.value.backgroundColorArgb)
        assertEquals(null, vm.board.value.backgroundImageFileName)
        val second = newViewModel()
        assertEquals(0xFF00FF00.toInt(), second.board.value.backgroundColorArgb)
    }

    @Test
    fun `setBackgroundImage imports the file and clears any background color`() {
        val vm = newViewModel()
        vm.setBackgroundColor(0xFF00FF00.toInt())
        val uri = Uri.parse("content://fake/bg.jpg")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("bg".toByteArray()))

        vm.setBackgroundImage(uri)

        assertTrue(vm.board.value.backgroundImageFileName != null)
        assertEquals(null, vm.board.value.backgroundColorArgb)
    }

    @Test
    fun `clearBackground resets both color and image`() {
        val vm = newViewModel()
        vm.setBackgroundColor(0xFF00FF00.toInt())

        vm.clearBackground()

        assertEquals(null, vm.board.value.backgroundColorArgb)
        assertEquals(null, vm.board.value.backgroundImageFileName)
    }

    @Test
    fun `setBoardTileOpacity persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setBoardTileOpacity(0.5f)

        assertEquals(0.5f, vm.board.value.tileOpacity)
        val second = newViewModel()
        assertEquals(0.5f, second.board.value.tileOpacity)
    }

    @Test
    fun `setBoardTileOpacity coerces into the 0 to 1 range`() {
        val vm = newViewModel()

        vm.setBoardTileOpacity(5f)

        assertEquals(1f, vm.board.value.tileOpacity)
    }

    @Test
    fun `setPageOpacity overrides only the targeted page`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.setPageOpacity(1, 0.4f)

        assertEquals(null, vm.board.value.pages[0].opacity)
        assertEquals(0.4f, vm.board.value.pages[1].opacity)
    }

    @Test
    fun `row height defaults to standard and persists across a fresh view model`() {
        val vm = newViewModel()
        assertEquals(RowHeight.STANDARD, vm.board.value.rowHeight)

        vm.setRowHeight(RowHeight.TALL)

        assertEquals(RowHeight.TALL, newViewModel().board.value.rowHeight)
    }

    @Test
    fun `setLabelStyle persists across a fresh view model`() {
        val vm = newViewModel()
        assertEquals(LabelStyle(), vm.board.value.labelStyle)
        val style = LabelStyle(minSizeSp = 16f, maxSizeSp = 40f, font = LabelFont.ATKINSON_HYPERLEGIBLE, bold = true, allCaps = true)

        vm.setLabelStyle(style)

        assertEquals(style, newViewModel().board.value.labelStyle)
    }

    @Test
    fun `setLabelStyle keeps the size range in bounds and in order`() {
        val vm = newViewModel()

        vm.setLabelStyle(LabelStyle(minSizeSp = 30f, maxSizeSp = 20f))
        assertEquals(30f, vm.board.value.labelStyle.minSizeSp)
        assertEquals(30f, vm.board.value.labelStyle.maxSizeSp)

        vm.setLabelStyle(LabelStyle(minSizeSp = 1f, maxSizeSp = 500f))
        assertEquals(LabelStyle.SIZE_RANGE_SP.start, vm.board.value.labelStyle.minSizeSp)
        assertEquals(LabelStyle.SIZE_RANGE_SP.endInclusive, vm.board.value.labelStyle.maxSizeSp)
    }

    @Test
    fun `setPageRowHeight overrides only the targeted page and null clears it`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.setPageRowHeight(1, RowHeight.SHORT)

        assertEquals(null, vm.board.value.pages[0].rowHeight)
        assertEquals(RowHeight.SHORT, newViewModel().board.value.pages[1].rowHeight)

        vm.setPageRowHeight(1, null)

        assertEquals(null, vm.board.value.pages[1].rowHeight)
    }

    @Test
    fun `setLandscapeGrid and setLandscapeLayout persist across a fresh view model`() {
        repo.save(Board(pages = listOf(Page(name = "A"))))
        val vm = newViewModel()

        vm.setLandscapeGrid(0, 2, 6)
        vm.setLandscapeLayout(LandscapeLayout.FIT_TO_SCREEN)

        val reloaded = newViewModel().board.value
        assertEquals(6, reloaded.pages[0].landscapeColumns)
        assertEquals(2, reloaded.pages[0].landscapeRows)
        assertEquals(LandscapeLayout.FIT_TO_SCREEN, reloaded.landscapeLayout)
    }

    @Test
    fun `setOpacity overrides only the targeted tile`() {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setOpacity("a", 0.3f)

        assertEquals(0.3f, vm.board.value.currentPage.tiles.first { it.id == "a" }.opacity)
        assertEquals(null, vm.board.value.currentPage.tiles.first { it.id == "b" }.opacity)
    }

    @Test
    fun `setOpacity edits the home page's tile even from a different current page`() {
        repo.save(
            Board(
                pages = listOf(
                    Page(name = "Home", isHome = true, tiles = listOf(Tile(id = "a"))),
                    Page(name = "Other")
                ),
                currentPageIndex = 1
            )
        )
        val vm = newViewModel()

        vm.setOpacity("a", 0.3f)

        assertEquals(0.3f, vm.board.value.homePage?.tiles?.first { it.id == "a" }?.opacity)
    }

    @Test
    fun `setBoardTileBorder persists across a fresh view model`() {
        val vm = newViewModel()

        vm.setBoardTileBorder(TileBorder(enabled = true, colorArgb = 0xFF00FF00.toInt(), widthDp = 2f))

        assertEquals(TileBorder(enabled = true, colorArgb = 0xFF00FF00.toInt(), widthDp = 2f), vm.board.value.tileBorder)
        val second = newViewModel()
        assertEquals(TileBorder(enabled = true, colorArgb = 0xFF00FF00.toInt(), widthDp = 2f), second.board.value.tileBorder)
    }

    @Test
    fun `setPageBorder overrides only the targeted page`() {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.setPageBorder(1, TileBorder(enabled = true))

        assertEquals(null, vm.board.value.pages[0].border)
        assertEquals(TileBorder(enabled = true), vm.board.value.pages[1].border)
    }

    @Test
    fun `setBorder overrides only the targeted tile`() {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setBorder("a", TileBorder(enabled = true))

        assertEquals(TileBorder(enabled = true), vm.board.value.currentPage.tiles.first { it.id == "a" }.border)
        assertEquals(null, vm.board.value.currentPage.tiles.first { it.id == "b" }.border)
    }

    @Test
    fun `setBorder edits the home page's tile even from a different current page`() {
        repo.save(
            Board(
                pages = listOf(
                    Page(name = "Home", isHome = true, tiles = listOf(Tile(id = "a"))),
                    Page(name = "Other")
                ),
                currentPageIndex = 1
            )
        )
        val vm = newViewModel()

        vm.setBorder("a", TileBorder(enabled = true))

        assertEquals(TileBorder(enabled = true), vm.board.value.homePage?.tiles?.first { it.id == "a" }?.border)
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
    fun `setLabel edits a home page tile regardless of the current page`() {
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

        vm.setLabel("hey", "Hey!")

        assertEquals("Hey!", vm.board.value.homePage!!.tiles.first { it.id == "hey" }.label)
        assertEquals("old", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `clearing a home row tile unloads and deletes its sound`() {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true))))
        repo.soundFile("hey.mp3").apply { parentFile?.mkdirs() }.writeText("hey")
        val vm = newViewModel()

        vm.clearTile("hey")

        val tile = vm.board.value.homePage!!.tiles.first { it.id == "hey" }
        assertEquals("", tile.label)
        assertNull(tile.fileName)
        assertTrue(player.unloaded.contains("hey.mp3"))
        assertFalse(repo.soundFile("hey.mp3").exists())
    }

    @Test
    fun `openBoard with a factory ref loads the bundled asset`() {
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals("Jeremy Draft Care Board", vm.board.value.name)
        assertEquals(listOf("Trouble", "Needs", "Talking"), vm.board.value.pages.map { it.name })
        assertEquals("Loaded \"Jeremy Draft Care Board\"", vm.message.value)
    }

    @Test
    fun `openBoard with a factory ref records it in recentBoards`() {
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals(listOf("Jeremy Draft Care Board"), vm.recentBoards.value.map { it.label })
        assertEquals(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"), vm.recentBoards.value.first().ref)
    }

    @Test
    fun `applying the same preset twice moves it to the front instead of duplicating it`() {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))
        vm.saveBoardAs("Other Board")
        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals(2, vm.recentBoards.value.size)
        assertEquals("Jeremy Draft Care Board", vm.recentBoards.value.first().label)
    }

    @Test
    fun `saveBoardAs renames the board and appears in presets`() {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.saveBoardAs("My Layout")

        assertEquals("My Layout", vm.board.value.name)
        assertEquals(listOf("My Layout"), vm.savedBoards.value.map { it.name })
    }

    @Test
    fun `saveBoardAs records the new preset in recentBoards`() {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.saveBoardAs("My Layout")

        assertEquals(listOf("My Layout"), vm.recentBoards.value.map { it.label })
        assertTrue(vm.recentBoards.value.first().ref is BoardRef.Saved)
    }

    @Test
    fun `openBoard with a saved ref restores that snapshot`() {
        repo.save(boardWith(Tile(id = "a", label = "first")))
        val vm = newViewModel()
        vm.saveBoardAs("Version 1")
        val savedId = vm.savedBoards.value.first().id

        vm.setLabel("a", "changed")
        vm.openBoard(BoardRef.Saved(savedId))

        assertEquals("first", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `openBoard with an unknown saved id reports failure without touching the board`() {
        repo.save(boardWith(Tile(id = "a", label = "unchanged")))
        val vm = newViewModel()

        vm.openBoard(BoardRef.Saved("does-not-exist"))

        assertEquals("unchanged", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
        assertEquals("Couldn't open board", vm.message.value)
    }

    @Test
    fun `saving a preset protects its sound from being pruned after the live tile changes`() {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        val vm = newViewModel()

        vm.saveBoardAs("Backup layout")
        vm.clearTile("a")

        assertTrue(repo.soundFile("a.mp3").exists())
    }

    @Test
    fun `refreshStrayClips finds a file no tile or preset points at`() {
        repo.save(boardWith(Tile(id = "a", fileName = "used.mp3")))
        repo.soundFile("used.mp3").apply { parentFile?.mkdirs() }.writeText("used")
        repo.soundFile("stray.mp3").writeText("stray")
        val vm = newViewModel()

        vm.refreshStrayClips()

        assertEquals(listOf("stray.mp3"), vm.strayClips.value.map { it.fileName })
    }

    @Test
    fun `refreshStrayClips excludes a file only a saved preset still references`() {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        repo.soundFile("a.mp3").apply { parentFile?.mkdirs() }.writeText("a")
        val vm = newViewModel()
        vm.saveBoardAs("Backup layout")
        vm.clearTile("a")

        vm.refreshStrayClips()

        assertTrue(vm.strayClips.value.isEmpty())
    }

    @Test
    fun `deleteStrayClips removes the files and clears the list`() {
        repo.save(boardWith(Tile(id = "a")))
        val stray = repo.soundFile("stray.mp3").apply { parentFile?.mkdirs() }.also { it.writeText("stray") }
        val vm = newViewModel()
        vm.refreshStrayClips()

        vm.deleteStrayClips()

        assertFalse(stray.exists())
        assertTrue(vm.strayClips.value.isEmpty())
    }

    @Test
    fun `exportAndDeleteStrayClips only deletes after a successful export`() {
        repo.save(boardWith(Tile(id = "a")))
        val stray = repo.soundFile("stray.mp3").apply { parentFile?.mkdirs() }.also { it.writeText("stray") }
        val vm = newViewModel()
        vm.refreshStrayClips()
        val zipOut = File(context.filesDir, "export.zip")

        vm.exportAndDeleteStrayClips(Uri.fromFile(zipOut))

        assertFalse(stray.exists())
        assertTrue(vm.strayClips.value.isEmpty())
        assertTrue(zipOut.exists())
    }
}
