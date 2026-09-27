package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.Page
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlin.test.Test

/**
 * Screenshots of the board and its dialogs, compared with the reference images in
 * shared/screenshots (#221). A layout change shows up as a changed image in the pull request.
 * `./gradlew :shared:recordRoborazziAndroidHostTest` records them again after a change on
 * purpose; the usual test run compares against them. Android only (Robolectric's graphics).
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest : BoardUiTest() {

    private fun tiles(prefix: String, vararg labels: String) =
        labels.mapIndexed { i, label -> Tile(id = "$prefix$i", label = label, speakWhenNoSound = true) }

    /** A small care board: a red first row, long labels, and a blank tile to fill. */
    private fun careBoard(
        labelStyle: LabelStyle = LabelStyle(),
        themeMode: ThemeMode = ThemeMode.LIGHT,
        onHomePage: Boolean = true
    ) = Board(
        name = "Care board",
        themeMode = themeMode,
        labelStyle = labelStyle,
        stickyHomeRowEnabled = true,
        currentPageIndex = if (onHomePage) 1 else 2,
        pages = listOf(
            Page(name = "Trouble", rows = 2, columns = 3, tiles = tiles("t", "I can't breathe", "Pain", "Dizzy", "Too hot", "Too cold", "Itchy")),
            Page(
                name = "Needs",
                isHome = true,
                rows = 4,
                columns = 4,
                tiles = tiles("h", "Hey", "Something's wrong", "Call the doctor", "911").map { it.copy(colorArgb = RED) } +
                    tiles("n", "Water", "Ice chips", "Pain meds", "Bathroom", "Sit me up", "Nauseous", "Yes", "No", "Thank you", "Later", "I'm okay") +
                    Tile(id = "blank")
            ),
            Page(name = "Talking", rows = 2, columns = 2, tiles = tiles("k", "How are you?", "I love you", "Tell me a story", "Good night"))
        )
    )

    private fun ComposeUiTest.capture(name: String) {
        waitForIdle()
        captureScreenRoboImage("screenshots/$name.png", OPTIONS)
    }

    @Test
    fun boardPortrait() = runUiTest {
        launchBoard(careBoard())
        capture("board_portrait")
    }

    @Test
    fun boardLandscape() = runUiTest(landscape = true) {
        launchBoard(careBoard())
        capture("board_landscape")
    }

    @Test
    fun stickyRowOnAnotherPage() = runUiTest {
        launchBoard(careBoard(onHomePage = false))
        capture("board_sticky_row")
    }

    @Test
    fun darkTheme() = runUiTest {
        launchBoard(careBoard(themeMode = ThemeMode.DARK))
        capture("board_dark")
    }

    @Test
    fun eachLabelFont() = LabelFont.entries.forEach { font ->
        runUiTest {
            launchBoard(careBoard(labelStyle = LabelStyle(font = font, bold = font == LabelFont.ATKINSON_HYPERLEGIBLE)))
            capture("label_font_${font.name.lowercase()}")
        }
    }

    @Test
    fun tileEditor() = runUiTest {
        launchBoard(careBoard())
        // The home page's blank tile; the pager also composes the pages beside it.
        val blanks = onAllNodesWithText("+")
        blanks[blanks.fetchSemanticsNodes().indices.first { blanks[it].isDisplayed() }].performClick()
        capture("tile_editor")
    }

    @Test
    fun settings() = runUiTest {
        launchBoard(careBoard())
        onNodeWithContentDescription("Menu").performClick()
        onNodeWithText("Settings").performClick()
        capture("settings")
    }

    @Test
    fun showMode() = runUiTest {
        val app = launchBoard(careBoard())
        runOnIdle { app.vm.setShowModeEnabled(true) }
        onNodeWithText("Water").performClick()
        capture("show_mode")
    }

    private companion object {
        const val RED = 0xFFE57373.toInt()

        // Robolectric draws text the same way on every OS, but allow a sliver of anti-aliasing.
        val OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
        )
    }
}
