package com.example.soundboard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.soundboard.BoardViewModel
import com.example.soundboard.audio.Player
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Tile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakePlayer : Player {
    val played = mutableListOf<String>()
    override fun load(key: String, file: File) {}
    override fun play(key: String, volume: Float) {
        played += key
    }
    override fun unload(key: String) {}
    override fun clear() {}
    override fun release() {}
}

@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repo: BoardRepository
    private lateinit var player: FakePlayer

    private fun launchWith(board: Board) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = BoardRepository(context)
        repo.save(board)
        player = FakePlayer()

        composeRule.setContent {
            BoardScreen(vm = BoardViewModel(repo, player))
        }
    }

    @Test
    fun tappingFilledTilePlaysIt() {
        launchWith(
            Board(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))
        )

        composeRule.onNodeWithText("Air horn").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { player.played.contains("a.mp3") }
    }

    @Test
    fun tappingEmptyTileOpensEditDialogInsteadOfPlaying() {
        launchWith(Board(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))

        composeRule.onNodeWithText("+").performClick()

        composeRule.onNodeWithText("Edit tile").assertIsDisplayed()
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun tappingEditPencilOpensDialogWithNamePrefilled() {
        // Long-press now drives drag-to-reorder (see commit 883a0b7); the edit
        // dialog opens from the tile's corner pencil instead.
        launchWith(
            Board(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))
        )

        composeRule.onNodeWithText("✎").performClick()

        composeRule.onNodeWithText("Edit tile").assertIsDisplayed()
        // The name shows up twice: the (now-obscured) tile behind the dialog, and
        // the dialog's prefilled text field. Checking count avoids disambiguating
        // which node the dialog's overlay window leaves "displayed".
        assertEquals(2, composeRule.onAllNodesWithText("Air horn").fetchSemanticsNodes().size)
    }

    @Test
    fun saveInDialogUpdatesTileLabel() {
        launchWith(Board(rows = 1, columns = 1, tiles = listOf(Tile(id = "a"))))

        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Boo")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Boo").assertIsDisplayed()
    }

    @Test
    fun applyingNewGridSizeChangesTileCount() {
        // All tiles filled so the empty-tile "+" glyph can't collide with the
        // grid dialog's stepper "+" buttons.
        launchWith(
            Board(
                rows = 2,
                columns = 2,
                tiles = (0 until 4).map { Tile(id = "t$it", label = "S$it", fileName = "$it.mp3") }
            )
        )

        composeRule.onNodeWithText("2 x 2").performClick()
        // Steppers render Rows then Columns; bump the columns stepper.
        composeRule.onAllNodesWithText("+")[1].performClick()
        composeRule.onNodeWithText("Apply").performClick()

        composeRule.onNodeWithText("2 x 3").assertIsDisplayed()
    }
}
