package com.example.soundboard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import com.example.soundboard.ui.theme.SoundboardTheme
import kotlin.math.abs
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

    /** Shows [BoardScreen] for a [TestBoardApp] on [board], in the board's theme as the apps do. */
    fun ComposeUiTest.launchBoard(board: Board, builtIns: Map<String, BuiltInBoard> = emptyMap()): TestBoardApp {
        val app = TestBoardApp(board, builtIns, dispatcher)
        check(app.vm.board.value.pages.first().id == board.pages.first().id) { "the board didn't load" }
        val platformWidthDialogs = dialogsUsePlatformWidth()
        setContent {
            val shown by app.vm.board.collectAsState()
            CompositionLocalProvider(LocalDialogUsesPlatformWidth provides platformWidthDialogs) {
                SoundboardTheme(themeMode = shown.themeMode) { BoardScreen(vm = app.vm) }
            }
        }
        waitForIdle()
        return app
    }

    protected fun ComposeUiTest.openSettings() {
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Settings").performClick()
    }

    /** Moves Compose's clock on by [millis], for delay()s that only run on it. */
    protected fun ComposeUiTest.advanceClock(millis: Long) {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(millis)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** Flips one of the menu's switches, then closes the menu. */
    protected fun ComposeUiTest.toggleMenuSwitch(label: String) {
        onNodeWithContentDescription("Menu").performClick()
        clickSwitchBeside(label)
        closeMenu()
        onNodeWithText(label).assertDoesNotExist()
    }

    /**
     * Flips the switch for [label]. In Settings the whole row is the toggle (SwitchRow); the
     * menu's rows are a bare Switch beside a Text, so fall back to the switch level with it.
     */
    protected fun ComposeUiTest.clickSwitchBeside(label: String) {
        val labelNode = onNodeWithText(label)
        runCatching { labelNode.performScrollTo() }
        val labelCenter = labelNode.fetchSemanticsNode().boundsInRoot.center
        val switches = onAllNodes(isToggleable())
        val nodes = switches.fetchSemanticsNodes()
        val index = nodes.indexOfFirst { it.boundsInRoot.contains(labelCenter) }
            .takeIf { it >= 0 }
            ?: nodes.indexOfFirst { abs(it.boundsInRoot.center.y - labelCenter.y) < 40f }
        switches[index].performClick()
        waitForIdle()
    }

    /** Holds a page tab down past the long-press timeout, which opens its options. */
    protected fun ComposeUiTest.longPressPageTab(pageName: String) {
        onNode(hasText(pageName) and hasClickAction()).performTouchInput { down(center) }
        advanceClock(600)
        onNode(hasText(pageName) and hasClickAction()).performTouchInput { up() }
        waitForIdle()
    }
}
