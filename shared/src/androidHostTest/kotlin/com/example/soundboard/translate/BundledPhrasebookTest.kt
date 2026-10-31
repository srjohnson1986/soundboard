package com.example.soundboard.translate

import androidx.compose.ui.test.ExperimentalTestApi
import com.example.soundboard.ui.UiTest
import com.example.soundboard.ui.runUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phrasebook file that ships in the app (#342), read the way the app reads it, as a Compose
 * resource. It is a [UiTest] only because on the Android host JVM a resource needs Robolectric's
 * context. It isn't in `commonTest`: the browser tests' server doesn't serve Compose resources, so there
 * the file can only be checked by running the web app (docs/ARCHITECTURE.md, "Filling in Spanish automatically").
 */
@OptIn(ExperimentalTestApi::class)
class BundledPhrasebookTest : UiTest() {

    @Test
    fun theBundledPhrasebookLoadsAndCoversTheBuiltInBoards() = runUiTest {
        val book = Phrasebook.bundledSpanish()

        assertTrue(book.size >= 100, "${book.size} phrases")
        assertEquals("Gracias", book.lookup("Thank you.", "es"))
        assertEquals("Agua, por favor", book.lookup("Water, please.", "es"))
        assertEquals("Llama al nueve uno uno", book.lookup("  CALL 911  ", "es"))
        assertEquals("Estoy bien", book.lookup("I'm okay", "es"))
        assertEquals("¿Dónde estás?", book.lookup("Where are you?", "es"))
        assertNull(book.lookup("A phrase nobody wrote", "es"))
    }
}
