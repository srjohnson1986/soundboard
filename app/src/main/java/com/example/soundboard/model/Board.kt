package com.example.soundboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Whether the app follows the system's light/dark setting or is pinned to one. */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How pages lay out in landscape. [PAGE_GRID] uses each page's own landscape grid
 * ([Page.effectiveLandscapeColumns] x [Page.shownLandscapeRows]) with rows kept at
 * their portrait height; [FIT_TO_SCREEN] reflows the portrait tiles into however many
 * columns fit 4 rows on screen (the original landscape behavior).
 */
@Serializable
enum class LandscapeLayout { PAGE_GRID, FIT_TO_SCREEN }

/**
 * The tallest a tile row may get, as a multiple of the standard row height — the height
 * a 4-column row of the same tile shape would have in portrait. Rows are otherwise as
 * tall as their tiles' own width and shape make them, so this only bites on pages with
 * few columns (or, below [STANDARD], shortens every row). [UNLIMITED] never caps.
 */
@Serializable
enum class RowHeight(val maxScale: Float) {
    SHORT(0.75f),
    STANDARD(1f),
    TALL(1.5f),
    EXTRA_TALL(2f),
    UNLIMITED(Float.POSITIVE_INFINITY)
}

/** Typeface for tile labels. The two hyperlegible fonts are bundled (SIL OFL); the rest ship with Android. */
@Serializable
enum class LabelFont { DEFAULT, CONDENSED, SERIF, ATKINSON_HYPERLEGIBLE, LEXEND }

/**
 * How tile labels are drawn. Each grid uses one font size for all of its labels — the
 * largest in [minSizeSp]..[maxSizeSp] at which every label fits its tile — so labels grow
 * on big tiles (few columns) without one short label towering over its neighbors.
 */
@Serializable
data class LabelStyle(
    val minSizeSp: Float = 14f,
    val maxSizeSp: Float = 32f,
    val font: LabelFont = LabelFont.DEFAULT,
    val bold: Boolean = false,
    val allCaps: Boolean = false
) {
    companion object {
        /** The sizes Settings offers for either end of the range. */
        val SIZE_RANGE_SP = 10f..48f
    }
}

/**
 * A tile's border. [colorArgb] null means "Recommended" — resolved to the
 * current theme's outlineVariant color at render time rather than a fixed
 * value, so it looks right in both light and dark mode.
 */
@Serializable
data class TileBorder(
    val enabled: Boolean = false,
    val colorArgb: Int? = null,
    val widthDp: Float = 1f
)

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
    /**
     * Speak [speechText] aloud via on-device text-to-speech when there's no [fileName].
     * Stored as "speakLabel", its original name, so existing boards and presets still load.
     */
    @SerialName("speakLabel")
    val speakWhenNoSound: Boolean = false,
    /** Longer text to speak instead of [label] — most scripts read better than the short name shown on the tile. Null falls back to [label]. */
    val ttsScript: String? = null,
    /** Per-tile opacity override, 0..1; null inherits the page's, then the board's. */
    val opacity: Float? = null,
    /** Per-tile border override; null inherits the page's, then the board's. */
    val border: TileBorder? = null
) {
    /**
     * Whether tapping the tile makes a sound of some kind: it has a sound file, or it's set
     * to speak. A tile without one is "blank", even if it has a label still awaiting a
     * recording (see [hasContent]).
     */
    val hasSound: Boolean get() = fileName != null || speakWhenNoSound

    /** Whether the tile holds anything worth keeping on screen — a sound, speech, or even just a label still awaiting a recording. */
    val hasContent: Boolean get() = hasSound || label.isNotBlank()

    /** What TTS actually says for this tile: [ttsScript] when set, otherwise [label]. */
    val speechText: String get() = ttsScript?.takeIf { it.isNotBlank() } ?: label

    /**
     * Whether tapping this tile does anything: plays its clip, or speaks its [speechText]
     * when [speakWhenNoSound] is on or the board's [Board.speakUnrecordedTilesEnabled] fallback is.
     */
    fun isPlayable(speakUnrecordedTilesEnabled: Boolean): Boolean =
        fileName != null || ((speakWhenNoSound || speakUnrecordedTilesEnabled) && speechText.isNotBlank())
}

