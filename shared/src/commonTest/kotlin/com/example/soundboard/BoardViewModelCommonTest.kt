package com.example.soundboard

import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.data.BoardJson
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.FakeBundledBoards
import com.example.soundboard.data.FakeZipCodec
import com.example.soundboard.data.InMemoryFileStore
import com.example.soundboard.data.KeyValueStore
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.ZipEntryData
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * A smoke test that the real [BoardViewModel] runs on every platform, WebAssembly included.
 * The full suite lives in the app (BoardViewModelTest), against Android storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelCommonTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val files = InMemoryFileStore()
    private val zip = FakeZipCodec()
    private val speaker = RecordingSpeaker()
    private val player = RecordingPlayer()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun fallbackZip(board: Board) =
        zip.write(listOf(ZipEntryData("board.json", BoardJson.encodeToString(board).encodeToByteArray())))

    private fun newViewModel(bundled: Map<String, ByteArray> = emptyMap()) = BoardViewModel(
        boardRepo = BoardRepository(files, zip, FakeBundledBoards(bundled)),
        player = player,
        recorder = NoRecorder(),
        savedBoardRepo = SavedBoardRepository(files),
        speaker = speaker,
        devicePrefs = DevicePreferences(MapKeyValueStore()),
        recentBoardsRepo = RecentBoardsRepository(files),
        ioDispatcher = dispatcher,
        now = { 42L }
    )

    @Test
    fun `a fresh install opens the fallback built-in board`() = runTest(dispatcher) {
        val fallback = Board(name = "Fallback", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", speakWhenNoSound = true)))))

        val vm = newViewModel(mapOf("jeremy-care-board.zip" to fallbackZip(fallback)))

        assertEquals("Fallback", vm.board.value.name)
    }

    @Test
    fun `tapping a speaking tile speaks it, and an edit is saved`() = runTest(dispatcher) {
        BoardRepository(files, zip, FakeBundledBoards()).save(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", speakWhenNoSound = true)))))
        )
        val vm = newViewModel()

        vm.activate(vm.board.value.findTile("hey")!!)
        vm.setLabel("hey", "Hello")

        assertEquals(listOf("Hey"), speaker.spoken)
        assertEquals("Hello", BoardRepository(files, zip, FakeBundledBoards()).load().findTile("hey")?.label)
    }

    @Test
    fun `saving a board as a new one records it as recently used`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.saveBoardAs("Mine")
        vm.refreshRecentBoards()

        assertEquals(listOf("Mine" to 42L), vm.recentBoards.value.map { it.label to it.usedAt })
    }
}

private class RecordingSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) {
        spoken += text
    }
    override fun stop() {}
    override fun shutdown() {}
}

private class RecordingPlayer : Player {
    override fun load(key: String, path: String) {}
    override fun play(key: String, volume: Float) {}
    override fun unload(key: String) {}
    override fun clear() {}
    override fun release() {}
}

private class NoRecorder : Recorder {
    override fun start(path: String): Boolean = false
    override fun stop(): Boolean = false
    override fun cancel() {}
}

private class MapKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
}
