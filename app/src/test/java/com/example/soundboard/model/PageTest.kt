package com.example.soundboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTest {

    private fun tile(id: String, label: String = "") = Tile(id = id, label = label)

    @Test
    fun `growing keeps existing tiles and appends empty ones`() {
        val original = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val grown = original.resized(4, 5)

        assertEquals(4, grown.rows)
        assertEquals(5, grown.columns)
        assertEquals(original.tiles, grown.tiles.take(16))
        assertEquals(20, grown.tiles.size)
        assertEquals(4, grown.tiles.drop(16).count { it.isEmpty })
    }

    @Test
    fun `shrinking keeps the first tiles in order and drops the rest`() {
        val original = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val shrunk = original.resized(3, 3)

        assertEquals(3, shrunk.rows)
        assertEquals(3, shrunk.columns)
        // resized() never drops tiles from the backing list, only the visible window.
        assertEquals(original.tiles, shrunk.tiles)
        assertEquals(original.tiles.take(9), shrunk.visibleTiles)
    }

    @Test
    fun `resizing to the same dimensions is a no-op`() {
        val original = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val result = original.resized(4, 4)

        assertEquals(original, result)
    }

    @Test
    fun `1x1 page works`() {
        val original = Page(rows = 1, columns = 1, tiles = listOf(tile("only")))

        val result = original.resized(1, 1)

        assertEquals(1, result.rows)
        assertEquals(1, result.columns)
        assertEquals(listOf(tile("only")), result.tiles)
    }

    @Test
    fun `rows and columns are updated not just tile count`() {
        val original = Page(rows = 2, columns = 2, tiles = (0 until 4).map { tile("t$it") })

        val result = original.resized(1, 8)

        assertEquals(1, result.rows)
        assertEquals(8, result.columns)
        assertEquals(8, result.tiles.size)
    }

    @Test
    fun `default pages are not equal because tile ids are random`() {
        assertNotEquals(Page(), Page())
    }

    @Test
    fun `tileAspectRatio and color default to square and unset`() {
        val page = Page()

        assertEquals(1f, page.tileAspectRatio)
        assertEquals(null, page.color)
    }

    private fun filledTile(id: String) = Tile(id = id, fileName = "$id.mp3")

    @Test
    fun `withAutoGrownTrailingRow is a no-op when the last row still has a blank tile`() {
        val page = Page(rows = 1, columns = 2, tiles = listOf(filledTile("a"), Tile(id = "b")))

        val result = page.withAutoGrownTrailingRow()

        assertEquals(page, result)
    }

    @Test
    fun `withAutoGrownTrailingRow appends a blank row once the last row is completely filled`() {
        val page = Page(rows = 1, columns = 2, tiles = listOf(filledTile("a"), filledTile("b")))

        val result = page.withAutoGrownTrailingRow()

        assertEquals(2, result.rows)
        assertEquals(2, result.columns)
        assertEquals(4, result.tiles.size)
        assertEquals(listOf(filledTile("a"), filledTile("b")), result.tiles.take(2))
        assertTrue(result.tiles.drop(2).all { it.isEmpty })
    }

    @Test
    fun `withAutoGrownTrailingRow never reveals tiles hidden by a prior shrink`() {
        // Same shape resized() leaves behind when shrinking: more backing tiles than
        // rows x columns currently shows, and the hidden ones happen to be filled too.
        val page = Page(rows = 1, columns = 1, tiles = listOf(filledTile("a"), filledTile("b")))

        val result = page.withAutoGrownTrailingRow()

        assertEquals(page, result)
    }
}
