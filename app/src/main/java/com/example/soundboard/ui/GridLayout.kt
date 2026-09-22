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
