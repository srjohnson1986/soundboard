package com.example.soundboard.ui

import kotlinx.coroutines.runBlocking
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

/**
 * What needs a real device: drag gestures, long-press timing and scrolling. The rest of the
 * board screen is tested on the JVM and in the browser by shared/.../ui/BoardScreenUiTest (#219).
 */
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
            File(context.filesDir, "sounds/$name").apply { parentFile?.mkdirs() }.writeText(name)
        }
        runBlocking { repo.save(board) }
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

    /** The one on-screen node with [text], ignoring copies on neighboring pages composed off-screen. */
    private fun displayedNode(text: String): SemanticsNodeInteraction {
        val nodes = composeRule.onAllNodesWithText(text)
        return nodes[nodes.fetchSemanticsNodes().indices.single { nodes[it].isDisplayed() }]
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

}
