package com.example.soundboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.soundboard.BoardViewModel
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.Tile
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repo: BoardRepository
    private lateinit var player: FakePlayer
    private lateinit var speaker: FakeSpeaker
    private lateinit var vm: BoardViewModel

    private fun launchWith(board: Board) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = BoardRepository(context)
        // load() treats a fileName with no file behind it as "needs recording" (see
        // BoardRepository.sanitizeMissingSounds), so give every referenced sound a file.
        board.pages.flatMap { it.tiles }.mapNotNull { it.fileName }.forEach { name ->
            repo.soundFile(name).apply { parentFile?.mkdirs() }.writeText(name)
        }
        repo.save(board)
        player = FakePlayer()
        speaker = FakeSpeaker()
        // Show mode lives in device prefs, which outlive each test — start every test with it off.
        DevicePreferences(context).showMode = ShowModeSettings()
        vm = BoardViewModel(repo, player, FakeRecorder(), SavedBoardRepository(context), speaker, DevicePreferences(context), RecentBoardsRepository(context))

        composeRule.setContent {
            BoardScreen(vm = vm)
        }
        // The view model loads the board off the main thread, which Compose's idling doesn't
        // track — on a slow emulator the first assertions would otherwise see the empty
        // default board instead of this one.
        composeRule.waitUntil(timeoutMillis = 5_000) { vm.board.value.pages.first().id == board.pages.first().id }
        composeRule.waitForIdle()
    }

    @Test
    fun tappingFilledTilePlaysIt() {
        launchWith(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))))
        )

        composeRule.onNodeWithText("Air horn").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { player.playedKeys.contains("a.mp3") }
    }

    @Test
    fun tappingEmptyTileOpensEditDialogInsteadOfPlaying() {
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        composeRule.onNodeWithText("+").performClick()

        composeRule.onNodeWithText("Edit tile").assertIsDisplayed()
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun editModeTogglePutsFilledTileTapsIntoEditDialog() {
        // Edit mode is toggled from the menu rather than always-on per tile (a
        // permanent pencil crowded small tiles); once on, a pencil reappears on
        // every tile and tapping a filled one edits it instead of playing.
        launchWith(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))))
        )

        composeRule.onNodeWithContentDescription("Menu").performClick()
        clickSwitchBeside("Edit mode")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Air horn").performClick()

        composeRule.onNodeWithText("Edit tile").assertIsDisplayed()
        // The name shows up twice: the (now-obscured) tile behind the dialog, and
        // the dialog's prefilled text field. Checking count avoids disambiguating
        // which node the dialog's overlay window leaves "displayed".
        assertEquals(2, composeRule.onAllNodesWithText("Air horn").fetchSemanticsNodes().size)
        assertTrue(player.played.isEmpty())
    }

    /**
     * Flips the switch for [label]. In Settings the whole row is the toggle (SwitchRow), so
     * the toggleable node contains the label; the menu's Edit mode row is a bare Switch
     * beside a Text, so fall back to the switch level with the label.
     */
    private fun clickSwitchBeside(label: String) {
        val labelNode = composeRule.onNodeWithText(label)
        runCatching { labelNode.performScrollTo() }
        val labelCenter = labelNode.fetchSemanticsNode().boundsInRoot.center
        val switches = composeRule.onAllNodes(isToggleable())
        val nodes = switches.fetchSemanticsNodes()
        val index = nodes.indexOfFirst { it.boundsInRoot.contains(labelCenter) }
            .takeIf { it >= 0 }
            ?: nodes.indexOfFirst { abs(it.boundsInRoot.center.y - labelCenter.y) < 40f }
        switches[index].performClick()
        composeRule.waitForIdle()
    }

    /** How many nodes with [text] are actually on screen — the pager keeps neighboring pages composed off-screen too. */
    private fun displayedCount(text: String): Int {
        val nodes = composeRule.onAllNodesWithText(text)
        return nodes.fetchSemanticsNodes().indices.count { nodes[it].isDisplayed() }
    }

    /** The one on-screen node with [text], ignoring copies on neighboring pages composed off-screen. */
    private fun displayedNode(text: String): SemanticsNodeInteraction {
        val nodes = composeRule.onAllNodesWithText(text)
        return nodes[nodes.fetchSemanticsNodes().indices.single { nodes[it].isDisplayed() }]
    }

    @Test
    fun settingsGroupsOpenTheirOwnDialogAndBackReturnsToTheList() {
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Screen").performClick()
        clickSwitchBeside("Keep screen awake")
        composeRule.waitUntil(timeoutMillis = 2_000) { vm.board.value.keepScreenAwake }

        composeRule.onNodeWithText("Back").performClick()
        // Back to the group list, whose summary now reflects the change.
        composeRule.onNodeWithText("Tile labels").assertIsDisplayed()
        composeRule.onNodeWithText("Stays awake", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(0, composeRule.onAllNodesWithText("Tile labels").fetchSemanticsNodes().size)
    }

    @Test
    fun saveBoardAsRenamesTheBoard() {
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Save board as...").performClick()
        // "New Board" appears twice once the dialog is up (the title bar behind
        // it, and the field's prefilled value) — hasSetTextAction() narrows to
        // the actual editable field regardless of window traversal order.
        composeRule.onNode(hasSetTextAction() and hasText("New Board")).performTextReplacement("Family Board")
        composeRule.onNodeWithText("Save").performClick()

        // The title bar reads "Soundboard" now, not the board's name.
        composeRule.waitUntil(timeoutMillis = 2_000) { vm.board.value.name == "Family Board" }
    }

    @Test
    fun saveInDialogUpdatesTileLabel() {
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Boo")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Boo").assertIsDisplayed()
    }

    @Test
    fun applyingNewGridSizeChangesTileCount() {
        launchWith(
            Board(
                pages = listOf(
                    Page(
                        rows = 2,
                        columns = 2,
                        // One blank tile keeps the last row from being full, which would auto-grow a row.
                        tiles = (0 until 3).map { Tile(id = "t$it", label = "S$it", fileName = "$it.mp3") } + Tile(id = "t3")
                    )
                )
            )
        )

        // Grid size now lives in the page-options dialog, opened by long-pressing the
        // page's own tab — hold past the long-press timeout via the test clock, the same
        // way idleTimeoutReturnsToTheHomePageAfterInactivity drives a real delay()-based wait.
        longPressPageTab("Page 1")
        composeRule.onNodeWithText("Grid size (2x2", substring = true).performClick()
        composeRule.onNodeWithContentDescription("Increase Columns").performClick()
        composeRule.onNodeWithText("Apply").performClick()
        pageTab("Page 1").performTouchInput { up() }

        longPressPageTab("Page 1")
        composeRule.onNodeWithText("Grid size (2x3", substring = true).assertIsDisplayed()
        pageTab("Page 1").performTouchInput { up() }
    }

    @Test
    fun pageOptionsMenuItemOpensTheSameDialogAsLongPressingTheTab() {
        // The long-press-and-hold gesture on a page tab is easy to trigger by
        // accident while trying to drag/scroll the tab row (#1) — this explicit
        // menu entry opens the same PageOptionsDialog for the current page.
        launchWith(
            Board(pages = listOf(Page(name = "Feelings", rows = 1, columns = 1, tiles = listOf(Tile(id = "a")))))
        )

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Page options (Feelings)").performClick()

        // "Feelings" itself is ambiguous here — it's both the tab label behind the
        // dialog and the dialog's own title — so assert on content unique to the dialog.
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Grid size (1x1", substring = true).assertIsDisplayed()
    }

    /** A page's tab — its name alone also matches the page options dialog's title once that's open. */
    private fun pageTab(pageName: String) = composeRule.onNode(hasText(pageName) and hasClickAction())

    /** Holds a page tab down past the long-press timeout to open its PageOptionsDialog, without releasing it. */
    private fun longPressPageTab(pageName: String) {
        pageTab(pageName).performTouchInput { down(center) }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun longPressDragReordersTiles() {
        // adb shell input's synthetic swipe interpolates movement from the very
        // first frame, tripping touch-slop cancellation before the long-press
        // timeout fires (see ARCHITECTURE.md). Driving raw pointer events through
        // Compose's TouchInjectionScope avoids that: hold position until past the
        // long-press timeout, then move, exactly like a real long-press-then-drag.
        launchWith(
            Board(
                pages = listOf(
                    Page(
                        rows = 1,
                        columns = 3,
                        tiles = listOf(
                            Tile(id = "a", label = "A", fileName = "a.mp3"),
                            Tile(id = "b", label = "B", fileName = "b.mp3"),
                            Tile(id = "c", label = "C", fileName = "c.mp3")
                        )
                    )
                )
            )
        )

        val aLeft = composeRule.onNodeWithText("A").fetchSemanticsNode().boundsInRoot.left
        val bLeft = composeRule.onNodeWithText("B").fetchSemanticsNode().boundsInRoot.left
        val cellStepPx = bLeft - aLeft

        composeRule.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(600) // past the platform's ~500ms long-press timeout
            moveBy(Offset(x = cellStepPx * 1.2f, y = 0f))
            up()
        }

        composeRule.waitForIdle()

        assertEquals(listOf("B", "A", "C"), vm.board.value.currentPage.visibleTiles.map { it.label }.filter { it.isNotEmpty() })
    }

    @Test
    fun longPressWithoutMovingAGridTilePreviewsItWithoutReordering() {
        // A hold that clears the long-press timeout but never crosses into a drag
        // (#4) shares the same gesture detector as longPressDragReordersTiles above —
        // it should preview the tile in place instead of reordering it.
        launchWith(
            Board(
                pages = listOf(
                    Page(
                        rows = 1,
                        columns = 2,
                        tiles = listOf(
                            Tile(id = "a", label = "A", fileName = "a.mp3"),
                            Tile(id = "b", label = "B", fileName = "b.mp3")
                        )
                    )
                )
            )
        )

        composeRule.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 2_000) { player.playedKeys.contains("a.mp3") }
        assertEquals(listOf("A", "B"), vm.board.value.currentPage.visibleTiles.map { it.label }.filter { it.isNotEmpty() })
    }

    @Test
    fun longPressPreviewIsDisabledInEditMode() {
        launchWith(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))))
        )
        composeRule.onNodeWithContentDescription("Menu").performClick()
        clickSwitchBeside("Edit mode")
        composeRule.onNodeWithContentDescription("Menu").performClick()

        composeRule.onNodeWithText("Air horn").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(player.played.isEmpty())
    }

    @Test
    fun longPressWithoutMovingAStickyHomeRowTilePreviewsItWithoutOpeningTheEditor() {
        launchWith(
            Board(
                pages = listOf(
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))),
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true)
                ),
                stickyHomeRowEnabled = true
            )
        )

        displayedNode("Hey").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 2_000) { player.playedKeys.contains("hey.mp3") }
        composeRule.onNodeWithText("Edit tile").assertDoesNotExist()
    }

    @Test
    fun tappingAPageTabSwitchesTheVisibleGrid() {
        launchWith(
            Board(
                pages = listOf(
                    Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Alpha", fileName = "a.mp3"))),
                    Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Beta", fileName = "b.mp3")))
                )
            )
        )

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()

        composeRule.onNodeWithText("Second").performClick()

        composeRule.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun addPageCreatesANewNamedPageAndSwitchesToIt() {
        launchWith(Board(pages = listOf(Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        composeRule.onNodeWithContentDescription("Add page").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Feelings")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Feelings").assertIsDisplayed()
        assertEquals(listOf("First", "Feelings"), vm.board.value.pages.map { it.name })
        assertEquals(1, vm.board.value.currentPageIndex)
    }

    @Test
    fun stickyHomeRowShowsHomePagesFirstRowOnOtherPagesButNotOnHomeItself() {
        launchWith(
            Board(
                pages = listOf(
                    Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Alpha", fileName = "a.mp3"))),
                    Page(name = "Home", rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true),
                    Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Beta", fileName = "b.mp3")))
                )
            )
        )

        assertEquals(0, displayedCount("Hey"))

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Home page").performClick()
        clickSwitchBeside("Sticky home row")
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(1, displayedCount("Hey"))
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()

        composeRule.onNodeWithText("Second").performClick()
        composeRule.waitForIdle()

        assertEquals(1, displayedCount("Hey"))
        composeRule.onNodeWithText("Beta").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitForIdle()

        // Shown once, as ordinary page content — not duplicated by the sticky banner.
        assertEquals(1, displayedCount("Hey"))
    }

    @Test
    fun stickyHomeRowPinsTheHomePagesFirstRowWhileScrollingTheHomePageItself() {
        val tiles = (0 until 20).map { i -> Tile(id = "t$i", label = "T$i", fileName = "$i.mp3") }
        launchWith(
            Board(
                stickyHomeRowEnabled = true,
                pages = listOf(Page(rows = 10, columns = 2, tiles = tiles, isHome = true))
            )
        )

        repeat(6) {
            composeRule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.3f) }
            composeRule.waitForIdle()
        }

        // The pinned first row stays visible even after scrolling the rest of the
        // home page's own grid far enough to reach its last row.
        composeRule.onNodeWithText("T0").assertIsDisplayed()
        composeRule.onNodeWithText("T1").assertIsDisplayed()
        composeRule.onNodeWithText("T19").assertIsDisplayed()
    }

    @Test
    fun withoutStickyHomeRowEnabledTheHomePageIsOneOrdinaryScrollingGrid() {
        val tiles = (0 until 20).map { i -> Tile(id = "t$i", label = "T$i", fileName = "$i.mp3") }
        launchWith(
            Board(
                stickyHomeRowEnabled = false,
                pages = listOf(Page(rows = 10, columns = 2, tiles = tiles, isHome = true))
            )
        )

        repeat(6) {
            composeRule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.3f) }
            composeRule.waitForIdle()
        }

        // With the toggle off, the first row scrolls away like any other row instead
        // of staying pinned.
        composeRule.onNodeWithText("T0").assertDoesNotExist()
        composeRule.onNodeWithText("T19").assertIsDisplayed()
    }

    @Test
    fun swipingChangesTheActivePageAndTabSelection() {
        launchWith(
            Board(
                pages = listOf(
                    Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Alpha", fileName = "a.mp3"))),
                    Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Beta", fileName = "b.mp3")))
                )
            )
        )

        composeRule.onNodeWithText("Alpha").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Beta").assertIsDisplayed()
        assertEquals(1, vm.board.value.currentPageIndex)
    }

    @Test
    fun idleTimeoutReturnsToTheHomePageAfterInactivity() {
        // Relies on Compose's test clock advancing the coroutine delay() inside
        // BoardScreen's idle-timer effect — verify on-device if this ever flakes,
        // since virtual-time support for plain delay() (vs. frame-based animation)
        // is more infrastructure-dependent than the rest of this test suite.
        launchWith(
            Board(
                pages = listOf(
                    Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Alpha", fileName = "a.mp3")), isHome = true),
                    Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Beta", fileName = "b.mp3")))
                )
            )
        )

        composeRule.onNodeWithText("Second").performClick()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(6 * 60 * 1000L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(0, vm.board.value.currentPageIndex)
    }

    @Test
    fun showModeToggleFromMenuShowsTheScriptAndTapCloses() {
        launchWith(
            Board(
                pages = listOf(
                    Page(
                        rows = 1,
                        columns = 1,
                        tiles = listOf(Tile(id = "a", label = "Water", ttsScript = "Some water please", fileName = "a.mp3"))
                    )
                )
            )
        )

        composeRule.onNodeWithContentDescription("Menu").performClick()
        clickSwitchBeside("Show mode")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Water").performClick()

        composeRule.onNodeWithText("Some water please").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 2_000) { player.playedKeys == listOf("a.mp3") }

        composeRule.onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).performClick()

        assertEquals(0, composeRule.onAllNodesWithText("Some water please").fetchSemanticsNodes().size)
        // The closing tap landed on the text screen, not the tile underneath it.
        assertEquals(listOf("a.mp3"), player.playedKeys)
    }

    @Test
    fun showModeWithoutTapToCloseIgnoresTapsAndClosesOnTheTimer() {
        launchWith(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Water", fileName = "a.mp3")))))
        )
        composeRule.runOnIdle {
            vm.setShowModeEnabled(true)
            vm.setShowModeMuteSounds(true)
            vm.setShowModeTimerSeconds(3)
            vm.setShowModeTapToClose(false)
        }

        composeRule.onNodeWithText("Water").performClick()
        composeRule.onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).performClick()

        composeRule.onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).assertIsDisplayed()
        assertTrue(player.played.isEmpty())
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodes(hasTestTag(SHOW_TEXT_OVERLAY_TAG)).fetchSemanticsNodes().isEmpty()
        }
    }
}
