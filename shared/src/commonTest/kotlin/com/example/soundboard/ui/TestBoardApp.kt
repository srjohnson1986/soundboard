package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.example.soundboard.BoardViewModel
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.data.BoardJson
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.FakeBundledBoards
import com.example.soundboard.data.FakeZipCodec
import com.example.soundboard.data.InMemoryFileStore
import com.example.soundboard.data.MapKeyValueStore
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.ZipEntryData
import com.example.soundboard.model.Board
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The real [BoardViewModel] on in-memory storage with fake audio, for the shared UI tests.
 * [builtIns] are the built-in boards the build packages, by asset name. Its work runs on
 * [dispatcher], which [BoardUiTest] also makes the main one.
 */
class TestBoardApp(board: Board, builtIns: Map<String, BuiltInBoard> = emptyMap(), dispatcher: CoroutineDispatcher) {
    val files = InMemoryFileStore()
    val zip = FakeZipCodec()
    val player = FakePlayer()
    val recorder = FakeRecorder()
    val speaker = FakeSpeaker()
    val devicePrefs = DevicePreferences(MapKeyValueStore())

    init {
        files.put("board.json", BoardJson.encodeToString(board).encodeToByteArray())
        // load() treats a fileName with no file behind it as "needs recording", so give every sound a file.
        board.soundFileNames.forEach { files.put("sounds/$it", it.encodeToByteArray()) }
    }

    val vm = BoardViewModel(
        boardRepo = BoardRepository(files, zip, FakeBundledBoards(builtIns.mapValues { (_, b) -> b.zip(zip) })),
        player = player,
        recorder = recorder,
        savedBoardRepo = SavedBoardRepository(files),
        speaker = speaker,
        devicePrefs = devicePrefs,
        recentBoardsRepo = RecentBoardsRepository(files),
        ioDispatcher = dispatcher,
        now = { 42L }
    )
}

/** A built-in board: its layout and the clips it ships, by file name. */
class BuiltInBoard(val board: Board, val sounds: Map<String, ByteArray> = emptyMap()) {
    fun zip(codec: FakeZipCodec): ByteArray = codec.write(
        listOf(ZipEntryData("board.json", BoardJson.encodeToString(board).encodeToByteArray())) +
            sounds.map { (name, bytes) -> ZipEntryData("sounds/$name", bytes) }
    )
}

/**
 * The base class for tests of [BoardScreen]. The view model's coroutines run at once, on an
 * unconfined test dispatcher: in the browser a test can't wait for real ones, since waiting
 * there doesn't give the page's event loop a turn.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
abstract class BoardUiTest : UiTest() {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMain() {} // = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    /** Shows [BoardScreen] for a [TestBoardApp] on [board]. */
    fun ComposeUiTest.launchBoard(board: Board, builtIns: Map<String, BuiltInBoard> = emptyMap()): TestBoardApp {
        val app = TestBoardApp(board, builtIns, dispatcher)
        check(app.vm.board.value.pages.first().id == board.pages.first().id) { "the board didn't load" }
        setContent { BoardScreen(vm = app.vm) }
        waitForIdle()
        return app
    }
}
