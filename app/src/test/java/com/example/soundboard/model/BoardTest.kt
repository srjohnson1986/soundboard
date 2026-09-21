package com.example.soundboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BoardTest {

    @Test
    fun `default boards are not equal because tile ids are random`() {
        assertNotEquals(Board(), Board())
    }

    @Test
    fun `addPage appends a page and switches to it`() {
        val board = Board(pages = listOf(Page(name = "First")))

        val result = board.addPage("Second")

        assertEquals(listOf("First", "Second"), result.pages.map { it.name })
        assertEquals(1, result.currentPageIndex)
    }

    @Test
    fun `addPage defaults to a numbered name when none is given`() {
        val board = Board(pages = listOf(Page(name = "First")))

        val result = board.addPage()

        assertEquals("Page 2", result.pages[1].name)
    }

    @Test
    fun `addPage uses the board's configured default grid size, tiles included`() {
        // 6x6 = 36 tiles, deliberately more than Page's own 16-tile default — a naive
        // Page(rows=6, columns=6) with no explicit tiles would under-fill the grid.
        val board = Board(
            pages = listOf(Page(name = "First")),
            defaultPageRows = 6,
            defaultPageColumns = 6
        )

        val result = board.addPage("Second")

        val added = result.pages[1]
        assertEquals(6, added.rows)
        assertEquals(6, added.columns)
        assertEquals(36, added.tiles.size)
        assertEquals(36, added.visibleTiles.size)
    }

    @Test
    fun `removePage is a no-op when only one page remains`() {
        val board = Board(pages = listOf(Page(name = "Only")))

        val result = board.removePage(0)

        assertEquals(board, result)
    }

    @Test
    fun `removePage drops the page and clamps the current index`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            currentPageIndex = 2
        )

        val result = board.removePage(2)

        assertEquals(listOf("A", "B"), result.pages.map { it.name })
        assertEquals(1, result.currentPageIndex)
    }

    @Test
    fun `renamePage updates only the target page`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.renamePage(1, "Renamed")

        assertEquals("A", result.pages[0].name)
        assertEquals("Renamed", result.pages[1].name)
    }

    @Test
    fun `renamePage falls back to the existing name when given a blank name`() {
        val board = Board(pages = listOf(Page(name = "A")))

        val result = board.renamePage(0, "   ")

        assertEquals("A", result.pages[0].name)
    }

    @Test
    fun `switchTo changes the current page index`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.switchTo(1)

        assertEquals(1, result.currentPageIndex)
    }

    @Test
    fun `switchTo out of range is a no-op`() {
        val board = Board(pages = listOf(Page(name = "A")))

        val result = board.switchTo(5)

        assertEquals(board, result)
    }

    @Test
    fun `updatingCurrentPage transforms only the current page`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B")),
            currentPageIndex = 1
        )

        val result = board.updatingCurrentPage { it.copy(name = "Changed") }

        assertEquals("A", result.pages[0].name)
        assertEquals("Changed", result.pages[1].name)
    }

    @Test
    fun `withHomePage sets the home index`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.withHomePage(1)

        assertEquals(1, result.homePageIndex)
    }

    @Test
    fun `withHomePage out of range is a no-op`() {
        val board = Board(pages = listOf(Page(name = "A")))

        val result = board.withHomePage(5)

        assertEquals(board, result)
    }

    @Test
    fun `removePage clears the home index when the home page itself is removed`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B", isHome = true)))

        val result = board.removePage(1)

        assertEquals(null, result.homePageIndex)
    }

    @Test
    fun `removePage shifts the home index down when a page before it is removed`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C", isHome = true))
        )

        val result = board.removePage(0)

        assertEquals(1, result.homePageIndex)
    }

    @Test
    fun `pinnedTiles and homePageIndex default to empty and null`() {
        val board = Board()

        assertEquals(emptyList<Tile>(), board.pinnedTiles)
        assertEquals(null, board.homePageIndex)
    }

    @Test
    fun `movedPage reorders pages`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")))

        val result = board.movedPage(0, 2)

        assertEquals(listOf("B", "C", "A"), result.pages.map { it.name })
    }

    @Test
    fun `movedPage out of range is a no-op`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.movedPage(0, 5)

        assertEquals(board, result)
    }

    @Test
    fun `movedPage keeps the current page selected as it shifts left`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            currentPageIndex = 2
        )

        val result = board.movedPage(0, 2)

        assertEquals("C", result.pages[result.currentPageIndex].name)
    }

    @Test
    fun `movedPage keeps the current page selected as it shifts right`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            currentPageIndex = 0
        )

        val result = board.movedPage(0, 2)

        assertEquals("A", result.pages[result.currentPageIndex].name)
    }

    @Test
    fun `movedPage follows the home page along with its page`() {
        val board = Board(
            pages = listOf(Page(name = "A", isHome = true), Page(name = "B"), Page(name = "C"))
        )

        val result = board.movedPage(0, 2)

        assertEquals("A", result.pages[result.homePageIndex!!].name)
    }

    @Test
    fun `hasAnySound is false for an all-empty board`() {
        val board = Board(pages = listOf(Page(rows = 1, columns = 2, tiles = listOf(Tile(), Tile()))))

        assertEquals(false, board.hasAnySound)
    }

    @Test
    fun `hasAnySound is true when a page tile has a sound`() {
        val board = Board(
            pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(fileName = "a.mp3"))))
        )

        assertEquals(true, board.hasAnySound)
    }

    @Test
    fun `hasAnySound is true when only a pinned tile has a sound`() {
        val board = Board(
            pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile()))),
            pinnedTiles = listOf(Tile(fileName = "hey.mp3"))
        )

        assertEquals(true, board.hasAnySound)
    }

    @Test
    fun `addingPinnedRow materializes an empty row at pinnedRowSize`() {
        val board = Board(pinnedRowSize = 3)

        val result = board.addingPinnedRow()

        assertEquals(3, result.pinnedTiles.size)
        assertEquals(true, result.pinnedTiles.all { it.isEmpty })
    }

    @Test
    fun `addingPinnedRow is a no-op once a row already exists`() {
        val board = Board(pinnedTiles = listOf(Tile(id = "hey")))

        val result = board.addingPinnedRow()

        assertEquals(board, result)
    }

    @Test
    fun `resizedPinnedRow on an empty row just remembers the starting width`() {
        val board = Board()

        val result = board.resizedPinnedRow(6)

        assertEquals(6, result.pinnedRowSize)
        assertEquals(emptyList<Tile>(), result.pinnedTiles)
    }

    @Test
    fun `resizedPinnedRow growing an existing row appends empty tiles without touching the rest`() {
        val board = Board(pinnedTiles = listOf(Tile(id = "hey", label = "Hey")), pinnedRowSize = 1)

        val result = board.resizedPinnedRow(3)

        assertEquals(3, result.pinnedRowSize)
        assertEquals(3, result.pinnedTiles.size)
        assertEquals("Hey", result.pinnedTiles[0].label)
    }

    @Test
    fun `resizedPinnedRow shrinking hides trailing tiles without dropping them`() {
        val board = Board(
            pinnedTiles = listOf(Tile(id = "hey", fileName = "hey.mp3"), Tile(id = "sos", fileName = "sos.mp3")),
            pinnedRowSize = 2
        )

        val result = board.resizedPinnedRow(1)

        assertEquals(1, result.pinnedRowSize)
        assertEquals(listOf("hey", "sos"), result.pinnedTiles.map { it.id })
        assertEquals(listOf("hey"), result.pinnedVisibleTiles.map { it.id })
    }

    @Test
    fun `resizedPinnedRow growing again reveals a previously hidden tile`() {
        val board = Board(
            pinnedTiles = listOf(Tile(id = "hey", fileName = "hey.mp3"), Tile(id = "sos", fileName = "sos.mp3")),
            pinnedRowSize = 1
        )

        val result = board.resizedPinnedRow(2)

        assertEquals(listOf("hey", "sos"), result.pinnedVisibleTiles.map { it.id })
    }

    @Test
    fun `removingPinnedRow clears the row but keeps pinnedRowSize`() {
        val board = Board(pinnedTiles = listOf(Tile(id = "hey")), pinnedRowSize = 5)

        val result = board.removingPinnedRow()

        assertEquals(emptyList<Tile>(), result.pinnedTiles)
        assertEquals(5, result.pinnedRowSize)
    }

    @Test
    fun `pinnedVisibleTiles truncates to pinnedRowSize`() {
        val board = Board(
            pinnedTiles = listOf(Tile(id = "a"), Tile(id = "b"), Tile(id = "c")),
            pinnedRowSize = 2
        )

        assertEquals(listOf("a", "b"), board.pinnedVisibleTiles.map { it.id })
    }
}
