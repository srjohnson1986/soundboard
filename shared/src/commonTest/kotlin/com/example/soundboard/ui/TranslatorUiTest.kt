package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.example.soundboard.audio.spokenOf
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import com.example.soundboard.translate.TranslatorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Translating on the device (#332), in the screens: Suggest in the tile editor, Translate all tiles
 * in Settings, and the Speak... box's live Spanish preview, each with what it says where translating
 * isn't ready or can't happen. The translator is a fake; TestBoardApp's starts out unavailable.
 */
@OptIn(ExperimentalTestApi::class)
class TranslatorUiTest : BoardUiTest() {

    private val water = Tile(id = "w", label = "Water", fileName = "w.mp3")
    private val juice = Tile(id = "j", label = "Juice", speakWhenNoSound = true)

    private fun boardOf(vararg tiles: Tile) = Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList())))

    private fun ComposeUiTest.openEditor(label: String) {
        toggleMenuSwitch("Edit mode")
        onNodeWithText(label).performClick()
        onNodeWithText("Edit tile").assertIsDisplayed()
    }

    private fun ComposeUiTest.openSpeak() {
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Speak...").performClick()
    }

    private fun ComposeUiTest.openVoiceSettings() {
        openSettings()
        onNodeWithText("Voice").performScrollTo().performClick()
        waitForIdle()
    }

    private fun spanishField() = hasSetTextAction() and hasText("Spanish")

    // --- Suggest ---

    @Test
    fun suggestFillsTheSpanishFieldAndTheSavedTextIsWhatPlays() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.translations["Water"] = "Agua"
        openEditor("Water")

        onNodeWithText("Suggest").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Agua").performScrollTo().assertIsDisplayed()
        // Nothing is on the tile until Save: it's a draft, like every other field.
        assertEquals(emptyMap(), app.vm.board.value.findTile("w")?.translations)
        onNodeWithText("Save").performClick()
        waitForIdle()

        assertEquals(mapOf("es" to "Agua"), app.vm.board.value.findTile("w")?.translations)
        // Playing it in Spanish mode says the saved text, with no translating at all.
        val askedBefore = app.translator.translated.size
        toggleMenuSwitch("Edit mode")
        onNodeWithContentDescription("Spanish").performClick()
        onNodeWithText("Water").performClick()
        assertEquals(spokenOf("Agua" to "es"), app.speaker.spokenIn)
        assertEquals(askedBefore, app.translator.translated.size)
    }

    @Test
    fun aSuggestionCanBeEditedBeforeSaving() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.translations["Water"] = "Agua"
        openEditor("Water")

        onNodeWithText("Suggest").performScrollTo().performClick()
        waitForIdle()
        onNode(spanishField()).performScrollTo().performTextReplacement("Agua fria")
        onNodeWithText("Save").performClick()
        waitForIdle()

        assertEquals(mapOf("es" to "Agua fria"), app.vm.board.value.findTile("w")?.translations)
    }

    @Test
    fun suggestTranslatesWhatToSayWhenTheTileHasIt() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "w", label = "Water", ttsScript = "A glass of water please", fileName = "w.mp3")))
        app.translator.status.value = TranslatorStatus.Ready
        openEditor("Water")

        onNodeWithText("Suggest").performScrollTo().performClick()

        assertEquals(listOf("A glass of water please"), app.translator.translated)
    }

    @Test
    fun suggestNeedsSomethingToTranslate() = runUiTest {
        val app = launchBoard(boardOf(Tile(id = "e")))
        app.translator.status.value = TranslatorStatus.Ready

        onNodeWithText("+").performClick()

        onNodeWithText("Suggest").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Give the tile a name or What to say first", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun suggestExplainsAndDownloadsTheModelTheFirstTime() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.NeedsDownload(30)
        openEditor("Water")

        onNodeWithText("downloads the Spanish language model (about 30 MB)", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("Suggest").performScrollTo().assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(1, app.translator.prepared)
        onNodeWithText("[es] Water").performScrollTo().assertIsDisplayed()
        onNodeWithText("downloads the Spanish language model", substring = true).assertDoesNotExist()
    }

    @Test
    fun suggestShowsAFailedDownloadAndCanTryAgain() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.NeedsDownload(30)
        app.translator.downloadFails = "The download needs an internet connection."
        openEditor("Water")

        onNodeWithText("Suggest").performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Couldn't translate: The download needs an internet connection.").performScrollTo().assertIsDisplayed()
        app.translator.downloadFails = null
        onNodeWithText("Suggest").performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithText("[es] Water").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun suggestSaysWhyItIsUnavailableInsteadOfDisappearing() = runUiTest {
        launchBoard(boardOf(water))
        openEditor("Water")

        onNodeWithText("Suggest").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        onNodeWithText("Translating isn't available here: the test board has no translator.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun suggestWhileTheModelDownloadsIsOffAndShowsTheProgress() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.Downloading(40)
        openEditor("Water")

        onNodeWithText("Suggest").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Downloading the Spanish language model (40%)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noSecondLanguageMeansNoSuggest() = runUiTest {
        launchBoard(boardOf(water).copy(secondLanguage = null))
        openEditor("Water")

        onNodeWithText("Suggest").assertDoesNotExist()
    }

    // --- Translate all tiles ---

    @Test
    fun translateAllTilesFillsInTheSpanishAndSaysHowMany() = runUiTest {
        val app = launchBoard(boardOf(water, juice, Tile(id = "x", label = "Hi", translations = mapOf("es" to "Hola"))))
        app.translator.status.value = TranslatorStatus.Ready
        openVoiceSettings()

        onNodeWithText("Fills in Spanish for the 2 tiles that have none", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("Translate all tiles").performScrollTo().assertIsEnabled().performClick()
        waitForIdle()

        assertEquals("[es] Water", app.vm.board.value.findTile("w")?.translations?.get("es"))
        assertEquals("[es] Juice", app.vm.board.value.findTile("j")?.translations?.get("es"))
        assertEquals("Hola", app.vm.board.value.findTile("x")?.translations?.get("es"))
        onNodeWithText("Every tile with words already has Spanish.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Translate all tiles").assertIsNotEnabled()
    }

    @Test
    fun translateAllTilesSaysWhyItCantWhereTranslatingIsUnavailable() = runUiTest {
        val app = launchBoard(boardOf(water, juice))
        openVoiceSettings()

        onNodeWithText("Translate all tiles").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        onNodeWithText("Translating isn't available here: the test board has no translator.").performScrollTo().assertIsDisplayed()
        assertTrue(app.translator.translated.isEmpty())
    }

    @Test
    fun translateAllTilesOffersTheDownloadFirst() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.NeedsDownload(30)
        openVoiceSettings()

        onNodeWithText("Download the Spanish model").performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, app.translator.prepared)
        onNodeWithText("Download the Spanish model").assertDoesNotExist()
    }

    @Test
    fun translateAllTilesShowsAFailureInTheMessage() = runUiTest {
        val app = launchBoard(boardOf(water, juice))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.failWith = "The translator broke."
        openVoiceSettings()

        onNodeWithText("Translate all tiles").performScrollTo().performClick()
        waitForIdle()

        // In the message and in the status under the button.
        onAllNodesWithText("The translator broke.", substring = true).onFirst().assertIsDisplayed()
        assertEquals(emptyMap(), app.vm.board.value.findTile("w")?.translations)
    }

    // --- The Speak... box ---

    @Test
    fun theSpeakBoxPreviewsTheSpanishAsYouType() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.translations["Where is the bathroom"] = "Donde esta el bano"
        openSpeak()

        onNode(hasSetTextAction()).performTextInput("Where is the bathroom")
        advanceClock(1_000)
        waitForIdle()

        onNodeWithTag(SPEAK_PREVIEW_TAG).assertTextEquals("Donde esta el bano")
        // In English mode Speak still says what was typed.
        onNode(hasText("Speak") and hasClickAction()).performClick()
        assertEquals(spokenOf("Where is the bathroom" to null), app.speaker.spokenIn)
    }

    @Test
    fun inSpanishModeTheSpeakBoxSaysTheTranslation() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.translations["Water please"] = "Agua por favor"
        onNodeWithContentDescription("Spanish").performClick()
        openSpeak()

        onNodeWithText("type it in English", substring = true).assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("Water please")
        onNode(hasText("Speak") and hasClickAction()).assertIsNotEnabled() // until the translation is there
        advanceClock(1_000)
        waitForIdle()
        onNode(hasText("Speak") and hasClickAction()).assertIsEnabled().performClick()

        assertEquals(spokenOf("Agua por favor" to "es"), app.speaker.spokenIn)
    }

    @Test
    fun theSpeakBoxSaysWhenAPreviewCouldNotBeMade() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.Ready
        app.translator.failWith = "x"
        openSpeak()

        onNode(hasSetTextAction()).performTextInput("Hello")
        advanceClock(1_000)
        waitForIdle()

        onNodeWithText("Couldn't translate: x").assertIsDisplayed()
        onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun theSpeakBoxSaysWhyThereIsNoPreviewAndSpeaksWhatIsTypedInSpanishMode() = runUiTest {
        val app = launchBoard(boardOf(water))
        onNodeWithContentDescription("Spanish").performClick()
        openSpeak()

        onNodeWithText("Translating isn't available here: the test board has no translator.").assertIsDisplayed()
        onNodeWithText("so type it in Spanish", substring = true).assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("Buenos dias")
        onNode(hasText("Speak") and hasClickAction()).performClick()

        assertEquals(spokenOf("Buenos dias" to "es"), app.speaker.spokenIn)
    }

    @Test
    fun theSpeakBoxOffersTheDownloadBeforeItCanPreview() = runUiTest {
        val app = launchBoard(boardOf(water))
        app.translator.status.value = TranslatorStatus.NeedsDownload(30)
        openSpeak()

        onNodeWithText("Download the Spanish model").performClick()
        waitForIdle()

        assertEquals(1, app.translator.prepared)
        onNodeWithText("Download the Spanish model").assertDoesNotExist()
    }
}
