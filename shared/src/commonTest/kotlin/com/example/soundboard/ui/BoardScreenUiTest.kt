package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The board screen's main flows, on the JVM and in the browser (#219). What needs a real
 * device (rotation, drag gestures, the system font size) stays in the app's emulator tests.
 */
@OptIn(ExperimentalTestApi::class)
class BoardScreenUiTest : BoardUiTest() {

    private fun oneTile(tile: Tile) = Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(tile))))

    private fun twoPages(firstIsHome: Boolean = false) = Board(
        pages = listOf(
            Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Alpha", fileName = "a.mp3")), isHome = firstIsHome),
            Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Beta", fileName = "b.mp3")))
        )
    )

    // --- Tiles and the tile editor ---

    @Test
    fun tappingAFilledTilePlaysIt() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))

        onNodeWithText("Air horn").performClick()

        waitUntil(timeoutMillis = 2_000) { "a.mp3" in app.player.playedKeys }
    }

    @Test
    fun tappingAnEmptyTileOpensTheEditorInsteadOfPlaying() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a")))

        onNodeWithText("+").performClick()

        onNodeWithText("Edit tile").assertIsDisplayed()
        assertTrue(app.player.playedKeys.isEmpty())
    }

    @Test
    fun theEditorsCancelDiscardsChangesAndSaveKeepsThem() = runUiTest {
        // #207: the editor works on a draft; nothing reaches the tile until Save.
        val app = launchBoard(oneTile(Tile(id = "a")))

        onNodeWithText("+").performClick()
        onNode(hasSetTextAction() and hasText("Name")).performTextInput("Water")
        onNodeWithText("Cancel").performClick()

        onNodeWithText("Edit tile").assertDoesNotExist()
        assertEquals("", app.vm.board.value.findTile("a")?.label)

        onNodeWithText("+").performClick()
        onNode(hasSetTextAction() and hasText("Name")).performTextInput("Water")
        onNodeWithText("Save").performClick()

        waitForIdle()
        assertEquals("Water", app.vm.board.value.findTile("a")?.label)
        onNodeWithText("Water").assertIsDisplayed()
    }

    @Test
    fun editModeMakesATapOnAFilledTileEditIt() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))

        toggleMenuSwitch("Edit mode")
        onNodeWithText("Air horn").performClick()

        onNodeWithText("Edit tile").assertIsDisplayed()
        assertTrue(app.player.playedKeys.isEmpty())
    }

    @Test
    fun restoreOriginalSoundPutsBackTheBuiltInBoardsClipOnSave() = runUiTest {
        // #208: matched by label on the built-in board the board came from.
        val builtIn = BuiltInBoard(
            Board(name = "TTS Care Board", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "w", label = "Water", fileName = "water.m4a", volume = 0.5f))))),
            sounds = mapOf("water.m4a" to byteArrayOf(1, 2, 3))
        )
        val board = oneTile(Tile(id = "a", label = "water ")).copy(builtInSource = "tts-care-board.zip")
        val app = launchBoard(board, builtIns = mapOf("tts-care-board.zip" to builtIn))

        toggleMenuSwitch("Edit mode")
        onNodeWithText("water ").performClick()
        onNodeWithText("Restore original sound").performClick()
        waitUntil(timeoutMillis = 2_000) { onAllNodesWithText("Restored the original sound from TTS Care Board", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Only the draft has it so far.
        assertNull(app.vm.board.value.findTile("a")?.fileName)

        onNodeWithText("Save").performClick()

        waitForIdle()
        val tile = app.vm.board.value.findTile("a")
        assertNotNull(tile?.fileName)
        assertEquals(0.5f, tile.volume)
    }

    @Test
    fun restoreOriginalSoundSaysWhereItLookedWhenNothingMatches() = runUiTest {
        val builtIn = BuiltInBoard(Board(name = "TTS Care Board", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "w", label = "Water"))))))
        launchBoard(
            oneTile(Tile(id = "a", label = "Juice")).copy(builtInSource = "tts-care-board.zip"),
            builtIns = mapOf("tts-care-board.zip" to builtIn)
        )

        toggleMenuSwitch("Edit mode")
        onNodeWithText("Juice").performClick()
        onNodeWithText("Restore original sound").performClick()

        waitUntil(timeoutMillis = 2_000) {
            onAllNodesWithText("No original clip found for the “Juice” tile in TTS Care Board.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // --- The menu ---

    @Test
    fun switchBoardListsTheBuiltInBoardsAndOpensOne() = runUiTest {
        // #197: the built-in boards are in the first dropdown, not only behind "See all boards...".
        val builtIn = BuiltInBoard(Board(name = "TTS Care Board", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "w", label = "Water", speakWhenNoSound = true))))))
        val app = launchBoard(oneTile(Tile(id = "a")), builtIns = mapOf("tts-care-board.zip" to builtIn))

        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Switch board").performClick()
        onNodeWithText("See all boards...").assertIsDisplayed()
        onNodeWithText("TTS Care Board").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.name == "TTS Care Board" }
        onNodeWithText("Water").assertIsDisplayed()
    }

    @Test
    fun saveBoardAsRenamesTheBoard() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a")))

        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Save board as...").performClick()
        onNode(hasSetTextAction() and hasText("New Board")).performTextReplacement("Family Board")
        onNodeWithText("Save").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.name == "Family Board" }
    }

    // --- Settings ---

    @Test
    fun aSettingsGroupOpensItsOwnDialogAndBackReturnsToTheList() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a")))

        openSettings()
        onNodeWithText("Screen").performClick()
        clickSwitchBeside("Keep screen awake")
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.keepScreenAwake }

        onNodeWithText("Back").performClick()
        // Back on the list, whose summary shows the change.
        onNodeWithText("Tile labels").assertIsDisplayed()
        onNodeWithText("Stays awake", substring = true).assertIsDisplayed()
        onNodeWithText("Done").performClick()
        onNodeWithText("Tile labels").assertDoesNotExist()
    }

    @Test
    fun theRecordingGroupSetsHowMuchToTrimAndTheListSummarizesIt() = runUiTest {
        // #204: per device, so it's in DevicePreferences rather than on the board.
        val app = launchBoard(oneTile(Tile(id = "a")))

        openSettings()
        onNodeWithText("Leaves off the last 0.25 s").assertIsDisplayed()
        onNodeWithText("Recording").performClick()
        onNodeWithText("Trim the end of new recordings").performClick()
        onNodeWithText("0.5 s").performClick()

        waitUntil(timeoutMillis = 2_000) { app.devicePrefs.recordingTrimEndMillis == 500 }
        onNodeWithText("Back").performClick()
        onNodeWithText("Leaves off the last 0.5 s").assertIsDisplayed()
    }

    // --- Show mode ---

    @Test
    fun showModeFromTheMenuShowsTheScriptAndATapClosesIt() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Water", ttsScript = "Some water please", fileName = "a.mp3")))

        toggleMenuSwitch("Show mode")
        onNodeWithText("Water").performClick()

        onNodeWithText("Some water please").assertIsDisplayed()
        assertEquals(listOf("a.mp3"), app.player.playedKeys)

        onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).performClick()

        onNodeWithText("Some water please").assertDoesNotExist()
        // The closing tap landed on the text screen, not the tile under it.
        assertEquals(listOf("a.mp3"), app.player.playedKeys)
    }

    @Test
    fun showModeWithoutTapToCloseIgnoresTapsAndClosesOnItsTimer() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Water", fileName = "a.mp3")))
        runOnIdle {
            app.vm.setShowModeEnabled(true)
            app.vm.setShowModeMuteSounds(true)
            app.vm.setShowModeTimerSeconds(3)
            app.vm.setShowModeTapToClose(false)
        }

        onNodeWithText("Water").performClick()
        onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).performClick()

        onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).assertIsDisplayed()
        assertTrue(app.player.playedKeys.isEmpty())

        advanceClock(3_500L)

        assertTrue(onAllNodes(hasTestTag(SHOW_TEXT_OVERLAY_TAG)).fetchSemanticsNodes().isEmpty())
    }

    // --- The page tab row ---

    @Test
    fun tappingAPageTabShowsThatPage() = runUiTest {
        val app = launchBoard(twoPages())

        onNodeWithText("Second").performClick()

        waitForIdle()
        assertEquals(1, app.vm.board.value.currentPageIndex)
        displayedNode("Beta").assertIsDisplayed()
    }

    @Test
    fun addPageMakesANewNamedPageAndShowsIt() = runUiTest {
        val app = launchBoard(Board(pages = listOf(Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        onNodeWithContentDescription("Add page").performClick()
        onNode(hasSetTextAction()).performTextReplacement("Feelings")
        onNodeWithText("Save").performClick()

        waitForIdle()
        assertEquals(listOf("First", "Feelings"), app.vm.board.value.pages.map { it.name })
        assertEquals(1, app.vm.board.value.currentPageIndex)
    }

    @Test
    fun longPressingAPageTabOpensItsOptionsAndGridSizeChangesIt() = runUiTest {
        val tiles = (0 until 3).map { Tile(id = "t$it", label = "S$it", fileName = "$it.mp3") } + Tile(id = "t3")
        val app = launchBoard(Board(pages = listOf(Page(rows = 2, columns = 2, tiles = tiles))))

        longPressPageTab("Page 1")
        onNodeWithText("Grid size (2x2", substring = true).performClick()
        onNodeWithContentDescription("Increase Columns").performClick()
        onNodeWithText("Apply").performClick()

        waitForIdle()
        assertEquals(3, app.vm.board.value.pages.single().columns)
    }

    // #233: in a landscape phone window a dialog has little height, and its lower controls are
    // only reachable if its content scrolls. performScrollTo fails without a scrolling parent.
    @Test
    fun inLandscapePageOptionsScrollsToItsLastAction() = runUiTest(landscape = true) {
        launchBoard(twoPages())

        // The menu is taller than a landscape window too; it scrolls on its own (a Material
        // dropdown), so its lower items are reached the same way.
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Page options", substring = true).performScrollTo().performClick()

        onNodeWithText("Delete page").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theMenusPageOptionsOpensTheSameDialog() = runUiTest {
        launchBoard(Board(pages = listOf(Page(name = "Feelings", rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Page options (Feelings)").performClick()

        onNodeWithText("Rename").assertIsDisplayed()
        onNodeWithText("Grid size (1x1", substring = true).assertIsDisplayed()
    }

    @Test
    fun theStickyHomeRowShowsOnOtherPagesButNotTwiceOnHome() = runUiTest {
        launchBoard(
            Board(
                pages = listOf(
                    Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Alpha", fileName = "a.mp3"))),
                    Page(name = "Home", rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true),
                    Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Beta", fileName = "b.mp3")))
                )
            )
        )
        assertEquals(0, displayedCount("Hey"))

        openSettings()
        onNodeWithText("Home page").performClick()
        clickSwitchBeside("Sticky home row")
        onNodeWithText("Done").performClick()

        assertEquals(1, displayedCount("Hey"))
        onNodeWithText("Second").performClick()
        waitForIdle()
        assertEquals(1, displayedCount("Hey"))
        onNodeWithText("Home").performClick()
        waitForIdle()
        // Once, as the page's own content.
        assertEquals(1, displayedCount("Hey"))
    }

    @Test
    fun afterTheIdleTimeoutTheBoardGoesBackToTheHomePage() = runUiTest {
        val app = launchBoard(twoPages(firstIsHome = true))

        onNodeWithText("Second").performClick()
        waitForIdle()
        assertEquals(1, app.vm.board.value.currentPageIndex)

        advanceClock(6 * 60 * 1000L)

        assertEquals(0, app.vm.board.value.currentPageIndex)
    }

    // --- Helpers ---

    private fun ComposeUiTest.openSettings() {
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Settings").performClick()
    }

    /** Moves Compose's clock on by [millis], for delay()s that only run on it. */
    private fun ComposeUiTest.advanceClock(millis: Long) {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(millis)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** Flips one of the menu's switches, then closes the menu. */
    private fun ComposeUiTest.toggleMenuSwitch(label: String) {
        onNodeWithContentDescription("Menu").performClick()
        clickSwitchBeside(label)
        closeMenu()
        onNodeWithText(label).assertDoesNotExist()
    }

    /**
     * Flips the switch for [label]. In Settings the whole row is the toggle (SwitchRow); the
     * menu's rows are a bare Switch beside a Text, so fall back to the switch level with it.
     */
    private fun ComposeUiTest.clickSwitchBeside(label: String) {
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

    /** How many nodes with [text] are on screen; the pager also composes neighboring pages. */
    private fun ComposeUiTest.displayedCount(text: String): Int {
        val nodes = onAllNodesWithText(text)
        return nodes.fetchSemanticsNodes().indices.count { nodes[it].isDisplayed() }
    }

    /** The one on-screen node with [text]. */
    private fun ComposeUiTest.displayedNode(text: String): SemanticsNodeInteraction {
        val nodes = onAllNodesWithText(text)
        return nodes[nodes.fetchSemanticsNodes().indices.single { nodes[it].isDisplayed() }]
    }

    /** Holds a page tab down past the long-press timeout, which opens its options. */
    private fun ComposeUiTest.longPressPageTab(pageName: String) {
        onNode(hasText(pageName) and hasClickAction()).performTouchInput { down(center) }
        advanceClock(600)
        onNode(hasText(pageName) and hasClickAction()).performTouchInput { up() }
        waitForIdle()
    }
}
