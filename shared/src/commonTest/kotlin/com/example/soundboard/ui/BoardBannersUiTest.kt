package com.example.soundboard.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.soundboard.BoardViewModel
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bars above the board: why taps can't be heard (#248) and the backup reminder (#249). */
@OptIn(ExperimentalTestApi::class)
class BoardBannersUiTest : BoardUiTest() {

    private fun boardOf(vararg tiles: Tile) = Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList())))

    private val volumeOff = "The media volume is off"
    private val noSpeech = "Speech isn't working on this device"

    @Test
    fun aMutedMediaVolumeIsShownUntilItsTurnedBackUp() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "a", label = "Hey", fileName = "a.mp3")))
        onNodeWithText(volumeOff, substring = true).assertDoesNotExist()

        app.mediaVolume.muted.value = true
        waitForIdle()
        onNodeWithText(volumeOff, substring = true).assertIsDisplayed()

        app.mediaVolume.muted.value = false
        waitForIdle()
        onNodeWithText(volumeOff, substring = true).assertDoesNotExist()
    }

    @Test
    fun speechNotWorkingIsShownWhenTheBoardHasTilesThatSpeak() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "a", label = "Water", speakWhenNoSound = true)))

        app.speaker.available.value = false
        waitForIdle()

        onNodeWithText(noSpeech, substring = true).assertIsDisplayed()
    }

    @Test
    fun speechNotWorkingIsntShownForABoardOfRecordings() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "a", label = "Hey", fileName = "a.mp3")))

        app.speaker.available.value = false
        waitForIdle()

        onNodeWithText(noSpeech, substring = true).assertDoesNotExist()
    }

    @Test
    fun speechStillStartingIsntShownAsNotWorking() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "a", label = "Water", speakWhenNoSound = true)))

        app.speaker.available.value = null
        waitForIdle()

        onNodeWithText(noSpeech, substring = true).assertDoesNotExist()
    }

    @Test
    fun theBackupReminderShowsAboveTheBoardAndLaterPutsItAway() = runUiTest {
        // #249: TestBoardApp's clock reads 42, so this board has gone a week without a backup.
        val app = launchBoard(boardOf(Tile(id = "a", label = "Hey", fileName = "a.mp3")))
        app.devicePrefs.unbackedChangesSince = 42 - BoardViewModel.BACKUP_REMINDER_AFTER.inWholeMilliseconds
        onNodeWithText("Hey").performClick()

        onNodeWithText("aren't in a backup yet", substring = true).assertIsDisplayed()
        onNodeWithText("Back up").assertIsDisplayed()
        onNodeWithText("Later").performClick()

        onNodeWithText("aren't in a backup yet", substring = true).assertDoesNotExist()
        assertEquals(42 + BoardViewModel.BACKUP_REMINDER_SNOOZE.inWholeMilliseconds, app.devicePrefs.backupReminderSnoozedUntil)
    }
}
