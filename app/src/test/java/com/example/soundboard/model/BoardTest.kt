package com.example.soundboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BoardTest {

    private fun tile(id: String, label: String = "") = Tile(id = id, label = label)

    @Test
    fun `growing keeps existing tiles and appends empty ones`() {
        val original = Board(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val grown = original.resized(4, 5)

        assertEquals(4, grown.rows)
        assertEquals(5, grown.columns)
        assertEquals(original.tiles, grown.tiles.take(16))
        assertEquals(20, grown.tiles.size)
        assertEquals(4, grown.tiles.drop(16).count { it.isEmpty })
    }

    @Test
    fun `shrinking keeps the first tiles in order and drops the rest`() {
        val original = Board(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val shrunk = original.resized(3, 3)

        assertEquals(3, shrunk.rows)
        assertEquals(3, shrunk.columns)
        // resized() never drops tiles from the backing list, only the visible window.
        assertEquals(original.tiles, shrunk.tiles)
        assertEquals(original.tiles.take(9), shrunk.visibleTiles)
    }

    @Test
    fun `resizing to the same dimensions is a no-op`() {
        val original = Board(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val result = original.resized(4, 4)

        assertEquals(original, result)
    }

    @Test
    fun `1x1 board works`() {
        val original = Board(rows = 1, columns = 1, tiles = listOf(tile("only")))

        val result = original.resized(1, 1)

        assertEquals(1, result.rows)
        assertEquals(1, result.columns)
        assertEquals(listOf(tile("only")), result.tiles)
    }

    @Test
    fun `rows and columns are updated not just tile count`() {
        val original = Board(rows = 2, columns = 2, tiles = (0 until 4).map { tile("t$it") })

        val result = original.resized(1, 8)

        assertEquals(1, result.rows)
        assertEquals(8, result.columns)
        assertEquals(8, result.tiles.size)
    }

    @Test
    fun `default boards are not equal because tile ids are random`() {
        assertNotEquals(Board(), Board())
    }
}
