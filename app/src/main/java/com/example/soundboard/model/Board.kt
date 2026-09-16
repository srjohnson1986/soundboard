package com.example.soundboard.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One pad on the board. [fileName] points at a file inside the app's private
 * sounds directory, never at the URI the user originally picked.
 */
@Serializable
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val fileName: String? = null
) {
    val isEmpty: Boolean get() = fileName == null
}

@Serializable
data class Board(
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = List(16) { Tile() }
) {
    /** Tiles currently shown on the grid, in row-major order. */
    val visibleTiles: List<Tile> get() = tiles.take(rows * columns)

    /**
     * Changes the visible grid size without ever dropping a tile. Shrinking just
     * hides the trailing tiles — their sound stays assigned — and growing reveals
     * them again, only appending fresh empty tiles if the board has never been
     * this large. The only way to lose a tile's sound is clearing it directly.
     */
    fun resized(newRows: Int, newColumns: Int): Board {
        val target = newRows * newColumns
        val next = if (tiles.size < target) {
            tiles + List(target - tiles.size) { Tile() }
        } else {
            tiles
        }
        return copy(rows = newRows, columns = newColumns, tiles = next)
    }
}
