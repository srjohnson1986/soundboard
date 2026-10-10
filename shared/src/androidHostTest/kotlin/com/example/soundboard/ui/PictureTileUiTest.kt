package com.example.soundboard.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test

/**
 * Tile pictures actually drawn: on the tile, and on Show mode's screen. Android host only: a
 * picture decodes asynchronously, and in the browser the test's own idle wait holds the one
 * thread that decoding needs (#279), so the same test would only time out there. What is saved
 * and kept for a picture is covered on every platform in the view model and repository tests.
 */
@OptIn(ExperimentalTestApi::class)
class PictureTileUiTest : BoardUiTest() {

    private fun oneTile(tile: Tile) = Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(tile))))

    @Test
    fun aPictureTileDrawsItsPictureWithItsName() = runUiTest {
        launchBoard(oneTile(Tile(id = "a", label = "Photo", fileName = "a.mp3", imageFileName = "p.png")))

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Photo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Photo").assertIsDisplayed()
    }

    @Test
    fun showModeShowsATilesPictureBigWithItsWords() = runUiTest {
        launchBoard(oneTile(Tile(id = "a", label = "Photo", fileName = "a.mp3", imageFileName = "p.png")))

        toggleMenuSwitch("Show mode")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Photo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Photo").performClick()

        onNodeWithTag(SHOW_TEXT_OVERLAY_TAG).assertIsDisplayed()
        // The tile's own picture, and the one on the Show mode screen.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Photo").fetchSemanticsNodes().size == 2 }
    }

    @Test
    fun aPictureThatIsGoneFromStorageFallsBackToTheName() = runUiTest {
        val app = launchBoard(oneTile(Tile(id = "a", label = "Photo", fileName = "a.mp3", imageFileName = "p.png")), withIcons = false)

        waitForIdle()
        onNodeWithText("Photo").assertIsDisplayed()
        kotlin.test.assertNull(app.vm.board.value.findTile("a")?.imageFileName)
    }
}
