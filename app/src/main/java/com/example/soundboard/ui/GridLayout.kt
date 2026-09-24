package com.example.soundboard.ui

import kotlin.math.ceil

/**
 * How many columns to lay a landscape grid out in, wide enough to show
 * [targetRows] full rows in [heightPx] without forcing tiles narrower than
 * [minTileWidthPx] (a touch-target floor). Never returns fewer than
 * [baseColumns] — landscape only ever adds columns, never removes them.
 */
internal fun landscapeColumnCount(
    baseColumns: Int,
    aspectRatio: Float,
    widthPx: Float,
    heightPx: Float,
    spacingPx: Float,
    targetRows: Int = 4,
    minTileWidthPx: Float = 0f
): Int {
    if (baseColumns <= 0 || widthPx <= 0f || heightPx <= 0f || targetRows <= 0) return baseColumns
    val maxTileHeight = (heightPx - (targetRows - 1) * spacingPx) / targetRows
    if (maxTileHeight <= 0f) return baseColumns
    val maxTileWidthForRows = maxTileHeight * aspectRatio
    val columnsForRows = ceil((widthPx + spacingPx) / (maxTileWidthForRows + spacingPx)).toInt()
    val columnsForMinWidth = if (minTileWidthPx > 0f) {
        ((widthPx + spacingPx) / (minTileWidthPx + spacingPx)).toInt()
    } else {
        Int.MAX_VALUE
    }
    return minOf(columnsForRows, columnsForMinWidth).coerceAtLeast(baseColumns)
}

/**
 * Height of one tile row on a portrait grid [portraitGridWidthPx] wide, split into
 * [columns] columns of [aspectRatio] (width:height) tiles. Landscape's page grid uses
 * this so rows keep their portrait height rather than following the landscape tiles'
 * own (different) width.
 */
internal fun portraitRowHeightPx(
    portraitGridWidthPx: Float,
    columns: Int,
    aspectRatio: Float,
    spacingPx: Float
): Float {
    if (columns <= 0 || portraitGridWidthPx <= 0f || aspectRatio <= 0f) return 0f
    val tileWidth = (portraitGridWidthPx - spacingPx * (columns - 1)) / columns
    return (tileWidth / aspectRatio).coerceAtLeast(0f)
}

/** Columns whose portrait row height counts as the standard [com.example.soundboard.model.RowHeight] measures against. */
internal const val STANDARD_ROW_COLUMNS = 4

/**
 * A page's row height: the portrait row height its own [columns] and [aspectRatio] give
 * (see [portraitRowHeightPx]), capped at [maxScale] times the standard — the height a
 * [STANDARD_ROW_COLUMNS]-column row of the same shape would have — so a 1- or 2-column
 * page gets full-width bars instead of huge squares.
 */
internal fun cappedRowHeightPx(
    portraitGridWidthPx: Float,
    columns: Int,
    aspectRatio: Float,
    spacingPx: Float,
    maxScale: Float
): Float {
    val natural = portraitRowHeightPx(portraitGridWidthPx, columns, aspectRatio, spacingPx)
    val standard = portraitRowHeightPx(portraitGridWidthPx, STANDARD_ROW_COLUMNS, aspectRatio, spacingPx)
    return minOf(natural, standard * maxScale)
}

/**
 * The tile index a drag lands on after moving [rowDelta] rows and [colDelta] grid columns
 * from [index]. The first [pinnedCount] tiles form their own pinned row above rows of
 * [columns] (0 for a plain grid). The pinned row can hold fewer, wider tiles than the rows
 * under it (landscape), so a column is carried between rows as a fraction of the width
 * rather than as a raw index: a tile dragged straight down lands under where it was.
 */
internal fun dragTargetIndex(
    index: Int,
    rowDelta: Int,
    colDelta: Int,
    columns: Int,
    pinnedCount: Int,
    lastIndex: Int
): Int {
    if (columns <= 0 || lastIndex < 0) return index
    fun rowOf(i: Int) = if (pinnedCount > 0) {
        if (i < pinnedCount) 0 else 1 + (i - pinnedCount) / columns
    } else {
        i / columns
    }
    fun rowStart(row: Int) = if (pinnedCount > 0) {
        if (row == 0) 0 else pinnedCount + (row - 1) * columns
    } else {
        row * columns
    }
    fun rowLength(row: Int) = if (pinnedCount > 0 && row == 0) pinnedCount else columns

    val row = rowOf(index)
    val fraction = (index - rowStart(row) + 0.5f) / rowLength(row) + colDelta.toFloat() / columns
    val targetRow = (row + rowDelta).coerceAtLeast(0)
    val targetLength = rowLength(targetRow)
    val targetCol = kotlin.math.floor(fraction * targetLength).toInt().coerceIn(0, targetLength - 1)
    return (rowStart(targetRow) + targetCol).coerceIn(0, lastIndex)
}
