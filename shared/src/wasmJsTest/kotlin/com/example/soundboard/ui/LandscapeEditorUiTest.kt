package com.example.soundboard.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test

/**
 * #233: the tile editor in a landscape phone window scrolls to its last control. Browser
 * only: under Robolectric, opening the editor in a landscape window never settles, which
 * predates this and doesn't happen on a device (#234). The Android side is covered on the
 * emulator by LayoutAndLabelTest.theTileEditorScrollsInLandscape.
 */
@OptIn(ExperimentalTestApi::class)
class LandscapeEditorUiTest : BoardUiTest() {

    @Test
    fun inLandscapeTheTileEditorScrollsToItsLastControl() = runUiTest(landscape = true) {
        launchBoard(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))))

        // Landscape shows several blank tiles for a one-tile page; any of them opens the editor.
        onAllNodesWithText("+").onFirst().performClick()

        onNodeWithText("Override border").performScrollTo().assertIsDisplayed()
        onNodeWithText("Save").assertIsDisplayed()
    }
}
