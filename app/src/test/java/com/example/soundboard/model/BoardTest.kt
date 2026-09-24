package com.example.soundboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    @Test
    fun `default boards are not equal because tile ids are random`() {
        assertNotEquals(Board(), Board())
    }

    @Test
    fun `withPageAdded appends a page and switches to it`() {
        val board = Board(pages = listOf(Page(name = "First")))

        val result = board.withPageAdded("Second")

        assertEquals(listOf("First", "Second"), result.pages.map { it.name })
        assertEquals(1, result.currentPageIndex)
    }

    @Test
    fun `withPageAdded defaults to a numbered name when none is given`() {
        val board = Board(pages = listOf(Page(name = "First")))

        val result = board.withPageAdded()

        assertEquals("Page 2", result.pages[1].name)
    }

    @Test
    fun `withPageAdded uses the board's configured default grid size, tiles included`() {
        // 6x6 = 36 tiles, deliberately more than Page's own 16-tile default — a naive
        // Page(rows=6, columns=6) with no explicit tiles would under-fill the grid.
        val board = Board(
            pages = listOf(Page(name = "First")),
            defaultPageRows = 6,
            defaultPageColumns = 6
        )

        val result = board.withPageAdded("Second")

        val added = result.pages[1]
        assertEquals(6, added.rows)
        assertEquals(6, added.columns)
        assertEquals(36, added.tiles.size)
        assertEquals(36, added.visibleTiles.size)
    }

    @Test
    fun `withPageRemoved is a no-op when only one page remains`() {
        val board = Board(pages = listOf(Page(name = "Only")))

        val result = board.withPageRemoved(0)

        assertEquals(board, result)
    }

    @Test
    fun `withPageRemoved drops the page and clamps the current index`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            currentPageIndex = 2
        )

        val result = board.withPageRemoved(2)

        assertEquals(listOf("A", "B"), result.pages.map { it.name })
        assertEquals(1, result.currentPageIndex)
    }

    @Test
    fun `withPageRenamed updates only the target page`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.withPageRenamed(1, "Renamed")

        assertEquals("A", result.pages[0].name)
        assertEquals("Renamed", result.pages[1].name)
    }

    @Test
    fun `withPageRenamed falls back to the existing name when given a blank name`() {
        val board = Board(pages = listOf(Page(name = "A")))

        val result = board.withPageRenamed(0, "   ")

        assertEquals("A", result.pages[0].name)
    }

    @Test
    fun `withCurrentPage changes the current page index`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.withCurrentPage(1)

        assertEquals(1, result.currentPageIndex)
    }

    @Test
    fun `withCurrentPage out of range is a no-op`() {
        val board = Board(pages = listOf(Page(name = "A")))

        val result = board.withCurrentPage(5)

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
    fun `updatingTile edits the tile on whichever page holds it`() {
        val board = Board(
            pages = listOf(
                Page(name = "A", tiles = listOf(Tile(id = "a", label = "old"))),
                Page(name = "B", tiles = listOf(Tile(id = "b", label = "old")))
            ),
            currentPageIndex = 0
        )

        val result = board.updatingTile("b") { it.copy(label = "new") }

        assertEquals("old", result.pages[0].tiles[0].label)
        assertEquals("new", result.pages[1].tiles[0].label)
    }

    @Test
    fun `updatingTile with an unknown id leaves the board unchanged`() {
        val board = Board(pages = listOf(Page(tiles = listOf(Tile(id = "a")))))

        assertEquals(board, board.updatingTile("missing") { it.copy(label = "new") })
    }

    @Test
    fun `findTile finds a tile on any page and null for an unknown id`() {
        val board = Board(
            pages = listOf(
                Page(tiles = listOf(Tile(id = "a"))),
                Page(tiles = listOf(Tile(id = "b", label = "B")))
            )
        )

        assertEquals("B", board.findTile("b")?.label)
        assertEquals(null, board.findTile("missing"))
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
    fun `withPageRemoved clears the home index when the home page itself is removed`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B", isHome = true)))

        val result = board.withPageRemoved(1)

        assertEquals(null, result.homePageIndex)
    }

    @Test
    fun `withPageRemoved shifts the home index down when a page before it is removed`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C", isHome = true))
        )

        val result = board.withPageRemoved(0)

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
    fun `hideBlankTilesEnabled defaults to false`() {
        assertEquals(false, Board().hideBlankTilesEnabled)
    }

    @Test
    fun `background color and image default to null`() {
        assertEquals(null, Board().backgroundColorArgb)
        assertEquals(null, Board().backgroundImageFileName)
    }

    @Test
    fun `tileOpacity defaults to fully opaque`() {
        assertEquals(1f, Board().tileOpacity)
    }

    @Test
    fun `tile and page opacity overrides default to null (inherit)`() {
        assertEquals(null, Tile().opacity)
        assertEquals(null, Page().opacity)
    }

    @Test
    fun `tileBorder defaults to disabled`() {
        assertEquals(TileBorder(), Board().tileBorder)
        assertEquals(false, Board().tileBorder.enabled)
    }

    @Test
    fun `tile and page border overrides default to null (inherit)`() {
        assertEquals(null, Tile().border)
        assertEquals(null, Page().border)
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
    fun `withPageMoved reorders pages`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")))

        val result = board.withPageMoved(0, 2)

        assertEquals(listOf("B", "C", "A"), result.pages.map { it.name })
    }

    @Test
    fun `withPageMoved out of range is a no-op`() {
        val board = Board(pages = listOf(Page(name = "A"), Page(name = "B")))

        val result = board.withPageMoved(0, 5)

        assertEquals(board, result)
    }

    @Test
    fun `withPageMoved keeps the current page selected as it shifts left`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            currentPageIndex = 2
        )

        val result = board.withPageMoved(0, 2)

        assertEquals("C", result.pages[result.currentPageIndex].name)
    }

    @Test
    fun `withPageMoved keeps the current page selected as it shifts right`() {
        val board = Board(
            pages = listOf(Page(name = "A"), Page(name = "B"), Page(name = "C")),
            currentPageIndex = 0
        )

        val result = board.withPageMoved(0, 2)

        assertEquals("A", result.pages[result.currentPageIndex].name)
    }

    @Test
    fun `withPageMoved follows the home page along with its page`() {
        val board = Board(
            pages = listOf(Page(name = "A", isHome = true), Page(name = "B"), Page(name = "C"))
        )

        val result = board.withPageMoved(0, 2)

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

    @Test
    fun `isPlayable is true for a tile with a sound file`() {
        assertTrue(Tile(fileName = "a.mp3").isPlayable(speakUnrecordedTilesEnabled = false))
    }

    @Test
    fun `isPlayable speaks a labeled tile only when speakWhenNoSound or the board fallback is on`() {
        val unrecorded = Tile(label = "Water")
        assertTrue(unrecorded.isPlayable(speakUnrecordedTilesEnabled = true))
        assertFalse(unrecorded.isPlayable(speakUnrecordedTilesEnabled = false))
        assertTrue(unrecorded.copy(speakWhenNoSound = true).isPlayable(speakUnrecordedTilesEnabled = false))
    }

    @Test
    fun `isPlayable is false with nothing to say`() {
        assertFalse(Tile(label = "", speakWhenNoSound = true).isPlayable(speakUnrecordedTilesEnabled = true))
    }

    @Test
    fun `soundFileNames collects every page's sound files once`() {
        val board = Board(
            pages = listOf(
                Page(tiles = listOf(Tile(fileName = "a.mp3"), Tile(), Tile(fileName = "b.mp3"))),
                Page(tiles = listOf(Tile(fileName = "a.mp3")))
            )
        )

        assertEquals(setOf("a.mp3", "b.mp3"), board.soundFileNames)
    }

    @Test
    fun `opacityFor and borderFor prefer the tile, then the page, then the board`() {
        val boardBorder = TileBorder()
        val pageBorder = TileBorder(enabled = true, widthDp = 2f)
        val tileBorder = TileBorder(enabled = true, widthDp = 4f)
        val page = Page(opacity = 0.5f, border = pageBorder)

        assertEquals(0.2f, page.opacityFor(Tile(opacity = 0.2f), boardOpacity = 1f))
        assertEquals(0.5f, page.opacityFor(Tile(), boardOpacity = 1f))
        assertEquals(1f, Page().opacityFor(Tile(), boardOpacity = 1f))
        assertEquals(tileBorder, page.borderFor(Tile(border = tileBorder), boardBorder))
        assertEquals(pageBorder, page.borderFor(Tile(), boardBorder))
        assertEquals(boardBorder, Page().borderFor(Tile(), boardBorder))
    }
}
