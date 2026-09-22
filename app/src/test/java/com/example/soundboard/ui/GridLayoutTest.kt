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
}
