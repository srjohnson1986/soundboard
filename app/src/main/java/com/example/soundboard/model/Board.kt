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
    /** Grows or trims the tile list to match rows x columns, keeping existing tiles in order. */
    fun resized(newRows: Int, newColumns: Int): Board {
        val target = newRows * newColumns
        val next = if (tiles.size < target) {
            tiles + List(target - tiles.size) { Tile() }
        } else {
            tiles.take(target)
        }
        return copy(rows = newRows, columns = newColumns, tiles = next)
    }
}
