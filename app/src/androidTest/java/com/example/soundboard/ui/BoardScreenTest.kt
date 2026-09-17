package com.example.soundboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
    private lateinit var vm: BoardViewModel

    private fun launchWith(board: Board) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = BoardRepository(context)
        repo.save(board)
        player = FakePlayer()
        vm = BoardViewModel(repo, player)

        composeRule.setContent {
            BoardScreen(vm = vm)
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
    fun editModeTogglePutsFilledTileTapsIntoEditDialog() {
        // Edit mode is toggled from the menu rather than always-on per tile (a
        // permanent pencil crowded small tiles); once on, a pencil reappears on
        // every tile and tapping a filled one edits it instead of playing.
        launchWith(
            Board(rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Air horn", fileName = "a.mp3")))
        )

        composeRule.onNodeWithText("☰").performClick()
        composeRule.onNodeWithText("Edit mode").performClick()
        composeRule.onNodeWithText("Air horn").performClick()

        composeRule.onNodeWithText("Edit tile").assertIsDisplayed()
        // The name shows up twice: the (now-obscured) tile behind the dialog, and
        // the dialog's prefilled text field. Checking count avoids disambiguating
        // which node the dialog's overlay window leaves "displayed".
        assertEquals(2, composeRule.onAllNodesWithText("Air horn").fetchSemanticsNodes().size)
        assertTrue(player.played.isEmpty())
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

        composeRule.onNodeWithText("☰").performClick()
        composeRule.onNodeWithText("Grid size (2 x 2)").performClick()
        // Steppers render Rows then Columns; bump the columns stepper.
        composeRule.onAllNodesWithText("+")[1].performClick()
        composeRule.onNodeWithText("Apply").performClick()

        composeRule.onNodeWithText("☰").performClick()
        composeRule.onNodeWithText("Grid size (2 x 3)").assertIsDisplayed()
    }

    @Test
    fun longPressDragReordersTiles() {
        // adb shell input's synthetic swipe interpolates movement from the very
        // first frame, tripping touch-slop cancellation before the long-press
        // timeout fires (see ARCHITECTURE.md). Driving raw pointer events through
        // Compose's TouchInjectionScope avoids that: hold position until past the
        // long-press timeout, then move, exactly like a real long-press-then-drag.
        launchWith(
            Board(
                rows = 1,
                columns = 3,
                tiles = listOf(
                    Tile(id = "a", label = "A", fileName = "a.mp3"),
                    Tile(id = "b", label = "B", fileName = "b.mp3"),
                    Tile(id = "c", label = "C", fileName = "c.mp3")
                )
            )
        )

        val aLeft = composeRule.onNodeWithText("A").fetchSemanticsNode().boundsInRoot.left
        val bLeft = composeRule.onNodeWithText("B").fetchSemanticsNode().boundsInRoot.left
        val cellStepPx = bLeft - aLeft

        composeRule.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(600) // past the platform's ~500ms long-press timeout
            moveBy(Offset(x = cellStepPx * 1.2f, y = 0f))
            up()
        }

        composeRule.waitForIdle()

        assertEquals(listOf("B", "A", "C"), vm.board.value.tiles.map { it.label })
    }
}
