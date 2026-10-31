package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.soundboard.audio.GateTranslator
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import com.example.soundboard.translate.Phrasebook
import com.example.soundboard.translate.TranslatorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spanish that fills itself in (#342), in the screens: the banner that shows while it works and the
 * note that explains what it couldn't do, and the tile editor's "Filled in automatically".
 */
@OptIn(ExperimentalTestApi::class)
class SpanishFillUiTest : BoardUiTest() {

    private val phrasebook = Phrasebook("es", mapOf("Water" to "Agua", "Yes" to "Sí"))

    private fun boardOf(vararg tiles: Tile) = Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList())))

    private fun ComposeUiTest.switchToSpanish() {
        onNodeWithContentDescription("Spanish").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openEditor(label: String) {
        toggleMenuSwitch("Edit mode")
        onNodeWithText(label).performClick()
        onNodeWithText("Edit tile").assertIsDisplayed()
    }

    private fun spanishField() = hasSetTextAction() and hasText("Spanish")

    // --- The banner ---

    @Test
    fun switchingToEsFillsTheTilesFromThePhrasebookWithNothingToSay() = runUiTest {
        val app = launchBoard(
            boardOf(Tile(id = "w", label = "Water", speakWhenNoSound = true), Tile(id = "y", label = "Yes", speakWhenNoSound = true)),
            phrasebook = phrasebook
        )

        switchToSpanish()

        assertEquals("Agua", app.vm.board.value.findTile("w")?.translations?.get("es"))
        assertEquals("Sí", app.vm.board.value.findTile("y")?.translations?.get("es"))
        // Nothing is left to explain, so there is no banner and no note.
        onNodeWithText("Adding Spanish", substring = true).assertDoesNotExist()
        onNodeWithText("no Spanish yet", substring = true).assertDoesNotExist()
    }

    @Test
    fun theBannerShowsHowFarItHasGotAndCancelStopsIt() = runUiTest {
        val gate = GateTranslator()
        val app = launchBoard(
            boardOf(Tile(id = "a", label = "Juice"), Tile(id = "b", label = "Milk"), Tile(id = "c", label = "Water")),
            phrasebook = phrasebook,
            translator = gate
        )

        switchToSpanish()
        onNodeWithText("Adding Spanish... 1 of 3").assertIsDisplayed()
        gate.release("Jugo")
        waitForIdle()
        onNodeWithText("Adding Spanish... 2 of 3").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()
        waitForIdle()

        onNodeWithText("Adding Spanish", substring = true).assertDoesNotExist()
        onNodeWithText("1 tile has no Spanish yet", substring = true).assertIsDisplayed()
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithText("no Spanish yet", substring = true).assertDoesNotExist()
        assertEquals("Jugo", app.vm.board.value.findTile("a")?.translations?.get("es"))
    }

    @Test
    fun theBannerShowsTheModelDownloading() = runUiTest {
        launchBoard(boardOf(Tile(id = "a", label = "Juice")), translator = GateTranslator(TranslatorStatus.Downloading(40)))

        switchToSpanish()

        onNodeWithText("Adding Spanish... 0 of 1").assertIsDisplayed()
        onNodeWithText("Downloading the Spanish language model (40%)").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun whereTranslatingIsUnavailableANoteSaysWhyAndPointsToTheEditor() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "w", label = "Water"), Tile(id = "j", label = "Juice")), phrasebook = phrasebook)

        switchToSpanish()

        onNodeWithText("1 tile has no Spanish yet", substring = true).assertIsDisplayed()
        onNodeWithText("the test board has no translator", substring = true).assertIsDisplayed()
        onNodeWithText("open its editor", substring = true).assertIsDisplayed()
        assertTrue(app.vm.board.value.findTile("j")!!.lacksTranslation("es"))
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithText("no Spanish yet", substring = true).assertDoesNotExist()
    }

    // --- The tile editor ---

    @Test
    fun theEditorSaysWhenTheSpanishWasFilledInAutomatically() = runUiTest {
        launchBoard(
            boardOf(
                Tile(id = "w", label = "Water", translations = mapOf("es" to "Agua"), autoTranslated = setOf("es")),
                Tile(id = "y", label = "Yes", translations = mapOf("es" to "Sí"))
            )
        )
        openEditor("Water")

        onNodeWithText("Filled in automatically").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun spanishTypedByHandIsNotLabelledAutomatic() = runUiTest {
        launchBoard(boardOf(Tile(id = "y", label = "Yes", translations = mapOf("es" to "Sí"))))
        openEditor("Yes")

        onNodeWithText("Filled in automatically").assertDoesNotExist()
    }

    @Test
    fun editingAnAutomaticTranslationMakesItYoursForGood() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "w", label = "Water", translations = mapOf("es" to "Agua"), autoTranslated = setOf("es"))))
        openEditor("Water")

        onNode(spanishField()).performScrollTo().performTextReplacement("Agüita")
        waitForIdle()
        onNodeWithText("Filled in automatically").assertDoesNotExist()
        onNodeWithText("Save").performClick()
        waitForIdle()

        val tile = app.vm.board.value.findTile("w")!!
        assertEquals("Agüita", tile.translations["es"])
        assertFalse(tile.isAutoTranslated("es"))
    }

    @Test
    fun aSuggestionIsMarkedAutomaticAndSavedThatWay() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "j", label = "Juice")))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.translations["Juice"] = "Jugo"
        openEditor("Juice")

        onNodeWithText("Suggest").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Filled in automatically").performScrollTo().assertIsDisplayed()
        onNodeWithText("Save").performClick()
        waitForIdle()

        assertTrue(app.vm.board.value.findTile("j")!!.isAutoTranslated("es"))
    }
}
