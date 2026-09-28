package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.example.soundboard.BoardViewModel
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The caregiver lock (#251): what it hides, how it unlocks, and that it locks again. */
@OptIn(ExperimentalTestApi::class)
class CaregiverLockUiTest : BoardUiTest() {

    /** Two pages: a clip, a label with nothing to play, and a blank tile; then a second page. */
    private val board = Board(
        pages = listOf(
            Page(
                name = "First", rows = 1, columns = 3,
                tiles = listOf(Tile(id = "a", label = "Hey", fileName = "a.mp3"), Tile(id = "b", label = "Juice"), Tile(id = "c"))
            ),
            Page(name = "Second", rows = 1, columns = 1, tiles = listOf(Tile(id = "d")))
        )
    )

    @Test
    fun lockEditingInTheMenuLocksAtOnce() = runUiTest {
        val app = launchBoard(board)

        onNodeWithContentDescription("Menu").performClick()
        clickSwitchBeside("Lock editing")

        waitUntil(timeoutMillis = 2_000) { app.vm.editingLocked.value }
        onNodeWithText("Hold to unlock").assertDoesNotExist() // the menu closed
        assertTrue(app.devicePrefs.editingLockEnabled)
    }

    @Test
    fun lockedTheMenuOffersOnlyEverydayItems() = runUiTest {
        val app = launchBoard(board)
        app.vm.setEditingLockEnabled(true)
        waitForIdle()

        onNodeWithContentDescription("Menu").performClick()

        onNodeWithText("Speak...").assertIsDisplayed()
        onNodeWithText("Show mode").assertIsDisplayed()
        onNodeWithText("Hold to unlock").assertIsDisplayed()
        listOf("Edit mode", "Settings", "Switch board", "Rename board", "Export backup", "Import backup", "Lock editing")
            .forEach { onNodeWithText(it, substring = true).assertDoesNotExist() }
    }

    @Test
    fun lockedTheBoardItselfHasNoWayIntoEditing() = runUiTest {
        val app = launchBoard(board)
        app.vm.setEditingLockEnabled(true)
        waitForIdle()

        onNodeWithContentDescription("Add page").assertDoesNotExist()
        onNodeWithText("+").assertDoesNotExist() // blank tiles are hidden

        onNodeWithText("Juice").assertDoesNotExist() // nothing to play, so hidden with the blanks

        longPressPageTab("First")
        onNodeWithText("Rename").assertDoesNotExist() // no Page options

        onNodeWithText("Hey").performClick() // tiles still play
        waitUntil(timeoutMillis = 2_000) { "a.mp3" in app.player.playedKeys }
    }

    @Test
    fun aShortHoldDoesntUnlockAndAFullOneDoes() = runUiTest {
        val app = launchBoard(board)
        app.vm.setEditingLockEnabled(true)
        waitForIdle()
        onNodeWithContentDescription("Menu").performClick()

        holdUnlock(millis = 1_000)
        assertTrue(app.vm.editingLocked.value)

        holdUnlock(millis = EditingLock.HOLD_TO_UNLOCK.inWholeMilliseconds + 100)
        assertFalse(app.vm.editingLocked.value)
        // The menu stays open, now with everything in it.
        onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aScreenReaderUnlocksWithItsUnlockAction() = runUiTest {
        val app = launchBoard(board)
        app.vm.setEditingLockEnabled(true)
        waitForIdle()
        onNodeWithContentDescription("Menu").performClick()

        onNode(hasText("Hold to unlock")).performCustomAccessibilityActionWithLabel("Unlock")

        waitUntil(timeoutMillis = 2_000) { !app.vm.editingLocked.value }
    }

    @Test
    fun unlockedItLocksItselfAgainOnceIdle() = runUiTest {
        // Five minutes in the app; shortened here, as the test clock steps through it frame by frame.
        val app = launchBoard(board, relockAfterIdle = 2.seconds)
        app.vm.setEditingLockEnabled(true)
        app.vm.unlockEditing()
        waitForIdle()

        advanceClock(1_500)
        assertFalse(app.vm.editingLocked.value)

        advanceClock(1_000)
        assertTrue(app.vm.editingLocked.value)
    }

    @Test
    fun lockedTheBackupReminderWaitsForWhoeverSetTheLock() = runUiTest {
        val app = launchBoard(board)
        app.devicePrefs.unbackedChangesSince = 42 - BoardViewModel.BACKUP_REMINDER_AFTER.inWholeMilliseconds
        app.vm.setEditingLockEnabled(true)
        onNodeWithText("Hey").performClick()

        onNodeWithText("aren't in a backup yet", substring = true).assertDoesNotExist()

        app.vm.unlockEditing()
        waitForIdle()
        onNodeWithText("aren't in a backup yet", substring = true).assertIsDisplayed()
    }

    /**
     * Holds the menu's Unlock down for [millis] of Compose's clock, then lets go. The clock is
     * stopped first: left running, waiting for idle would run the ring's animation to its end.
     */
    private fun ComposeUiTest.holdUnlock(millis: Long) {
        waitForIdle() // lets the menu finish opening before the clock stops
        mainClock.autoAdvance = false
        onNode(hasText("Hold to unlock")).performTouchInput { down(center) }
        mainClock.advanceTimeBy(millis)
        onAllNodes(hasText("Hold to unlock")).fetchSemanticsNodes().firstOrNull()?.let {
            onNode(hasText("Hold to unlock")).performTouchInput { up() }
        }
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
