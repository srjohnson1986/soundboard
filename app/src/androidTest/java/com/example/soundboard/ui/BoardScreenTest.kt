package com.example.soundboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.soundboard.BoardViewModel
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.PresetRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakePlayer : Player {
    val played = mutableListOf<String>()
    override fun load(key: String, file: File) {}
    override fun play(key: String, volume: Float) {
        played += key
    }
    override fun unload(key: String) {}
    override fun clear() {}
    override fun release() {}
}

private class FakeRecorder : Recorder {
    override fun start(file: File) = true
    override fun stop() = true
    override fun cancel() {}
}

private class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) {
        spoken += text
    }
    override fun stop() {}
    override fun shutdown() {}
}

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
        repo.save(board)
        player = FakePlayer()
        speaker = FakeSpeaker()
        vm = BoardViewModel(repo, player, FakeRecorder(), PresetRepository(context), speaker, DevicePreferences(context))

        composeRule.setContent {
            BoardScreen(vm = vm)
        }
    }

    @Test
    fun tappingFilledTilePlaysIt() {
        launchWith(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))))
        )

        composeRule.onNodeWithText("Air horn").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { player.played.contains("a.mp3") }
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
        composeRule.onNodeWithText("Edit mode").performClick()
        composeRule.onNodeWithText("Air horn").performClick()

        composeRule.onNodeWithText("Edit tile").assertIsDisplayed()
        // The name shows up twice: the (now-obscured) tile behind the dialog, and
        // the dialog's prefilled text field. Checking count avoids disambiguating
        // which node the dialog's overlay window leaves "displayed".
        assertEquals(2, composeRule.onAllNodesWithText("Air horn").fetchSemanticsNodes().size)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun saveAsPresetRenamesBoardAndUpdatesTitle() {
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Save as preset").performClick()
        // "New Board" appears twice once the dialog is up (the title bar behind
        // it, and the field's prefilled value) — hasSetTextAction() narrows to
        // the actual editable field regardless of window traversal order.
        composeRule.onNode(hasSetTextAction() and hasText("New Board")).performTextReplacement("Family Board")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Family Board").assertIsDisplayed()
        assertEquals("Family Board", vm.board.value.name)
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
                        tiles = (0 until 4).map { Tile(id = "t$it", label = "S$it", fileName = "$it.mp3") }
                    )
                )
            )
        )

        // Grid size now lives in the page-options dialog, opened by long-pressing the
        // page's own tab — hold past the long-press timeout via the test clock, the same
        // way idleTimeoutReturnsToTheHomePageAfterInactivity drives a real delay()-based wait.
        longPressPageTab("Page 1")
        composeRule.onNodeWithText("Grid size (2x2)").performClick()
        composeRule.onNodeWithContentDescription("Increase Columns").performClick()
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithText("Page 1").performTouchInput { up() }

        longPressPageTab("Page 1")
        composeRule.onNodeWithText("Grid size (2x3)").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1").performTouchInput { up() }
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
        composeRule.onNodeWithText("Grid size (1x1)").assertIsDisplayed()
    }

    /** Holds a page tab down past the long-press timeout to open its PageOptionsDialog, without releasing it. */
    private fun longPressPageTab(pageName: String) {
        composeRule.onNodeWithText(pageName).performTouchInput { down(center) }
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

        assertEquals(listOf("B", "A", "C"), vm.board.value.currentPage.tiles.map { it.label })
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

        composeRule.waitUntil(timeoutMillis = 2_000) { player.played.contains("a.mp3") }
        assertEquals(listOf("A", "B"), vm.board.value.currentPage.tiles.map { it.label })
    }

    @Test
    fun longPressPreviewIsDisabledInEditMode() {
        launchWith(
            Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))))
        )
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Edit mode").performClick()

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

        composeRule.onNodeWithText("Hey").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 2_000) { player.played.contains("hey.mp3") }
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

        composeRule.onNodeWithText("Hey").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Sticky home row").performClick()
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText("Hey").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()

        composeRule.onNodeWithText("Second").performClick()

        composeRule.onNodeWithText("Hey").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()

        // Shown once, as ordinary page content — not duplicated by the sticky banner.
        composeRule.onAllNodesWithText("Hey").assertCountEquals(1)
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
}
