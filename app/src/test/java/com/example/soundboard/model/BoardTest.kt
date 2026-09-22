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
    fun `stickyHomeRowEnabled and homePageIndex default to false and null`() {
        val board = Board()

        assertEquals(false, board.stickyHomeRowEnabled)
        assertEquals(null, board.homePageIndex)
    }

    @Test
    fun `speakUnrecordedTilesEnabled defaults to true`() {
        assertEquals(true, Board().speakUnrecordedTilesEnabled)
    }

    @Test
    fun `homePage is null when no page is marked home`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        assertEquals(null, board.homePage)
    }

    @Test
    fun `homePage returns the page marked isHome`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B", isHome = true)))

        assertEquals("B", board.homePage?.name)
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
    fun `speechText falls back to the label when ttsScript is null`() {
        val tile = Tile(label = "Water")

        assertEquals("Water", tile.speechText)
    }

    @Test
    fun `speechText prefers ttsScript over the label when set`() {
        val tile = Tile(label = "Water", ttsScript = "I would like a glass of water please")

        assertEquals("I would like a glass of water please", tile.speechText)
    }

    @Test
    fun `speechText falls back to the label when ttsScript is blank`() {
        val tile = Tile(label = "Water", ttsScript = "   ")

        assertEquals("Water", tile.speechText)
    }

}
