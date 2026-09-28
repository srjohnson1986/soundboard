package com.example.soundboard.data

import kotlinx.coroutines.test.runTest
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The built-in boards the app actually ships, imported through the real Android storage: the
 * APK's assets, java.util.zip and the files directory. Guards against a zip and the app's Board
 * schema drifting apart. [BoardRepository]'s own logic is tested in the shared module.
 */
@RunWith(RobolectricTestRunner::class)
class ShippedBoardsTest {

    private lateinit var context: android.content.Context
    private lateinit var repo: BoardRepository
    private lateinit var boardFile: File
    private lateinit var soundsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = BoardRepository(context)
        boardFile = File(context.filesDir, "board.json")
        soundsDir = File(context.filesDir, "sounds")
    }

    private fun soundFile(name: String) = File(soundsDir, name)

    @Test
    fun `the bundled jeremy-care-board preset imports and loads as a valid three-page board`() = runTest {
        // Regression coverage for the actual shipped preset (presets/jeremy-care-board.zip,
        // mirrored at app/src/main/assets/jeremy-care-board.zip) — guards against the zip
        // and the app's Board schema drifting apart silently.
        val imported = repo.importFromAsset("jeremy-care-board.zip")

        assertTrue(imported)
        val board = repo.load()

        assertEquals("Jeremy Draft Care Board", board.name)
        assertEquals(listOf("Trouble", "Needs", "Talking"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertTrue(board.stickyHomeRowEnabled)
        // The home page carries an extra sticky first row (4 tiles), so it's one row taller.
        board.pages.forEachIndexed { index, page ->
            if (index == board.homePageIndex) {
                assertEquals(28, page.visibleTiles.size)
                assertEquals(7, page.rows)
            } else {
                assertEquals(24, page.visibleTiles.size)
                assertEquals(6, page.rows)
            }
            assertEquals(4, page.columns)
        }
        // Every referenced sound file actually landed in app storage.
        val allTiles = board.pages.flatMap { it.tiles }
        allTiles.mapNotNull { it.fileName }.forEach { name ->
            assertTrue("missing sound file $name", soundFile(name).exists())
        }
        assertEquals("chime.wav", allTiles.first { it.label == "Chime" }.fileName)
    }

    @Test
    fun `the bundled tts-care-board preset imports with speech instead of recordings`() = runTest {
        // Same layout as jeremy-care-board.zip, but every labeled tile speaks
        // instead of playing a recording — no sounds/ directory at all.
        val imported = repo.importFromAsset("tts-care-board.zip")

        assertTrue(imported)
        val board = repo.load()

        assertEquals("TTS Care Board", board.name)
        assertEquals(listOf("Trouble", "Needs", "Talking"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertTrue(board.stickyHomeRowEnabled)

        val allTiles = board.pages.flatMap { it.tiles }
        val labeled = allTiles.filter { it.label.isNotBlank() }
        val blank = allTiles.filter { it.label.isBlank() }

        assertTrue("expected some labeled tiles", labeled.isNotEmpty())
        labeled.forEach { tile ->
            assertNull("expected no fileName on \"${tile.label}\"", tile.fileName)
            assertTrue("expected speakWhenNoSound on \"${tile.label}\"", tile.speakWhenNoSound)
        }
        blank.forEach { tile ->
            assertFalse("expected blank tile ${tile.id} to be empty", tile.hasSound)
        }

        // Most labeled tiles carry the actual sentence recorded for that clip, not
        // just their short label — see presets/README.md's "TTS Care Board" section.
        assertEquals("Water, please.", labeled.first { it.label == "Water" }.ttsScript)
        assertEquals("The medicine isn't working.", labeled.first { it.label == "Meds not working" }.ttsScript)
        // A few have no real sentence to assign and fall back to their plain label.
        assertNull(labeled.first { it.label == "Chime" }.ttsScript)
        assertNull(labeled.first { it.label == "Ha!" }.ttsScript)
    }

    @Test
    fun `the bundled sarah-care-board preset imports and loads as a valid three-page board`() = runTest {
        // Same layout as jeremy-care-board.zip, but recorded with ElevenLabs' Sarah
        // voice, and Trouble's two generic "Get ___" tiles are 5 named contacts instead.
        val imported = repo.importFromAsset("sarah-care-board.zip")

        assertTrue(imported)
        val board = repo.load()

        assertEquals("Sarah (ElevenLabs) Care Board", board.name)
        assertEquals(listOf("Trouble", "Needs", "Talking"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertTrue(board.stickyHomeRowEnabled)
        // Trouble's last row is now completely filled by the 5 new contact tiles, so
        // BoardRepository.load()'s auto-grow (Page.withAutoGrownTrailingRow) gives it
        // one extra blank row beyond jeremy-care-board.zip's plain 24/6 shape.
        board.pages.forEachIndexed { index, page ->
            when {
                index == board.homePageIndex -> {
                    assertEquals(28, page.visibleTiles.size)
                    assertEquals(7, page.rows)
                }
                page.name == "Trouble" -> {
                    assertEquals(28, page.visibleTiles.size)
                    assertEquals(7, page.rows)
                    assertTrue(page.visibleTiles.drop(24).none { it.hasSound })
                }
                else -> {
                    assertEquals(24, page.visibleTiles.size)
                    assertEquals(6, page.rows)
                }
            }
            assertEquals(4, page.columns)
        }
        val allTiles = board.pages.flatMap { it.tiles }
        allTiles.mapNotNull { it.fileName }.forEach { name ->
            assertTrue("missing sound file $name", soundFile(name).exists())
        }
        assertEquals("chime.wav", allTiles.first { it.label == "Chime" }.fileName)

        val trouble = board.pages.first { it.name == "Trouble" }
        assertEquals(
            listOf("Get nurse", "Get husband", "Get wife", "Get son", "Get daughter"),
            trouble.tiles.subList(19, 24).map { it.label }
        )
        assertEquals("Can you get the nurse?", trouble.tiles[19].ttsScript)

        // The recording script's no_2.wav ("Mm-mm") isn't in sarah-clips.zip, so that
        // one tile ships as a gap, same treatment as Talking's "Not that" elsewhere.
        val mmTile = allTiles.first { it.label == "Mm-mm" }
        assertNull(mmTile.fileName)
    }

    @Test
    fun `the steve-care-board preset imports and loads as a valid three-page board`() = runTest {
        // presets/steve-care-board.zip is debug-only now (app/src/debug/assets/), not
        // auto-loaded like jeremy-care-board.zip, so this exercises the same import
        // path a user tapping Import would, via a fake content:// uri pointed at the
        // checked-in zip.
        val zip = File("../presets/steve-care-board.zip")
        assertTrue("expected ${zip.absolutePath} to exist", zip.exists())
        val uri = Uri.parse("content://fake/steve-care-board.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, zip.inputStream())

        val imported = repo.importFrom(UriPickedFile(context, uri))

        assertTrue(imported)
        val board = repo.load()

        assertEquals(listOf("Trouble", "Needs", "Talking"), board.pages.map { it.name })
        assertEquals(1, board.homePageIndex)
        assertTrue(board.stickyHomeRowEnabled)
        board.pages.forEachIndexed { index, page ->
            if (index == board.homePageIndex) {
                assertEquals(28, page.visibleTiles.size)
                assertEquals(7, page.rows)
            } else {
                assertEquals(24, page.visibleTiles.size)
                assertEquals(6, page.rows)
            }
            assertEquals(4, page.columns)
        }
        val allTiles = board.pages.flatMap { it.tiles }
        allTiles.mapNotNull { it.fileName }.forEach { name ->
            assertTrue("missing sound file $name", soundFile(name).exists())
        }
        assertEquals("chime.wav", allTiles.first { it.label == "Chime" }.fileName)
    }
}
