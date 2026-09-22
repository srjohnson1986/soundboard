package com.example.soundboard.data

import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PresetRepositoryTest {

    private lateinit var context: android.content.Context
    private lateinit var presetRepo: PresetRepository
    private lateinit var presetsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presetRepo = PresetRepository(context)
        presetsDir = File(context.filesDir, "presets")
    }

    private fun boardNamed(name: String, vararg tiles: Tile) = Board(
        name = name,
        pages = listOf(Page(rows = 1, columns = tiles.size.coerceAtLeast(1), tiles = tiles.toList()))
    )

    @Test
    fun `save then load round-trips the board`() {
        val board = boardNamed("My Layout", Tile(id = "a", label = "Hey", fileName = "a.mp3"))

        val id = presetRepo.save(board)
        val loaded = presetRepo.load(id)

        assertEquals(board, loaded)
    }

    @Test
    fun `load returns null for an id that was never saved`() {
        assertNull(presetRepo.load("does-not-exist"))
    }

    @Test
    fun `save then load round-trips a tile's ttsScript`() {
        val board = boardNamed("My Layout", Tile(id = "a", label = "Water", ttsScript = "I would like a glass of water please"))

        val id = presetRepo.save(board)
        val loaded = presetRepo.load(id)

        assertEquals("I would like a glass of water please", loaded?.currentPage?.tiles?.first { it.id == "a" }?.ttsScript)
    }

    @Test
    fun `list returns saved presets newest first`() {
        val firstId = presetRepo.save(boardNamed("First"))
        File(presetsDir, "$firstId.json").setLastModified(1_000L)
        val secondId = presetRepo.save(boardNamed("Second"))
        File(presetsDir, "$secondId.json").setLastModified(2_000L)

        val listed = presetRepo.list()

        assertEquals(listOf("Second", "First"), listed.map { it.name })
    }

    @Test
    fun `list skips a preset file that fails to decode rather than crashing`() {
        presetRepo.save(boardNamed("Good"))
        presetsDir.mkdirs()
        File(presetsDir, "corrupt.json").writeText("not json at all")

        val listed = presetRepo.list()

        assertEquals(listOf("Good"), listed.map { it.name })
    }

    @Test
    fun `allReferencedFileNames collects fileNames across every saved preset`() {
        presetRepo.save(boardNamed("A", Tile(id = "1", fileName = "one.mp3")))
        presetRepo.save(
            Board(
                name = "B",
                pages = listOf(
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "2", fileName = "two.mp3"))),
                    Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "hey", fileName = "hey.mp3")), isHome = true)
                )
            )
        )

        val referenced = presetRepo.allReferencedFileNames()

        assertEquals(setOf("one.mp3", "two.mp3", "hey.mp3"), referenced)
    }

    @Test
    fun `allReferencedFileNames is empty when nothing has been saved`() {
        assertTrue(presetRepo.allReferencedFileNames().isEmpty())
    }
}
