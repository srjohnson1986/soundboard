package com.example.soundboard.data

import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SavedBoardRepositoryTest {

    private val files = InMemoryFileStore()
    private val savedBoardRepo = SavedBoardRepository(files)

    private fun boardNamed(name: String, vararg tiles: Tile) = Board(
        name = name,
        pages = listOf(Page(rows = 1, columns = tiles.size.coerceAtLeast(1), tiles = tiles.toList()))
    )

    @Test
    fun `save then load round-trips the board with its ttsScript`() = runTest {
        val board = boardNamed(
            "My Layout",
            Tile(id = "a", label = "Hey", fileName = "a.mp3"),
            Tile(id = "b", label = "Water", ttsScript = "I would like a glass of water please")
        )

        val id = savedBoardRepo.save(board)

        assertEquals(board, savedBoardRepo.load(id))
    }

    @Test
    fun `load returns null for an id that was never saved`() = runTest {
        assertNull(savedBoardRepo.load("does-not-exist"))
    }

    @Test
    fun `list returns saved boards newest first`() = runTest {
        val first = savedBoardRepo.save(boardNamed("First"))
        val second = savedBoardRepo.save(boardNamed("Second"))

        val listed = savedBoardRepo.list()

        assertEquals(listOf("Second", "First"), listed.map { it.name })
        assertEquals(listOf(second, first), listed.map { it.id })
    }

    @Test
    fun `list skips a file that fails to decode rather than crashing`() = runTest {
        savedBoardRepo.save(boardNamed("Good"))
        files.put("presets/corrupt.json", "not json at all".encodeToByteArray())

        assertEquals(listOf("Good"), savedBoardRepo.list().map { it.name })
    }

    @Test
    fun `allReferencedFileNames collects sounds across every saved board and page`() = runTest {
        savedBoardRepo.save(boardNamed("A", Tile(id = "1", fileName = "one.mp3")))
        savedBoardRepo.save(
            Board(
                name = "B",
                pages = listOf(
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "2", fileName = "two.mp3"))),
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", fileName = "hey.mp3")), isHome = true)
                )
            )
        )

        assertEquals(setOf("one.mp3", "two.mp3", "hey.mp3"), savedBoardRepo.allReferencedFileNames())
    }

    @Test
    fun `allReferencedFileNames is empty when nothing has been saved`() = runTest {
        assertTrue(savedBoardRepo.allReferencedFileNames().isEmpty())
    }
}
