package com.example.soundboard.ui

import android.app.UiAutomation
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.soundboard.BoardViewModel
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.PresetRepository
import com.example.soundboard.data.RecentPresetsRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.Tile
import kotlin.math.abs
import kotlin.math.min
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The landscape grid (#143), row height cap (#141) and label text (#142), checked on a real
 * device: tile positions and sizes come from actual layout, and landscape is a real rotation.
 */
@RunWith(AndroidJUnit4::class)
class LayoutAndLabelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: BoardViewModel

    private val uiAutomation: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @After
    fun restoreRotation() {
        uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    /**
     * Rotates the device and waits for the test activity to come back in that orientation.
     * Must run before [launchWith]: rotating recreates the activity, which drops any content.
     */
    private fun rotate(landscape: Boolean) {
        uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        val wanted = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.orientation == wanted
        }
    }

    private fun launchWith(board: Board, fontScale: Float? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = BoardRepository(context)
        board.pages.flatMap { it.tiles }.mapNotNull { it.fileName }.forEach { name ->
            repo.soundFile(name).apply { parentFile?.mkdirs() }.writeText(name)
        }
        repo.save(board)
        vm = BoardViewModel(repo, FakePlayer(), FakeRecorder(), PresetRepository(context), FakeSpeaker(), DevicePreferences(context), RecentPresetsRepository(context))
        composeRule.setContent {
            if (fontScale == null) {
                BoardScreen(vm = vm)
            } else {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BoardScreen(vm = vm)
                }
            }
        }
        // The board loads off the main thread; wait for this one rather than the default.
        composeRule.waitUntil(timeoutMillis = 5_000) { vm.board.value.pages.first().id == board.pages.first().id }
        composeRule.waitForIdle()
    }

    private fun labeledPage(prefix: String, rows: Int, columns: Int, name: String = "Page 1", isHome: Boolean = false, aspectRatio: Float = 1f) =
        Page(
            name = name,
            rows = rows,
            columns = columns,
            tileAspectRatio = aspectRatio,
            isHome = isHome,
            tiles = (0 until rows * columns).map { Tile(id = "$prefix$it", label = "$prefix$it", fileName = "$prefix$it.mp3") }
        )

    /** The one on-screen node with [text] — the pager keeps neighboring pages composed off-screen too. */
    private fun displayed(text: String, useUnmergedTree: Boolean = false): SemanticsNodeInteraction {
        val nodes = composeRule.onAllNodesWithText(text, useUnmergedTree = useUnmergedTree)
        return nodes[nodes.fetchSemanticsNodes().indices.first { nodes[it].isDisplayed() }]
    }

    private fun displayedCount(text: String): Int {
        val nodes = composeRule.onAllNodesWithText(text)
        return nodes.fetchSemanticsNodes().indices.count { nodes[it].isDisplayed() }
    }

    /** A tile's own bounds (its label merges into the clickable card), in px. */
    private fun tileBounds(label: String): Rect = displayed(label).fetchSemanticsNode().boundsInRoot

    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        displayed(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun labelTextStyle(label: String): TextStyle = textLayout(label).layoutInput.style

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    /**
     * The row height a [columns]-column page of [aspectRatio] tiles has in portrait: the
     * window's shorter side, less the grid's 12dp side padding, split with 8dp gaps.
     */
    private fun portraitRowHeightPx(columns: Int, aspectRatio: Float = 1f): Float {
        val decor = composeRule.activity.window.decorView
        val gridWidth = min(decor.width, decor.height) - px(24f)
        return (gridWidth - px(8f) * (columns - 1)) / columns / aspectRatio
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 3f, what: String = "") =
        assertTrue("$what expected ~$expected but was $actual", abs(expected - actual) <= tolerance)

    // --- Landscape (#143) ---

    @Test
    fun landscapePageGridDoublesTheColumnsAndKeepsThePortraitRowHeight() {
        rotate(landscape = true)
        launchWith(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4))))

        // 8 across: T7 shares T0's row, T8 starts the next one under T0.
        assertClose(tileBounds("T0").top, tileBounds("T7").top, what = "T7 top")
        assertTrue(tileBounds("T8").top > tileBounds("T0").bottom)
        assertClose(tileBounds("T0").left, tileBounds("T8").left, what = "T8 left")
        assertClose(portraitRowHeightPx(columns = 4), tileBounds("T0").height, what = "row height")
    }

    @Test
    fun landscapeFitToScreenKeepsTheOldSquareTiles() {
        rotate(landscape = true)
        launchWith(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4)), landscapeLayout = LandscapeLayout.FIT_TO_SCREEN))

        val t0 = tileBounds("T0")
        assertClose(t0.width, t0.height, what = "square tile")
    }

    @Test
    fun customLandscapeColumnsOverrideTheDefault() {
        rotate(landscape = true)
        launchWith(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4).copy(landscapeColumns = 6))))

        assertClose(tileBounds("T0").top, tileBounds("T5").top, what = "T5 top")
        assertTrue(tileBounds("T6").top > tileBounds("T0").bottom)
    }

    @Test
    fun landscapeStickyRowShowsTheHomePagesFirstLandscapeRowAtTheSameHeight() {
        rotate(landscape = true)
        // Home sits two pages away, so the pager doesn't compose its own grid off-screen.
        launchWith(
            Board(
                pages = listOf(
                    labeledPage("O", rows = 4, columns = 4, name = "Other"),
                    labeledPage("M", rows = 1, columns = 1, name = "Middle"),
                    labeledPage("H", rows = 4, columns = 4, name = "Home", isHome = true)
                ),
                stickyHomeRowEnabled = true
            )
        )

        (0 until 8).forEach { assertEquals("H$it", 1, displayedCount("H$it")) }
        assertEquals(0, displayedCount("H8"))
        assertClose(tileBounds("O0").height, tileBounds("H0").height, what = "sticky row height")
    }

    @Test
    fun hideBlankTilesAlsoHidesTheExtraLandscapeSlots() {
        rotate(landscape = true)
        launchWith(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4)), hideBlankTilesEnabled = true))

        assertTrue(vm.board.value.currentPage.landscapeTiles.any { it.isEmpty })
        assertEquals(0, displayedCount("+"))
    }

    // --- Drag to reorder ---

    @Test
    fun draggingDownOneLandscapeRowMovesATileByTheLandscapeColumnCount() {
        rotate(landscape = true)
        launchWith(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4))))

        val rowStep = tileBounds("T8").top - tileBounds("T0").top
        displayed("T0").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, rowStep * 1.1f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals("T0", vm.board.value.currentPage.tiles[8].label)
    }

    @Test
    fun draggingWideTilesDownSeveralRowsLandsOnTheRightRow() {
        // Wide tiles are shorter than they are wide; the drag used to step rows by the tile
        // width, so a long vertical drag undershot by a row.
        rotate(landscape = false)
        launchWith(Board(pages = listOf(labeledPage("T", rows = 4, columns = 4, aspectRatio = 4f / 3f))))

        val threeRows = tileBounds("T12").top - tileBounds("T0").top
        displayed("T0").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, threeRows))
            up()
        }
        composeRule.waitForIdle()

        assertEquals("T0", vm.board.value.currentPage.tiles[12].label)
    }

    // --- Row height (#141) ---

    @Test
    fun aOneColumnPageGetsFullWidthBarsAtTheStandardRowHeight() {
        rotate(landscape = false)
        launchWith(Board(pages = listOf(labeledPage("A", rows = 3, columns = 1))))

        val a0 = tileBounds("A0")
        assertClose(portraitRowHeightPx(columns = 4), a0.height, what = "capped row height")
        assertClose(portraitRowHeightPx(columns = 1), a0.width, what = "full-width bar")
    }

    @Test
    fun aPageRowHeightOverrideBeatsTheBoardSetting() {
        rotate(landscape = false)
        launchWith(
            Board(
                pages = listOf(labeledPage("A", rows = 3, columns = 1).copy(rowHeight = RowHeight.SHORT)),
                rowHeight = RowHeight.TALL
            )
        )

        assertClose(portraitRowHeightPx(columns = 4) * 0.75f, tileBounds("A0").height, what = "short row height")
    }

    @Test
    fun noLimitRestoresTheOldTallTiles() {
        rotate(landscape = false)
        launchWith(Board(pages = listOf(labeledPage("A", rows = 2, columns = 2)), rowHeight = RowHeight.UNLIMITED))

        val a0 = tileBounds("A0")
        assertClose(a0.width, a0.height, what = "square tile")
    }

    // --- Label text (#142) ---

    @Test
    fun labelsOnAPageShareOneSizeThatGrowsOnBigTiles() {
        rotate(landscape = false)
        val tiles = listOf("Yes", "Going to be sick", "No").mapIndexed { i, label -> Tile(id = "t$i", label = label, fileName = "t$i.mp3") }
        launchWith(Board(pages = listOf(Page(rows = 3, columns = 1, tiles = tiles))))

        val sizes = listOf("Yes", "Going to be sick", "No").map { labelTextStyle(it).fontSize.value }
        assertEquals(1, sizes.distinct().size)
        assertTrue("expected growth past the 14sp minimum, got ${sizes.first()}", sizes.first() > 14f)
    }

    @Test
    fun aFourColumnPageWithALongLabelStaysAtTheMinimumSize() {
        rotate(landscape = false)
        val labels = listOf("Hey", "Something's wrong", "Call the doctor", "911")
        val tiles = labels.mapIndexed { i, label -> Tile(id = "t$i", label = label, fileName = "t$i.mp3") }
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 4, tiles = tiles, tileAspectRatio = 4f / 3f))))

        labels.forEach { assertEquals(it, 14f, labelTextStyle(it).fontSize.value) }
    }

    @Test
    fun fontBoldAndAllCapsApplyToTileLabels() {
        rotate(landscape = false)
        launchWith(
            Board(
                pages = listOf(Page(rows = 1, columns = 2, tiles = listOf(Tile(id = "w", label = "Water", fileName = "w.mp3"), Tile(id = "b")))),
                labelStyle = LabelStyle(font = LabelFont.ATKINSON_HYPERLEGIBLE, bold = true, allCaps = true)
            )
        )

        val style = labelTextStyle("WATER")
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(LabelFont.ATKINSON_HYPERLEGIBLE.fontFamily(), style.fontFamily)
        assertEquals(0, displayedCount("Water"))
    }

    @Test
    fun labelsAreNeverClippedVerticallyAtTheLargestSystemFontSize() {
        rotate(landscape = false)
        val labels = listOf("Hey", "Something's wrong", "Call the doctor", "Meds not working")
        val tiles = labels.mapIndexed { i, label -> Tile(id = "t$i", label = label, fileName = "t$i.mp3") }
        launchWith(Board(pages = listOf(Page(rows = 1, columns = 4, tiles = tiles, tileAspectRatio = 4f / 3f))), fontScale = 2f)

        labels.forEach { label ->
            val textHeight = textLayout(label).size.height
            val room = tileBounds(label).height - px(12f)
            assertTrue("\"$label\" is ${textHeight}px tall in a ${room}px box", textHeight <= room + 1f)
        }
        // Proves the 2x scale took effect: at least one label had to be cut short with "…".
        assertTrue(labels.any { textLayout(it).hasVisualOverflow })
    }
}