/** One grid of tiles within a [Board]; a board can have several, switched via tabs. */
@Serializable
data class Page(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Page 1",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = List(16) { Tile() },
    /** Card width:height: [SQUARE_TILE_ASPECT_RATIO], or [WIDE_TILE_ASPECT_RATIO] for wider-than-tall. */
    val tileAspectRatio: Float = SQUARE_TILE_ASPECT_RATIO,
    /** Page-identity accent; null keeps today's neutral theme color. Overridden per-tile by [Tile.colorArgb]. */
    val color: Int? = null,
    /** Auto-return snaps back to whichever page has this set; at most one page should. */
    val isHome: Boolean = false,
    /** Per-page opacity override, 0..1; null inherits the board's global setting. Overridden per-tile by [Tile.opacity]. */
    val opacity: Float? = null,
    /** Per-page border override; null inherits the board's global setting. Overridden per-tile by [Tile.border]. */
    val border: TileBorder? = null,
    /** Landscape grid rows; null derives it from [rows] (see [configuredLandscapeRows]). */
    val landscapeRows: Int? = null,
    /** Landscape grid columns; null derives it from [columns] (see [effectiveLandscapeColumns]). */
    val landscapeColumns: Int? = null,
    /** Per-page row height cap override; null inherits the board's [Board.rowHeight]. */
    val rowHeight: RowHeight? = null
) {
    /** Tiles currently shown on the portrait grid, in row-major order. */
    val visibleTiles: List<Tile> get() = tiles.take(rows * columns)

    /** Landscape columns: [landscapeColumns] when set, otherwise twice the portrait [columns]. */
    val effectiveLandscapeColumns: Int get() = landscapeColumns ?: defaultLandscapeColumns(columns)

    /**
     * Landscape rows before content is accounted for: [landscapeRows] when set, otherwise
     * half the portrait [rows] (rounded up) plus one — rows keep their portrait height in
     * landscape, so this roughly fills the shorter screen and always leaves spare blanks.
     */
    val configuredLandscapeRows: Int get() = landscapeRows ?: defaultLandscapeRows(rows)

    /** Rows landscape actually shows: [configuredLandscapeRows], extended so no tile with content is hidden. */
    val shownLandscapeRows: Int get() = maxOf(configuredLandscapeRows, rowsNeededFor(effectiveLandscapeColumns))

    /** Tiles shown on the landscape grid (under [LandscapeLayout.PAGE_GRID]), in row-major order. */
    val landscapeTiles: List<Tile> get() = tiles.take(shownLandscapeRows * effectiveLandscapeColumns)

    /** Whether any tile on this page has a sound or speech set up — see [Tile.hasSound]. */
    val hasAnySound: Boolean get() = tiles.any { it.hasSound }

    /** [tile]'s opacity: its own override, else this page's, else [boardOpacity]. */
    fun opacityFor(tile: Tile, boardOpacity: Float): Float = tile.opacity ?: opacity ?: boardOpacity

    /** [tile]'s border: its own override, else this page's, else [boardBorder]. */
    fun borderFor(tile: Tile, boardBorder: TileBorder): TileBorder = tile.border ?: border ?: boardBorder

    /** Rows needed at [gridColumns] wide to reach the last tile with a sound or label; 0 if there is none. */
    fun rowsNeededFor(gridColumns: Int): Int {
        if (gridColumns <= 0) return 0
        val last = tiles.indexOfLast { it.hasContent }
        return if (last < 0) 0 else last / gridColumns + 1
    }

    /**
     * Brings the page back in line with its layout rules after any change, so neither
     * orientation ever hides a tile with content: portrait [rows] grow to reach the last
     * such tile (e.g. one filled in a landscape-only slot), a completely filled last row
     * gets a fresh blank row after it (see [withAutoGrownTrailingRow]), and the backing
     * list is padded with blank tiles to cover every landscape slot too.
     */
    fun normalized(): Page {
        var page = this
        val needed = page.rowsNeededFor(page.columns)
        if (needed > page.rows) page = page.withGridSize(needed, page.columns)
        page = page.withAutoGrownTrailingRow()
        if (page.landscapeRows != null) {
            val landscapeColumns = page.effectiveLandscapeColumns
            val lastRow = page.landscapeTiles.takeLast(landscapeColumns)
            val lastRowFull = lastRow.size == landscapeColumns && lastRow.all { it.hasSound }
            page = page.copy(landscapeRows = page.shownLandscapeRows + if (lastRowFull) 1 else 0)
        }
        val slots = page.shownLandscapeRows * page.effectiveLandscapeColumns
        if (page.tiles.size < slots) {
            page = page.copy(tiles = page.tiles + List(slots - page.tiles.size) { Tile() })
        }
        return page
    }

    /**
     * Changes the portrait grid size without ever dropping a tile. Shrinking hides
     * trailing blank tiles, and growing reveals them again, only appending fresh empty
     * tiles if the page has never been this large. [normalized] then keeps the rows
     * from shrinking past any tile with content, so no sound is ever hidden.
     */
    fun withGridSize(newRows: Int, newColumns: Int): Page {
        val target = newRows * newColumns
        val next = if (tiles.size < target) {
            tiles + List(target - tiles.size) { Tile() }
        } else {
            tiles
        }
        return copy(rows = newRows, columns = newColumns, tiles = next)
    }

    /**
     * Appends a fresh blank row once every tile in the last visible portrait row is
     * filled, so there's always at least one blank tile to add a new pad to without
     * opening Page options first.
     */
    fun withAutoGrownTrailingRow(): Page {
        if (rows <= 0 || columns <= 0) return this
        val lastRow = visibleTiles.takeLast(columns)
        return if (lastRow.size == columns && lastRow.all { it.hasSound }) {
            withGridSize(rows + 1, columns)
        } else {
            this
        }
    }

    /**
     * Reorders tiles by moving [fromIndex] to [toIndex]. Indexes are into the full
     * backing list, since portrait and landscape each show a different-length prefix of it.
     */
    fun withTileMoved(fromIndex: Int, toIndex: Int): Page {
        if (fromIndex !in tiles.indices || toIndex !in tiles.indices || fromIndex == toIndex) return this
        val next = tiles.toMutableList()
        val tile = next.removeAt(fromIndex)
        next.add(toIndex, tile)
        return copy(tiles = next)
    }

    companion object {
        /** The default tile shape. */
        const val SQUARE_TILE_ASPECT_RATIO = 1f

        /** The "Wide" tile shape Grid size offers — roomier for two-line labels. */
        const val WIDE_TILE_ASPECT_RATIO = 4f / 3f

        /** Landscape columns for a page with [columns] portrait columns and no override. */
        fun defaultLandscapeColumns(columns: Int): Int = columns * 2

        /** Landscape rows for a page with [rows] portrait rows and no override: half (rounded up) plus one. */
        fun defaultLandscapeRows(rows: Int): Int = (rows + 1) / 2 + 1
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
    /** Whether a tile with no sound file speaks its label via TTS, even without [Tile.speakWhenNoSound] set. */
    val speakUnrecordedTilesEnabled: Boolean = true,
    /** Whether blank tiles are hidden (and untappable) outside of edit mode, to avoid a stray tap opening the editor. */
    val hideBlankTilesEnabled: Boolean = false,
    /** Solid background color behind the whole board; cleared whenever [backgroundImageFileName] is set. */
    val backgroundColorArgb: Int? = null,
    /** Background image file name (in the app's private backgrounds directory); takes precedence over [backgroundColorArgb] if somehow both are set. */
    val backgroundImageFileName: String? = null,
    /** Grid size a newly-added page starts at — it can still be resized individually afterward. */
    val defaultPageRows: Int = 4,
    val defaultPageColumns: Int = 4,
    /** Global tile opacity, 0..1; overridden per-page by [Page.opacity] and per-tile by [Tile.opacity]. */
    val tileOpacity: Float = 1f,
    /** Global tile border; overridden per-page by [Page.border] and per-tile by [Tile.border]. */
    val tileBorder: TileBorder = TileBorder(),
    /** How pages lay out in landscape: each page's own landscape grid, or auto-fit to the screen. */
    val landscapeLayout: LandscapeLayout = LandscapeLayout.PAGE_GRID,
    /** Global row height cap; overridden per-page by [Page.rowHeight]. */
    val rowHeight: RowHeight = RowHeight.STANDARD,
    /** Font, size range, weight and case for every tile label on the board. */
    val labelStyle: LabelStyle = LabelStyle(),
    /** Forward-looking marker for the on-disk schema shape; not branched on yet. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    val currentPage: Page get() = pages[currentPageIndex.coerceIn(pages.indices)]

    /** Index of the page auto-return snaps back to; null if no page is marked home. */
    val homePageIndex: Int? get() = pages.indexOfFirst { it.isHome }.takeIf { it >= 0 }

    /** The page auto-return snaps back to; null if no page is marked home. */
    val homePage: Page? get() = homePageIndex?.let { pages[it] }

    /** Whether any tile on any page has a sound or speech set up, for gating destructive replace actions. */
    val hasAnySound: Boolean get() = pages.any { it.hasAnySound }

    /**
     * Every sound file any tile on any page points at. The home row is just the home
     * page's first row, so it needs no separate pass.
     */
    val soundFileNames: Set<String>
        get() = pages.flatMap { it.tiles }.mapNotNull { it.fileName }.toSet()

    /** Every page brought in line with its layout rules; see [Page.normalized]. */
    fun normalized(): Board = copy(pages = pages.map { it.normalized() })

    /** Applies [transform] to the current page only, leaving the rest of the board untouched. */
    fun updatingCurrentPage(transform: (Page) -> Page): Board =
        updatingPage(currentPageIndex.coerceIn(pages.indices), transform)

    /** Applies [transform] to the page at [index] only; a no-op if out of range. */
    fun updatingPage(index: Int, transform: (Page) -> Page): Board {
        if (index !in pages.indices) return this
        return copy(pages = pages.mapIndexed { i, page -> if (i == index) transform(page) else page })
    }

    /** The tile with [tileId] on whichever page holds it; null if no page does. */
    fun findTile(tileId: String): Tile? =
        pages.firstNotNullOfOrNull { page -> page.tiles.firstOrNull { it.id == tileId } }

    /**
     * Applies [transform] to the tile with [tileId], on whichever page holds it. Tile ids
     * are unique across the board — the sticky home row shows the home page's own tiles
     * rather than copies — so this also edits a home-row tile tapped from another page.
     */
    fun updatingTile(tileId: String, transform: (Tile) -> Tile): Board =
        copy(pages = pages.map { page ->
            if (page.tiles.none { it.id == tileId }) page
            else page.copy(tiles = page.tiles.map { if (it.id == tileId) transform(it) else it })
        })

    /** Appends a new empty page and switches to it. */
    fun withPageAdded(name: String = "Page ${pages.size + 1}"): Board {
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
    fun withPageRemoved(index: Int): Board {
        if (pages.size <= 1 || index !in pages.indices) return this
        val nextPages = pages.filterIndexed { i, _ -> i != index }
        return copy(
            pages = nextPages,
            currentPageIndex = currentPageIndex.coerceIn(nextPages.indices)
        )
    }

    fun withPageRenamed(index: Int, name: String): Board {
        if (index !in pages.indices) return this
        val page = pages[index]
        val nextName = name.ifBlank { page.name }
        return copy(pages = pages.mapIndexed { i, p -> if (i == index) p.copy(name = nextName) else p })
    }

    /** Switches the active page; a no-op if [index] is out of range. */
    fun withCurrentPage(index: Int): Board =
        if (index in pages.indices) copy(currentPageIndex = index) else this

    /** Marks [index] as the page auto-return snaps back to, and no other; a no-op if out of range. */
    fun withHomePage(index: Int): Board =
        if (index in pages.indices) {
            copy(pages = pages.mapIndexed { i, p -> p.copy(isHome = i == index) })
        } else {
            this
        }

    /** Clears the home page, disabling auto-return. */
    fun withoutHomePage(): Board = copy(pages = pages.map { it.copy(isHome = false) })

    /** Reorders pages by moving [fromIndex] to [toIndex]; the current and home page follow their page. */
    fun withPageMoved(fromIndex: Int, toIndex: Int): Board {
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
