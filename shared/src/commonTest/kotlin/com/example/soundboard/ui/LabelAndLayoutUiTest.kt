package com.example.soundboard.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tile labels and the grid, measured as each platform really lays them out (#219). Rotating a
 * real device and the system font size stay in the app's emulator tests.
 */
@OptIn(ExperimentalTestApi::class)
class LabelAndLayoutUiTest : BoardUiTest() {

    private fun labeledPage(prefix: String, rows: Int, columns: Int) = Page(
        rows = rows,
        columns = columns,
        tiles = (0 until rows * columns).map { Tile(id = "$prefix$it", label = "$prefix$it", fileName = "$prefix$it.mp3") }
    )

    // --- Labels (#142, #209) ---

    @Test
    fun labelsOnAPageShareOneSizeThatGrowsOnBigTiles() = runUiTest {
        val labels = listOf("Yes", "Going to be sick", "No")
        val tiles = labels.mapIndexed { i, label -> Tile(id = "t$i", label = label, fileName = "t$i.mp3") }
        launchBoard(Board(pages = listOf(Page(rows = 3, columns = 1, tiles = tiles))))

        val sizes = labels.map { labelStyle(it).fontSize.value }
        assertEquals(1, sizes.distinct().size)
        assertTrue(sizes.first() > 14f, "expected growth past the 14sp minimum, got ${sizes.first()}")
    }

    @Test
    fun aLabelWithAWordTooWideForItsTileShrinksOnItsOwnInsteadOfSplittingTheWord() = runUiTest {
        // #209: "Something's wrong" in a narrow row used to break as "Something / 's". Three
        // columns on this 320dp window are as wide as the four of the original report on a
        // 411dp phone.
        val labels = listOf("Hey", "Something's wrong", "911")
        val tiles = labels.mapIndexed { i, label -> Tile(id = "t$i", label = label, fileName = "t$i.mp3") }
        launchBoard(Board(pages = listOf(Page(rows = 1, columns = 3, tiles = tiles, tileAspectRatio = 4f / 3f))))

        // The row keeps the 14sp minimum; the long word's label goes below it on its own.
        listOf("Hey", "911").forEach { assertEquals(14f, labelStyle(it).fontSize.value, it) }
        assertTrue(labelStyle("Something's wrong").fontSize.value in 10f..<14f)
        labels.forEach { assertFalse(splitsAWord(textLayout(it)), "$it is split mid-word") }
    }

    @Test
    fun boldAndAllCapsApplyToTileLabels() = runUiTest {
        launchBoard(
            Board(
                pages = listOf(Page(rows = 1, columns = 2, tiles = listOf(Tile(id = "w", label = "Water", fileName = "w.mp3"), Tile(id = "b")))),
                labelStyle = LabelStyle(font = LabelFont.ATKINSON_HYPERLEGIBLE, bold = true, allCaps = true)
            )
        )

        assertEquals(FontWeight.Bold, labelStyle("WATER").fontWeight)
        assertEquals(0, onAllNodesWithText("Water").fetchSemanticsNodes().size)
    }

    // --- Landscape (#143), in a landscape window rather than a rotated device ---

    @Test
    fun aLandscapePageGridDoublesTheColumns() = runUiTest(landscape = true) {
        launchBoard(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4))))

        // 8 across: T7 shares T0's row, T8 starts the next one under T0.
        assertClose(tileBounds("T0").top, tileBounds("T7").top, "T7 top")
        assertTrue(tileBounds("T8").top > tileBounds("T0").bottom)
        assertClose(tileBounds("T0").left, tileBounds("T8").left, "T8 left")
    }

    @Test
    fun customLandscapeColumnsOverrideTheDefault() = runUiTest(landscape = true) {
        launchBoard(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4).copy(landscapeColumns = 6))))

        assertClose(tileBounds("T0").top, tileBounds("T5").top, "T5 top")
        assertTrue(tileBounds("T6").top > tileBounds("T0").bottom)
    }

    // --- Helpers ---

    /** The one on-screen node with [text]; the pager also composes neighboring pages. */
    private fun ComposeUiTest.displayed(text: String, useUnmergedTree: Boolean = false): SemanticsNodeInteraction {
        val nodes = onAllNodesWithText(text, useUnmergedTree = useUnmergedTree)
        return nodes[nodes.fetchSemanticsNodes().indices.first { nodes[it].isDisplayed() }]
    }

    /** A tile's bounds: its label merges into the clickable card. */
    private fun ComposeUiTest.tileBounds(label: String): Rect = displayed(label).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        displayed(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun ComposeUiTest.labelStyle(label: String): TextStyle = textLayout(label).layoutInput.style

    /** Whether any line of [layout] ends in the middle of a word, rather than at a space or hyphen. */
    private fun splitsAWord(layout: TextLayoutResult): Boolean {
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount - 1).any { line ->
            val end = layout.getLineEnd(line)
            end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-'
        }
    }

    private fun assertClose(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) <= 3f, "$what expected ~$expected but was $actual")
}
