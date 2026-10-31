package com.example.soundboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holding a tile turns Edit mode on or off, and holding then dragging reorders (#328); locked,
 * a hold is just a slow tap; and the banner that says tiles won't play in Edit mode.
 *
 * A gesture here is a finger put down, Compose's clock moved past the long-press timeout, and
 * the finger lifted, with the clock stopped throughout so nothing animates away in between.
 */
@OptIn(ExperimentalTestApi::class)
class EditModeHoldUiTest : BoardUiTest() {

    private val bannerText = "Tiles won't play while Edit mode is on"

    private fun threeTiles() = Board(
        pages = listOf(
            Page(
                rows = 1, columns = 3,
                tiles = listOf(
                    Tile(id = "a", label = "A", fileName = "a.mp3"),
                    Tile(id = "b", label = "B", fileName = "b.mp3"),
                    Tile(id = "c", label = "C", fileName = "c.mp3")
                )
            )
        )
    )

    private fun TestBoardApp.labels() =
        vm.board.value.currentPage.tiles.map { it.label }.filter { it.isNotEmpty() }

    private fun ComposeUiTest.assertEditModeOn() = onNodeWithText(bannerText, substring = true).assertIsDisplayed()

    private fun ComposeUiTest.assertEditModeOff() = onNodeWithText(bannerText, substring = true).assertDoesNotExist()

    /** Puts a finger on [tile] and holds it past the long-press timeout, leaving it down. */
    private fun ComposeUiTest.holdOn(tile: SemanticsNodeInteraction) {
        mainClock.autoAdvance = false
        tile.performTouchInput { down(center) }
        mainClock.advanceTimeBy(600)
        mainClock.advanceTimeByFrame()
    }

    private fun ComposeUiTest.letGo(tile: SemanticsNodeInteraction) {
        tile.performTouchInput { up() }
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()
    }

    private fun ComposeUiTest.hold(label: String) {
        holdOn(onNodeWithText(label))
        letGo(onNodeWithText(label))
    }

    /** How far apart neighboring tiles are, measured from where the first two are drawn. */
    private fun ComposeUiTest.cellStepPx(): Float =
        onNodeWithText("B").fetchSemanticsNode().boundsInRoot.left - onNodeWithText("A").fetchSemanticsNode().boundsInRoot.left

    /** Holds [label], drags it [cells] cells to the right, and lets go. */
    private fun ComposeUiTest.holdAndDrag(label: String, cells: Float) {
        val step = cellStepPx()
        holdOn(onNodeWithText(label))
        onNodeWithText(label).performTouchInput { moveBy(Offset(step * cells, 0f)) }
        mainClock.advanceTimeByFrame()
        letGo(onNodeWithText(label))
    }

    private fun ComposeUiTest.turnOnEditModeInTheMenu() = toggleMenuSwitch("Edit mode")

    // --- Unlocked ---

    @Test
    fun holdingATileInTheNormalBoardTurnsEditModeOn() = runUiTest {
        val app = launchBoard(threeTiles())
        assertEditModeOff()

        hold("A")

        assertEditModeOn()
        assertEquals(listOf("A", "B", "C"), app.labels())
    }

    @Test
    fun holdingATileInEditModeTurnsEditModeOff() = runUiTest {
        val app = launchBoard(threeTiles())
        turnOnEditModeInTheMenu()
        assertEditModeOn()

        hold("A")

        assertEditModeOff()
        assertEquals(listOf("A", "B", "C"), app.labels())
    }

    @Test
    fun holdingTwiceTurnsEditModeOnThenOff() = runUiTest {
        launchBoard(threeTiles())

        hold("B")
        assertEditModeOn()
        hold("B")
        assertEditModeOff()
    }

    @Test
    fun holdingAndDraggingFromTheNormalBoardMovesTheTileAndTurnsEditModeOn() = runUiTest {
        val app = launchBoard(threeTiles())

        holdAndDrag("A", cells = 1.2f)

        assertEquals(listOf("B", "A", "C"), app.labels())
        assertEditModeOn()
    }

    @Test
    fun holdingAndDraggingInEditModeMovesTheTileAndLeavesEditModeOn() = runUiTest {
        val app = launchBoard(threeTiles())
        turnOnEditModeInTheMenu()

        holdAndDrag("A", cells = 1.2f)

        assertEquals(listOf("B", "A", "C"), app.labels())
        assertEditModeOn()
    }

    @Test
    fun editModeTurnsOnTheMomentTheHoldBeginsNotWhenYouLetGo() = runUiTest {
        launchBoard(threeTiles())

        holdOn(onNodeWithText("A"))
        assertEditModeOn() // the finger is still down
        letGo(onNodeWithText("A"))
        assertEditModeOn()
    }

    @Test
    fun aHoldThatDragsBackToWhereItStartedStillCountsAsADrag() = runUiTest {
        val app = launchBoard(threeTiles())
        turnOnEditModeInTheMenu()
        val step = cellStepPx()

        holdOn(onNodeWithText("A"))
        onNodeWithText("A").performTouchInput { moveBy(Offset(step * 0.4f, 0f)) }
        mainClock.advanceTimeByFrame()
        onNodeWithText("A").performTouchInput { moveBy(Offset(-step * 0.4f, 0f)) }
        mainClock.advanceTimeByFrame()
        letGo(onNodeWithText("A"))

        assertEquals(listOf("A", "B", "C"), app.labels())
        assertEditModeOn() // it moved, so letting go isn't the hold that turns Edit mode off
    }

    @Test
    fun lettingGoAfterAHoldNeverPlaysTheTile() = runUiTest {
        val app = launchBoard(threeTiles())

        hold("A") // normal board: Edit mode on
        hold("A") // Edit mode: off
        holdAndDrag("B", cells = 1.2f)

        assertTrue(app.player.playedKeys.isEmpty(), "played ${app.player.playedKeys}")
        assertTrue(app.speaker.spoken.isEmpty(), "spoke ${app.speaker.spoken}")
    }

    @Test
    fun tilesDontPlayWhileEditModeIsOnAfterAHold() = runUiTest {
        val app = launchBoard(threeTiles())

        hold("A")
        onNodeWithText("B").performClick()

        onNodeWithText("Edit tile").assertIsDisplayed()
        assertTrue(app.player.playedKeys.isEmpty())
    }

    @Test
    fun saveInTheTileEditorLeavesEditModeOn() = runUiTest {
        launchBoard(threeTiles())
        hold("A")
        onNodeWithText("A").performClick()
        onNodeWithText("Edit tile").assertIsDisplayed()

        onNodeWithText("Save").performClick()

        waitForIdle()
        onNodeWithText("Edit tile").assertDoesNotExist()
        assertEditModeOn()
    }

    @Test
    fun cancelInTheTileEditorLeavesEditModeOn() = runUiTest {
        launchBoard(threeTiles())
        hold("A")
        onNodeWithText("A").performClick()
        onNodeWithText("Edit tile").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()

        waitForIdle()
        onNodeWithText("Edit tile").assertDoesNotExist()
        assertEditModeOn()
    }

    // --- The pinned home row ---

    private fun stickyRowBoard() = Board(
        pages = listOf(
            Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "x", label = "X", fileName = "x.mp3"))),
            Page(name = "Home", rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", label = "Hey", fileName = "hey.mp3")), isHome = true)
        ),
        stickyHomeRowEnabled = true
    )

    @Test
    fun holdingATileInTheStickyRowTurnsEditModeOnThenOffWithoutPlayingIt() = runUiTest {
        val app = launchBoard(stickyRowBoard())
        // The row is above the pager, whose neighboring page also draws "Hey".
        fun pinnedHey() = onAllNodesWithText("Hey").onFirst()

        holdOn(pinnedHey())
        letGo(pinnedHey())
        assertEditModeOn()

        holdOn(pinnedHey())
        letGo(pinnedHey())
        assertEditModeOff()
        assertTrue(app.player.playedKeys.isEmpty(), "played ${app.player.playedKeys}")
    }

    // --- Locked ---

    private fun ComposeUiTest.lock(app: TestBoardApp) {
        app.vm.setEditingLockEnabled(true)
        waitForIdle()
    }

    @Test
    fun lockedAHoldPlaysTheTileWhenYouLetGoOnIt() = runUiTest {
        val app = launchBoard(threeTiles())
        lock(app)

        hold("A")

        waitUntil(timeoutMillis = 2_000) { "a.mp3" in app.player.playedKeys }
        assertEditModeOff()
    }

    @Test
    fun lockedAHoldThatSlidesOffTheTileDoesNothing() = runUiTest {
        val app = launchBoard(threeTiles())
        lock(app)

        holdOn(onNodeWithText("A"))
        onNodeWithText("A").performTouchInput { moveBy(Offset(0f, height * 2f)) }
        mainClock.advanceTimeByFrame()
        letGo(onNodeWithText("A"))

        assertTrue(app.player.playedKeys.isEmpty(), "played ${app.player.playedKeys}")
        assertEditModeOff()
    }

    @Test
    fun lockedDraggingATileDoesntReorderIt() = runUiTest {
        val app = launchBoard(threeTiles())
        lock(app)

        holdAndDrag("A", cells = 1.2f)

        assertEquals(listOf("A", "B", "C"), app.labels())
        assertEditModeOff()
    }

    @Test
    fun lockedHoldingATileInTheStickyRowPlaysItAndDoesntTurnEditModeOn() = runUiTest {
        val app = launchBoard(stickyRowBoard())
        lock(app)

        holdOn(onAllNodesWithText("Hey").onFirst())
        letGo(onAllNodesWithText("Hey").onFirst())

        waitUntil(timeoutMillis = 2_000) { "hey.mp3" in app.player.playedKeys }
        assertEditModeOff()
    }

    // --- The banner ---

    @Test
    fun theBannerShowsOnlyWhileEditModeIsOn() = runUiTest {
        launchBoard(threeTiles())
        assertEditModeOff()

        turnOnEditModeInTheMenu()
        assertEditModeOn()
        onNodeWithText("Tap here, or hold a tile, to turn it off.", substring = true).assertIsDisplayed()

        turnOnEditModeInTheMenu() // the same switch turns it off
        assertEditModeOff()
    }

    @Test
    fun tappingTheBannerTurnsEditModeOff() = runUiTest {
        launchBoard(threeTiles())
        turnOnEditModeInTheMenu()

        onNodeWithText(bannerText, substring = true).performClick()

        waitForIdle()
        assertEditModeOff()
    }

    @Test
    fun lockingTakesTheBannerAwayAndItStaysAway() = runUiTest {
        val app = launchBoard(threeTiles())
        turnOnEditModeInTheMenu()
        assertEditModeOn()

        lock(app)

        assertEditModeOff()
        app.vm.unlockEditing()
        waitForIdle()
        assertEditModeOff() // locking ended Edit mode; unlocking doesn't bring it back
    }
}
