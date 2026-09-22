package com.example.soundboard.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Whether the app follows the system's light/dark setting or is pinned to one. */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * One pad on the board. [fileName] points at a file inside the app's private
 * sounds directory, never at the URI the user originally picked.
 */
@Serializable
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val fileName: String? = null,
    val volume: Float = 1f,
    val colorArgb: Int? = null,
    /** Speak [label] aloud via on-device text-to-speech when there's no [fileName]. */
    val speakLabel: Boolean = false,
    /** Longer text to speak instead of [label] — most scripts read better than the short name shown on the tile. Null falls back to [label]. */
    val ttsScript: String? = null
) {
    val isEmpty: Boolean get() = fileName == null && !speakLabel

    /** What TTS actually says for this tile: [ttsScript] when set, otherwise [label]. */
    val speechText: String get() = ttsScript?.takeIf { it.isNotBlank() } ?: label
}

/** One grid of tiles within a [Board]; a board can have several, switched via tabs. */
@Serializable
data class Page(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Page 1",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = List(16) { Tile() },
    /** Card width:height, e.g. 1f for square or 4f/3f for wider-than-tall. */
    val tileAspectRatio: Float = 1f,
    /** Page-identity accent; null keeps today's neutral theme color. Overridden per-tile by [Tile.colorArgb]. */
    val color: Int? = null,
    /** Auto-return snaps back to whichever page has this set; at most one page should. */
    val isHome: Boolean = false
) {
    /** Tiles currently shown on the grid, in row-major order. */
    val visibleTiles: List<Tile> get() = tiles.take(rows * columns)

    /**
     * Changes the visible grid size without ever dropping a tile. Shrinking just
     * hides the trailing tiles — their sound stays assigned — and growing reveals
     * them again, only appending fresh empty tiles if the page has never been
     * this large. The only way to lose a tile's sound is clearing it directly.
     */
    fun resized(newRows: Int, newColumns: Int): Page {
        val target = newRows * newColumns
        val next = if (tiles.size < target) {
            tiles + List(target - tiles.size) { Tile() }
        } else {
            tiles
        }
        return copy(rows = newRows, columns = newColumns, tiles = next)
    }

    /**
     * Appends a fresh blank row once every tile in the last visible row is filled, so
     * there's always at least one blank tile to add a new pad to without opening Page
     * options first. Never fires on a page that's currently shrunk with hidden trailing
     * tiles ([tiles] longer than [rows] x [columns], see [resized]) — revealing those is
     * what a manual resize is for; auto-grow only ever adds a genuinely new row.
     */
    fun withAutoGrownTrailingRow(): Page {
        if (rows <= 0 || columns <= 0 || tiles.size > rows * columns) return this
        val lastRow = visibleTiles.takeLast(columns)
        return if (lastRow.size == columns && lastRow.none { it.isEmpty }) {
            resized(rows + 1, columns)
        } else {
            this
        }
    }

    /** Reorders the visible tiles by moving [fromIndex] to [toIndex]; hidden tiles are untouched. */
    fun moved(fromIndex: Int, toIndex: Int): Page {
        val visibleCount = rows * columns
        if (fromIndex !in 0 until visibleCount || toIndex !in 0 until visibleCount || fromIndex == toIndex) {
            return this
        }
        val visible = tiles.take(visibleCount).toMutableList()
        val tile = visible.removeAt(fromIndex)
        visible.add(toIndex, tile)
        return copy(tiles = visible + tiles.drop(visibleCount))
    }
}

