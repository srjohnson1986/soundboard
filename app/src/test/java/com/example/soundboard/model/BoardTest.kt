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
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B")),
            homePageIndex = 1
        )

        val result = board.removePage(1)

        assertEquals(null, result.homePageIndex)
    }

    @Test
    fun `removePage shifts the home index down when a page before it is removed`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            homePageIndex = 2
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
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            homePageIndex = 0
        )

        val result = board.movedPage(0, 2)

        assertEquals("A", result.pages[result.homePageIndex!!].name)
    }
}
