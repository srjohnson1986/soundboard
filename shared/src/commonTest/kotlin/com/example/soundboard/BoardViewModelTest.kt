package com.example.soundboard

import com.example.soundboard.audio.FakeMediaVolume
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.data.BoardJson
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.CapturingSaveTarget
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.FakeBundledBoards
import com.example.soundboard.data.FakePickedFile
import com.example.soundboard.data.FakeZipCodec
import com.example.soundboard.data.InMemoryFileStore
import com.example.soundboard.data.MapKeyValueStore
import com.example.soundboard.data.RecentBoardEntry
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.ZipEntryData
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.SpeechSettings
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * [BoardViewModel] against in-memory storage, so it runs on the JVM and in the browser alike
 * (#241). The built-in boards are small stand-ins with the shape these tests rely on; the real
 * ones are checked in the app (ShippedBoardsTest), where they're packaged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val files = InMemoryFileStore()
    private val zip = FakeZipCodec()
    // One store for the whole test, so settings carry over to a fresh view model the way
    // they do across launches.
    private val prefsStore = MapKeyValueStore()
    private val repo = BoardRepository(files, zip, FakeBundledBoards(builtInBoards(zip)))
    private val player = FakePlayer()
    private val recorder = FakeRecorder()
    private val savedBoardRepo = SavedBoardRepository(files)
    private val speaker = FakeSpeaker()
    private val devicePrefs = DevicePreferences(prefsStore)
    private val recentBoardsRepo = RecentBoardsRepository(files)
    private val mediaVolume = FakeMediaVolume()

    /** The time the view model sees, in epoch milliseconds; tests move it on. */
    private var clock = 1_000_000_000L
    private val week = BoardViewModel.BACKUP_REMINDER_AFTER.inWholeMilliseconds

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun newViewModel() =
        BoardViewModel(repo, player, recorder, savedBoardRepo, speaker, devicePrefs, recentBoardsRepo, ioDispatcher = dispatcher, now = { clock }, mediaVolume = mediaVolume)

    private fun boardWith(vararg tiles: Tile) = Board(
        pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList()))
    )

    @Test
    fun `play on a filled tile calls through to the player`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", fileName = "a.mp3", volume = 0.7f)

        vm.play(tile)

        assertEquals(listOf("a.mp3" to 0.7f), player.played)
    }

    @Test
    fun `the speaker starts with this device's stored voice and speed and pitch`() = runTest(dispatcher) {
        devicePrefs.speech = SpeechSettings(voiceId = "v2", ratePercent = 75)

        val vm = newViewModel()

        assertEquals(SpeechSettings(voiceId = "v2", ratePercent = 75), speaker.configured)
        assertEquals(SpeechSettings(voiceId = "v2", ratePercent = 75), vm.speech.value)
    }

    @Test
    fun `changing the voice or speed or pitch reaches the speaker and survives a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setSpeechVoice("v2")
        vm.setSpeechRatePercent(125)
        vm.setSpeechPitchPercent(75)

        val expected = SpeechSettings(voiceId = "v2", ratePercent = 125, pitchPercent = 75)
        assertEquals(expected, speaker.configured)
        assertEquals(expected, newViewModel().speech.value)
    }

    @Test
    fun `preview says a sample sentence`() = runTest(dispatcher) {
        newViewModel().previewSpeech()

        assertEquals(listOf(BoardViewModel.SPEECH_PREVIEW), speaker.spoken)
    }

    // --- Caregiver lock (#251) ---

    @Test
    fun `the lock is off by default and turning it on locks at once and on every launch after`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertFalse(vm.editingLocked.value)

        vm.setEditingLockEnabled(true)

        assertTrue(vm.editingLocked.value)
        assertTrue(newViewModel().editingLocked.value)
    }

    @Test
    fun `unlocking lasts until the board relocks or the app restarts`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setEditingLockEnabled(true)

        vm.unlockEditing()
        assertFalse(vm.editingLocked.value)
        assertTrue(vm.editingLockEnabled.value)
        assertTrue(newViewModel().editingLocked.value)

        vm.relockEditing()
        assertTrue(vm.editingLocked.value)
    }

    @Test
    fun `with the lock off relocking does nothing`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setEditingLockEnabled(true)
        vm.setEditingLockEnabled(false)

        vm.relockEditing()

        assertFalse(vm.editingLocked.value)
        assertFalse(newViewModel().editingLocked.value)
    }

    // --- Backup reminder (#249) ---

    @Test
    fun `a board nobody has changed never asks for a backup`() = runTest(dispatcher) {
        val vm = newViewModel()

        clock += 4 * week
        vm.activate(Tile(id = "x", label = "Hey", speakWhenNoSound = true))

        assertFalse(vm.backupReminderDue.value)
    }

    @Test
    fun `changes that go a week without a backup bring the reminder and it lasts across launches`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.setLabel("a", "Water")
        clock += week - 1
        vm.activate(tile(vm, "a"))
        assertFalse(vm.backupReminderDue.value)

        clock += 1
        vm.activate(tile(vm, "a"))
        assertTrue(vm.backupReminderDue.value)
        assertTrue(newViewModel().backupReminderDue.value)
    }

    @Test
    fun `a backup puts the reminder away until there are new changes`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        vm.setLabel("a", "Water")
        clock += week
        vm.activate(tile(vm, "a"))
        assertTrue(vm.backupReminderDue.value)

        vm.exportBoard(CapturingSaveTarget())
        assertFalse(vm.backupReminderDue.value)
        clock += 4 * week
        vm.activate(tile(vm, "a"))
        assertFalse(vm.backupReminderDue.value)

        vm.setLabel("a", "Juice")
        clock += week
        vm.activate(tile(vm, "a"))
        assertTrue(vm.backupReminderDue.value)
    }

    @Test
    fun `later puts the reminder away for a week`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        vm.setLabel("a", "Water")
        clock += week
        vm.activate(tile(vm, "a"))

        vm.snoozeBackupReminder()
        assertFalse(vm.backupReminderDue.value)
        clock += week - 1
        vm.activate(tile(vm, "a"))
        assertFalse(vm.backupReminderDue.value)

        clock += 1
        vm.activate(tile(vm, "a"))
        assertTrue(vm.backupReminderDue.value)
    }

    @Test
    fun `opening another board starts the week over`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        vm.setLabel("a", "Water")
        clock += week
        vm.activate(tile(vm, "a"))
        assertTrue(vm.backupReminderDue.value)

        vm.openBoard(BoardRef.BuiltIn("tts-care-board.zip", "TTS Care Board"))

        assertFalse(vm.backupReminderDue.value)
    }

    @Test
    fun `a tap checks the media volume again in case a change went unreported`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.play(Tile(id = "a", fileName = "a.mp3"))

        assertEquals(1, mediaVolume.refreshes)
    }

    @Test
    fun `speech availability and a muted media volume come through for the board to show`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertEquals(true, vm.speechAvailable.value)
        assertFalse(vm.mediaMuted.value)

        speaker.available.value = false
        mediaVolume.muted.value = true

        assertEquals(false, vm.speechAvailable.value)
        assertTrue(vm.mediaMuted.value)
    }

    @Test
    fun `play on an empty tile does nothing`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", fileName = null)

        vm.play(tile)

        assertTrue(player.played.isEmpty())
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play on a tile with speakWhenNoSound set but no sound speaks its label`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "I need water", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("I need water"), speaker.spoken)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `play prefers a sound file over speaking even when speakWhenNoSound is set`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Air horn", fileName = "a.mp3", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("a.mp3" to 1f), player.played)
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play on a speakWhenNoSound tile with a blank label does nothing`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "", speakWhenNoSound = true)

        vm.play(tile)

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `play speaks ttsScript instead of the label when set`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Water", ttsScript = "I would like a glass of water please", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("I would like a glass of water please"), speaker.spoken)
    }

    @Test
    fun `play falls back to the label when ttsScript is null`() = runTest(dispatcher) {
        val vm = newViewModel()
        val tile = Tile(id = "a", label = "Water", speakWhenNoSound = true)

        vm.play(tile)

        assertEquals(listOf("Water"), speaker.spoken)
    }

    @Test
    fun `a speakWhenNoSound tile has sound`() = runTest(dispatcher) {
        assertTrue(Tile(speakWhenNoSound = true).hasSound)
    }

    @Test
    fun `play speaks an unrecorded labeled tile when the fallback setting is on`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "Water")))
        val vm = newViewModel()
        assertTrue(vm.board.value.speakUnrecordedTilesEnabled)

        vm.play(vm.board.value.currentPage.tiles.first { it.id == "a" })

        assertEquals(listOf("Water"), speaker.spoken)
    }

    @Test
    fun `play does nothing for an unrecorded labeled tile when the fallback setting is off`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "Water")).copy(speakUnrecordedTilesEnabled = false))
        val vm = newViewModel()

        vm.play(vm.board.value.currentPage.tiles.first { it.id == "a" })

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `setSpeakWhenNoSound updates only the target tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setSpeakWhenNoSound("a", true)

        assertTrue(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "b" }.speakWhenNoSound)
    }

    @Test
    fun `setSpeakWhenNoSound edits a home page tile from another page and leaves the current page alone`() = runTest(dispatcher) {
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
    fun `setTtsScript updates only the target tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setTtsScript("a", "I would like a glass of water please")

        assertEquals("I would like a glass of water please", vm.board.value.currentPage.tiles.first { it.id == "a" }.ttsScript)
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "b" }.ttsScript)
    }

    @Test
    fun `setTtsScript stores null instead of a blank script`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", ttsScript = "old script")))
        val vm = newViewModel()

        vm.setTtsScript("a", "   ")

        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.ttsScript)
    }

    @Test
    fun `setTtsScript edits a home page tile from another page and leaves the current page alone`() = runTest(dispatcher) {
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
    fun `clearTile also turns off speakWhenNoSound`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "Water", speakWhenNoSound = true)))
        val vm = newViewModel()

        vm.clearTile("a")

        assertFalse(vm.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
    }

    @Test
    fun `clearTile on a home page tile also turns off speakWhenNoSound`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", speakWhenNoSound = true)), isHome = true))))
        val vm = newViewModel()

        vm.clearTile("hey")

        assertFalse(vm.board.value.homePage!!.tiles.first { it.id == "hey" }.speakWhenNoSound)
    }

    @Test
    fun `removeSound clears the file but keeps the label and speakWhenNoSound`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "Water", fileName = "a.mp3", speakWhenNoSound = true)))
        val vm = newViewModel()

        vm.removeSound("a")

        val tile = vm.board.value.currentPage.tiles.first { it.id == "a" }
        assertNull(tile.fileName)
        assertEquals("Water", tile.label)
        assertTrue(tile.speakWhenNoSound)
    }

    @Test
    fun `removeSound on a home page tile clears the file but keeps the label and speakWhenNoSound`() = runTest(dispatcher) {
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
    fun `removeSound prunes the now-orphaned file`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        files.put("sounds/a.mp3", "a".encodeToByteArray())
        val vm = newViewModel()

        vm.removeSound("a")

        assertFalse(files.has("sounds/a.mp3"))
    }

    @Test
    fun `speakAdHoc speaks arbitrary text without touching any tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.speakAdHoc("I'll be there in five minutes")

        assertEquals(listOf("I'll be there in five minutes"), speaker.spoken)
    }

    @Test
    fun `setSpeakWhenNoSound persists across a fresh view model`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.setSpeakWhenNoSound("a", true)

        val second = newViewModel()
        assertTrue(second.board.value.currentPage.tiles.first { it.id == "a" }.speakWhenNoSound)
    }

    @Test
    fun `setLabel updates only the target tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "old"), Tile(id = "b", label = "keep")))
        val vm = newViewModel()

        vm.setLabel("a", "new")

        assertEquals("new", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
        assertEquals("keep", vm.board.value.currentPage.tiles.first { it.id == "b" }.label)
    }

    private fun tile(vm: BoardViewModel, id: String) = vm.board.value.findTile(id)!!

    /** Picks a sound in the tile editor the way the dialog does; returns the new file's name. */
    private fun pickSound(vm: BoardViewModel, content: String = "clip"): String {
        var picked: String? = null
        vm.importSound(FakePickedFile(content.encodeToByteArray(), "mp3")) { picked = it }
        return picked!!
    }

    /** Records and stops in the tile editor the way the dialog does; returns the new file's name. */
    private fun record(vm: BoardViewModel): String {
        vm.startRecording()
        files.put(recorder.startedPath!!, "recorded".encodeToByteArray())
        var recorded: String? = null
        vm.stopRecording { recorded = it }
        return recorded!!
    }

    @Test
    fun `a picked sound is loaded for the editor but only reaches the tile on Save`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        val name = pickSound(vm)

        assertTrue(player.loaded.contains(name))
        assertNull(tile(vm, "a").fileName)

        vm.saveTile("a", tile(vm, "a").copy(fileName = name))

        assertEquals(name, tile(vm, "a").fileName)
        assertTrue(files.has("sounds/${name}"))
    }

    @Test
    fun `cancelling the editor after recording deletes the new clip and keeps the old one`() = runTest(dispatcher) {
        // #207: a recording used to replace the tile's clip the moment it stopped, even if
        // the edit was then cancelled.
        repo.save(boardWith(Tile(id = "a", label = "Hey", fileName = "old.m4a")))
        files.put("sounds/old.m4a", "old".encodeToByteArray())
        val vm = newViewModel()

        val recorded = record(vm)
        vm.discardTileEdits()

        assertEquals("old.m4a", tile(vm, "a").fileName)
        assertTrue(files.has("sounds/old.m4a"))
        assertFalse(files.has("sounds/${recorded}"))
        assertFalse(player.loaded.contains(recorded))
    }

    @Test
    fun `saving a recording over a tile's clip replaces it and deletes the old file`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "Hey", fileName = "old.m4a")))
        files.put("sounds/old.m4a", "old".encodeToByteArray())
        val vm = newViewModel()

        val recorded = record(vm)
        vm.saveTile("a", tile(vm, "a").copy(fileName = recorded))

        assertEquals(recorded, tile(vm, "a").fileName)
        assertTrue(files.has("sounds/${recorded}"))
        assertFalse(files.has("sounds/old.m4a"))
    }

    @Test
    fun `saving keeps only the last of several sounds tried in one edit`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        val first = pickSound(vm, "first")
        val second = record(vm)
        vm.saveTile("a", tile(vm, "a").copy(fileName = second))

        assertEquals(second, tile(vm, "a").fileName)
        assertFalse(files.has("sounds/${first}"))
        assertFalse(player.loaded.contains(first))
    }

    @Test
    fun `a sound added in the editor survives another board edit made before Save`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        val name = pickSound(vm)
        vm.renameBoard("Something else") // commits, which prunes unreferenced sounds

        assertTrue(files.has("sounds/${name}"))
        vm.saveTile("a", tile(vm, "a").copy(fileName = name))
        assertEquals(name, tile(vm, "a").fileName)
    }

    @Test
    fun `a recording that finishes after its edit was cancelled is deleted rather than handed back`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        vm.startRecording()
        val path = recorder.startedPath!!
        files.put(path, "late".encodeToByteArray())
        var handedBack: String? = null

        // The edit ends without the recorder being cancelled first, so Stop's result
        // arrives after the edit it was recorded in is over.
        vm.saveTile("a", tile(vm, "a"))
        vm.stopRecording { handedBack = it }

        assertNull(handedBack)
        assertFalse(files.has(path))
    }

    @Test
    fun `saveTile saves every field at once trimmed and clamped`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.saveTile(
            "a",
            tile(vm, "a").copy(
                label = "  Water  ",
                ttsScript = "   ",
                volume = 1.5f,
                colorArgb = 0xFF00FF00.toInt(),
                opacity = -1f,
                speakWhenNoSound = true
            )
        )

        val saved = tile(vm, "a")
        assertEquals("Water", saved.label)
        assertNull(saved.ttsScript)
        assertEquals(1f, saved.volume)
        assertEquals(0xFF00FF00.toInt(), saved.colorArgb)
        assertEquals(0f, saved.opacity)
        assertTrue(saved.speakWhenNoSound)
        assertEquals("a", saved.id)
    }

    @Test
    fun `filling the last empty tile in the last row grows the page by one row`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.saveTile("a", tile(vm, "a").copy(fileName = pickSound(vm)))

        val page = vm.board.value.currentPage
        assertEquals(2, page.rows)
        assertEquals(2, page.visibleTiles.size)
        assertFalse(page.visibleTiles[1].hasSound)
    }

    @Test
    fun `recording start-stop hands back the recorded file loaded for the tile to save`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.startRecording()
        assertTrue(vm.isRecording.value)
        val recordedPath = recorder.startedPath
        assertTrue(recordedPath != null)
        var recorded: String? = null

        vm.stopRecording { recorded = it }

        assertFalse(vm.isRecording.value)
        assertEquals(recordedPath.substringAfterLast('/'), recorded)
        assertTrue(player.loaded.contains(recorded))
        assertNull(tile(vm, "a").fileName)
    }

    @Test
    fun `a failed stop leaves the tile untouched deletes the partial file and reports a message`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()
        recorder.stopSucceeds = false

        vm.startRecording()
        val recordedPath = recorder.startedPath!!.also { files.put(it, "partial".encodeToByteArray()) }
        var recorded: String? = null

        vm.stopRecording { recorded = it }

        assertNull(recorded)
        assertFalse(vm.isRecording.value)
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.fileName)
        assertFalse(files.has(recordedPath))
        assertEquals("Recording failed", vm.message.value)
    }

    @Test
    fun `cancelRecording discards the in-progress file without touching the tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.startRecording()
        val recordedPath = recorder.startedPath!!.also { files.put(it, "abandoned".encodeToByteArray()) }

        vm.cancelRecording()

        assertFalse(vm.isRecording.value)
        assertTrue(recorder.cancelled)
        assertFalse(files.has(recordedPath))
        assertNull(vm.board.value.currentPage.tiles.first { it.id == "a" }.fileName)
    }

    @Test
    fun `a recording saves onto a home page tile`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey")), isHome = true))))
        val vm = newViewModel()

        val recorded = record(vm)
        vm.saveTile("hey", tile(vm, "hey").copy(fileName = recorded))

        val tile = vm.board.value.homePage!!.tiles.first { it.id == "hey" }
        assertEquals(recorded, tile.fileName)
    }

    @Test
    fun `clearTile blanks the tile unloads the clip and deletes the audio file`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))
        files.put("sounds/a.mp3", "data".encodeToByteArray())
        val vm = newViewModel()

        vm.clearTile("a")

        val tile = vm.board.value.currentPage.tiles.first { it.id == "a" }
        assertEquals("", tile.label)
        assertNull(tile.fileName)
        assertTrue(player.unloaded.contains("a.mp3"))
        assertFalse(files.has("sounds/a.mp3"))
    }

    @Test
    fun `replacing a tiles sound unloads the old clip and deletes the old file`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", fileName = "old.mp3")))
        files.put("sounds/old.mp3", "old".encodeToByteArray())
        val vm = newViewModel()

        vm.saveTile("a", tile(vm, "a").copy(fileName = pickSound(vm, "new")))

        assertTrue(player.unloaded.contains("old.mp3"))
        assertFalse(files.has("sounds/old.mp3"))
    }

    @Test
    fun `clearing a tile whose file is shared does not delete the shared file`() = runTest(dispatcher) {
        repo.save(
            boardWith(
                Tile(id = "a", fileName = "shared.mp3"),
                Tile(id = "b", fileName = "shared.mp3")
            )
        )
        files.put("sounds/shared.mp3", "shared".encodeToByteArray())
        val vm = newViewModel()

        vm.clearTile("a")

        assertTrue(files.has("sounds/shared.mp3"))
    }

    @Test
    fun `shrinking the grid never hides or drops a tile with a sound`() = runTest(dispatcher) {
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
        files.put("sounds/a.mp3", "a".encodeToByteArray())
        files.put("sounds/b.mp3", "b".encodeToByteArray())
        val vm = newViewModel()

        vm.resize(0, 1, 1)

        val page = vm.board.value.currentPage
        assertTrue(files.has("sounds/a.mp3"))
        assertTrue(files.has("sounds/b.mp3"))
        assertFalse(player.unloaded.contains("b.mp3"))
        assertEquals(1, page.columns)
        // Two rows reach "b"; that last row is then full, so one blank row follows it.
        assertEquals(listOf("a", "b", "c"), page.visibleTiles.map { it.id })
    }

    @Test
    fun `every mutation persists across a fresh view model`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.setLabel("a", "new")

        val second = newViewModel()
        assertEquals("new", second.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `renameBoard updates the board name and persists it`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.renameBoard("Family Board")

        assertEquals("Family Board", vm.board.value.name)
        val second = newViewModel()
        assertEquals("Family Board", second.board.value.name)
    }

    @Test
    fun `renameBoard falls back to New Board when given a blank name`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.renameBoard("   ")

        assertEquals("New Board", vm.board.value.name)
    }

    @Test
    fun `addPage appends a page and switches to it and persists`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.addPage("Feelings")

        assertEquals(listOf("Page 1", "Feelings"), vm.board.value.pages.map { it.name })
        assertEquals(1, vm.board.value.currentPageIndex)
        val second = newViewModel()
        assertEquals(listOf("Page 1", "Feelings"), second.board.value.pages.map { it.name })
    }

    @Test
    fun `renamePage updates the page name and persists it`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.renamePage(0, "Requests")

        assertEquals("Requests", vm.board.value.pages[0].name)
        val second = newViewModel()
        assertEquals("Requests", second.board.value.pages[0].name)
    }

    @Test
    fun `deletePage removes the page when more than one exists`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.deletePage(0)

        assertEquals(listOf("B"), vm.board.value.pages.map { it.name })
    }

    @Test
    fun `switchPage changes the current page without persisting`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.switchPage(1)

        assertEquals(1, vm.board.value.currentPageIndex)
        val second = newViewModel()
        assertEquals(0, second.board.value.currentPageIndex)
    }

    @Test
    fun `a sound referenced only on a non-current page is preloaded and survives pruning`() = runTest(dispatcher) {
        repo.save(
            Board(
                pages = listOf(
                    Page(name = "A", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", fileName = "a.mp3"))),
                    Page(name = "B", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", fileName = "b.mp3")))
                )
            )
        )
        files.put("sounds/a.mp3", "a".encodeToByteArray())
        files.put("sounds/b.mp3", "b".encodeToByteArray())
        val vm = newViewModel()

        assertTrue(player.loaded.contains("b.mp3"))

        vm.setLabel("a", "renamed")

        assertTrue(files.has("sounds/b.mp3"))
    }

    @Test
    fun `setHomePage marks the current page as home and persists it`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B")), currentPageIndex = 1))
        val vm = newViewModel()

        vm.setHomePage(1)

        assertEquals(1, vm.board.value.homePageIndex)
        val second = newViewModel()
        assertEquals(1, second.board.value.homePageIndex)
    }

    @Test
    fun `a fresh view model resumes the last-viewed page by default`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A", isHome = true), Page(name = "B")), currentPageIndex = 1))

        val vm = newViewModel()

        assertEquals(1, vm.board.value.currentPageIndex)
    }

    @Test
    fun `openOnHomePage jumps a fresh view model to the home page without touching disk`() = runTest(dispatcher) {
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
    fun `openOnHomePage is a no-op when no page is marked home`() = runTest(dispatcher) {
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
    fun `setOpenOnHomePage persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setOpenOnHomePage(true)

        assertTrue(vm.board.value.openOnHomePage)
        val second = newViewModel()
        assertTrue(second.board.value.openOnHomePage)
    }

    @Test
    fun `setIdleTimeoutMinutes persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setIdleTimeoutMinutes(2)

        assertEquals(2, vm.board.value.idleTimeoutMinutes)
        val second = newViewModel()
        assertEquals(2, second.board.value.idleTimeoutMinutes)
    }

    @Test
    fun `setLongPressDurationMillis persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setLongPressDurationMillis(800)

        assertEquals(800, vm.board.value.longPressDurationMillis)
        val second = newViewModel()
        assertEquals(800, second.board.value.longPressDurationMillis)
    }

    @Test
    fun `setThemeMode persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, vm.board.value.themeMode)
        val second = newViewModel()
        assertEquals(ThemeMode.DARK, second.board.value.themeMode)
    }

    @Test
    fun `setKeepScreenAwake persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setKeepScreenAwake(true)

        assertTrue(vm.board.value.keepScreenAwake)
        val second = newViewModel()
        assertTrue(second.board.value.keepScreenAwake)
    }

    @Test
    fun `setHapticFeedbackEnabled persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setHapticFeedbackEnabled(false)

        assertFalse(vm.board.value.hapticFeedbackEnabled)
        val second = newViewModel()
        assertFalse(second.board.value.hapticFeedbackEnabled)
    }

    @Test
    fun `performanceModeEnabled defaults to false`() = runTest(dispatcher) {
        val vm = newViewModel()

        assertFalse(vm.performanceModeEnabled.value)
    }

    @Test
    fun `setPerformanceModeEnabled persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setPerformanceModeEnabled(true)

        assertTrue(vm.performanceModeEnabled.value)
        val second = newViewModel()
        assertTrue(second.performanceModeEnabled.value)
    }

    @Test
    fun `setPerformanceModeEnabled survives loading a different board`() = runTest(dispatcher) {
        // Device-local (DevicePreferences), not board content — a preset with no
        // opinion of its own on this setting must not silently flip it back off.
        val vm = newViewModel()
        vm.setPerformanceModeEnabled(true)

        vm.openBoard(BoardRef.Saved(savedBoardRepo.save(Board(name = "Other"))))

        assertTrue(vm.performanceModeEnabled.value)
    }

    @Test
    fun `activate with Show mode off plays the tile and shows nothing`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertEquals(listOf("a.mp3"), player.playedKeys)
        assertNull(vm.shownText.value)
    }

    @Test
    fun `activate in Show mode shows the script and still plays`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)

        vm.activate(Tile(id = "a", label = "Water", ttsScript = "I would like some water", speakWhenNoSound = true))

        assertEquals("I would like some water", vm.shownText.value)
        assertEquals(listOf("I would like some water"), speaker.spoken)
    }

    @Test
    fun `activate in Show mode falls back to the label for a recorded tile`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)

        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertEquals("Water", vm.shownText.value)
        assertEquals(listOf("a.mp3"), player.playedKeys)
    }

    @Test
    fun `activate in Show mode with mute on shows the text without playing`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.setShowModeMuteSounds(true)

        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertEquals("Water", vm.shownText.value)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `activate in Show mode with nothing to show just plays even when muted`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.setShowModeMuteSounds(true)

        vm.activate(Tile(id = "a", label = " ", fileName = "a.mp3"))

        assertNull(vm.shownText.value)
        assertEquals(listOf("a.mp3"), player.playedKeys)
    }

    @Test
    fun `activate on an unplayable tile shows nothing`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSpeakUnrecordedTilesEnabled(false)
        vm.setShowModeEnabled(true)

        vm.activate(Tile(id = "a", label = "Awaiting a recording"))

        assertNull(vm.shownText.value)
    }

    @Test
    fun `play never opens the Show mode text`() = runTest(dispatcher) {
        // The tile editor's Play button calls play() directly — previewing a recording
        // while editing shouldn't black out the screen.
        val vm = newViewModel()
        vm.setShowModeEnabled(true)

        vm.play(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        assertNull(vm.shownText.value)
    }

    @Test
    fun `dismissShownText closes the text`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        vm.dismissShownText()

        assertNull(vm.shownText.value)
    }

    @Test
    fun `turning Show mode off closes any text on screen`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.activate(Tile(id = "a", label = "Water", fileName = "a.mp3"))

        vm.setShowModeEnabled(false)

        assertNull(vm.shownText.value)
    }

    @Test
    fun `Show mode can't be left with neither a timer nor tap to close`() = runTest(dispatcher) {
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
    fun `Show mode settings persist across a fresh view model and survive loading a different board`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setShowModeEnabled(true)
        vm.setShowModeTimerSeconds(30)
        vm.setShowModeMuteSounds(true)
        vm.setShowModeFlipped(true)

        vm.openBoard(BoardRef.Saved(savedBoardRepo.save(Board(name = "Other"))))

        val expected = ShowModeSettings(enabled = true, timerSeconds = 30, tapToClose = true, muteSounds = true, flipped = true)
        assertEquals(expected, vm.showMode.value)
        assertEquals(expected, newViewModel().showMode.value)
    }

    @Test
    fun `settings are captured by saveBoardAs and restored by openBoard`() = runTest(dispatcher) {
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
    fun `layout and label settings are captured by saveBoardAs and restored by openBoard`() = runTest(dispatcher) {
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
    fun `recording onto a landscape-only tile grows the portrait grid to show it`() = runTest(dispatcher) {
        // A 4x4 page shows 8x3 = 24 slots in landscape; slot 20 doesn't exist in portrait.
        repo.save(Board(pages = listOf(Page(rows = 4, columns = 4, tiles = List(16) { Tile(id = "t$it") }))))
        val vm = newViewModel()
        val landscapeOnly = vm.board.value.currentPage.landscapeTiles[19]
        assertFalse(vm.board.value.currentPage.visibleTiles.contains(landscapeOnly))

        vm.saveTile(landscapeOnly.id, tile(vm, landscapeOnly.id).copy(fileName = record(vm)))

        val page = vm.board.value.currentPage
        assertEquals(5, page.rows)
        assertTrue(page.visibleTiles.any { it.id == landscapeOnly.id })
    }

    @Test
    fun `once a tile's sound is cleared shrinking the grid can hide it again`() = runTest(dispatcher) {
        repo.save(
            Board(pages = listOf(Page(rows = 2, columns = 2, tiles = listOf(Tile(id = "a"), Tile(id = "b"), Tile(id = "c"), Tile(id = "d", label = "Water", fileName = "d.mp3")))))
        )
        files.put("sounds/d.mp3", "d".encodeToByteArray())
        val vm = newViewModel()

        vm.resize(0, 1, 2)
        assertEquals(2, vm.board.value.currentPage.rows)

        vm.clearTile("d")
        vm.resize(0, 1, 2)
        assertEquals(1, vm.board.value.currentPage.rows)
        assertFalse(vm.board.value.currentPage.visibleTiles.any { it.id == "d" })
    }

    @Test
    fun `setStickyHomeRowEnabled persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setStickyHomeRowEnabled(true)

        assertTrue(vm.board.value.stickyHomeRowEnabled)
        val second = newViewModel()
        assertTrue(second.board.value.stickyHomeRowEnabled)
    }

    @Test
    fun `setHideBlankTilesEnabled persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setHideBlankTilesEnabled(true)

        assertTrue(vm.board.value.hideBlankTilesEnabled)
        val second = newViewModel()
        assertTrue(second.board.value.hideBlankTilesEnabled)
    }

    @Test
    fun `setBackgroundColor persists and clears any background image`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setBackgroundImage(FakePickedFile("bg".encodeToByteArray(), "jpg"))
        assertTrue(vm.board.value.backgroundImageFileName != null)

        vm.setBackgroundColor(0xFF00FF00.toInt())

        assertEquals(0xFF00FF00.toInt(), vm.board.value.backgroundColorArgb)
        assertEquals(null, vm.board.value.backgroundImageFileName)
        val second = newViewModel()
        assertEquals(0xFF00FF00.toInt(), second.board.value.backgroundColorArgb)
    }

    @Test
    fun `setBackgroundImage imports the file and clears any background color`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setBackgroundColor(0xFF00FF00.toInt())

        vm.setBackgroundImage(FakePickedFile("bg".encodeToByteArray(), "jpg"))

        assertTrue(vm.board.value.backgroundImageFileName != null)
        assertEquals(null, vm.board.value.backgroundColorArgb)
    }

    @Test
    fun `clearBackground resets both color and image`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setBackgroundColor(0xFF00FF00.toInt())

        vm.clearBackground()

        assertEquals(null, vm.board.value.backgroundColorArgb)
        assertEquals(null, vm.board.value.backgroundImageFileName)
    }

    @Test
    fun `setBoardTileOpacity persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setBoardTileOpacity(0.5f)

        assertEquals(0.5f, vm.board.value.tileOpacity)
        val second = newViewModel()
        assertEquals(0.5f, second.board.value.tileOpacity)
    }

    @Test
    fun `setBoardTileOpacity coerces into the 0 to 1 range`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setBoardTileOpacity(5f)

        assertEquals(1f, vm.board.value.tileOpacity)
    }

    @Test
    fun `setPageOpacity overrides only the targeted page`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.setPageOpacity(1, 0.4f)

        assertEquals(null, vm.board.value.pages[0].opacity)
        assertEquals(0.4f, vm.board.value.pages[1].opacity)
    }

    @Test
    fun `row height defaults to standard and persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertEquals(RowHeight.STANDARD, vm.board.value.rowHeight)

        vm.setRowHeight(RowHeight.TALL)

        assertEquals(RowHeight.TALL, newViewModel().board.value.rowHeight)
    }

    @Test
    fun `setLabelStyle persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertEquals(LabelStyle(), vm.board.value.labelStyle)
        val style = LabelStyle(minSizeSp = 16f, maxSizeSp = 40f, font = LabelFont.ATKINSON_HYPERLEGIBLE, bold = true, allCaps = true)

        vm.setLabelStyle(style)

        assertEquals(style, newViewModel().board.value.labelStyle)
    }

    @Test
    fun `setLabelStyle keeps the size range in bounds and in order`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setLabelStyle(LabelStyle(minSizeSp = 30f, maxSizeSp = 20f))
        assertEquals(30f, vm.board.value.labelStyle.minSizeSp)
        assertEquals(30f, vm.board.value.labelStyle.maxSizeSp)

        vm.setLabelStyle(LabelStyle(minSizeSp = 1f, maxSizeSp = 500f))
        assertEquals(LabelStyle.SIZE_RANGE_SP.start, vm.board.value.labelStyle.minSizeSp)
        assertEquals(LabelStyle.SIZE_RANGE_SP.endInclusive, vm.board.value.labelStyle.maxSizeSp)
    }

    @Test
    fun `setPageRowHeight overrides only the targeted page and null clears it`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.setPageRowHeight(1, RowHeight.SHORT)

        assertEquals(null, vm.board.value.pages[0].rowHeight)
        assertEquals(RowHeight.SHORT, newViewModel().board.value.pages[1].rowHeight)

        vm.setPageRowHeight(1, null)

        assertEquals(null, vm.board.value.pages[1].rowHeight)
    }

    @Test
    fun `setLandscapeGrid and setLandscapeLayout persist across a fresh view model`() = runTest(dispatcher) {
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
    fun `setOpacity overrides only the targeted tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setOpacity("a", 0.3f)

        assertEquals(0.3f, vm.board.value.currentPage.tiles.first { it.id == "a" }.opacity)
        assertEquals(null, vm.board.value.currentPage.tiles.first { it.id == "b" }.opacity)
    }

    @Test
    fun `setOpacity edits the home page's tile even from a different current page`() = runTest(dispatcher) {
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
    fun `setBoardTileBorder persists across a fresh view model`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setBoardTileBorder(TileBorder(enabled = true, colorArgb = 0xFF00FF00.toInt(), widthDp = 2f))

        assertEquals(TileBorder(enabled = true, colorArgb = 0xFF00FF00.toInt(), widthDp = 2f), vm.board.value.tileBorder)
        val second = newViewModel()
        assertEquals(TileBorder(enabled = true, colorArgb = 0xFF00FF00.toInt(), widthDp = 2f), second.board.value.tileBorder)
    }

    @Test
    fun `setPageBorder overrides only the targeted page`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(name = "A"), Page(name = "B"))))
        val vm = newViewModel()

        vm.setPageBorder(1, TileBorder(enabled = true))

        assertEquals(null, vm.board.value.pages[0].border)
        assertEquals(TileBorder(enabled = true), vm.board.value.pages[1].border)
    }

    @Test
    fun `setBorder overrides only the targeted tile`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.setBorder("a", TileBorder(enabled = true))

        assertEquals(TileBorder(enabled = true), vm.board.value.currentPage.tiles.first { it.id == "a" }.border)
        assertEquals(null, vm.board.value.currentPage.tiles.first { it.id == "b" }.border)
    }

    @Test
    fun `setBorder edits the home page's tile even from a different current page`() = runTest(dispatcher) {
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
    fun `setDefaultPageRows and setDefaultPageColumns persist across a fresh view model`() = runTest(dispatcher) {
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
    fun `resize and setTileAspectRatio and setPageColor target the given page rather than the current one`() = runTest(dispatcher) {
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
    fun `setLabel edits a home page tile regardless of the current page`() = runTest(dispatcher) {
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
    fun `clearing a home row tile unloads and deletes its sound`() = runTest(dispatcher) {
        repo.save(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true))))
        files.put("sounds/hey.mp3", "hey".encodeToByteArray())
        val vm = newViewModel()

        vm.clearTile("hey")

        val tile = vm.board.value.homePage!!.tiles.first { it.id == "hey" }
        assertEquals("", tile.label)
        assertNull(tile.fileName)
        assertTrue(player.unloaded.contains("hey.mp3"))
        assertFalse(files.has("sounds/hey.mp3"))
    }

    @Test
    fun `a fresh install opens the Jeremy board`() = runTest(dispatcher) {
        val vm = newViewModel()

        assertEquals("Jeremy Draft Care Board", vm.board.value.name)
    }

    @Test
    fun `a fresh install on a build without the Jeremy board opens the TTS board`() = runTest(dispatcher) {
        // The web build packages no recorded boards, so it starts on the one that speaks.
        val ttsOnly = builtInBoards(zip).filterKeys { it == "tts-care-board.zip" }
        val vm = BoardViewModel(
            BoardRepository(files, zip, FakeBundledBoards(ttsOnly)), player, recorder, savedBoardRepo, speaker,
            devicePrefs, recentBoardsRepo, ioDispatcher = dispatcher
        )

        assertEquals("TTS Care Board", vm.board.value.name)
    }

    @Test
    fun `openBoard with a factory ref loads the bundled asset`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals("Jeremy Draft Care Board", vm.board.value.name)
        assertEquals(listOf("Trouble", "Needs", "Talking"), vm.board.value.pages.map { it.name })
        assertEquals("Loaded \"Jeremy Draft Care Board\"", vm.message.value)
    }

    @Test
    fun `openBoard with a factory ref records it in recentBoards`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals(listOf("Jeremy Draft Care Board"), vm.recentBoards.value.map { it.label })
        assertEquals(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"), vm.recentBoards.value.first().ref)
    }

    @Test
    fun `applying the same preset twice moves it to the front instead of duplicating it`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))
        vm.saveBoardAs("Other Board")
        vm.openBoard(BoardRef.BuiltIn("jeremy-care-board.zip", "Jeremy Draft Care Board"))

        assertEquals(2, vm.recentBoards.value.size)
        assertEquals("Jeremy Draft Care Board", vm.recentBoards.value.first().label)
    }

    @Test
    fun `saveBoardAs renames the board and appears in presets`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.saveBoardAs("My Layout")

        assertEquals("My Layout", vm.board.value.name)
        assertEquals(listOf("My Layout"), vm.savedBoards.value.map { it.name })
    }

    @Test
    fun `saveBoardAs records the new preset in recentBoards`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "old")))
        val vm = newViewModel()

        vm.saveBoardAs("My Layout")

        assertEquals(listOf("My Layout"), vm.recentBoards.value.map { it.label })
        assertTrue(vm.recentBoards.value.first().ref is BoardRef.Saved)
    }

    @Test
    fun `openBoard with a saved ref restores that snapshot`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "first")))
        val vm = newViewModel()
        vm.saveBoardAs("Version 1")
        val savedId = vm.savedBoards.value.first().id

        vm.setLabel("a", "changed")
        vm.openBoard(BoardRef.Saved(savedId))

        assertEquals("first", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
    }

    @Test
    fun `openBoard with an unknown saved id reports failure without touching the board`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", label = "unchanged")))
        val vm = newViewModel()

        vm.openBoard(BoardRef.Saved("does-not-exist"))

        assertEquals("unchanged", vm.board.value.currentPage.tiles.first { it.id == "a" }.label)
        assertEquals("Couldn't open board", vm.message.value)
    }

    @Test
    fun `saving a preset protects its sound from being pruned after the live tile changes`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        files.put("sounds/a.mp3", "a".encodeToByteArray())
        val vm = newViewModel()

        vm.saveBoardAs("Backup layout")
        vm.clearTile("a")

        assertTrue(files.has("sounds/a.mp3"))
    }

    @Test
    fun `refreshStrayClips finds a file no tile or preset points at`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", fileName = "used.mp3")))
        files.put("sounds/used.mp3", "used".encodeToByteArray())
        files.put("sounds/stray.mp3", "stray".encodeToByteArray())
        val vm = newViewModel()

        vm.refreshStrayClips()

        assertEquals(listOf("stray.mp3"), vm.strayClips.value.map { it.fileName })
    }

    @Test
    fun `refreshStrayClips excludes a file only a saved preset still references`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a", fileName = "a.mp3")))
        files.put("sounds/a.mp3", "a".encodeToByteArray())
        val vm = newViewModel()
        vm.saveBoardAs("Backup layout")
        vm.clearTile("a")

        vm.refreshStrayClips()

        assertTrue(vm.strayClips.value.isEmpty())
    }

    @Test
    fun `deleteStrayClips removes the files and clears the list`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        files.put("sounds/stray.mp3", "stray".encodeToByteArray())
        val vm = newViewModel()
        vm.refreshStrayClips()

        vm.deleteStrayClips()

        assertFalse(files.has("sounds/stray.mp3"))
        assertTrue(vm.strayClips.value.isEmpty())
    }

    @Test
    fun `exportAndDeleteStrayClips only deletes after a successful export`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        files.put("sounds/stray.mp3", "stray".encodeToByteArray())
        val vm = newViewModel()
        vm.refreshStrayClips()
        val zipOut = CapturingSaveTarget()

        vm.exportAndDeleteStrayClips(zipOut)

        assertFalse(files.has("sounds/stray.mp3"))
        assertTrue(vm.strayClips.value.isEmpty())
        assertTrue(zipOut.written != null)
    }

    /** Asks for [label]'s original sound the way the tile editor does, and returns the result. */
    private fun restoreOriginal(vm: BoardViewModel, label: String): OriginalSound {
        var result: OriginalSound? = null
        vm.restoreOriginalSound(label) { result = it }
        return result!!
    }

    @Test
    fun `restoring a tile's original sound brings back its clip from the built-in board pending until Save`() = runTest(dispatcher) {
        // #208: the Jeremy board's "Water" tile plays water.wav.
        repo.save(boardWith(Tile(id = "w", label = "Water", fileName = "mine.m4a")).copy(builtInSource = "jeremy-care-board.zip"))
        files.put("sounds/mine.m4a", "mine".encodeToByteArray())
        val vm = newViewModel()

        val result = restoreOriginal(vm, "Water") as OriginalSound.Restored

        val restored = result.tile.fileName!!
        assertEquals("Jeremy Draft Care Board", result.boardLabel)
        assertTrue(restored.endsWith(".wav"))
        assertTrue(files.sizeOf("sounds/${restored}") > 1_000)
        assertTrue(player.loaded.contains(restored))
        assertEquals("mine.m4a", tile(vm, "w").fileName)

        vm.saveTile("w", tile(vm, "w").copy(fileName = restored))

        assertEquals(restored, tile(vm, "w").fileName)
        assertFalse(files.has("sounds/mine.m4a"))
    }

    @Test
    fun `cancelling after restoring the original sound keeps the tile's own clip`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "w", label = "Water", fileName = "mine.m4a")).copy(builtInSource = "jeremy-care-board.zip"))
        files.put("sounds/mine.m4a", "mine".encodeToByteArray())
        val vm = newViewModel()

        val restored = (restoreOriginal(vm, "Water") as OriginalSound.Restored).tile.fileName!!
        vm.discardTileEdits()

        assertEquals("mine.m4a", tile(vm, "w").fileName)
        assertFalse(files.has("sounds/${restored}"))
    }

    @Test
    fun `a tile with no counterpart on the built-in board reports which board was searched`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "x", label = "My own phrase")).copy(builtInSource = "jeremy-care-board.zip"))
        val vm = newViewModel()

        val result = restoreOriginal(vm, "My own phrase")

        assertEquals("Jeremy Draft Care Board", (result as OriginalSound.NotFound).boardLabel)
    }

    @Test
    fun `a board from before its source was recorded finds its built-in board by name`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "h", label = "Hey")).copy(name = "Sarah (ElevenLabs) Care Board"))
        val vm = newViewModel()

        val result = restoreOriginal(vm, "Hey")

        assertEquals("Sarah (ElevenLabs) Care Board", (result as OriginalSound.Restored).boardLabel)
    }

    @Test
    fun `opening a built-in board records it as the board's source`() = runTest(dispatcher) {
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        vm.openBoard(BoardRef.BuiltIn("tts-care-board.zip", "TTS Care Board"))

        assertEquals("tts-care-board.zip", vm.board.value.builtInSource)
    }

    @Test
    fun `stopping a recording trims it by the device's trim setting`() = runTest(dispatcher) {
        // #204: the tap on Stop is otherwise the last thing on the clip.
        repo.save(boardWith(Tile(id = "a")))
        val vm = newViewModel()

        record(vm)
        assertEquals(250, recorder.lastTrimEndMillis)

        vm.setRecordingTrimEndMillis(0)
        record(vm)
        assertEquals(0, recorder.lastTrimEndMillis)
        assertEquals(0, vm.recordingTrimEndMillis.value)
    }
}