@Serializable
data class Board(
    val name: String = "New Board",
    val pages: List<Page> = listOf(Page()),
    val currentPageIndex: Int = 0,
    // The settings below live on the board rather than in device SharedPreferences so they
    // travel with it — switching to a different person's preset switches these too.
    /** When on, the home page's first row shows fixed above every other page (never on the home page itself). */
    val stickyHomeRowEnabled: Boolean = false,
    /** Whether a fresh launch jumps straight to the home page instead of resuming the last-viewed one. */
    val openOnHomePage: Boolean = false,
    /** Minutes of inactivity before auto-returning to the home page; 0 disables auto-return. */
    val idleTimeoutMinutes: Int = 5,
    /** How long a page-tab press must be held before it counts as a long-press, in milliseconds. */
    val longPressDurationMillis: Int = 500,
    /** Whether to force light/dark or follow the system setting. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Whether to prevent the screen from auto-locking while the board is open. */
    val keepScreenAwake: Boolean = false,
    /** Whether long-press haptics (page-tab options, tile drag-reorder arm) fire. */
    val hapticFeedbackEnabled: Boolean = true,
    /** Whether a tile with no sound file speaks its label via TTS, even without [Tile.speakLabel] set. */
    val speakUnrecordedTilesEnabled: Boolean = true,
    /** Grid size a newly-added page starts at — it can still be resized individually afterward. */
    val defaultPageRows: Int = 4,
    val defaultPageColumns: Int = 4,
    /** Forward-looking marker for the on-disk schema shape; not branched on yet. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    val currentPage: Page get() = pages[currentPageIndex.coerceIn(pages.indices)]

    /** Index of the page auto-return snaps back to; null if no page is marked home. */
    val homePageIndex: Int? get() = pages.indexOfFirst { it.isHome }.takeIf { it >= 0 }

    /** The page auto-return snaps back to; null if no page is marked home. */
    val homePage: Page? get() = homePageIndex?.let { pages[it] }

    /** Whether any tile on any page has a sound or speech set up, for gating destructive replace actions. */
    val hasAnySound: Boolean get() = pages.any { page -> page.tiles.any { !it.isEmpty } }

    /** Applies [transform] to the current page only, leaving the rest of the board untouched. */
    fun updatingCurrentPage(transform: (Page) -> Page): Board =
        updatingPage(currentPageIndex.coerceIn(pages.indices), transform)

    /** Applies [transform] to the page at [index] only; a no-op if out of range. */
    fun updatingPage(index: Int, transform: (Page) -> Page): Board {
        if (index !in pages.indices) return this
        return copy(pages = pages.mapIndexed { i, page -> if (i == index) transform(page) else page })
    }

    /** Appends a new empty page and switches to it. */
    fun addPage(name: String = "Page ${pages.size + 1}"): Board {
        // Page's own tiles default (List(16)) is sized for its own 4x4 rows/columns default,
        // not necessarily this board's configured default — build tiles to actually match so
        // a larger default grid (e.g. 6x6) doesn't start with fewer tiles than cells.
        val newPage = Page(
            name = name,
            rows = defaultPageRows,
            columns = defaultPageColumns,
            tiles = List(defaultPageRows * defaultPageColumns) { Tile() }
        )
        return copy(pages = pages + newPage, currentPageIndex = pages.size)
    }

    /** Removes the page at [index]; a no-op if it's the only page left. */
    fun removePage(index: Int): Board {
        if (pages.size <= 1 || index !in pages.indices) return this
        val nextPages = pages.filterIndexed { i, _ -> i != index }
        return copy(
            pages = nextPages,
            currentPageIndex = currentPageIndex.coerceIn(nextPages.indices)
        )
    }

    fun renamePage(index: Int, name: String): Board {
        if (index !in pages.indices) return this
        val page = pages[index]
        val nextName = name.ifBlank { page.name }
        return copy(pages = pages.mapIndexed { i, p -> if (i == index) p.copy(name = nextName) else p })
    }

    /** Switches the active page; a no-op if [index] is out of range. */
    fun switchTo(index: Int): Board =
        if (index in pages.indices) copy(currentPageIndex = index) else this

    /** Marks [index] as the page auto-return snaps back to, and no other; a no-op if out of range. */
    fun withHomePage(index: Int): Board =
        if (index in pages.indices) {
            copy(pages = pages.mapIndexed { i, p -> p.copy(isHome = i == index) })
        } else {
            this
        }

    /** Clears the home page, disabling auto-return. */
    fun clearingHomePage(): Board = copy(pages = pages.map { it.copy(isHome = false) })

    /** Reorders pages by moving [fromIndex] to [toIndex]; the current and home page follow their page. */
    fun movedPage(fromIndex: Int, toIndex: Int): Board {
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) return this
        val nextPages = pages.toMutableList()
        val moving = nextPages.removeAt(fromIndex)
        nextPages.add(toIndex, moving)

        fun remap(index: Int): Int = when {
            index == fromIndex -> toIndex
            fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
            fromIndex > toIndex && index in toIndex until fromIndex -> index + 1
            else -> index
        }

        return copy(
            pages = nextPages,
            currentPageIndex = remap(currentPageIndex)
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
