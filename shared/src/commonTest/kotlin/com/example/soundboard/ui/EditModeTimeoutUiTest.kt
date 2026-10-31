package com.example.soundboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Edit mode turns itself off after a few idle seconds (#344), so a stray hold doesn't leave the
 * board unable to play. A timeout second lasts 50 milliseconds here, as the test clock steps
 * through a wait frame by frame, and the tests use a 60 second timeout (3 seconds of the test
 * clock) to leave room for the menu animations that run while they set Edit mode up.
 */
@OptIn(ExperimentalTestApi::class)
class EditModeTimeoutUiTest : BoardUiTest() {

    private val bannerText = "Tiles won't play while Edit mode is on"

    /** The timeout the tests use, 60 s, as the test clock sees it. */
    private val timeoutMillis = 3_000L

    private fun twoPages(editModeTimeoutSeconds: Int = 60) = Board(
        pages = listOf(
            Page(
                name = "First", rows = 1, columns = 3,
                tiles = listOf(
                    Tile(id = "a", label = "A", fileName = "a.mp3"),
                    Tile(id = "b", label = "B", fileName = "b.mp3"),
                    Tile(id = "c", label = "C", fileName = "c.mp3")
                )
            ),
            Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "d", label = "D", fileName = "d.mp3")))
        ),
        editModeTimeoutSeconds = editModeTimeoutSeconds
    )

    private fun ComposeUiTest.launch(board: Board, leaveEditModeAfterMillis: Long? = null) =
        launchBoard(board, editModeSecond = 50.milliseconds, leaveEditModeAfterMillis = leaveEditModeAfterMillis)

    private fun ComposeUiTest.assertEditModeOn() = onNodeWithText(bannerText, substring = true).assertIsDisplayed()

    private fun ComposeUiTest.assertEditModeOff() = onNodeWithText(bannerText, substring = true).assertDoesNotExist()

    private fun ComposeUiTest.turnOnEditModeInTheMenu() = toggleMenuSwitch("Edit mode")

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

    // --- Timing out ---

    @Test
    fun editModeTurnsOffAfterTheTimeout() = runUiTest {
        launch(twoPages())
        turnOnEditModeInTheMenu()
        assertEditModeOn()

        advanceClock(timeoutMillis - 900)
        assertEditModeOn()

        advanceClock(1_800)
        assertEditModeOff()
    }

    @Test
    fun whenItTimesOutTilesPlayAgain() = runUiTest {
        val app = launch(twoPages())
        turnOnEditModeInTheMenu()
        advanceClock(timeoutMillis + 900)
        assertEditModeOff()

        onNodeWithText("B").performClick()

        waitUntil(timeoutMillis = 2_000) { "b.mp3" in app.player.playedKeys }
        onNodeWithText("Edit tile").assertDoesNotExist()
    }

    @Test
    fun theTimeoutFollowsTheSetting() = runUiTest {
        val app = launch(twoPages(editModeTimeoutSeconds = 300))
        turnOnEditModeInTheMenu()

        advanceClock(timeoutMillis * 2) // well past 60 s
        assertEditModeOn()

        app.vm.setEditModeTimeoutSeconds(60) // restarts the countdown under the new duration
        waitForIdle()
        advanceClock(timeoutMillis + 900)
        assertEditModeOff()
    }

    @Test
    fun offNeverTimesOut() = runUiTest {
        launch(twoPages(editModeTimeoutSeconds = 0))
        turnOnEditModeInTheMenu()

        advanceClock(timeoutMillis * 4)

        assertEditModeOn()
    }

    // --- Held off ---

    @Test
    fun itStaysOnWhileTheTileEditorIsOpenAndTimesOutAfterItCloses() = runUiTest {
        launch(twoPages())
        turnOnEditModeInTheMenu()
        onNodeWithText("A").performClick()
        onNodeWithText("Edit tile").assertIsDisplayed()

        advanceClock(timeoutMillis * 2)
        onNodeWithText("Edit tile").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEditModeOn() // it didn't go off behind the editor

        advanceClock(timeoutMillis - 900) // and the wait starts over once the editor is closed
        assertEditModeOn()
        advanceClock(1_800)
        assertEditModeOff()
    }

    @Test
    fun itStaysOnWhileSettingsIsOpen() = runUiTest {
        launch(twoPages())
        turnOnEditModeInTheMenu()
        openSettings()

        advanceClock(timeoutMillis * 2)
        onNodeWithText("Done").performClick()
        waitForIdle()

        assertEditModeOn()
        advanceClock(timeoutMillis + 900)
        assertEditModeOff()
    }

    @Test
    fun aHoldInProgressKeepsItOnThroughTheSecondStageRing() = runUiTest {
        // The ring leaves Edit mode on its own after 100 s here, far past the timeout.
        launch(twoPages(), leaveEditModeAfterMillis = 100_000)
        turnOnEditModeInTheMenu()

        holdOn(onNodeWithText("A"))
        mainClock.advanceTimeBy(timeoutMillis * 2)
        mainClock.advanceTimeByFrame()
        assertEditModeOn()

        letGo(onNodeWithText("A"))
        assertEditModeOn()
        advanceClock(timeoutMillis - 900) // restarted when the hold ended
        assertEditModeOn()
        advanceClock(1_800)
        assertEditModeOff()
    }

    @Test
    fun aDragInProgressKeepsItOn() = runUiTest {
        launch(twoPages(), leaveEditModeAfterMillis = 100_000)
        turnOnEditModeInTheMenu()

        holdOn(onNodeWithText("A"))
        onNodeWithText("A").performTouchInput { moveBy(Offset(width * 1.2f, 0f)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(timeoutMillis * 2)
        mainClock.advanceTimeByFrame()
        assertEditModeOn()

        letGo(onNodeWithText("A"))
        assertEditModeOn()
        advanceClock(timeoutMillis + 900)
        assertEditModeOff()
    }

    // --- Restarting ---

    @Test
    fun switchingPagesRestartsTheTimer() = runUiTest {
        launch(twoPages())
        turnOnEditModeInTheMenu()
        advanceClock(timeoutMillis - 900)
        assertEditModeOn()

        onNodeWithText("Second").performClick()
        waitForIdle()
        advanceClock(timeoutMillis - 900) // past the first countdown, within the new one
        assertEditModeOn()

        advanceClock(1_800)
        assertEditModeOff()
    }

    @Test
    fun swipingToAnotherPageRestartsTheTimer() = runUiTest {
        val app = launch(twoPages())
        turnOnEditModeInTheMenu()
        advanceClock(timeoutMillis - 900)
        assertEditModeOn()

        onNodeWithText("A").performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(1, app.vm.board.value.currentPageIndex)
        advanceClock(timeoutMillis - 900)
        assertEditModeOn()

        advanceClock(1_800)
        assertEditModeOff()
    }

    @Test
    fun scrollingTheGridRestartsTheTimer() = runUiTest {
        val rows = 60 // far more than fit, so the grid really scrolls
        val tall = Board(
            pages = listOf(
                Page(
                    name = "Tall", rows = rows, columns = 1,
                    tiles = (1..rows).map { Tile(id = "t$it", label = "Tile $it", fileName = "t$it.mp3") }
                )
            ),
            editModeTimeoutSeconds = 60
        )
        launch(tall)
        onNodeWithText("Tile 60").assertDoesNotExist()
        turnOnEditModeInTheMenu()
        advanceClock(timeoutMillis - 900)
        assertEditModeOn()

        mainClock.autoAdvance = false
        onNodeWithText("Tile 1").performTouchInput { swipeUp() }
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithText("Tile 1").assertDoesNotExist() // it scrolled
        advanceClock(timeoutMillis - 1_800) // past the first countdown, within the one the scroll restarted
        assertEditModeOn()

        advanceClock(2_700)
        assertEditModeOff()
    }

    @Test
    fun turningEditModeOffAndOnAgainStartsAFreshCountdown() = runUiTest {
        launch(twoPages())
        turnOnEditModeInTheMenu()
        advanceClock(timeoutMillis - 900)

        turnOnEditModeInTheMenu() // off
        assertEditModeOff()
        turnOnEditModeInTheMenu() // and on
        advanceClock(timeoutMillis - 900)
        assertEditModeOn()

        advanceClock(1_800)
        assertEditModeOff()
    }

    // --- The banner ---

    @Test
    fun theBannerSaysWhenItWillTurnOff() = runUiTest {
        launch(twoPages(editModeTimeoutSeconds = 10))

        turnOnEditModeInTheMenu()

        onNodeWithText("Turns off after 10 seconds idle").assertIsDisplayed()
    }

    @Test
    fun theBannerSaysMinutesForALongTimeout() = runUiTest {
        launch(twoPages(editModeTimeoutSeconds = 60))

        turnOnEditModeInTheMenu()

        onNodeWithText("Turns off after 1 minute idle").assertIsDisplayed()
    }

    @Test
    fun theBannerSaysNothingExtraWhenTheTimeoutIsOff() = runUiTest {
        launch(twoPages(editModeTimeoutSeconds = 0))

        turnOnEditModeInTheMenu()

        assertEditModeOn()
        onNodeWithText("Turns off after", substring = true).assertDoesNotExist()
    }
}
