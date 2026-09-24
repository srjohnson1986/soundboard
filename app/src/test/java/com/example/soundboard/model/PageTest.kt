package com.example.soundboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTest {

    private fun tile(id: String, label: String = "") = Tile(id = id, label = label)

    @Test
    fun `growing keeps existing tiles and appends empty ones`() {
        val original = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val grown = original.withGridSize(4, 5)

        assertEquals(4, grown.rows)
        assertEquals(5, grown.columns)
        assertEquals(original.tiles, grown.tiles.take(16))
        assertEquals(20, grown.tiles.size)
        assertEquals(4, grown.tiles.drop(16).count { !it.hasSound })
    }

    @Test
    fun `shrinking keeps the first tiles in order and drops the rest`() {
        val original = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val shrunk = original.withGridSize(3, 3)

        assertEquals(3, shrunk.rows)
        assertEquals(3, shrunk.columns)
        // withGridSize() never drops tiles from the backing list, only the visible window.
        assertEquals(original.tiles, shrunk.tiles)
        assertEquals(original.tiles.take(9), shrunk.visibleTiles)
    }

    @Test
    fun `resizing to the same dimensions is a no-op`() {
        val original = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val result = original.withGridSize(4, 4)

        assertEquals(original, result)
    }

    @Test
    fun `1x1 page works`() {
        val original = Page(rows = 1, columns = 1, tiles = listOf(tile("only")))

        val result = original.withGridSize(1, 1)

        assertEquals(1, result.rows)
        assertEquals(1, result.columns)
        assertEquals(listOf(tile("only")), result.tiles)
    }

    @Test
    fun `rows and columns are updated not just tile count`() {
        val original = Page(rows = 2, columns = 2, tiles = (0 until 4).map { tile("t$it") })

        val result = original.withGridSize(1, 8)

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
        assertTrue(result.tiles.drop(2).none { it.hasSound })
    }

    @Test
    fun `landscape defaults to twice the columns and half the rows rounded up plus one`() {
        assertEquals(8, Page(rows = 4, columns = 4).effectiveLandscapeColumns)
        assertEquals(3, Page(rows = 4, columns = 4).configuredLandscapeRows)
        assertEquals(4, Page(rows = 5, columns = 4).configuredLandscapeRows)
        assertEquals(2, Page(rows = 1, columns = 1).configuredLandscapeRows)
        assertEquals(2, Page(rows = 1, columns = 1).effectiveLandscapeColumns)
    }

    @Test
    fun `landscape overrides replace the derived defaults`() {
        val page = Page(rows = 4, columns = 4, landscapeRows = 2, landscapeColumns = 5)

        assertEquals(5, page.effectiveLandscapeColumns)
        assertEquals(2, page.configuredLandscapeRows)
    }

    @Test
    fun `normalized pads the backing list to cover every landscape slot`() {
        val page = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val result = page.normalized()

        // 8 columns x 3 rows in landscape.
        assertEquals(24, result.tiles.size)
        assertEquals(page.tiles, result.tiles.take(16))
        assertEquals(24, result.landscapeTiles.size)
        assertEquals(16, result.visibleTiles.size)
    }

    @Test
    fun `a tile filled in a landscape-only slot grows the portrait rows to show it`() {
        val tiles = (0 until 24).map { if (it == 19) filledTile("t$it") else tile("t$it") }
        val page = Page(rows = 4, columns = 4, tiles = tiles)

        val result = page.normalized()

        assertEquals(5, result.rows)
        assertTrue(result.visibleTiles.any { it.id == "t19" })
    }

    @Test
    fun `shrinking never hides a tile with content`() {
        val tiles = (0 until 16).map { if (it == 13) filledTile("t$it") else tile("t$it") }
        val page = Page(rows = 4, columns = 4, tiles = tiles)

        val result = page.withGridSize(2, 4).normalized()

        assertEquals(4, result.rows)
        assertTrue(result.visibleTiles.any { it.id == "t13" })
    }

    @Test
    fun `a label still awaiting a recording counts as content`() {
        val tiles = (0 until 16).map { if (it == 13) tile("t$it", label = "Water") else tile("t$it") }

        val result = Page(rows = 4, columns = 4, tiles = tiles).withGridSize(2, 4).normalized()

        assertEquals(4, result.rows)
    }

    @Test
    fun `shrinking still hides trailing blank tiles`() {
        val page = Page(rows = 4, columns = 4, tiles = (0 until 16).map { tile("t$it") })

        val result = page.withGridSize(2, 4).normalized()

        assertEquals(2, result.rows)
        assertEquals(8, result.visibleTiles.size)
    }

    @Test
    fun `landscape rows extend past a too-small override to reach content`() {
        val tiles = (0 until 8).map { if (it == 5) filledTile("t$it") else tile("t$it") }
        val page = Page(rows = 4, columns = 2, tiles = tiles, landscapeRows = 1, landscapeColumns = 2)

        assertEquals(3, page.shownLandscapeRows)
        assertTrue(page.landscapeTiles.any { it.id == "t5" })
    }

    @Test
    fun `normalized adds a blank landscape row once an overridden landscape grid's last row is full`() {
        val tiles = listOf(filledTile("a"), filledTile("b"), tile("c"), tile("d"))
        val page = Page(rows = 2, columns = 2, tiles = tiles, landscapeRows = 1, landscapeColumns = 2)

        val result = page.normalized()

        assertEquals(2, result.landscapeRows)
        assertFalse(result.landscapeTiles.last().hasSound)
    }

    @Test
    fun `normalized is idempotent`() {
        val tiles = (0 until 24).map { if (it == 19) filledTile("t$it") else tile("t$it") }
        val once = Page(rows = 4, columns = 4, tiles = tiles).normalized()

        assertEquals(once, once.normalized())
    }

    @Test
    fun `withTileMoved can reach tiles past the portrait grid`() {
        val page = Page(rows = 1, columns = 2, tiles = (0 until 4).map { tile("t$it") })

        val result = page.withTileMoved(0, 3)

        assertEquals(listOf("t1", "t2", "t3", "t0"), result.tiles.map { it.id })
    }
}
