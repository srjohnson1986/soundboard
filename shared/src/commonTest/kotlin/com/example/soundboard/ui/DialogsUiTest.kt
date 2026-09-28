package com.example.soundboard.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.ui.theme.presetColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each dialog does, beyond opening (#242): Speak, the board-management dialogs,
 * every Settings group, and the page dialogs. On the JVM and in the browser.
 */
@OptIn(ExperimentalTestApi::class)
class DialogsUiTest : BoardUiTest() {

    private fun oneTile(tile: Tile = Tile(id = "a")) = Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(tile))))

    private fun twoPages(second: Tile = Tile(id = "b")) = Board(
        pages = listOf(
            Page(name = "First", rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))),
            Page(name = "Second", rows = 1, columns = 1, tiles = listOf(second))
        )
    )

    // --- Speak ---

    @Test
    fun speakSaysWhatsTypedAndStaysOpenForTheNextPhrase() = runUiTest {
        val app = launchBoard(oneTile())

        openMenuItem("Speak...")
        val speak = onNode(hasText("Speak") and hasClickAction())
        speak.assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput("Can I have some water")
        speak.assertIsEnabled().performClick()

        assertEquals(listOf("Can I have some water"), app.speaker.spoken)
        onNodeWithText("Close").performClick()
        onNodeWithText("Type what you want to say...").assertDoesNotExist()
    }

    // --- Board management ---

    @Test
    fun openBoardListsTheBuiltInAndSavedBoardsAndOpensOneStraightAwayWhenNothingWouldBeLost() = runUiTest {
        // Only the built-in boards this build packages are offered.
        val app = launchBoard(oneTile(), builtIns = mapOf("tts-care-board.zip" to BuiltInBoard(Board(name = "TTS Care Board"))))
        SavedBoardRepository(app.files).save(Board(name = "Kitchen"))

        openMenuItem("Open board...")

        onNodeWithText("TTS Care Board").performScrollTo().assertIsDisplayed()
        onNodeWithText("Jeremy Draft Care Board").assertDoesNotExist()
        onNodeWithText("Saved ·", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("Kitchen").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.name == "Kitchen" }
        onNodeWithText("Open board").assertDoesNotExist()
    }

    @Test
    fun openingABoardOverOneWithSoundsAsksFirst() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Hey", fileName = "a.mp3")))
        SavedBoardRepository(app.files).save(Board(name = "Kitchen"))
        val original = app.vm.board.value.name

        openMenuItem("Open board...")
        onNodeWithText("Kitchen").performClick()
        onNodeWithText("Replace current board with \"Kitchen\"?").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()

        onNodeWithText("Replace current board", substring = true).assertDoesNotExist()
        assertEquals(original, app.vm.board.value.name)

        openMenuItem("Open board...")
        onNodeWithText("Kitchen").performClick()
        onNodeWithText("Open").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.name == "Kitchen" }
    }

    @Test
    fun cleanUpUnusedClipsCountsThemAndDeleteRemovesThem() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Hey", fileName = "a.mp3")))
        app.files.put("sounds/stray.mp3", ByteArray(340_000))

        openMenuItem("Clean up unused clips")
        onNodeWithText("1 unused clip found, totaling 340 KB", substring = true).assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        assertTrue(app.files.has("sounds/stray.mp3"))

        openMenuItem("Clean up unused clips")
        onNodeWithText("Delete").performClick()

        waitUntil(timeoutMillis = 2_000) { !app.files.has("sounds/stray.mp3") }
        assertTrue(app.files.has("sounds/a.mp3"))
        onNodeWithText("Clean up unused clips").assertDoesNotExist()
    }

    @Test
    fun cleanUpUnusedClipsSaysWhenThereAreNone() = runUiTest {
        launchBoard(oneTile())

        openMenuItem("Clean up unused clips")
        onNodeWithText("No unused clips found.").assertIsDisplayed()
        onNodeWithText("Close").performClick()

        onNodeWithText("No unused clips found.").assertDoesNotExist()
    }

    @Test
    fun renameBoardRenamesIt() = runUiTest {
        val app = launchBoard(oneTile())

        openMenuItem("Rename board", substring = true)
        onNode(hasSetTextAction()).performTextReplacement("  Kitchen  ")
        onNodeWithText("Save").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.name == "Kitchen" }
    }

    // --- Settings ---

    @Test
    fun theSettingsListSummarizesEveryGroup() = runUiTest {
        launchBoard(oneTile())

        openSettings()

        listOf(
            "No home page set",
            "System theme · plain background",
            "Default (Roboto), 14–32 sp",
            "New pages 4×4 · standard rows · page grid in landscape",
            "Long-press: default · haptics on",
            "performance mode off",
            "Leaves off the last 0.25 s"
        ).forEach { onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed() }
        onNodeWithText("Off").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homePageSettingsNeedAHomePageAndSetTheAutoReturn() = runUiTest {
        val app = launchBoard(twoPages())

        openSettingsGroup("Home page")
        onNodeWithText("Set a home page", substring = true).assertIsDisplayed()
        onNode(hasText("Sticky home row") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertIsNotEnabled()
        onNodeWithText("Done").performClick()

        app.vm.setHomePage(0)
        openSettingsGroup("Home page")
        pickOption("Auto-return to home page after", "5 min")
        clickSwitchBeside("Sticky home row")

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.idleTimeoutMinutes == 5 && app.vm.board.value.stickyHomeRowEnabled }
        onNodeWithText("Back").performClick()
        onNodeWithText("Auto-return after 5 min · sticky row on").assertIsDisplayed()
    }

    @Test
    fun theLookGroupSetsTheThemeAndTheBoardsTileBorder() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Look")
        onNodeWithText("Dark").performClick()
        onNodeWithText("Clear").assertIsNotEnabled()
        clickSwitchBeside("Show border")

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.themeMode == ThemeMode.DARK && app.vm.board.value.tileBorder.enabled }
        onNodeWithText("Back").performClick()
        onNodeWithText("Dark theme · plain background").assertIsDisplayed()
    }

    @Test
    fun theTileLabelsGroupSetsTheFontBoldAndCapsAndPreviewsThem() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Tile labels")
        pickOption("Font", "Atkinson Hyperlegible")
        clickSwitchBeside("Bold")
        clickSwitchBeside("All caps")

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.labelStyle.allCaps }
        val style = app.vm.board.value.labelStyle
        assertEquals(LabelFont.ATKINSON_HYPERLEGIBLE, style.font)
        assertTrue(style.bold)
        onNodeWithText("CALL THE DOCTOR").performScrollTo().assertIsDisplayed()
        onNodeWithText("Back").performClick()
        onNodeWithText("Atkinson Hyperlegible, 14–32 sp, bold, all caps").assertIsDisplayed()
    }

    @Test
    fun theGridLayoutGroupSetsNewPagesRowHeightAndTheLandscapeLayout() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Grid layout")
        onNodeWithContentDescription("Increase Rows").performClick()
        onNodeWithContentDescription("Decrease Columns").performClick()
        pickOption("Max row height", "Tall")
        onNodeWithText("Fit to screen").performScrollTo().performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.landscapeLayout == LandscapeLayout.FIT_TO_SCREEN }
        val board = app.vm.board.value
        assertEquals(5 to 3, board.defaultPageRows to board.defaultPageColumns)
        assertEquals(RowHeight.TALL, board.rowHeight)
        onNodeWithText("Adds columns until 4 rows fit on screen.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Back").performClick()
        onNodeWithText("New pages 5×3 · tall rows · fit to screen in landscape").assertIsDisplayed()
    }

    @Test
    fun theTappingAndSpeechGroupSetsEachOfItsOptions() = runUiTest {
        val app = launchBoard(oneTile())
        val before = app.vm.board.value

        openSettingsGroup("Tapping & speech")
        clickSwitchBeside("Speak label when there's no clip")
        clickSwitchBeside("Hide blank tiles")
        pickOption("Long-press duration", "Long")
        clickSwitchBeside("Haptic feedback")

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.hapticFeedbackEnabled != before.hapticFeedbackEnabled }
        val board = app.vm.board.value
        assertEquals(!before.speakUnrecordedTilesEnabled, board.speakUnrecordedTilesEnabled)
        assertEquals(!before.hideBlankTilesEnabled, board.hideBlankTilesEnabled)
        assertEquals(800, board.longPressDurationMillis)
        onNodeWithText("Back").performClick()
        onNodeWithText("Long-press: long · haptics off").assertIsDisplayed()
    }

    @Test
    fun theScreenGroupTurnsOnPerformanceModeForThisDevice() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Screen")
        clickSwitchBeside("Performance mode")

        waitUntil(timeoutMillis = 2_000) { app.devicePrefs.performanceModeEnabled }
        onNodeWithText("Back").performClick()
        onNodeWithText("performance mode on", substring = true).assertIsDisplayed()
    }

    @Test
    fun theShowModeGroupKeepsAWayToCloseTheText() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Show mode")
        onNode(hasText("Show mode") and isToggleable()).performClick()
        // With the timer off, tap to close is the only way out, so it's locked on.
        pickOption("Close the text after", "Off")
        onNodeWithText("Stays on while the timer is off", substring = true).performScrollTo().assertIsDisplayed()
        onNode(hasText("Tap to close") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertIsNotEnabled()
        clickSwitchBeside("Mute sounds while showing")
        clickSwitchBeside("Flip text upside down")

        waitUntil(timeoutMillis = 2_000) { app.devicePrefs.showMode.flipped }
        val showMode = app.devicePrefs.showMode
        assertTrue(showMode.enabled && showMode.tapToClose && showMode.muteSounds)
        assertEquals(0, showMode.timerSeconds)
        onNodeWithText("Back").performClick()
        onNodeWithText("On · closes on tap · sounds muted · upside down").assertIsDisplayed()
    }

    // --- Page dialogs ---

    @Test
    fun theHomePagesOptionsSayItsHomeAndCantBeMadeHomeAgain() = runUiTest {
        launchBoard(twoPages().let { it.copy(pages = listOf(it.pages[0].copy(isHome = true), it.pages[1])) })

        // The page's tab already has the home icon; its options' title adds a second.
        onAllNodesWithContentDescription("Home page").assertCountEquals(1)
        longPressPageTab("First")

        onAllNodesWithContentDescription("Home page").assertCountEquals(2)
        onNode(hasText("Set as home page") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertIsNotEnabled()
    }

    @Test
    fun pageOptionsRenamesAndMovesAPage() = runUiTest {
        val app = launchBoard(twoPages())

        longPressPageTab("First")
        onNodeWithText("Rename").performClick()
        onNode(hasSetTextAction()).performTextReplacement(" Trouble ")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].name == "Trouble" }

        longPressPageTab("Trouble")
        onNodeWithText("Move left").assertIsNotEnabled()
        onNodeWithText("Move right").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages.map { it.name } == listOf("Second", "Trouble") }
    }

    @Test
    fun deletingAPageWithSoundsAsksFirstButAnEmptyOneGoesStraightAway() = runUiTest {
        val app = launchBoard(twoPages(second = Tile(id = "b", label = "Hey", fileName = "b.mp3")))

        longPressPageTab("Second")
        onNodeWithText("Delete page").performScrollTo().performClick()
        onNodeWithText("Delete \"Second\"?").assertIsDisplayed()
        onNodeWithText("This page has 1 tile with sound", substring = true).assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        assertEquals(2, app.vm.board.value.pages.size)

        longPressPageTab("Second")
        onNodeWithText("Delete page").performScrollTo().performClick()
        onNodeWithText("Delete").performClick()
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages.map { it.name } == listOf("First") }

        // The last page can't be deleted at all.
        longPressPageTab("First")
        onNodeWithText("Delete page").assertDoesNotExist()
        onNodeWithText("Cancel").performClick()

        app.vm.addPage("Empty")
        longPressPageTab("Empty")
        onNodeWithText("Delete page").performScrollTo().performClick()
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages.map { it.name } == listOf("First") }
        onNodeWithText("Delete \"Empty\"?").assertDoesNotExist()
    }

    @Test
    fun gridSizeSetsACustomLandscapeGridTileShapeAndRowHeight() = runUiTest {
        val app = launchBoard(twoPages())

        longPressPageTab("First")
        onNodeWithText("Grid size", substring = true).performClick()
        onNodeWithText("Landscape: 2 rows x 2 columns (twice the columns).").assertIsDisplayed()
        clickSwitchBeside("Custom landscape size")
        onNodeWithContentDescription("Increase Landscape rows").performScrollTo().performClick()
        onNodeWithText("Wide").performScrollTo().performClick()
        pickOption("Max row height", "Short")
        onNodeWithText("Apply").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].rowHeight == RowHeight.SHORT }
        val page = app.vm.board.value.pages[0]
        assertEquals(3 to 2, page.landscapeRows to page.landscapeColumns)
        assertEquals(Page.WIDE_TILE_ASPECT_RATIO, page.tileAspectRatio)
    }

    @Test
    fun gridSizeSaysWhenFitToScreenOverridesTheLandscapeGrid() = runUiTest {
        launchBoard(twoPages().copy(landscapeLayout = LandscapeLayout.FIT_TO_SCREEN))

        longPressPageTab("First")
        onNodeWithText("Grid size (1x1)").performClick()

        onNodeWithText("Settings has landscape set to Fit to screen", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pageAppearanceOverridesTheBoardsOpacityAndBorderForOnePage() = runUiTest {
        val app = launchBoard(twoPages())

        longPressPageTab("First")
        onNodeWithText("Page appearance").performClick()
        clickSwitchBeside("Override the board's tile opacity")
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].opacity == 1f }
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        clickSwitchBeside("Override the board's tile border")

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].border?.enabled == true }
        assertEquals(0.5f, app.vm.board.value.pages[0].opacity)
        onNodeWithText("Opacity: 50%").performScrollTo().assertIsDisplayed()

        clickSwitchBeside("Override the board's tile opacity")
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].opacity == null }
        // Only this page: the other still follows the board.
        assertNull(app.vm.board.value.pages[1].border)
        assertNull(app.vm.board.value.pages[1].opacity)
    }

    // --- Color swatches (#245): named and selectable, for screen readers and for these tests ---

    @Test
    fun aPageColorIsPickedByNameAndShowsAsSelected() = runUiTest {
        val app = launchBoard(twoPages())

        longPressPageTab("First")
        onNodeWithText("Page appearance").performClick()
        onNodeWithContentDescription("Default color").assertIsSelected()
        pickSwatch("Blue")

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].color == BLUE }
        onNodeWithContentDescription("Blue").assertIsSelected()
        onNodeWithContentDescription("Default color").assertIsNotSelected()

        pickSwatch("Default color")
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.pages[0].color == null }
    }

    @Test
    fun aTilesColorIsPickedByNameAndSavedWithTheTile() = runUiTest {
        val app = launchBoard(oneTile())

        onNodeWithText("+").performClick()
        pickSwatch("Teal")
        onNodeWithText("Save").performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.findTile("a")?.colorArgb == TEAL }
    }

    @Test
    fun theBackgroundColorIsPickedByNameAndClearTakesItOff() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Look")
        onNodeWithContentDescription("No background color").performScrollTo().assertIsSelected()
        // Look has two pickers, the background's and then the border's.
        pickSwatch("Green", picker = 0)
        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.backgroundColorArgb == GREEN }

        onNodeWithText("Back").performClick()
        onNodeWithText("System theme · background color").assertIsDisplayed()
        onNodeWithText("Look").performClick()
        onNodeWithText("Clear").performScrollTo().assertIsEnabled().performClick()

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.backgroundColorArgb == null }
        onNodeWithContentDescription("No background color").performScrollTo().assertIsSelected()
    }

    @Test
    fun theBordersColorIsPickedByNameAndItsNoneIsRecommended() = runUiTest {
        val app = launchBoard(oneTile())

        openSettingsGroup("Look")
        clickSwitchBeside("Show border")
        onNodeWithContentDescription("Recommended").performScrollTo().assertIsSelected()
        // Look has two pickers, the background's and then the border's.
        onAllNodesWithContentDescription("Purple").assertCountEquals(2)
        pickSwatch("Purple", picker = 1)

        waitUntil(timeoutMillis = 2_000) { app.vm.board.value.tileBorder.colorArgb == PURPLE }
        assertNull(app.vm.board.value.backgroundColorArgb)
    }

    // --- Helpers ---

    /** Opens the ☰ menu and picks [label] from it. */
    private fun ComposeUiTest.openMenuItem(label: String, substring: Boolean = false) {
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText(label, substring = substring).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openSettingsGroup(title: String) {
        openSettings()
        onNodeWithText(title).performScrollTo().performClick()
        waitForIdle()
    }

    /**
     * Picks the swatch named [name] from the [picker]th color picker on screen. The swatches
     * are a sideways-scrolling row wider than a phone's dialog, so it scrolls that row to the
     * swatch first; performScrollTo only scrolls the dialog.
     */
    private fun ComposeUiTest.pickSwatch(name: String, picker: Int = 0) {
        val row = onAllNodes(hasScrollAction() and hasAnyChild(hasContentDescription(name)))[picker]
        row.performScrollTo()
        row.performScrollToNode(hasContentDescription(name))
        onAllNodesWithContentDescription(name)[picker].performClick()
        waitForIdle()
    }

    /** Opens the dropdown labeled [label] and picks [option]. */
    private fun ComposeUiTest.pickOption(label: String, option: String) {
        onNodeWithText(label).performScrollTo().performClick()
        onAllNodes(hasText(option) and hasClickAction()).onFirst().performClick()
        waitForIdle()
    }
}

private val BLUE = presetArgb("Blue")
private val TEAL = presetArgb("Teal")
private val GREEN = presetArgb("Green")
private val PURPLE = presetArgb("Purple")

private fun presetArgb(name: String): Int = presetColors.first { it.name == name }.color.toArgb()