/**
 * The built-in boards, as small stand-ins: the names and labels the view model looks for, the
 * Jeremy board's three pages and its "Water" clip, and a Sarah board with a recorded "Hey".
 */
private fun builtInBoards(zip: FakeZipCodec): Map<String, ByteArray> {
    fun board(board: Board, clips: Map<String, ByteArray> = emptyMap()) = zip.write(
        listOf(ZipEntryData("board.json", BoardJson.encodeToString(board).encodeToByteArray())) +
            clips.map { (name, bytes) -> ZipEntryData("sounds/$name", bytes) }
    )
    val clip = ByteArray(2_000) { it.toByte() }
    return mapOf(
        "jeremy-care-board.zip" to board(
            Board(
                name = "Jeremy Draft Care Board",
                pages = listOf(
                    Page(name = "Trouble", rows = 1, columns = 1, tiles = listOf(Tile(id = "help", label = "Help", fileName = "help.wav"))),
                    Page(name = "Needs", rows = 1, columns = 1, tiles = listOf(Tile(id = "water", label = "Water", fileName = "water.wav")), isHome = true),
                    Page(name = "Talking", rows = 1, columns = 1, tiles = listOf(Tile(id = "yes", label = "Yes", fileName = "yes.wav")))
                )
            ),
            mapOf("help.wav" to clip, "water.wav" to clip, "yes.wav" to clip)
        ),
        "sarah-care-board.zip" to board(
            Board(name = "Sarah (ElevenLabs) Care Board", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3"))))),
            mapOf("hey.mp3" to clip)
        ),
        "tts-care-board.zip" to board(
            Board(name = "TTS Care Board", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", speakWhenNoSound = true)))))
        )
    )
}
