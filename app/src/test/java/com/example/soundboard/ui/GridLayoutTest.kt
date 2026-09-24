package com.example.soundboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLayoutTest {

    @Test
    fun `zero-size input returns baseColumns unchanged`() {
        assertEquals(4, landscapeColumnCount(baseColumns = 4, aspectRatio = 1f, widthPx = 0f, heightPx = 0f, spacingPx = 8f))
    }

    @Test
    fun `a short viewport that can't even fit one row returns baseColumns unchanged`() {
        val columns = landscapeColumnCount(
            baseColumns = 4,
            aspectRatio = 1f,
            widthPx = 1000f,
            heightPx = 4f,
            spacingPx = 8f,
            targetRows = 4
        )
        assertEquals(4, columns)
    }

    @Test
    fun `a tall wide viewport with a square aspect ratio grows columns to fit the target rows`() {
        // 1000px wide, 800px tall, square tiles, 4 target rows, no spacing for easy math:
        // each row gets 200px of height, so a square tile is 200px wide -> 5 columns fit in 1000px.
        val columns = landscapeColumnCount(
            baseColumns = 4,
            aspectRatio = 1f,
            widthPx = 1000f,
            heightPx = 800f,
            spacingPx = 0f,
            targetRows = 4
        )
        assertEquals(5, columns)
    }

    @Test
    fun `never returns fewer columns than baseColumns`() {
        // A short, narrow viewport that would otherwise compute fewer columns than base.
        val columns = landscapeColumnCount(
            baseColumns = 6,
            aspectRatio = 1f,
            widthPx = 100f,
            heightPx = 1000f,
            spacingPx = 0f,
            targetRows = 1
        )
        assertTrue(columns >= 6)
    }

    @Test
    fun `the minimum tile width floor caps columns on a very short viewport`() {
        // Without a floor, a very short viewport would demand a huge column count to
        // hit the target rows; the floor should cap it well below that.
        val withoutFloor = landscapeColumnCount(
            baseColumns = 4,
            aspectRatio = 1f,
            widthPx = 2000f,
            heightPx = 100f,
            spacingPx = 8f,
            targetRows = 4,
            minTileWidthPx = 0f
        )
        val withFloor = landscapeColumnCount(
            baseColumns = 4,
            aspectRatio = 1f,
            widthPx = 2000f,
            heightPx = 100f,
            spacingPx = 8f,
            targetRows = 4,
            minTileWidthPx = 56f
        )
        assertTrue(withFloor < withoutFloor)
    }

    @Test
    fun `portrait row height is the portrait tile width divided by the aspect ratio`() {
        // 4 columns across 424px with 8px gaps: (424 - 24) / 4 = 100px wide.
        assertEquals(100f, portraitRowHeightPx(portraitGridWidthPx = 424f, columns = 4, aspectRatio = 1f, spacingPx = 8f), 0.001f)
        assertEquals(75f, portraitRowHeightPx(portraitGridWidthPx = 424f, columns = 4, aspectRatio = 4f / 3f, spacingPx = 8f), 0.001f)
    }

    @Test
    fun `a 1-column page is capped at the standard 4-column row height`() {
        // Uncapped, one 424px-wide square column would be 424px tall; the standard is 100px.
        assertEquals(100f, cappedRowHeightPx(424f, columns = 1, aspectRatio = 1f, spacingPx = 8f, maxScale = 1f), 0.001f)
        assertEquals(75f, cappedRowHeightPx(424f, columns = 1, aspectRatio = 4f / 3f, spacingPx = 8f, maxScale = 1f), 0.001f)
    }

    @Test
    fun `pages with 4 or more columns keep their natural height under the standard cap`() {
        assertEquals(100f, cappedRowHeightPx(424f, columns = 4, aspectRatio = 1f, spacingPx = 8f, maxScale = 1f), 0.001f)
        // 6 columns: (424 - 40) / 6 = 64px, already under the cap.
        assertEquals(64f, cappedRowHeightPx(424f, columns = 6, aspectRatio = 1f, spacingPx = 8f, maxScale = 1f), 0.001f)
    }

    @Test
    fun `the cap scales with maxScale and never exceeds the natural height`() {
        assertEquals(150f, cappedRowHeightPx(424f, columns = 1, aspectRatio = 1f, spacingPx = 8f, maxScale = 1.5f), 0.001f)
        assertEquals(75f, cappedRowHeightPx(424f, columns = 4, aspectRatio = 1f, spacingPx = 8f, maxScale = 0.75f), 0.001f)
        // 2 columns: (424 - 8) / 2 = 208px natural, just over a 2x cap of 200px.
        assertEquals(200f, cappedRowHeightPx(424f, columns = 2, aspectRatio = 1f, spacingPx = 8f, maxScale = 2f), 0.001f)
        assertEquals(424f, cappedRowHeightPx(424f, columns = 1, aspectRatio = 1f, spacingPx = 8f, maxScale = Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun `portrait row height is zero for degenerate input`() {
        assertEquals(0f, portraitRowHeightPx(portraitGridWidthPx = 0f, columns = 4, aspectRatio = 1f, spacingPx = 8f), 0f)
        assertEquals(0f, portraitRowHeightPx(portraitGridWidthPx = 400f, columns = 0, aspectRatio = 1f, spacingPx = 8f), 0f)
    }

    @Test
    fun `in a plain grid a drag moves by whole rows and columns`() {
        assertEquals(9, dragTargetIndex(index = 0, rowDelta = 2, colDelta = 1, columns = 4, pinnedCount = 0, lastIndex = 15))
        assertEquals(0, dragTargetIndex(index = 5, rowDelta = -3, colDelta = -1, columns = 4, pinnedCount = 0, lastIndex = 15))
        assertEquals(15, dragTargetIndex(index = 14, rowDelta = 5, colDelta = 0, columns = 4, pinnedCount = 0, lastIndex = 15))
    }

    @Test
    fun `a pinned row as wide as the grid behaves like a plain grid`() {
        assertEquals(6, dragTargetIndex(index = 2, rowDelta = 1, colDelta = 0, columns = 4, pinnedCount = 4, lastIndex = 15))
    }

    @Test
    fun `dragging straight down from a wide pinned tile lands under it in the landscape grid`() {
        // 4 pinned tiles over rows of 8: pinned tile 1 spans the 2nd quarter of the width,
        // which sits over grid columns 2 and 3 of the first grid row (indices 6 and 7).
        assertEquals(7, dragTargetIndex(index = 1, rowDelta = 1, colDelta = 0, columns = 8, pinnedCount = 4, lastIndex = 27))
        // And back up again.
        assertEquals(1, dragTargetIndex(index = 7, rowDelta = -1, colDelta = 0, columns = 8, pinnedCount = 4, lastIndex = 27))
        // Two rows down skips the first grid row.
        assertEquals(15, dragTargetIndex(index = 1, rowDelta = 2, colDelta = 0, columns = 8, pinnedCount = 4, lastIndex = 27))
    }
}
