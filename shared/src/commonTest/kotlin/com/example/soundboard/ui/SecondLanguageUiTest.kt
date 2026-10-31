package com.example.soundboard.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.soundboard.audio.SpeechVoice
import com.example.soundboard.audio.spokenOf
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Speaking Spanish (#331): the EN / ES switch, the tile editor's Spanish field, Show mode and the no-voice warning. */
@OptIn(ExperimentalTestApi::class)
class SecondLanguageUiTest : BoardUiTest() {

    private val water = Tile(id = "w", label = "Water", fileName = "w.mp3", translations = mapOf("es" to "Agua"))
    private val thanks = Tile(id = "t", label = "Thanks", fileName = "t.mp3")

    private fun boardOf(vararg tiles: Tile, secondLanguage: String? = "es") =
        Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList())), secondLanguage = secondLanguage)

    private val noVoice = "This device has no Spanish voice"

    // --- The switch ---

    @Test
    fun theSwitchStartsOnEnglishAndTappingEsSpeaksSpanishAndIsRemembered() = runUiTest {
        val app = launchBoard(boardOf(water))
        onNodeWithContentDescription("English").assertIsSelected()
        assertFalse(app.devicePrefs.secondLanguageActive)

        onNodeWithContentDescription("Spanish").performClick()
        waitForIdle()
        onNodeWithContentDescription("Spanish").assertIsSelected()
        onNodeWithText("Water").performClick()

        assertTrue(app.devicePrefs.secondLanguageActive)
        assertEquals(spokenOf("Agua" to "es"), app.speaker.spokenIn)
        assertTrue(app.player.playedKeys.isEmpty())
    }

    @Test
    fun theSwitchGoesBackToEnglish() = runUiTest {
        val app = launchBoard(boardOf(water))
        onNodeWithContentDescription("Spanish").performClick()

        onNodeWithContentDescription("English").performClick()
        onNodeWithText("Water").performClick()

        assertFalse(app.devicePrefs.secondLanguageActive)
        assertEquals(listOf("w.mp3"), app.player.playedKeys)
        assertTrue(app.speaker.spokenIn.isEmpty())
    }

    @Test
    fun theSwitchIsStillThereAndWorksWhileTheBoardIsLocked() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.vm.setEditingLockEnabled(true)
        waitForIdle()

        onNodeWithContentDescription("Spanish").performClick()
        onNodeWithText("Water").performClick()

        assertEquals(spokenOf("Agua" to "es"), app.speaker.spokenIn)
    }

    @Test
    fun aBoardWithoutASecondLanguageHasNoSwitch() = runUiTest {
        launchBoard(boardOf(water, secondLanguage = null))

        onNodeWithContentDescription("English").assertDoesNotExist()
        onNodeWithContentDescription("Spanish").assertDoesNotExist()
    }

    @Test
    fun anUntranslatedTileGetsANoSpanishMarkerOnlyInSpanishMode() = runUiTest {
        launchBoard(boardOf(water, thanks))
        onNodeWithContentDescription("No Spanish").assertDoesNotExist()

        onNodeWithContentDescription("Spanish").performClick()

        // Only the tile with no Spanish has one; it still plays its English.
        onNodeWithContentDescription("No Spanish").assertIsDisplayed()
        onNodeWithContentDescription("English").performClick()
        onNodeWithContentDescription("No Spanish").assertDoesNotExist()
    }

    @Test
    fun anUntranslatedTilePlaysInEnglishInSpanishMode() = runUiTest {
        val app = launchBoard(boardOf(thanks))
        onNodeWithContentDescription("Spanish").performClick()

        onNodeWithText("Thanks").performClick()

        assertEquals(listOf("t.mp3"), app.player.playedKeys)
    }

    // --- Show mode ---

    @Test
    fun showModeInSpanishShowsTheSpanishLargeWithTheEnglishUnderneath() = runUiTest {
        val app = launchBoard(boardOf(water))
        toggleMenuSwitch("Show mode")
        onNodeWithContentDescription("Spanish").performClick()

        onNodeWithText("Water").performClick()

        onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).assertIsDisplayed()
        onNodeWithText("Agua").assertIsDisplayed()
        onNodeWithTag(SHOW_SUBTEXT_TAG).assertTextEquals("Water")
        assertEquals(spokenOf("Agua" to "es"), app.speaker.spokenIn)
    }

    @Test
    fun showModeInEnglishHasNoLineUnderTheWords() = runUiTest {
        launchBoard(boardOf(water))
        toggleMenuSwitch("Show mode")

        onNodeWithText("Water").performClick()

        onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).assertIsDisplayed()
        onNodeWithTag(SHOW_SUBTEXT_TAG).assertDoesNotExist()
    }

    // --- The tile editor ---

    private fun ComposeUiTest.openEditor(label: String) {
        toggleMenuSwitch("Edit mode")
        onNodeWithText(label).performClick()
        onNodeWithText("Edit tile").assertIsDisplayed()
    }

    @Test
    fun theEditorHasASpanishFieldThatSavesWithTheTile() = runUiTest {
        val app = launchBoard(boardOf(thanks))
        openEditor("Thanks")

        onNode(hasSetTextAction() and hasText("Spanish")).performScrollTo().performTextInput("Gracias")
        onNodeWithText("Save").performClick()

        waitForIdle()
        assertEquals(mapOf("es" to "Gracias"), app.vm.board.value.findTile("t")?.translations)
    }

    @Test
    fun theEditorShowsATilesExistingSpanishAndHearingItSpeaksSpanish() = runUiTest {
        val app = launchBoard(boardOf(water))
        openEditor("Water")

        onNodeWithText("Agua").performScrollTo().assertIsDisplayed()
        onNodeWithText("Hear the Spanish").performScrollTo().performClick()

        // Even in English mode: the field is there to check what the Spanish sounds like.
        assertEquals(spokenOf("Agua" to "es"), app.speaker.spokenIn)
    }

    @Test
    fun emptyingTheSpanishFieldRemovesIt() = runUiTest {
        val app = launchBoard(boardOf(water))
        openEditor("Water")

        onNode(hasSetTextAction() and hasText("Spanish")).performScrollTo().performTextClearance()
        onNodeWithText("Save").performClick()

        waitForIdle()
        assertEquals(emptyMap(), app.vm.board.value.findTile("w")?.translations)
    }

    @Test
    fun cancellingTheEditorKeepsTheOldSpanish() = runUiTest {
        val app = launchBoard(boardOf(water))
        openEditor("Water")

        onNode(hasSetTextAction() and hasText("Spanish")).performScrollTo().performTextInput(" y mas")
        onNodeWithText("Cancel").performClick()

        assertEquals(mapOf("es" to "Agua"), app.vm.board.value.findTile("w")?.translations)
    }

    @Test
    fun withoutASecondLanguageTheEditorHasNoSpanishField() = runUiTest {
        launchBoard(boardOf(thanks, secondLanguage = null))
        openEditor("Thanks")

        onNode(hasSetTextAction() and hasText("Spanish")).assertDoesNotExist()
        onNodeWithText("Hear the Spanish").assertDoesNotExist()
    }

    // --- No Spanish voice ---

    @Test
    fun noSpanishVoiceIsWarnedAboutInSpanishModeAndTheTilePlaysInEnglish() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.speaker.spanishVoices.value = emptyList()
        waitForIdle()
        // In English mode nothing is wrong, so there's nothing to say.
        onNodeWithText(noVoice, substring = true).assertDoesNotExist()

        onNodeWithContentDescription("Spanish").performClick()
        waitForIdle()
        onNodeWithText(noVoice, substring = true).assertIsDisplayed()
        onNodeWithText("Water").performClick()

        assertEquals(listOf("w.mp3"), app.player.playedKeys)
        assertTrue(app.speaker.spokenIn.isEmpty())
    }

    @Test
    fun theWarningGoesWhenTheVoiceArrivesOrTheSwitchGoesBackToEnglish() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.speaker.spanishVoices.value = emptyList()
        onNodeWithContentDescription("Spanish").performClick()
        waitForIdle()
        onNodeWithText(noVoice, substring = true).assertIsDisplayed()

        app.speaker.spanishVoices.value = listOf(SpeechVoice("es-1", "Spanish"))
        waitForIdle()
        onNodeWithText(noVoice, substring = true).assertDoesNotExist()

        app.speaker.spanishVoices.value = emptyList()
        waitForIdle()
        onNodeWithText(noVoice, substring = true).assertIsDisplayed()
        onNodeWithContentDescription("English").performClick()
        waitForIdle()
        onNodeWithText(noVoice, substring = true).assertDoesNotExist()
    }

    @Test
    fun voicesNotKnownYetIsNotAWarning() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.speaker.spanishVoices.value = null

        onNodeWithContentDescription("Spanish").performClick()
        waitForIdle()

        onNodeWithText(noVoice, substring = true).assertDoesNotExist()
    }

    @Test
    fun spanishSpeechFailingIsWarnedAboutEvenForATileWithARecording() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.speaker.available.value = false
        waitForIdle()
        // A recording needs no speech engine in English...
        onNodeWithText("Speech isn't working on this device", substring = true).assertDoesNotExist()

        // ...but in Spanish the tile speaks.
        onNodeWithContentDescription("Spanish").performClick()
        waitForIdle()
        onNodeWithText("Speech isn't working on this device", substring = true).assertIsDisplayed()
    }

    // --- The Speak... box ---

    @Test
    fun theSpeakBoxSaysWhichLanguageItSpeaksAndSpeaksItInSpanish() = runUiTest {
        val app = launchBoard(boardOf(water))
        onNodeWithContentDescription("Spanish").performClick()
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Speak...").performClick()

        onNodeWithText("ES is on", substring = true).assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("Buenos dias")
        onNode(hasText("Speak") and hasClickAction()).performClick()

        assertEquals(spokenOf("Buenos dias" to "es"), app.speaker.spokenIn)
    }

    @Test
    fun theSpeakBoxInEnglishSaysNothingAboutSpanish() = runUiTest {
        launchBoard(boardOf(water))
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Speak...").performClick()

        onNodeWithText("ES is on", substring = true).assertDoesNotExist()
    }
}
